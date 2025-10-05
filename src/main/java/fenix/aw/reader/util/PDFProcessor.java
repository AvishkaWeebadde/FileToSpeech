package fenix.aw.reader.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFProcessor
{

    private static final Logger logger = LoggerFactory.getLogger(PDFProcessor.class);

    public List<String> splitPdfIntoChunks(File pdfFile, int maxCharactersPerChunk) throws Exception {
        if (pdfFile == null) {
            throw new IllegalArgumentException("PDF file cannot be null");
        }
        if (!pdfFile.exists()) {
            throw new IOException("PDF file does not exist: " + pdfFile.getAbsolutePath());
        }
        if (maxCharactersPerChunk <= 0) {
            throw new IllegalArgumentException("Max characters per chunk must be positive");
        }

        logger.info("Splitting PDF into chunks: {}", pdfFile.getName());

        try (PDDocument document = PDDocument.load(pdfFile)) {
            if (document.isEncrypted()) {
                logger.error("Cannot process encrypted PDF: {}", pdfFile.getName());
                throw new IOException("Cannot process encrypted PDF files");
            }

            PDFTextStripper pdfStripper = new PDFTextStripper();
            String fullText = pdfStripper.getText(document);

            if (fullText == null || fullText.trim().isEmpty()) {
                logger.warn("PDF contains no text: {}", pdfFile.getName());
                return new ArrayList<>();
            }

            List<String> chunks = new ArrayList<>();
            int start = 0;
            // FIX: Improved chunking algorithm to break at sentence boundaries
            while (start < fullText.length()) {
                int end = Math.min(start + maxCharactersPerChunk, fullText.length());

                // FIX: Try to break at sentence boundary (period followed by space)
                if (end < fullText.length()) {
                    // Look for sentence end within the last 20% of the chunk
                    int searchStart = Math.max(start, end - maxCharactersPerChunk / 5);
                    int lastPeriod = fullText.lastIndexOf(". ", end);

                    if (lastPeriod > searchStart) {
                        end = lastPeriod + 1; // Include the period
                    } else {
                        // If no period found, try to break at space
                        int lastSpace = fullText.lastIndexOf(' ', end);
                        if (lastSpace > searchStart) {
                            end = lastSpace;
                        }
                    }
                }

                String chunk = fullText.substring(start, end).trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                start = end;
            }

            logger.info("Split PDF into {} chunks", chunks.size());
            return chunks;
        }
    }

    public List<String> splitPdfIntoSentences(File pdfFile, int maxSentencesPerChunk) throws Exception {
        if (pdfFile == null) {
            throw new IllegalArgumentException("PDF file cannot be null");
        }
        if (!pdfFile.exists()) {
            throw new IOException("PDF file does not exist: " + pdfFile.getAbsolutePath());
        }
        if (maxSentencesPerChunk <= 0) {
            throw new IllegalArgumentException("Max sentences per chunk must be positive");
        }

        logger.info("Splitting PDF into sentence-based chunks: {}", pdfFile.getName());

        try (PDDocument document = PDDocument.load(pdfFile)) {
            if (document.isEncrypted()) {
                throw new IOException("Cannot process encrypted PDF files");
            }

            PDFTextStripper pdfStripper = new PDFTextStripper();
            String fullText = pdfStripper.getText(document);

            if (fullText == null || fullText.trim().isEmpty()) {
                logger.warn("PDF contains no text: {}", pdfFile.getName());
                return new ArrayList<>();
            }

            // Split by sentence boundaries
            String[] sentences = fullText.split("(?<=[.!?])\\s+");

            List<String> chunks = new ArrayList<>();
            StringBuilder currentChunk = new StringBuilder();
            int sentenceCount = 0;

            for (String sentence : sentences) {
                sentence = sentence.trim();
                if (sentence.isEmpty()) {
                    continue;
                }

                if (sentenceCount >= maxSentencesPerChunk) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                    sentenceCount = 0;
                }

                currentChunk.append(sentence).append(" ");
                sentenceCount++;
            }

            // Add the last chunk if it's not empty
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
            }

            logger.info("Split PDF into {} sentence-based chunks", chunks.size());
            return chunks;
        }
    }
}
