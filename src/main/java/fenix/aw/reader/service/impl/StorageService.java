package fenix.aw.reader.service.impl;

import fenix.aw.reader.Exception.StorageException;
import fenix.aw.reader.Exception.StorageFileNotFoundException;
import fenix.aw.reader.StorageProperties;
import fenix.aw.reader.service.IStorageService;
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

    private final Path rootLocation;

    @Autowired
    public StorageService(StorageProperties properties)
    {
        if(properties.getLocation().trim().length() == 0)
        {
            throw new StorageException("File upload location cannot be empty.");
        }

        this.rootLocation = Paths.get(properties.getLocation());
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
            throw new StorageException("Failed to read stored files.", ex);
        }
    }

    @Override
    public Path load(String filename) {
        return rootLocation.resolve(filename);
    }

    @Override
    public Resource loadAsResource(String filename) {
        try
        {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());

            if(resource.exists() || resource.isReadable())
            {
                return resource;
            }
            else
            {
                throw new StorageFileNotFoundException("could not read file " + filename);
            }
        }
        catch(MalformedURLException ex)
        {
            throw new StorageFileNotFoundException("Could not read file: " + filename, ex);
        }
    }

    @Override
    public void store(MultipartFile file) {
        try
        {
            if(file.isEmpty())
            {
                throw new StorageException("Failed to store empty file.");
            }

            Path destinationFile = this.rootLocation.resolve(
                    Paths.get(Objects.requireNonNull(file.getOriginalFilename()))
            ).normalize().toAbsolutePath();

            if(destinationFile.getParent().equals(this.rootLocation.toAbsolutePath()))
            {
                throw new StorageException("cannot store file outside current directory.");
            }

            try (InputStream inputStream = file.getInputStream())
            {
                Files.copy(inputStream, destinationFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ex)
        {
            throw new StorageException("Failed to store file. " + ex.getMessage(), ex);
        }
    }


    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }

    @Override
    public void init()
    {
        try
        {
            Files.createDirectories(rootLocation);
        }
        catch (IOException e) {

            throw new StorageException("Could not initialize storage", e);
        }
    }
}
