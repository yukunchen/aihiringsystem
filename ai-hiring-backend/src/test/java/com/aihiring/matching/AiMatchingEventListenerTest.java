package com.aihiring.matching;

import com.aihiring.job.JobDescriptionSavedEvent;
import com.aihiring.resume.ResumeUploadedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMatchingEventListenerTest {

    @Mock AiMatchingClient client;
    @InjectMocks AiMatchingEventListener listener;

    @Test
    void onResumeUploaded_callsVectorizeResume() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, "John Smith resume", "txt");

        listener.onResumeUploaded(event);

        verify(client).vectorizeResume(resumeId, "John Smith resume");
    }

    @Test
    void onResumeUploaded_withNullText_skipsVectorize() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, null, "pdf");

        listener.onResumeUploaded(event);

        verify(client, never()).vectorizeResume(any(), any());
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
