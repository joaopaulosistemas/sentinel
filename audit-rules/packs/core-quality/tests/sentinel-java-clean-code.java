import java.util.*;
import java.text.SimpleDateFormat;

class Exemplo {

    void catches() {
        // ruleid: sentinel-java-catch-vazio
        try { run(); } catch (Exception e) {}

        // ok: sentinel-java-catch-vazio
        try { run(); } catch (Exception e) { LOG.error("falha", e); }

        // ok: sentinel-java-catch-vazio
        try { run(); } catch (Exception ignored) {
            // arquivo temporário; não vale falhar por isso
        }

        // ruleid: sentinel-java-catch-throwable
        try { run(); } catch (Throwable t) { LOG.error("x", t); }

        // ok: sentinel-java-catch-throwable
        try { run(); } catch (IllegalStateException t) { LOG.error("x", t); }
    }

    void saidas(Exception e) {


    }

    // ruleid: sentinel-java-retorna-null-colecao
    List<String> lista(boolean b) {
        if (b) { return null; }
        return new ArrayList<>();
    }

    // ok: sentinel-java-retorna-null-colecao
    List<String> listaOk(boolean b) {
        if (b) { return List.of(); }
        return new ArrayList<>();
    }

    // ok: sentinel-java-retorna-null-colecao
    String texto(boolean b) { return null; }

    void strings(String x) {
        // ruleid: sentinel-java-string-igualdade-literal
        if (x == "abc") { run(); }

        // ruleid: sentinel-java-string-igualdade-literal
        if (x != "abc") { run(); }

        // ok: sentinel-java-string-igualdade-literal
        if ("abc".equals(x)) { run(); }

        // ok: sentinel-java-string-igualdade-literal
        if (x == null) { run(); }
    }

    // ruleid: sentinel-java-transactional-privado
    @Transactional
    private void gravaPrivado() { }

    // ok: sentinel-java-transactional-privado
    @Transactional
    void gravaPublico() { }

    void logs(int id) {
        // ruleid: sentinel-java-log-concatenado
        LOG.info("Auditoria " + id);

        // ruleid: sentinel-java-log-concatenado
        log.debug("a" + id);

        // ok: sentinel-java-log-concatenado
        LOG.infof("Auditoria %d", id);

        // ok: sentinel-java-log-concatenado
        outro.info("a" + id);
    }

    void threads() {
        // ruleid: sentinel-java-thread-manual
        new Thread(() -> run()).start();
    }

    void locator() {
        // ruleid: sentinel-java-service-locator
        Object b = applicationContext.getBean(Servico.class);

        // ok: sentinel-java-service-locator
        Object o = mapa.getBean(Servico.class);
    }

    // ruleid: sentinel-java-singleton-manual
    public static Exemplo getInstance() { return null; }
}

class Estado {
    // ruleid: sentinel-java-estado-estatico-mutavel
    public static int contador = 0;

    // ok: sentinel-java-estado-estatico-mutavel
    public static final int LIMITE = 10;

    // ok: sentinel-java-estado-estatico-mutavel
    private static int privado = 0;
}

class Datas {
    // ruleid: sentinel-java-simpledateformat-estatico
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy");
}

class Datas2 {
    // ok: sentinel-java-simpledateformat-estatico
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy");
}

class Injecao {
    // ruleid: sentinel-java-autowired-campo
    @Autowired
    private Servico servico;
}

class InjecaoOk {
    private final Servico servico;
    // ok: sentinel-java-autowired-campo
    InjecaoOk(Servico servico) { this.servico = servico; }
}

class Comentarios {
    void m() {
        // ruleid: sentinel-comentario-pendencia
        // TODO: tratar o caso de erro

        // ruleid: sentinel-comentario-pendencia
        // fixme depois

        // ok: sentinel-comentario-pendencia
        // Explica por que o valor é arredondado para baixo

        // ruleid: sentinel-codigo-comentado
        //   int total = calcula(x);

        // ruleid: sentinel-codigo-comentado
        // servico.executa(x);

        // ok: sentinel-codigo-comentado
        // O cálculo considera o total do mês, sem feriados.
    }
}
