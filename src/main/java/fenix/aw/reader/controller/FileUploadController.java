package fenix.aw.reader.controller;

import fenix.aw.reader.Exception.StorageFileNotFoundException;
import fenix.aw.reader.service.IStorageService;
import fenix.aw.reader.service.impl.TTSClientService;
import fenix.aw.reader.util.PDFProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class FileUploadController implements IFileUploadController{

    private final IStorageService storageService;
    private final TTSClientService ttsClientService;

    @Autowired
    public FileUploadController(IStorageService storageService, TTSClientService ttsClientService)
    {
        this.storageService = storageService;
        this.ttsClientService = ttsClientService;
    }

    @GetMapping("/")
    public ResponseEntity<List<String>> listUploadedFiles() {
        List<String> fileUris = storageService.loadAll()
                .map(path -> MvcUriComponentsBuilder.fromMethodName(
                                FileUploadController.class,
                                "serveFile",
                                path.getFileName().toString())
                        .build()
                        .toUri()
                        .toString())
                .collect(Collectors.toList());

        return ResponseEntity.ok(fileUris);
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename)
    {
        Resource file = storageService.loadAsResource(filename);

        if(file==null)
        {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity
                .ok()
                .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }

    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file")MultipartFile file, RedirectAttributes redirectAttributes)
    {
        storageService.store(file);
        redirectAttributes.addFlashAttribute("message",
                "Successfully uploaded" + file.getOriginalFilename() + "!");
        return "redirect/";
    }

    @PostMapping("/audiobooks")
    public ResponseEntity<String> processFileForTTS(String fileName) {
        try
        {
            // Locate te file in the storage directory
            Resource fileResource = storageService.loadAsResource(fileName);

            if( fileResource == null || !fileResource.exists()) {
                return ResponseEntity.badRequest().body("File not found " + fileName);
            }


            // Process the file for TTS
            List<String> audioPaths = ttsClientService.processFileForTTS
                    (
                            fileResource.getFile().getAbsolutePath()
                    );

            if (audioPaths.isEmpty())
            {
                return ResponseEntity.badRequest().body("Failed to generate audio files.");
            }

            String combinedFilePath = ttsClientService.combineAudioFiles(audioPaths, fileName);

            return ResponseEntity.ok("Audio files combined successfully. Path: " + combinedFilePath);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return ResponseEntity.status(500).body("Error processing file for TTS.");
        }
    }

    @GetMapping("/audiobooks/{filename}")
    public ResponseEntity<Resource> getAudiobook(@PathVariable String filename)
    {
        try
        {
            Path filePath = Paths.get("audio_files").resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists())
            {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }
        catch (Exception ex)
        {
            return ResponseEntity.internalServerError().build();
        }
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
}
