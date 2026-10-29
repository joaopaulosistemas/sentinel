package demo;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

class Exemplos {

    // ruleid: sentinel-quarkus-inject-campo-privado
    @Inject
    private ContaService privado;

    // ok: sentinel-quarkus-inject-campo-privado
    @Inject
    ContaService doPacote;

    void ambiente() {
        // ruleid: sentinel-quarkus-getenv-direto
        String url = System.getenv("DB_URL");
    }

    void bloqueia(Uni<String> u) {
        // ruleid: sentinel-quarkus-await-indefinido
        String v = u.await().indefinitely();
        // ruleid: sentinel-quarkus-await-indefinido
        String w = u.await().atMost(java.time.Duration.ofSeconds(1));
    }

    Uni<String> dorme() throws Exception {
        // ruleid: sentinel-quarkus-sleep-em-reativo
        Thread.sleep(100);
        return Uni.createFrom().item("x");
    }

    // ok: sentinel-quarkus-sleep-em-reativo
    void dormeFora() throws Exception {
        Thread.sleep(100);
    }

    void consultas(String nome) {
        // ruleid: sentinel-quarkus-panache-consulta-concatenada
        Conta.find("nome = '" + nome + "'");
        // ruleid: sentinel-quarkus-panache-consulta-concatenada
        Conta.list("nome = '" + nome + "'");
        // ok: sentinel-quarkus-panache-consulta-concatenada
        Conta.find("nome = ?1", nome);
    }

    void reflexao() throws Exception {
        // ruleid: sentinel-quarkus-reflexao-nativa
        Class<?> c = Class.forName("demo.Conta");
    }
}
