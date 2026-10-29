package demo;

import java.io.*;
import java.net.Socket;
import java.sql.DriverManager;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

class Arquivos {
    void ruim(File f) throws Exception {
        // ruleid: sentinel-boas-java-recurso-sem-try-with-resources
        InputStream in = new FileInputStream(f);
        in.read();
        in.close();
    }

    void ruimConexao(String url) throws Exception {
        // ruleid: sentinel-boas-java-recurso-sem-try-with-resources
        var c = DriverManager.getConnection(url);
    }

    void bom(File f) throws Exception {
        // ok: sentinel-boas-java-recurso-sem-try-with-resources
        try (InputStream in = new FileInputStream(f)) {
            in.read();
        }
    }
}

@RestController
class ContaController {
    // ruleid: sentinel-boas-spring-transactional-no-controller
    @Transactional
    public void gravar() { }

    public ResponseEntity<String> erro(Exception e) {
        // ruleid: sentinel-boas-spring-mensagem-de-excecao-ao-cliente
        return ResponseEntity.status(500).body(e.getMessage());
    }

    public ResponseEntity<String> erroOk(Exception e) {
        // ok: sentinel-boas-spring-mensagem-de-excecao-ao-cliente
        return ResponseEntity.status(500).body("Erro interno");
    }
}

class ContaService {
    // ok: sentinel-boas-spring-transactional-no-controller
    @Transactional
    public void gravar() { }

    // ruleid: sentinel-boas-spring-value-espalhado
    @Value("${app.url}")
    String url;
}
