import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;

class Configurado {
    // ok: sentinel-quarkus-getenv-direto
    @ConfigProperty(name = "app.regiao")
    String regiao;

    void assina(Uni<String> uni) {
        // ok: sentinel-quarkus-await-indefinido
        uni.subscribe().with(v -> System.out.println(v));
    }

    Class<?> tipo() {
        // ok: sentinel-quarkus-reflexao-nativa
        return Configurado.class;
    }
}
