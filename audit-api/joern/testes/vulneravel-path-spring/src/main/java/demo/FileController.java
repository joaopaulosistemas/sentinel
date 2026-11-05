package demo;

import org.springframework.web.bind.annotation.*;

@RestController
public class FileController {
    private final FileStore store = new FileStore();

    @GetMapping("/files")
    public byte[] get(@RequestParam String name) throws Exception {
        return store.read(name);
    }
}
