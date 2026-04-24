package com.aihiring.resume;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ResumeDeletedEvent extends ApplicationEvent {
    private final UUID resumeId;

    public ResumeDeletedEvent(Object source, UUID resumeId) {
        super(source);
        this.resumeId = resumeId;
    }
}
