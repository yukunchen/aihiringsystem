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
import org.springframework.web.client.ResourceAccessException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final ResumeRepository resumeRepository;

    @PostMapping
    @PreAuthorize("hasAuthority('job:read')")
    public ApiResponse<MatchResponse> match(@Valid @RequestBody MatchRequest request) {
        AiMatchResponse aiResponse;
        try {
            aiResponse = matchService.match(request.getJobId(), request.getTopK());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(422, "Job not ready for matching, please try again shortly");
        } catch (ResourceAccessException e) {
            throw new BusinessException(503, "AI matching service unavailable");
        }

        // Look up candidate names for all matched resumes
        Map<String, String> nameMap = new HashMap<>();
        List<UUID> resumeIds = new ArrayList<>();
        for (var item : aiResponse.getResults()) {
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

        // Only include resumes that exist in the database (filter out stale vector store entries)
        var results = aiResponse.getResults().stream()
            .filter(item -> nameMap.containsKey(item.getResumeId()))
            .map(item -> new MatchResultItem(
                item.getResumeId(),
                nameMap.get(item.getResumeId()),
                item.getVectorScore(), item.getLlmScore(),
                item.getReasoning(), item.getHighlights()
            ))
            .collect(Collectors.toList());

        return ApiResponse.success(new MatchResponse(
            UUID.fromString(aiResponse.getJobId()), results
        ));
    }
}
