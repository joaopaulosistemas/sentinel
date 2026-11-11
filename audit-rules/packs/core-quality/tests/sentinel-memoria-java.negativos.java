import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

class LeituraEmStreaming {
    void le(Path arquivo) throws IOException {
        // ok: sentinel-mem-leitura-total-de-arquivo
        try (BufferedReader r = Files.newBufferedReader(arquivo)) {
            r.lines().forEach(System.out::println);
        }
    }

    void dispara(ExecutorService pool, Runnable tarefa) {
        // ok: sentinel-mem-new-thread-solto
        pool.execute(tarefa);
    }
}
