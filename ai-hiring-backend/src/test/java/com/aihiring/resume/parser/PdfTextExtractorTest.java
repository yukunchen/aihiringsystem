package com.aihiring.resume.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PdfTextExtractorTest {
    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @TempDir
    Path tempDir;

    @Test
    void extract_shouldReturnTextFromPdf() throws IOException {
        Path pdfPath = TestFixtureGenerator.generatePdf(tempDir);
        String result;
        try (InputStream is = Files.newInputStream(pdfPath)) {
            result = extractor.extract(is);
        }
        assertTrue(result.contains("John Smith"));
        assertTrue(result.contains("Software Engineer"));
        assertTrue(result.contains("Java"));
    }
}
