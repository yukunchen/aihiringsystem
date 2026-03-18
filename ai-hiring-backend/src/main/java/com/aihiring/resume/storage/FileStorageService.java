package com.aihiring.resume.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface FileStorageService {
    String store(MultipartFile file, String storedName) throws IOException;
    Resource load(String filePath);
    void delete(String filePath);
}
