package fenix.aw.reader.controller;

import fenix.aw.reader.Exception.StorageFileNotFoundException;
import fenix.aw.reader.service.IStorageService;
import fenix.aw.reader.service.impl.TTSClientService;
import fenix.aw.reader.util.PDFProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    private final IStorageService storageService;
    private final TTSClientService ttsClientService;

    @Autowired
    public FileUploadController(IStorageService storageService, TTSClientService ttsClientService)
    {
        this.storageService = storageService;
        this.ttsClientService = ttsClientService;
    }

    @GetMapping("/")
    public ResponseEntity<List<String>> listUploadedFiles()
    {
        try
        {
            List<String> fileUris = storageService.loadAll()
                    .map(path -> MvcUriComponentsBuilder.fromMethodName(
                                    FileUploadController.class,
                                    "serveFile",
                                    path.getFileName().toString())
                            .build()
                            .toUri()
                            .toString())
                    .collect(Collectors.toList());

            logger.info("Listed {} uploaded files", fileUris.size());
            return ResponseEntity.ok(fileUris);
        }
        catch (Exception ex)
        {
            logger.error("Error listing uploaded files", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename)
    {
        try
        {
            if (filename == null || filename.trim().isEmpty())
            {
                logger.warn("Attempted to serve file with null or empty filename");
                return ResponseEntity.badRequest().build();
            }

            Resource file = storageService.loadAsResource(filename);

            if (file == null)
            {
                logger.warn("File not found: {}", filename);
                return ResponseEntity.notFound().build();
            }

            logger.info("Serving file: {}", filename);

            return ResponseEntity
                    .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
        }
        catch (Exception ex)
        {
            logger.error("Error serving file: {}", filename, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes)
    {
        try
        {
            if (file == null || file.isEmpty())
            {
                redirectAttributes.addFlashAttribute("message", "Please select a file to upload");
                logger.warn("Upload attempted with no file");
                return "redirect:/";
            }

            storageService.store(file);
            redirectAttributes.addFlashAttribute("message",
                    "Successfully uploaded " + file.getOriginalFilename() + "!");
            logger.info("Successfully uploaded file: {}", file.getOriginalFilename());
            return "redirect:/";
        }
        catch (Exception ex)
        {
            logger.error("Error uploading file", ex);
            redirectAttributes.addFlashAttribute("message",
                    "Failed to upload file: " + ex.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/audiobooks")
    public ResponseEntity<String> processFileForTTS(@RequestParam("fileName") String fileName)
    {
        try
        {
            if (fileName == null || fileName.trim().isEmpty())
            {
                logger.warn("TTS processing attempted with null or empty filename");
                return ResponseEntity.badRequest().body("Filename cannot be null or empty");
            }
            logger.info("Processing file for TTS: {}", fileName);
            // Locate te file in the storage directory
            Resource fileResource = storageService.loadAsResource(fileName);

            if( fileResource == null || !fileResource.exists()) {
                logger.warn("File not found for TTS processing: {}", fileName);
                return ResponseEntity.badRequest().body("File not found " + fileName);
            }


            // Process the file for TTS
            List<String> audioPaths = ttsClientService.processFileForTTS
                    (
                            fileResource.getFile().getAbsolutePath()
                    );

            if (audioPaths.isEmpty())
            {
                logger.error("Failed to generate audio files for: {}", fileName);
                return ResponseEntity.badRequest().body("Failed to generate audio files.");
            }

            String combinedFilePath = ttsClientService.combineAudioFiles(audioPaths, fileName);

            logger.info("Successfully generated audiobook for: {}", fileName);
            return ResponseEntity.ok("Audio files combined successfully. Path: " + combinedFilePath);
        }
        catch (Exception ex)
        {
            logger.error("Error processing file for TTS: {}", fileName, ex);
            return ResponseEntity.status(500).body("Error processing file for TTS.");
        }
    }

    @GetMapping("/audiobooks/{filename}")
    public ResponseEntity<Resource> getAudiobook(@PathVariable String filename)
    {
        try
        {
            if (filename == null || filename.trim().isEmpty())
            {
                logger.warn("Attempted to get audiobook with null or empty filename");
                return ResponseEntity.badRequest().build();
            }

            Path filePath = Paths.get("audio_files").resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists())
            {
                logger.warn("Audiobook not found: {}", filename);
                return ResponseEntity.notFound().build();
            }

            logger.info("Serving audiobook: {}", filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }
        catch (Exception ex)
        {
            logger.error("Error serving audiobook: {}", filename, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc)
    {
        logger.warn("Storage file not found: {}", exc.getMessage());
        return ResponseEntity.notFound().build();
    }
}
