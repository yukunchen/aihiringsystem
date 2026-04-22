package com.aihiring.matching;

import com.aihiring.job.JobDescriptionSavedEvent;
import com.aihiring.resume.Resume;
import com.aihiring.resume.ResumeRepository;
import com.aihiring.resume.ResumeStatus;
import com.aihiring.resume.ResumeUploadedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMatchingEventListenerTest {

    @Mock AiMatchingClient client;
    @Mock ResumeRepository resumeRepository;
    @InjectMocks AiMatchingEventListener listener;

    @Test
    void onResumeUploaded_success_updatesStatusToAiProcessed() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, "John Smith resume", "txt");
        Resume resume = new Resume();
        resume.setId(resumeId);
        resume.setStatus(ResumeStatus.TEXT_EXTRACTED);

        when(client.vectorizeResume(resumeId, "John Smith resume")).thenReturn(true);
        when(resumeRepository.findById(resumeId)).thenReturn(Optional.of(resume));

        listener.onResumeUploaded(event);

        assertThat(resume.getStatus()).isEqualTo(ResumeStatus.AI_PROCESSED);
        verify(resumeRepository).save(resume);
    }

    @Test
    void onResumeUploaded_failure_updatesStatusToVectorizationFailed() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, "John Smith resume", "txt");
        Resume resume = new Resume();
        resume.setId(resumeId);
        resume.setStatus(ResumeStatus.TEXT_EXTRACTED);

        when(client.vectorizeResume(resumeId, "John Smith resume")).thenReturn(false);
        when(resumeRepository.findById(resumeId)).thenReturn(Optional.of(resume));

        listener.onResumeUploaded(event);

        assertThat(resume.getStatus()).isEqualTo(ResumeStatus.VECTORIZATION_FAILED);
        verify(resumeRepository).save(resume);
    }

    @Test
    void onResumeUploaded_withNullText_skipsVectorize() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, null, "pdf");

        listener.onResumeUploaded(event);

        verify(client, never()).vectorizeResume(any(), any());
        verify(resumeRepository, never()).findById(any());
    }

    @Test
    void onResumeUploaded_resumeNotFoundInDb_doesNotThrow() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, "text", "txt");

        when(client.vectorizeResume(resumeId, "text")).thenReturn(true);
        when(resumeRepository.findById(resumeId)).thenReturn(Optional.empty());

        listener.onResumeUploaded(event);

        verify(resumeRepository, never()).save(any());
    }

    @Test
    void onResumeUploaded_unexpectedExceptionFromClient_marksVectorizationFailed() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, "text", "txt");
        Resume resume = new Resume();
        resume.setId(resumeId);
        resume.setStatus(ResumeStatus.TEXT_EXTRACTED);

        when(client.vectorizeResume(resumeId, "text"))
            .thenThrow(new RuntimeException("boom"));
        when(resumeRepository.findById(resumeId)).thenReturn(Optional.of(resume));

        listener.onResumeUploaded(event);

        assertThat(resume.getStatus()).isEqualTo(ResumeStatus.VECTORIZATION_FAILED);
        verify(resumeRepository).save(resume);
    }

    @Test
    void onJobSaved_callsVectorizeJob() {
        UUID jobId = UUID.randomUUID();
        var event = new JobDescriptionSavedEvent(
            this, jobId, "Engineer", "Build things", "5yr", "[\"Java\"]"
        );

        listener.onJobSaved(event);

        verify(client).vectorizeJob(jobId, "Engineer", "Build things", "5yr", "[\"Java\"]");
    }
}
