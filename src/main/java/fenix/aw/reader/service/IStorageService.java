package fenix.aw.reader.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.stream.Stream;

public interface IStorageService {

    Stream<Path> loadAll();

    Path load(String filename);

    Resource loadAsResource(String filename);


    void store(MultipartFile file);


    void deleteAll();

    void init();
}
