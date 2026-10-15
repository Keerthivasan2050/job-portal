package com.jobportal.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Component
public class TextExtractorUtil {

    private static final Logger logger = LoggerFactory.getLogger(TextExtractorUtil.class);

    public String extractText(String filePath, String fileType) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            logger.warn("File cannot be read at path: {}", filePath);
            return "";
        }

        String lowerType = (fileType != null ? fileType : "").toLowerCase();
        if (lowerType.contains("pdf") || filePath.toLowerCase().endsWith(".pdf")) {
            return extractFromPdf(file);
        } else if (lowerType.contains("docx") || filePath.toLowerCase().endsWith(".docx")) {
            return extractFromDocx(file);
        }

        logger.warn("Unsupported file format for text extraction: {}", filePath);
        return "";
    }

    private String extractFromPdf(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        } catch (IOException e) {
            logger.error("Error extracting text from PDF: {}", e.getMessage());
            return "Unable to extract text from scanned or encrypted PDF: " + e.getMessage();
        }
    }

    private String extractFromDocx(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            return text != null ? text.trim() : "";
        } catch (IOException e) {
            logger.error("Error extracting text from DOCX: {}", e.getMessage());
            return "Unable to extract text from DOCX document: " + e.getMessage();
        }
    }
}
