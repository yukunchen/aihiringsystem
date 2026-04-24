package com.aihiring.matching;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.matching.dto.*;
import com.aihiring.resume.Resume;
import com.aihiring.resume.ResumeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final ResumeRepository resumeRepository;

    // Over-fetch candidates from AI service so orphan-vector filtering (resumes present
    // in Qdrant but no longer in DB) still leaves enough live matches to fill top_k.
    // See issue #147.
    static final int ORPHAN_OVERFETCH_MULTIPLIER = 3;
    static final int ORPHAN_OVERFETCH_CAP = 50;

    @PostMapping
    @PreAuthorize("hasAuthority('job:read')")
    public ApiResponse<MatchResponse> match(@Valid @RequestBody MatchRequest request) {
        int requestedTopK = request.getTopK();
        int fetchTopK = Math.min(requestedTopK * ORPHAN_OVERFETCH_MULTIPLIER, ORPHAN_OVERFETCH_CAP);
        if (fetchTopK < requestedTopK) {
            fetchTopK = requestedTopK;
        }
        AiMatchResponse aiResponse;
        try {
            aiResponse = matchService.match(request.getJobId(), fetchTopK);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(422, "Job not ready for matching, please try again shortly");
        } catch (ResourceAccessException e) {
            throw new BusinessException(503, "AI matching service unavailable");
        } catch (HttpServerErrorException e) {
            throw new BusinessException(502, "AI matching service error, please try again shortly");
        }

        List<AiMatchResultItem> aiResults = aiResponse == null || aiResponse.getResults() == null
            ? List.of() : aiResponse.getResults();

        // Look up candidate names for all matched resumes, and filter out stale
        // resume_ids (present in vector store but no longer in DB).
        Map<String, String> nameMap = new HashMap<>();
        List<UUID> resumeIds = new ArrayList<>();
        for (var item : aiResults) {
            try {
                resumeIds.add(UUID.fromString(item.getResumeId()));
            } catch (IllegalArgumentException ignored) { }
        }
        if (!resumeIds.isEmpty()) {
            resumeRepository.findAllById(resumeIds).forEach(resume -> {
                String name = resume.getCandidateName();
                if (name == null || name.isBlank()) {
                    name = resume.getFileName().replaceFirst("\\.[^.]+$", "");
                }
                nameMap.put(resume.getId().toString(), name);
            });
        }

        var results = aiResults.stream()
            .filter(item -> {
                // Drop UUID-form resume_ids that don't exist in the DB (stale vector entries).
                // Non-UUID ids are left alone for backwards compatibility with test fixtures.
                try {
                    UUID.fromString(item.getResumeId());
                    return nameMap.containsKey(item.getResumeId());
                } catch (IllegalArgumentException e) {
                    return true;
                }
            })
            .map(item -> new MatchResultItem(
                item.getResumeId(),
                nameMap.getOrDefault(item.getResumeId(), item.getResumeId().length() > 8 ? item.getResumeId().substring(0, 8) : item.getResumeId()),
                item.getVectorScore(), item.getLlmScore(),
                item.getReasoning(), item.getHighlights()
            ))
            .limit(requestedTopK)
            .collect(Collectors.toList());

        return ApiResponse.success(new MatchResponse(
            UUID.fromString(aiResponse.getJobId()), results
        ));
    }
}
