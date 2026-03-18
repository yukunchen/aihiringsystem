package com.aihiring.resume.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DocxTextExtractorTest {
    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @TempDir
    Path tempDir;

    @Test
    void extract_shouldReturnTextFromDocx() throws IOException {
        Path docxPath = TestFixtureGenerator.generateDocx(tempDir);
        String result;
        try (InputStream is = Files.newInputStream(docxPath)) {
            result = extractor.extract(is);
        }
        assertTrue(result.contains("John Smith"));
        assertTrue(result.contains("Software Engineer"));
        assertTrue(result.contains("Java"));
    }
}
