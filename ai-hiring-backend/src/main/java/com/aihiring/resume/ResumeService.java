package com.aihiring.resume;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.resume.dto.UpdateStructuredRequest;
import com.aihiring.resume.parser.DocxTextExtractor;
import com.aihiring.resume.parser.PdfTextExtractor;
import com.aihiring.resume.parser.TextExtractor;
import com.aihiring.resume.parser.TxtTextExtractor;
import com.aihiring.resume.storage.FileStorageService;
import com.aihiring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeSearchRepository resumeSearchRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final DocxTextExtractor docxTextExtractor;
    private final TxtTextExtractor txtTextExtractor;
    private final ApplicationEventPublisher eventPublisher;

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final Map<String, String> TYPE_EXTENSIONS = Map.of(
        "application/pdf", "pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
        "text/plain", "txt"
    );

    @Transactional
    public Resume uploadSingle(MultipartFile file, ResumeSource source, UUID uploadedByUserId) throws IOException {
        validateFile(file);

        var user = userRepository.findById(uploadedByUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        byte[] fileBytes = file.getBytes();
        String fileHash = sha256Hex(fileBytes);
        resumeRepository.findFirstByFileHash(fileHash).ifPresent(existing -> {
            throw new DuplicateResumeException(existing.getId(), existing.getFileName());
        });

        String extension = TYPE_EXTENSIONS.get(file.getContentType());
        String storedName = UUID.randomUUID() + "." + extension;
        String storedPath = fileStorageService.store(file, storedName);

        String rawText = null;
        ResumeStatus status = ResumeStatus.UPLOADED;
        try {
            TextExtractor extractor = getExtractor(file.getContentType());
            rawText = extractor.extract(new ByteArrayInputStream(fileBytes));
            status = ResumeStatus.TEXT_EXTRACTED;
        } catch (Exception e) {
            log.warn("Text extraction failed for file: {}. Saving with UPLOADED status.", file.getOriginalFilename(), e);
        }

        Resume resume = new Resume();
        resume.setFileName(file.getOriginalFilename());
        resume.setFilePath(storedPath);
        resume.setFileSize(file.getSize());
        resume.setFileType(file.getContentType());
        resume.setFileHash(fileHash);
        resume.setRawText(rawText);
        resume.setSource(source);
        resume.setStatus(status);
        resume.setUploadedBy(user);

        resume = resumeRepository.save(resume);
        eventPublisher.publishEvent(new ResumeUploadedEvent(this, resume.getId(), rawText, file.getContentType()));
        return resume;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Transactional
    public Resume upload(MultipartFile file, ResumeSource source, UUID uploadedByUserId) throws IOException {
        return uploadSingle(file, source, uploadedByUserId);
    }

    public Resume getById(UUID id) {
        return resumeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
    }

    public Page<Resume> list(String search, ResumeSource source, ResumeStatus status, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return resumeSearchRepository.search(search, pageable);
        }
        if (source != null && status != null) {
            return resumeRepository.findBySourceAndStatus(source, status, pageable);
        }
        if (source != null) {
            return resumeRepository.findBySource(source, pageable);
        }
        if (status != null) {
            return resumeRepository.findByStatus(status, pageable);
        }
        return resumeRepository.findAll(pageable);
    }

    @Transactional
    public void delete(UUID id) {
        Resume resume = getById(id);
        fileStorageService.delete(resume.getFilePath());
        resumeRepository.delete(resume);
        eventPublisher.publishEvent(new ResumeDeletedEvent(this, id));
    }

    @Transactional
    public Resume updateStructured(UUID id, UpdateStructuredRequest request) {
        Resume resume = getById(id);
        if (request.getCandidateName() != null) resume.setCandidateName(request.getCandidateName());
        if (request.getCandidatePhone() != null) resume.setCandidatePhone(request.getCandidatePhone());
        if (request.getCandidateEmail() != null) resume.setCandidateEmail(request.getCandidateEmail());
        if (request.getEducation() != null) resume.setEducation(request.getEducation());
        if (request.getExperience() != null) resume.setExperience(request.getExperience());
        if (request.getProjects() != null) resume.setProjects(request.getProjects());
        if (request.getSkills() != null) resume.setSkills(request.getSkills());
        resume.setStatus(ResumeStatus.AI_PROCESSED);
        return resumeRepository.save(resume);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "File is empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessException(400, "Unsupported file type. Allowed: PDF, DOCX, TXT");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "File size exceeds 10MB limit");
        }
    }

    private TextExtractor getExtractor(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> pdfTextExtractor;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> docxTextExtractor;
            case "text/plain" -> txtTextExtractor;
            default -> throw new BusinessException(400, "Unsupported file type");
        };
    }
}
