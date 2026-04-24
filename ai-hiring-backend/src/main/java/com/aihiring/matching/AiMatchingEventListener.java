package com.aihiring.matching;

import com.aihiring.job.JobDescriptionSavedEvent;
import com.aihiring.resume.ResumeDeletedEvent;
import com.aihiring.resume.ResumeRepository;
import com.aihiring.resume.ResumeStatus;
import com.aihiring.resume.ResumeUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiMatchingEventListener {

    private final AiMatchingClient client;
    private final ResumeRepository resumeRepository;

    @Async
    @EventListener
    @Transactional
    public void onResumeUploaded(ResumeUploadedEvent event) {
        if (event.getRawText() == null) {
            log.debug("Skipping vectorization for resume {} — no raw text", event.getResumeId());
            return;
        }

        log.info("Vectorizing resume {}", event.getResumeId());
        boolean success;
        try {
            success = client.vectorizeResume(event.getResumeId(), event.getRawText());
        } catch (Throwable t) {
            log.error("Unexpected error vectorizing resume {}", event.getResumeId(), t);
            success = false;
        }

        ResumeStatus target = success ? ResumeStatus.AI_PROCESSED : ResumeStatus.VECTORIZATION_FAILED;
        markStatus(event.getResumeId(), target);
    }

    private void markStatus(java.util.UUID resumeId, ResumeStatus target) {
        try {
            resumeRepository.findById(resumeId).ifPresent(resume -> {
                resume.setStatus(target);
                resumeRepository.save(resume);
                log.info("Resume {} status → {}", resumeId, target);
            });
        } catch (Throwable t) {
            log.error("Failed to update resume {} status to {}", resumeId, target, t);
        }
    }

    @Async
    @EventListener
    public void onResumeDeleted(ResumeDeletedEvent event) {
        // Prevent orphan vectors: when a resume is deleted from the DB,
        // remove its Qdrant vector too. Otherwise stale vectors accumulate
        // and can crowd out live resumes in similarity search results.
        log.info("Deleting resume vector {}", event.getResumeId());
        try {
            client.deleteResumeVector(event.getResumeId());
        } catch (Throwable t) {
            log.error("Unexpected error deleting resume vector {}", event.getResumeId(), t);
        }
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
