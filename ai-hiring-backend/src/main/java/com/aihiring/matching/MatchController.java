package com.aihiring.matching;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.matching.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

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

        var results = aiResponse.getResults().stream()
            .map(item -> new MatchResultItem(
                item.getResumeId(), item.getVectorScore(), item.getLlmScore(),
                item.getReasoning(), item.getHighlights()
            ))
            .collect(Collectors.toList());

        return ApiResponse.success(new MatchResponse(
            UUID.fromString(aiResponse.getJobId()), results
        ));
    }
}
