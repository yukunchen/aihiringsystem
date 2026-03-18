package com.aihiring.resume.parser;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class TxtTextExtractorTest {
    private final TxtTextExtractor extractor = new TxtTextExtractor();

    @Test
    void extract_shouldReturnTextContent() throws IOException {
        String content = "John Smith\nSoftware Engineer";
        var input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        String result = extractor.extract(input);
        assertEquals(content, result);
    }

    @Test
    void extract_emptyFile_shouldReturnEmptyString() throws IOException {
        var input = new ByteArrayInputStream(new byte[0]);
        String result = extractor.extract(input);
        assertEquals("", result);
    }
}
