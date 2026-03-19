package com.aihiring.job;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class JobDescriptionSavedEvent extends ApplicationEvent {
    private final UUID jobId;
    private final String title;
    private final String description;
    private final String requirements;
    private final String skills;

    public JobDescriptionSavedEvent(Object source, UUID jobId, String title,
                                     String description, String requirements, String skills) {
        super(source);
        this.jobId = jobId;
        this.title = title;
        this.description = description;
        this.requirements = requirements;
        this.skills = skills;
    }
}
