package com.aihiring.matching;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.matching.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
        } catch (HttpServerErrorException e) {
            log.error("AI matching service returned server error for job {}: {}",
                    request.getJobId(), e.getMessage());
            throw new BusinessException(502, "AI matching service error");
        } catch (HttpClientErrorException e) {
            log.error("AI matching service returned client error for job {}: {}",
                    request.getJobId(), e.getMessage());
            throw new BusinessException(502, "AI matching service error");
        } catch (ResourceAccessException e) {
            throw new BusinessException(503, "AI matching service unavailable");
        }

        List<MatchResultItem> results = aiResponse.getResults() != null
            ? aiResponse.getResults().stream()
                .map(item -> new MatchResultItem(
                    item.getResumeId(), item.getVectorScore(), item.getLlmScore(),
                    item.getReasoning(), item.getHighlights()
                ))
                .collect(Collectors.toList())
            : Collections.emptyList();

        return ApiResponse.success(new MatchResponse(
            request.getJobId(), results
        ));
    }
}
