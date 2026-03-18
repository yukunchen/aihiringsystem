package com.aihiring.resume.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestFixtureGenerator {

    private static final String SAMPLE_TEXT = "John Smith\nSoftware Engineer\nSkills: Java, Spring Boot";

    public static Path generatePdf(Path dir) throws IOException {
        Path path = dir.resolve("sample-resume.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(14.5f);
                cs.newLineAtOffset(50, 700);
                for (String line : SAMPLE_TEXT.split("\n")) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(path.toFile());
        }
        return path;
    }

    public static Path generateDocx(Path dir) throws IOException {
        Path path = dir.resolve("sample-resume.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream os = Files.newOutputStream(path)) {
            for (String line : SAMPLE_TEXT.split("\n")) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line);
            }
            doc.write(os);
        }
        return path;
    }

    public static String getSampleText() {
        return SAMPLE_TEXT;
    }
}
