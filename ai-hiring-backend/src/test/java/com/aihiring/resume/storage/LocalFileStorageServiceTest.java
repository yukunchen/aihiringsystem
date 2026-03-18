package com.aihiring.resume.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString());
    }

    @Test
    void store_shouldSaveFileAndReturnPath() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "PDF content".getBytes()
        );
        String storedPath = storageService.store(file, "test-uuid.pdf");
        assertTrue(Files.exists(Path.of(storedPath)));
        assertEquals("PDF content", Files.readString(Path.of(storedPath)));
    }

    @Test
    void load_shouldReturnFileResource() throws IOException {
        Path testFile = tempDir.resolve("resumes").resolve("test.pdf");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "test content");
        Resource resource = storageService.load(testFile.toString());
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void load_nonExistentFile_shouldReturnNonExistentResource() {
        Resource resource = storageService.load(tempDir.resolve("nonexistent.pdf").toString());
        assertFalse(resource.exists());
    }

    @Test
    void delete_shouldRemoveFile() throws IOException {
        Path testFile = tempDir.resolve("resumes").resolve("delete-me.pdf");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "to be deleted");
        storageService.delete(testFile.toString());
        assertFalse(Files.exists(testFile));
    }

    @Test
    void delete_nonExistentFile_shouldNotThrow() {
        assertDoesNotThrow(() ->
            storageService.delete(tempDir.resolve("nonexistent.pdf").toString())
        );
    }
}
