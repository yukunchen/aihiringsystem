package com.aihiring.resume.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStructuredRequest {
    private String candidateName;
    private String candidatePhone;
    private String candidateEmail;
    private String education;
    private String experience;
    private String projects;
    private String skills;
}
