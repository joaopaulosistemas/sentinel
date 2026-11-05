import org.slf4j.Logger;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

class LogInjection {
    Logger log;

    String registrar(@RequestHeader("User-Agent") String agente, @RequestParam String texto) {
        String linha = String.format("%s %s", agente, texto);
        // ruleid: sentinel-log-injection
        log.debug(linha);
        // ruleid: sentinel-log-injection
        log.info("texto recebido: " + texto);
        // ok: sentinel-log-injection
        log.info("requisição recebida");
        String limpo = texto.replace("\n", "_").replace("\r", "_");
        // ok: sentinel-log-injection
        log.info(limpo);
        return "";
    }
}
