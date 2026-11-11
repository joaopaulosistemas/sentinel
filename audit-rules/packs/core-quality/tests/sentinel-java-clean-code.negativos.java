import java.util.concurrent.ExecutorService;

// ok: sentinel-java-singleton-manual
enum Configuracao {
    INSTANCIA;

    void roda(ExecutorService executor, Runnable tarefa) {
        // ok: sentinel-java-thread-manual
        executor.submit(tarefa);
    }
}
