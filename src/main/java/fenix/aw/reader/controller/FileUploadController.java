package fenix.aw.reader.controller;

import fenix.aw.reader.Exception.StorageFileNotFoundException;
import fenix.aw.reader.model.Job;
import fenix.aw.reader.service.IJobService;
import fenix.aw.reader.service.IStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class FileUploadController implements IFileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    private final IStorageService storageService;
    private final IJobService jobService;

    @Value("${audio.output.dir:../shared_data/combined_audio}")
    private String audioOutputDir;

    @Autowired
    public FileUploadController(IStorageService storageService, IJobService jobService) {
        this.storageService = storageService;
        this.jobService = jobService;
    }

    @GetMapping("/")
    public ResponseEntity<List<String>> listUploadedFiles() {
        try {
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
        } catch (Exception ex) {
            logger.error("Error listing uploaded files", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            if (filename == null || filename.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Resource file = storageService.loadAsResource(filename);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Serving file: {}", filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                    .body(file);
        } catch (Exception ex) {
            logger.error("Error serving file: {}", filename, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }

            storageService.store(file);
            logger.info("Successfully uploaded file: {}", file.getOriginalFilename());
            return ResponseEntity.ok("Successfully uploaded " + file.getOriginalFilename());
        } catch (Exception ex) {
            logger.error("Error uploading file", ex);
            return ResponseEntity.internalServerError().body("Failed to upload file: " + ex.getMessage());
        }
    }

    @PostMapping("/audiobooks")
    public ResponseEntity<Map<String, String>> processFileForTTS(@RequestParam("fileName") String fileName) {
        try {
            if (fileName == null || fileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Resource fileResource = storageService.loadAsResource(fileName);
            if (fileResource == null || !fileResource.exists()) {
                logger.warn("File not found for TTS processing: {}", fileName);
                return ResponseEntity.badRequest().body(Map.of("error", "File not found: " + fileName));
            }

            Job job = jobService.createJob(fileName);
            jobService.processJobAsync(job.getId(), fileResource.getFile().getAbsolutePath());

            logger.info("Queued job {} for file: {}", job.getId(), fileName);
            return ResponseEntity.accepted().body(Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus().name(),
                    "message", "Conversion started. Poll /jobs/" + job.getId() + " for status."
            ));
        } catch (Exception ex) {
            logger.error("Error queuing TTS job for: {}", fileName, ex);
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Job> getJobStatus(@PathVariable String jobId) {
        try {
            Job job = jobService.getJob(jobId);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            logger.error("Error fetching job status: {}", jobId, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/audiobooks/{filename:.+}")
    public ResponseEntity<Resource> getAudiobook(@PathVariable String filename) {
        try {
            if (filename == null || filename.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath = Paths.get(audioOutputDir).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                logger.warn("Audiobook not found: {}", filename);
                return ResponseEntity.notFound().build();
            }

            logger.info("Serving audiobook: {}", filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception ex) {
            logger.error("Error serving audiobook: {}", filename, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        logger.warn("Storage file not found: {}", exc.getMessage());
        return ResponseEntity.notFound().build();
    }
}
