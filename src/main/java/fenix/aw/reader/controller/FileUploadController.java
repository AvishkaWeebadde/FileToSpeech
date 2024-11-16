package fenix.aw.reader.controller;

import fenix.aw.reader.Exception.StorageFileNotFoundException;
import fenix.aw.reader.service.IStorageService;
import fenix.aw.reader.service.impl.TTSClientService;
import fenix.aw.reader.util.PDFProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

            // Extract text from the pdf
            String extractedText = PDFProcessor.extractTextFromPDF(fileResource.getFile().getAbsolutePath());

            if(extractedText == null || extractedText.isEmpty())
            {
                return ResponseEntity.badRequest().body("No text found in the PDF.");
            }

            ttsClientService.sendTextToTTS(extractedText);

            return ResponseEntity.ok("TTS generation request sent succesfully for: " + fileName);

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return ResponseEntity.status(500).body("Error processing file for TTS.");
        }
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
}
