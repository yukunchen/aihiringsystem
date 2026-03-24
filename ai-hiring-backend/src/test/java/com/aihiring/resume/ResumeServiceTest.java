package com.aihiring.resume;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.resume.dto.UpdateStructuredRequest;
import com.aihiring.resume.parser.DocxTextExtractor;
import com.aihiring.resume.parser.PdfTextExtractor;
import com.aihiring.resume.parser.TxtTextExtractor;
import com.aihiring.resume.storage.FileStorageService;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeSearchRepository resumeSearchRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PdfTextExtractor pdfTextExtractor;
    @Mock private DocxTextExtractor docxTextExtractor;
    @Mock private TxtTextExtractor txtTextExtractor;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ResumeService resumeService;

    @Test
    void upload_withPdf_shouldStoreExtractTextAndSave() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "pdf bytes".getBytes());
        UUID userId = UUID.randomUUID();
        User user = new User(); user.setId(userId); user.setUsername("admin");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.store(eq(file), any(String.class))).thenReturn("/uploads/resumes/uuid.pdf");
        when(pdfTextExtractor.extract(any())).thenReturn("John Smith Software Engineer");
        when(resumeRepository.save(any(Resume.class))).thenAnswer(i -> { Resume r = i.getArgument(0); r.setId(UUID.randomUUID()); return r; });

        Resume result = resumeService.upload(file, ResumeSource.MANUAL, userId);

        assertNotNull(result.getId());
        assertEquals("resume.pdf", result.getFileName());
        assertEquals("/uploads/resumes/uuid.pdf", result.getFilePath());
        assertEquals("application/pdf", result.getFileType());
        assertEquals(ResumeStatus.TEXT_EXTRACTED, result.getStatus());
        assertEquals("John Smith Software Engineer", result.getRawText());
        verify(eventPublisher).publishEvent(any(ResumeUploadedEvent.class));
    }

    @Test
    void upload_withTextExtractionFailure_shouldSaveWithUploadedStatus() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "pdf bytes".getBytes());
        UUID userId = UUID.randomUUID();
        User user = new User(); user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.store(eq(file), any(String.class))).thenReturn("/uploads/resumes/uuid.pdf");
        when(pdfTextExtractor.extract(any())).thenThrow(new IOException("Parse error"));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(i -> { Resume r = i.getArgument(0); r.setId(UUID.randomUUID()); return r; });

        Resume result = resumeService.upload(file, ResumeSource.MANUAL, userId);

        assertEquals(ResumeStatus.UPLOADED, result.getStatus());
        assertNull(result.getRawText());
        verify(eventPublisher).publishEvent(any(ResumeUploadedEvent.class));
    }

    @Test
    void upload_withEmptyFile_shouldThrowBusinessException() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]);
        assertThrows(BusinessException.class, () -> resumeService.upload(file, ResumeSource.MANUAL, UUID.randomUUID()));
    }

    @Test
    void upload_withUnsupportedType_shouldThrowBusinessException() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.jpg", "image/jpeg", "image bytes".getBytes());
        assertThrows(BusinessException.class, () -> resumeService.upload(file, ResumeSource.MANUAL, UUID.randomUUID()));
    }

    @Test
    void upload_withFileOver10MB_shouldThrowBusinessException() {
        byte[] bigContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", bigContent);
        BusinessException ex = assertThrows(BusinessException.class,
            () -> resumeService.upload(file, ResumeSource.MANUAL, UUID.randomUUID()));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("10MB"));
    }

    @Test
    void getById_shouldReturnResume() {
        UUID id = UUID.randomUUID();
        Resume resume = new Resume(); resume.setId(id);
        when(resumeRepository.findById(id)).thenReturn(Optional.of(resume));
        Resume result = resumeService.getById(id);
        assertEquals(id, result.getId());
    }

    @Test
    void getById_notFound_shouldThrowException() {
        UUID id = UUID.randomUUID();
        when(resumeRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> resumeService.getById(id));
    }

    @Test
    void delete_shouldDeleteFileAndRecord() {
        UUID id = UUID.randomUUID();
        Resume resume = new Resume(); resume.setId(id); resume.setFilePath("/uploads/resumes/uuid.pdf");
        when(resumeRepository.findById(id)).thenReturn(Optional.of(resume));
        resumeService.delete(id);
        verify(fileStorageService).delete("/uploads/resumes/uuid.pdf");
        verify(resumeRepository).delete(resume);
    }

    @Test
    void updateStructured_shouldUpdateFieldsAndSetAiProcessed() {
        UUID id = UUID.randomUUID();
        Resume resume = new Resume(); resume.setId(id); resume.setStatus(ResumeStatus.TEXT_EXTRACTED);
        when(resumeRepository.findById(id)).thenReturn(Optional.of(resume));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(i -> i.getArgument(0));

        UpdateStructuredRequest request = new UpdateStructuredRequest();
        request.setCandidateName("John Smith");
        request.setCandidateEmail("john@example.com");
        request.setSkills("[\"Java\", \"Spring Boot\"]");

        Resume result = resumeService.updateStructured(id, request);

        assertEquals("John Smith", result.getCandidateName());
        assertEquals("john@example.com", result.getCandidateEmail());
        assertEquals("[\"Java\", \"Spring Boot\"]", result.getSkills());
        assertEquals(ResumeStatus.AI_PROCESSED, result.getStatus());
    }

    @Test
    void list_withSearchQuery_shouldDelegateToSearchRepository() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(resumeSearchRepository.search("Java", pageable)).thenReturn(new PageImpl<>(List.of()));
        resumeService.list("Java", null, null, pageable);
        verify(resumeSearchRepository).search("Java", pageable);
    }

    @Test
    void list_withSourceFilter_shouldFilterBySource() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(resumeRepository.findBySource(ResumeSource.MANUAL, pageable)).thenReturn(new PageImpl<>(List.of()));
        resumeService.list(null, ResumeSource.MANUAL, null, pageable);
        verify(resumeRepository).findBySource(ResumeSource.MANUAL, pageable);
    }

    @Test
    void list_withNoFilters_shouldReturnAll() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(resumeRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));
        resumeService.list(null, null, null, pageable);
        verify(resumeRepository).findAll(pageable);
    }
}
