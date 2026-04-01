package com.aihiring.matching;

import com.aihiring.job.JobDescriptionSavedEvent;
import com.aihiring.resume.ResumeRepository;
import com.aihiring.resume.ResumeStatus;
import com.aihiring.resume.ResumeUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiMatchingEventListener {

    private final AiMatchingClient client;
    private final ResumeRepository resumeRepository;

    @Async
    @EventListener
    public void onResumeUploaded(ResumeUploadedEvent event) {
        if (event.getRawText() == null) {
            log.debug("Skipping vectorization for resume {} — no raw text", event.getResumeId());
            return;
        }

        log.info("Vectorizing resume {}", event.getResumeId());
        boolean success = client.vectorizeResume(event.getResumeId(), event.getRawText());

        resumeRepository.findById(event.getResumeId()).ifPresent(resume -> {
            if (success) {
                resume.setStatus(ResumeStatus.AI_PROCESSED);
                log.info("Resume {} vectorized successfully, status → AI_PROCESSED", event.getResumeId());
            } else {
                resume.setStatus(ResumeStatus.VECTORIZATION_FAILED);
                log.warn("Resume {} vectorization failed, status → VECTORIZATION_FAILED", event.getResumeId());
            }
            resumeRepository.save(resume);
        });
    }

    @Async
    @EventListener
    public void onJobSaved(JobDescriptionSavedEvent event) {
        log.info("Vectorizing job {}", event.getJobId());
        client.vectorizeJob(
            event.getJobId(), event.getTitle(), event.getDescription(),
            event.getRequirements(), event.getSkills()
        );
    }
}
