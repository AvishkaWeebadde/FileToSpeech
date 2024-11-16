package fenix.aw.reader.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class TTSClientService
{
    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${python.tts.url}")
    private String pythonTTSUrl;

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
}
