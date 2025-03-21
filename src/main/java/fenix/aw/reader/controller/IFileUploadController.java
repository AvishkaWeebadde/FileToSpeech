package fenix.aw.reader.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

public interface IFileUploadController {
    ResponseEntity<List<String>> listUploadedFiles();
    ResponseEntity<Resource> serveFile(@PathVariable String filename);
    String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes);
    ResponseEntity<String> processFileForTTS(@RequestParam("fileName") String fileName);
    ResponseEntity<Resource> getAudiobook(@PathVariable String filename);
}
