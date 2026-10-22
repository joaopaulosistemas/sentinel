package demo;

import java.io.File;
import java.util.zip.ZipEntry;

class Extras {

    void xmlRuim() throws Exception {
        // ruleid: sentinel-xxe-parser-xml
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.newDocumentBuilder();
    }

    void xmlSeguro() throws Exception {
        // ok: sentinel-xxe-parser-xml
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.newDocumentBuilder();
    }

    void spel(String entrada, SpelExpressionParser spelParser) {
        // ruleid: sentinel-spel-injection
        spelParser.parseExpression(entrada);
        // ok: sentinel-spel-injection
        spelParser.parseExpression("1 + 1");
    }

    void redirecionar(HttpServletResponse resp, String redirectUrl) throws Exception {
        // ruleid: sentinel-open-redirect
        resp.sendRedirect(redirectUrl);
        // ok: sentinel-open-redirect
        resp.sendRedirect("/home");
    }

    void json(ObjectMapper mapper) {
        // ruleid: sentinel-jackson-default-typing
        mapper.enableDefaultTyping();
        // ok: sentinel-jackson-default-typing
        mapper.findAndRegisterModules();
    }

    void descompactar(ZipEntry entry, File destino) {
        // ruleid: sentinel-zip-slip
        File arquivo = new File(destino, entry.getName());
    }

    void descompactarSeguro(File destino, String nome) {
        // ok: sentinel-zip-slip
        File arquivo = new File(destino, nome);
    }

    // ruleid: sentinel-crossorigin-aberto
    @CrossOrigin
    void a() {}

    // ruleid: sentinel-crossorigin-aberto
    @CrossOrigin(origins = "*")
    void b() {}

    // ok: sentinel-crossorigin-aberto
    @CrossOrigin(origins = "https://app.exemplo.com")
    void c() {}

    void logar(Logger log, String senhaUsuario, String nome) {
        // ruleid: sentinel-log-dado-sensivel
        log.info("login", senhaUsuario);
        // ok: sentinel-log-dado-sensivel
        log.info("login", nome);
    }

    void credenciais() {
        // ruleid: sentinel-senha-fixa-no-codigo
        String dbPassword = "admin123";
        // ok: sentinel-senha-fixa-no-codigo
        String dbPassword2 = System.getenv("DB_PASSWORD");
        // ok: sentinel-senha-fixa-no-codigo
        String nome = "Joao";
        // ruleid: sentinel-senha-fixa-no-codigo
        String jwtSecretKey = "k3y-s3gr3d0-Longo-9f2a";
        // ruleid: sentinel-senha-fixa-no-codigo
        String accessKeySecret = "changeme";
    }

    // nomes de campo, cabeçalho, prefixo e chave de cache não são credencial
    // ok: sentinel-senha-fixa-no-codigo
    static final String PASSWORD = "password";
    // ok: sentinel-senha-fixa-no-codigo
    static final String TOKEN_PREFIX = "Bearer ";
    // ok: sentinel-senha-fixa-no-codigo
    static final String TOKEN_HEADER = "Authorization";
    // ok: sentinel-senha-fixa-no-codigo
    static final String PASSWORD_HEADER = "X-PASSWORD";
    // ok: sentinel-senha-fixa-no-codigo
    static final String LOGIN_TOKEN_KEY = "login_tokens:";
    // ok: sentinel-senha-fixa-no-codigo
    static final String CREDENTIALS_ANNOTATION = "com.exemplo.seguranca.Credentials";
    // ok: sentinel-senha-fixa-no-codigo
    static final String SECRET_REF = "${app.secret}";

    void sair() {
        // ruleid: sentinel-system-exit
        System.exit(1);
    }

    void cookies(HttpServletResponse resp) {
        // ruleid: sentinel-cookie-sem-secure
        Cookie c = new Cookie("sid", "1");
        resp.addCookie(c);
    }

    void cookiesSeguros(HttpServletResponse resp) {
        // ok: sentinel-cookie-sem-secure
        Cookie c = new Cookie("sid", "1");
        c.setSecure(true);
        resp.addCookie(c);
    }
}
