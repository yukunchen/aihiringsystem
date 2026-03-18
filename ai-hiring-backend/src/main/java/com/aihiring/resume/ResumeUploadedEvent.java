package com.aihiring.resume;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ResumeUploadedEvent extends ApplicationEvent {
    private final UUID resumeId;
    private final String rawText;
    private final String fileType;

    public ResumeUploadedEvent(Object source, UUID resumeId, String rawText, String fileType) {
        super(source);
        this.resumeId = resumeId;
        this.rawText = rawText;
        this.fileType = fileType;
    }
}
