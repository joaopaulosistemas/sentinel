package demo;

import java.io.File;
import java.nio.file.Files;

public class FileStore {
    public byte[] read(String name) throws Exception {
        File f = new File("/data/files/" + name);
        return Files.readAllBytes(f.toPath());
    }
}
