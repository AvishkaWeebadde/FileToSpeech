package fenix.aw.reader.service.impl;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import fenix.aw.reader.util.PDFProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class TTSClientService
{
    private static final Logger logger = LoggerFactory.getLogger(TTSClientService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${python.tts.url}")
    private String pythonTTSUrl;

    @Value("${python.combine.url}")
    private String pythonCombineUrl;

    @Value("${tts.thread.pool.size:10}")
    private int threadPoolSize;

    @Value("${tts.batch.size:5}")
    private int batchSize;

    @Value("${tts.rate.limit:5.0}")
    private double rateLimit;

    @Value("${pdf.chunk.size:1000}")
    private int pdfChunkSize;


    // Create a rate limiter that allows 5 requests per second
    private RateLimiter rateLimiter;

    @PostConstruct
    public void init() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod((int) rateLimit)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        this.rateLimiter = RateLimiter.of("ttsRateLimiter", config);
        logger.info("TTS Client Service initialized with rate limit: {} req/s", rateLimit);
    }

    public List<String> processFileForTTS(String pdfPath)
    {
        if (pdfPath == null || pdfPath.trim().isEmpty())
        {
            throw new IllegalArgumentException("PDF path cannot be null or empty");
        }

        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists())
        {
            throw new IllegalArgumentException("PDF file does not exist: " + pdfPath);
        }
        logger.info("Processing PDF file for TTS: {}", pdfPath);

        List<String> textChunks = extractTextChunksFromPDF(pdfPath);
        if (textChunks.isEmpty())
        {
            logger.warn("No text extracted from PDF: {}", pdfPath);
            return Collections.emptyList();
        }
        logger.info("Extracted {} text chunks from PDF", textChunks.size());

        List<String> audioPaths = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try
        {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int i = 0; i < textChunks.size(); i += batchSize)
            {
                List<String> batch = textChunks.subList(i, Math.min(i + batchSize, textChunks.size()));
                futures.add(executor.submit(() -> sendTTSRequest(batch)));
            }
            for (Future<List<String>> future : futures)
            {
                try
                {
                    List<String> batchResults = future.get();
                    audioPaths.addAll(batchResults);
                    logger.debug("Processed batch, got {} audio files", batchResults.size());
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                    logger.error("TTS processing interrupted", ex);
                    throw new RuntimeException("TTS processing was interrupted", ex);
                }
                catch (ExecutionException ex)
                {
                    logger.error("Failed to process TTS batch", ex);
                    throw new RuntimeException("TTS batch processing failed", ex.getCause());
                }
            }

            logger.info("Successfully generated {} audio files", audioPaths.size());
            return audioPaths;
        }
        finally
        {
            executor.shutdown();
            try
            {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                {
                    logger.warn("Executor did not terminate in time, forcing shutdown");
                    executor.shutdownNow();
                }
            }
            catch (InterruptedException ex)
            {
                logger.error("Interrupted while waiting for executor termination", ex);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<String> sendTTSRequest(List<String> textChunks)
    {
        if (textChunks == null || textChunks.isEmpty())
        {
            logger.warn("Attempted to send empty text chunks to TTS");
            return Collections.emptyList();
        }

        rateLimiter.acquirePermission();
        Map<String, Object> request = new HashMap<>();
        request.put("text", textChunks);

        try
        {
            ResponseEntity<Map> response = restTemplate.postForEntity(pythonTTSUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                @SuppressWarnings("unchecked")
                List<String> filePaths = (List<String>) response.getBody().get("file_paths");

                if (filePaths == null)
                {
                    logger.error("TTS service did not return file_paths in response");
                    return Collections.emptyList();
                }

                return filePaths;

            }
            else
            {
                logger.error("Failed to generate audio files: {}", response.getStatusCode());
                throw new RuntimeException("Failed to generate audio files: " + response.getStatusCode());
            }
        }
        catch (RestClientException ex)
        {
            logger.error("Error communicating with TTS service", ex);
            throw new RuntimeException("TTS service communication failed", ex);
        }
    }

    private List<String> extractTextChunksFromPDF(String pdfPath)
    {
        try
        {
            File pdfFile = new File(pdfPath);
            PDFProcessor pdfProcessor = new PDFProcessor();

            List<String> chunks = pdfProcessor.splitPdfIntoChunks(pdfFile, pdfChunkSize);
            logger.info("Extracted {} chunks from PDF with chunk size {}", chunks.size(), pdfChunkSize);
            return chunks;
        }
        catch (Exception ex)
        {
            logger.error("Failed to extract text from PDF: {}", pdfPath, ex);
            throw new RuntimeException("PDF text extraction failed", ex);
        }
    }

    public String combineAudioFiles(List<String> filePaths, String fileName)
    {
        if (filePaths == null || filePaths.isEmpty())
        {
            throw new IllegalArgumentException("File paths list cannot be null or empty");
        }
        if (fileName == null || fileName.trim().isEmpty())
        {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }
        logger.info("Combining {} audio files for: {}", filePaths.size(), fileName);

        Map<String, Object> request = new HashMap<>();
        request.put("file_paths", filePaths);
        request.put("file_name", fileName);

        try
        {
            ResponseEntity<Map> response = restTemplate.postForEntity(pythonCombineUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                String filePath = (String) response.getBody().get("file_path");
                if (filePath == null || filePath.isEmpty()) {
                    throw new RuntimeException("Combine service did not return a file path");
                }
                logger.info("Successfully combined audio files: {}", filePath);
                return filePath;
            }
            else
            {
                throw new RuntimeException("Failed to combine audio files: " + response.getStatusCode());
            }
        }
        catch (RestClientException ex)
        {
            logger.error("Error occurred during combining audio files", ex);
            throw new RuntimeException("Audio file combination failed", ex);
        }
    }
}
