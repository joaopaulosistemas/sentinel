import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

class RecursoClasspath {
    static final String BASE = "/templates/";

    // Ajudante que recebe o nome já vindo do cliente: o caminho é montado por concatenação.
    InputStream ler(String nomeArquivo) {
        // ruleid: sentinel-resource-path-concatenado
        return this.getClass().getResourceAsStream(BASE + nomeArquivo);
    }

    @GetMapping("/arquivo")
    Object controlador(@RequestParam String arquivo) throws Exception {
        // ruleid: sentinel-resource-path-concatenado
        return new ClassPathResource("static/" + arquivo);
    }

    InputStream seguro(String nomeArquivo) {
        // ok: sentinel-resource-path-concatenado
        return this.getClass().getResourceAsStream(BASE + java.nio.file.Paths.get(nomeArquivo).getFileName().toString());
    }

    InputStream fixo() {
        // ok: sentinel-resource-path-concatenado
        return this.getClass().getResourceAsStream(BASE + "ajuda.txt");
    }

    InputStream semConcatenar(String nomeArquivo) {
        // ok: sentinel-resource-path-concatenado
        return this.getClass().getResourceAsStream(nomeArquivo);
    }
}
