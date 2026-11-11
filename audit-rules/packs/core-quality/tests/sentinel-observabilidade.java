import org.slf4j.Logger;
import org.slf4j.MDC;

class Observabilidade {
    private static final Logger log = null;

    // ---- log de erro sem a exceção ----------------------------------------------------------
    void a() {
        try {
            executa();
        } catch (Exception e) {
            // ruleid: sentinel-obs-log-erro-sem-excecao
            log.error("falha ao executar");
        }
    }

    void b() {
        try {
            executa();
        } catch (Exception e) {
            // ruleid: sentinel-obs-log-erro-sem-excecao
            log.warn("falha: " + e.getMessage());
        }
    }

    void c() {
        try {
            executa();
        } catch (Exception e) {
            // ok: sentinel-obs-log-erro-sem-excecao
            log.error("falha ao executar", e);
        }
        // ok: sentinel-obs-log-erro-sem-excecao
        log.error("erro fora de catch");
    }

    // receptor que não é logger (ex.: resultado de ferramenta) não é log de erro
    void resultadoDeFerramenta() {
        try {
            executa();
        } catch (Exception e) {
            // ok: sentinel-obs-log-erro-sem-excecao
            Resultado.error("Erro interno ao executar a ferramenta.");
        }
    }

    // ---- exceção relançada sem a causa --------------------------------------------------------
    void d() {
        try {
            executa();
        } catch (Exception e) {
            // ruleid: sentinel-obs-excecao-relancada-sem-causa
            throw new IllegalStateException("falha ao executar");
        }
    }

    void e() {
        try {
            executa();
        } catch (Exception e) {
            // ruleid: sentinel-obs-excecao-relancada-sem-causa
            throw new IllegalStateException(e.getMessage());
        }
    }

    void f() {
        try {
            executa();
        } catch (Exception e) {
            // ok: sentinel-obs-excecao-relancada-sem-causa
            throw new IllegalStateException("falha ao executar", e);
        }
    }

    void g() {
        try {
            executa();
        } catch (Exception e) {
            // ok: sentinel-obs-excecao-relancada-sem-causa
            throw new IllegalStateException(e);
        }
    }

    // ---- MDC sem limpeza -----------------------------------------------------------------------
    void h(String id) {
        // ruleid: sentinel-obs-mdc-sem-limpeza
        MDC.put("requisicao", id);
        executa();
    }

    void i(String id) {
        try {
            // ok: sentinel-obs-mdc-sem-limpeza
            MDC.put("requisicao", id);
            executa();
        } finally {
            MDC.clear();
        }
    }

    void executa() { }
}
