package fenix.aw.reader.service.impl;

import fenix.aw.reader.util.PDFProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

@Service
public class TTSClientService {

    private static final Logger logger = LoggerFactory.getLogger(TTSClientService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${python.tts.url}")
    private String pythonTTSUrl;

    @Value("${python.combine.url}")
    private String pythonCombineUrl;

    @Value("${tts.batch.size:5}")
    private int batchSize;

    @Value("${pdf.chunk.size:2000}")
    private int pdfChunkSize;

    public List<String> processFileForTTS(String pdfPath) {
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("PDF path cannot be null or empty");
        }

        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            throw new IllegalArgumentException("PDF file does not exist: " + pdfPath);
        }
        logger.info("Processing PDF for TTS: {}", pdfPath);

        List<String> textChunks = extractTextChunksFromPDF(pdfPath);
        if (textChunks.isEmpty()) {
            logger.warn("No text extracted from PDF: {}", pdfPath);
            return Collections.emptyList();
        }
        logger.info("Extracted {} text chunks from PDF", textChunks.size());

        List<String> audioPaths = new ArrayList<>();
        for (int i = 0; i < textChunks.size(); i += batchSize) {
            List<String> batch = textChunks.subList(i, Math.min(i + batchSize, textChunks.size()));
            List<String> batchResults = sendTTSRequest(batch);
            audioPaths.addAll(batchResults);
            logger.debug("Processed batch {}/{}, got {} audio files",
                    (i / batchSize) + 1, (int) Math.ceil((double) textChunks.size() / batchSize), batchResults.size());
        }

        logger.info("Successfully generated {} audio files", audioPaths.size());
        return audioPaths;
    }

    private List<String> sendTTSRequest(List<String> textChunks) {
        if (textChunks == null || textChunks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> request = new HashMap<>();
        request.put("text", textChunks);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(pythonTTSUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<String> filePaths = (List<String>) response.getBody().get("file_paths");

                if (filePaths == null) {
                    logger.error("TTS service did not return file_paths in response");
                    return Collections.emptyList();
                }
                return filePaths;
            } else {
                throw new RuntimeException("TTS service returned: " + response.getStatusCode());
            }
        } catch (RestClientException ex) {
            logger.error("Error communicating with TTS service", ex);
            throw new RuntimeException("TTS service communication failed", ex);
        }
    }

    private List<String> extractTextChunksFromPDF(String pdfPath) {
        try {
            PDFProcessor pdfProcessor = new PDFProcessor();
            List<String> chunks = pdfProcessor.splitPdfIntoChunks(new File(pdfPath), pdfChunkSize);
            logger.info("Extracted {} chunks from PDF (chunk size: {})", chunks.size(), pdfChunkSize);
            return chunks;
        } catch (Exception ex) {
            logger.error("Failed to extract text from PDF: {}", pdfPath, ex);
            throw new RuntimeException("PDF text extraction failed", ex);
        }
    }

    public String combineAudioFiles(List<String> filePaths, String fileName) {
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IllegalArgumentException("File paths list cannot be null or empty");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }
        logger.info("Combining {} audio files for: {}", filePaths.size(), fileName);

        Map<String, Object> request = new HashMap<>();
        request.put("file_paths", filePaths);
        request.put("file_name", fileName);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(pythonCombineUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String filePath = (String) response.getBody().get("file_path");
                if (filePath == null || filePath.isEmpty()) {
                    throw new RuntimeException("Combine service did not return a file path");
                }
                logger.info("Successfully combined audio files: {}", filePath);
                return filePath;
            } else {
                throw new RuntimeException("Combine service returned: " + response.getStatusCode());
            }
        } catch (RestClientException ex) {
            logger.error("Error combining audio files", ex);
            throw new RuntimeException("Audio file combination failed", ex);
        }
    }
}
