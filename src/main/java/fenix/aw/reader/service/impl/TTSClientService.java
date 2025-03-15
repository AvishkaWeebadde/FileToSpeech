package fenix.aw.reader.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import fenix.aw.reader.util.PDFProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TTSClientService
{
    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${python.tts.url}")
    private String pythonTTSUrl;
    @Value("${python.combine.url}")
    private String pythonCombineUrl;

    // Create a rate limiter that allows 5 requests per second
    private final RateLimiter rateLimiter = RateLimiter.create(5.0);

    public void sendTextToTTS(String text)
    {
        try
        {
            Map<String, String> request = new HashMap<>();
            request.put("text", text);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    pythonTTSUrl,
                    request,
                    String.class
            );
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public String generateAudio(String text)
    {
        try
        {
            rateLimiter.acquire();

            Map<String, String> request = new HashMap<>();
            request.put("text", text);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    pythonTTSUrl,
                    request,
                    Map.class
            );
            return (String) Objects.requireNonNull(response.getBody()).get("file_path");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return "Error";
        }
    }


    public List<String> processFileForTTS(String pdfPath)
    {
        List<String> textChunks = extractTextChunksFromPDF(pdfPath);
        List<String> audioPaths = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<List<String>>> futures = new ArrayList<>();

        for (int i = 0; i < textChunks.size(); i += 5)
        {
            List<String> batch = textChunks.subList(i, Math.min(i + 5, textChunks.size()));
            futures.add(executor.submit(() -> sendTTSRequest(batch)));
        }

        for (Future<List<String>> future : futures)
        {
            try
            {
                audioPaths.addAll(future.get());
            }
            catch (InterruptedException | ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        return audioPaths;
    }

    private List<String> sendTTSRequest(List<String> textChunks)
    {
        rateLimiter.acquire();
        Map<String, Object> request = new HashMap<>();
        request.put("text", textChunks);

        try
        {
            ResponseEntity<Map> response = restTemplate.postForEntity(pythonTTSUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful())
            {
                return (List<String>) response.getBody().get("file_paths");
            }
            else
            {
                throw new RuntimeException("Failed to generate audio files: " + response.getStatusCode());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<String> extractTextChunksFromPDF(String pdfPath) {
        try
        {
            File pdfFile = new File(pdfPath);
            PDFProcessor pdfProcessor = new PDFProcessor();
            return pdfProcessor.splitPdfIntoChunks(pdfFile, 1000); // Adjust the chunk size as needed
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public String combineAudioFiles(List<String> filePaths, String fileName) {
        Map<String, Object> request = new HashMap<>();
        request.put("file_paths", filePaths);
        request.put("file_name", fileName);

        try
        {
            ResponseEntity<Map> response = restTemplate.postForEntity(pythonCombineUrl, request, Map.class);
            if (response.getStatusCode().is2xxSuccessful())
            {
                return (String) response.getBody().get("file_path");
            }
            else
            {
                throw new RuntimeException("Failed to combine audio files: " + response.getStatusCode());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException("Error occurred during combining audio files", e);
        }
    }
}
