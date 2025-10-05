package fenix.aw.reader.service.impl;

import fenix.aw.reader.Exception.StorageException;
import fenix.aw.reader.Exception.StorageFileNotFoundException;
import fenix.aw.reader.util.StorageProperties;
import fenix.aw.reader.service.IStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class StorageService implements IStorageService {

    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final String[] ALLOWED_EXTENSIONS = {".pdf"};

    private final Path rootLocation;

    @Autowired
    public StorageService(StorageProperties properties)
    {
        if(properties.getLocation().trim().isEmpty())
        {
            throw new StorageException("File upload location cannot be empty.");
        }

        this.rootLocation = Paths.get(properties.getLocation());
        logger.info("Storage service initialized with root location: {}", rootLocation);
    }

    @Override
    public Stream<Path> loadAll() {
        try
        {
            return Files.walk(this.rootLocation, 1)
                    .filter(path -> !path.equals(this.rootLocation))
                    .map(this.rootLocation::relativize);
        }
        catch (IOException ex)
        {
            logger.error("Failed to read stored files from: {}", rootLocation, ex);
            throw new StorageException("Failed to read stored files.", ex);
        }
    }

    @Override
    public Path load(String filename)
    {
        if (filename == null || filename.trim().isEmpty())
        {
            throw new StorageException("Filename cannot be null or empty");
        }
        return rootLocation.resolve(filename);
    }

    @Override
    public Resource loadAsResource(String filename) {
        try
        {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());

            if(resource.exists() && resource.isReadable())
            {
                logger.debug("Successfully loaded resource: {}", filename);
                return resource;
            }
            else
            {
                logger.warn("Could not read file: {}", filename);
                throw new StorageFileNotFoundException("could not read file " + filename);
            }
        }
        catch(MalformedURLException ex)
        {
            logger.error("Malformed URL for file: {}", filename, ex);
            throw new StorageFileNotFoundException("Could not read file: " + filename, ex);
        }
    }

    @Override
    public void store(MultipartFile file) {
        try
        {
            validateFileStorage(file);

            String originalFilename = file.getOriginalFilename();
            Path destinationFile = this.rootLocation.resolve(
                    Paths.get(Objects.requireNonNull(originalFilename))
            ).normalize().toAbsolutePath();

            if(!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath()))
            {
                logger.error("Attempted path traversal attack with file: {}", originalFilename);
                throw new StorageException("Cannot store file outside current directory.");
            }

            try (InputStream inputStream = file.getInputStream())
            {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Successfully stored file: {}", originalFilename);
            }
        }
        catch (IOException ex)
        {
            logger.error("Failed to store file", ex);
            throw new StorageException("Failed to store file. " + ex.getMessage(), ex);
        }
    }

    private static void validateFileStorage(MultipartFile file) {
        if(file == null || file.isEmpty()) {
            throw new StorageException("File cannot be null or empty");
        }

        if(file.getSize() > MAX_FILE_SIZE) {
            throw new StorageException("File size exceeds maximum allowed size of " +
                    (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if(originalFilename == null || originalFilename.trim().isEmpty())
        {
            throw new StorageException("Original filename cannot be null or empty");
        }

        boolean validExtension = false;
        for(String ext : ALLOWED_EXTENSIONS) {
            if(originalFilename.toLowerCase().endsWith(ext)) {
                validExtension = true;
                break;
            }
        }
        if(!validExtension) {
            throw new StorageException("Invalid file type. Only PDF files are allowed.");
        }
    }


    @Override
    public void deleteAll()
    {
        logger.warn("Deleting all files from storage location: {}", rootLocation);
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }

    @Override
    public void init()
    {
        try
        {
            Files.createDirectories(rootLocation);
            logger.info("Storage directories created successfully");
            logger.debug("Storage directories created successfully@{}", rootLocation);
        }
        catch (IOException e)
        {
            logger.error("Could not initialize storage", e);
            throw new StorageException("Could not initialize storage", e);
        }
    }
}
