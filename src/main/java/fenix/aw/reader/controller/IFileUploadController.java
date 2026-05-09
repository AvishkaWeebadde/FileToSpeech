package fenix.aw.reader.controller;

import fenix.aw.reader.model.Job;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IFileUploadController {
    ResponseEntity<List<String>> listUploadedFiles();
    ResponseEntity<Resource> serveFile(@PathVariable String filename);
    ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file);
    ResponseEntity<Map<String, String>> processFileForTTS(@RequestParam("fileName") String fileName);
    ResponseEntity<Job> getJobStatus(@PathVariable String jobId);
    ResponseEntity<Resource> getAudiobook(@PathVariable String filename);
}
