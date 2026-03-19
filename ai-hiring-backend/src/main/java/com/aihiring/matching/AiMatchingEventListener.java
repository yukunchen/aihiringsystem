package com.aihiring.matching;

import com.aihiring.job.JobDescriptionSavedEvent;
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

    @Async
    @EventListener
    public void onResumeUploaded(ResumeUploadedEvent event) {
        if (event.getRawText() == null) {
            log.debug("Skipping vectorization for resume {} — no raw text", event.getResumeId());
            return;
        }
        log.info("Vectorizing resume {}", event.getResumeId());
        client.vectorizeResume(event.getResumeId(), event.getRawText());
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
