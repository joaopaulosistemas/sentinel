package demo;

import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import com.github.benmanes.caffeine.cache.Caffeine;

class ComThreadLocalSemRemove {
    // ruleid: sentinel-mem-threadlocal-sem-remove
    private static final ThreadLocal<String> USUARIO = new ThreadLocal<>();
}

class ComThreadLocalOk {
    // ok: sentinel-mem-threadlocal-sem-remove
    private static final ThreadLocal<String> USUARIO = new ThreadLocal<>();

    void fim() {
        USUARIO.remove();
    }
}

class Estaticos {
    // ruleid: sentinel-mem-colecao-estatica
    private static Map<String, Object> cache = new HashMap<>();
    // ok: sentinel-mem-colecao-estatica
    private static final Map<String, Object> CONSTANTE = Map.of();
    // ok: sentinel-mem-colecao-estatica
    private Map<String, Object> instancia = new HashMap<>();
}

class Caches {
    void a() {
        // ruleid: sentinel-mem-cache-sem-limite
        var c = Caffeine.newBuilder().build();
        // ok: sentinel-mem-cache-sem-limite
        var d = Caffeine.newBuilder().maximumSize(1000).build();
        // ok: sentinel-mem-cache-sem-limite
        var e = Caffeine.newBuilder().expireAfterWrite(java.time.Duration.ofMinutes(5)).build();
    }
}

class ExecutorSemShutdown {
    // ruleid: sentinel-mem-executor-sem-shutdown
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
}

class ExecutorOk {
    // ok: sentinel-mem-executor-sem-shutdown
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    void parar() {
        pool.shutdown();
    }
}

class Threads {
    void a() {
        // ruleid: sentinel-mem-new-thread-solto
        new Thread(() -> {}).start();
    }

    byte[] ler(Path p) throws Exception {
        // ruleid: sentinel-mem-leitura-total-de-arquivo
        return Files.readAllBytes(p);
    }
}
