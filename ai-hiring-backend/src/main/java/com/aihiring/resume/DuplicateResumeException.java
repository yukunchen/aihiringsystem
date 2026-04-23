package com.aihiring.resume;

import com.aihiring.common.exception.BusinessException;
import lombok.Getter;

import java.util.UUID;

@Getter
public class DuplicateResumeException extends BusinessException {
    private final UUID existingResumeId;
    private final String existingFileName;

    public DuplicateResumeException(UUID existingResumeId, String existingFileName) {
        super(409, "Duplicate resume: identical file already exists");
        this.existingResumeId = existingResumeId;
        this.existingFileName = existingFileName;
    }
}
