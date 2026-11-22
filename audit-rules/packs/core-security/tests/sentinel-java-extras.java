package demo;

import java.io.File;
import java.util.zip.ZipEntry;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import com.thoughtworks.xstream.XStream;

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

    void saxReaderRuim() throws Exception {
        // ruleid: sentinel-xxe-parser-xml
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.parse(new InputSource(System.in));
    }

    void saxReaderSeguro() throws Exception {
        // ok: sentinel-xxe-parser-xml
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        reader.parse(new InputSource(System.in));
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
        // ok: sentinel-open-redirect
        resp.sendRedirect(resp.encodeURL("ForwardMe?location=/index.jsp"));
        // ok: sentinel-open-redirect
        resp.sendRedirect(resp.encodeRedirectURL("login.jsp?err=falhou"));
        // ruleid: sentinel-open-redirect
        resp.sendRedirect(resp.encodeURL(redirectUrl));
    }

    void encaminhar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String location = req.getParameter("location");
        // ruleid: sentinel-forward-nao-validado
        getServletContext().getRequestDispatcher(location).forward(req, resp);
        String pagina = req.getParameter("p");
        // ruleid: sentinel-forward-nao-validado
        req.getRequestDispatcher("/paginas/" + pagina).forward(req, resp);
        // ok: sentinel-forward-nao-validado
        req.getRequestDispatcher("/vulnerability/avatar.jsp").forward(req, resp);
        String fixo = "/index.jsp";
        // ok: sentinel-forward-nao-validado
        req.getRequestDispatcher(fixo).forward(req, resp);
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
        // frase de mensagem ao usuário não é credencial
        // ok: sentinel-senha-fixa-no-codigo
        String secretData = "Congratulations, you have successfully exploited buffer over-read!";
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
        // ruleid: sentinel-cookie-sem-secure, sentinel-cookie-sem-httponly
        Cookie c = new Cookie("sid", "1");
        resp.addCookie(c);
    }

    void cookieSemHttpOnly(HttpServletResponse resp) {
        // ruleid: sentinel-cookie-sem-httponly
        resp.addCookie(new Cookie("accountType", "personal"));
        // ruleid: sentinel-cookie-sem-httponly, sentinel-cookie-sem-secure
        Cookie c = new Cookie("sid", "1");
        resp.addCookie(c);
    }

    void cookieComHttpOnly(HttpServletResponse resp) {
        // ok: sentinel-cookie-sem-httponly
        // ruleid: sentinel-cookie-sem-secure
        Cookie c = new Cookie("sid", "1");
        c.setHttpOnly(true);
        resp.addCookie(c);
    }

    void cookiesSeguros(HttpServletResponse resp) {
        // ok: sentinel-cookie-sem-secure
        // ruleid: sentinel-cookie-sem-httponly
        Cookie c = new Cookie("sid", "1");
        c.setSecure(true);
        resp.addCookie(c);
    }
}

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;

class RedirectNoSpring {
    ModelAndView real(String url) {
        // ruleid: sentinel-open-redirect-spring
        return new ModelAndView("redirect:" + url);
    }

    ResponseEntity<Void> semValidar(String url) {
        // ruleid: sentinel-open-redirect-spring
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    ModelAndView destinoFixo(java.util.Map<Integer, String> destinos, Integer id) {
        String dest = destinos.getOrDefault(id, "/inicio");
        // ok: sentinel-open-redirect-spring
        return new ModelAndView("redirect:" + dest);
    }

    ModelAndView literal() {
        // ok: sentinel-open-redirect-spring
        return new ModelAndView("redirect:/inicio");
    }
}

class SenhaComparadaComLiteral {
    boolean login(String username, String password, String hashBanco) {
        // ruleid: sentinel-senha-comparada-com-literal
        if ("CaptainJack".equals(username) && "BlackPearl".equals(password)) {
            return true;
        }
        // ruleid: sentinel-senha-comparada-com-literal
        if (password.equals("admin123")) {
            return true;
        }
        // ok: sentinel-senha-comparada-com-literal
        if ("CaptainJack".equals(username)) {
            return true;
        }
        // ok: sentinel-senha-comparada-com-literal
        return hashBanco.equals(password);
    }

    void xstreamQualquerTipo() {
        XStream xs = new XStream();
        // ruleid: sentinel-xstream-qualquer-tipo
        xs.addPermission(AnyTypePermission.ANY);
    }

    void xstreamTipoEspecifico() {
        XStream xs = new XStream();
        // ok: sentinel-xstream-qualquer-tipo
        xs.addPermission(new WildcardTypePermission(new String[] {"com.exemplo.dto.*"}));
    }

    void velocityComTextoVariavel(String modelo, VelocityContext ctx, java.io.StringWriter out) {
        // ruleid: sentinel-ssti-modelo-variavel
        Velocity.evaluate(ctx, out, "test", modelo);
    }

    void velocityComTextoFixo(VelocityContext ctx, java.io.StringWriter out) {
        // ok: sentinel-ssti-modelo-variavel
        Velocity.evaluate(ctx, out, "test", "Olá $nome");
    }

    void freemarkerComTextoVariavel(String modelo, freemarker.template.Configuration cfg) throws Exception {
        // ruleid: sentinel-ssti-modelo-variavel
        new freemarker.template.Template("t", new java.io.StringReader(modelo), cfg);
    }

    void fastjsonAutotypeLigado() {
        // ruleid: sentinel-fastjson-autotype
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
    }

    void fastjsonCampoNaoPublico(String json) {
        // ruleid: sentinel-fastjson-autotype
        JSON.parseObject(json, Feature.SupportNonPublicField);
    }

    void fastjsonPadrao(String json) {
        // ok: sentinel-fastjson-autotype
        JSON.parseObject(json);
    }

    void defineClassPorReflexao() throws Exception {
        // ruleid: sentinel-classloader-define-classe
        java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        m.setAccessible(true);
    }

    void outroMetodoDoClassLoader() throws Exception {
        // ok: sentinel-classloader-define-classe
        java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("getPackages");
    }

    // SSRF em método auxiliar: o destino é o próprio parâmetro do método (a origem fica em outro arquivo)
    static Object buscarUrl(String destino) throws Exception {
        // ruleid: sentinel-ssrf-url-do-parametro
        java.net.URLConnection c = new java.net.URL(destino).openConnection();
        return c.getInputStream();
    }

    static Object buscarUrlStream(String destino) throws Exception {
        // ruleid: sentinel-ssrf-url-do-parametro
        return new URL(destino).openStream();
    }

    static Object urlFixa() throws Exception {
        // ok: sentinel-ssrf-url-do-parametro
        return new java.net.URL("https://api.exemplo.com/v1").openConnection();
    }

    static Object urlComHostFixo(String caminho) throws Exception {
        // ok: sentinel-ssrf-url-do-parametro
        return new java.net.URL("https://api.exemplo.com/" + caminho).openConnection();
    }

    static Object buscarUrlEmVariavel(String requestUrl) throws Exception {
        // ruleid: sentinel-ssrf-url-do-parametro
        URL url = new URL(requestUrl);
        URLConnection conexao = url.openConnection();
        return conexao.getInputStream();
    }

    static Object urlEmVariavelFixa(String nome) throws Exception {
        // ok: sentinel-ssrf-url-do-parametro
        URL url = new URL("https://api.exemplo.com/fixo");
        URLConnection conexao = url.openConnection();
        return conexao.getInputStream();
    }

    static Object urlValidadaPorExpressao(String url) throws Exception {
        if (url.matches("https://api\\.exemplo\\.com/.*")) {
            // ok: sentinel-ssrf-url-do-parametro
            return new URL(url).openStream();
        }
        return null;
    }

    // ---- achados do sentinel-alvo: o exemplo vulnerável tem que ser pego e a defesa recomendada não ----

    void zipSlipDeclaradoAntesDoLaco(java.util.zip.ZipInputStream zip, File destino) throws Exception {
        ZipEntry e;
        while ((e = zip.getNextEntry()) != null) {
            // ruleid: sentinel-zip-slip
            File saida = new File(destino, e.getName());
        }
    }

    void zipSlipForEach(java.util.zip.ZipFile zip, File destino) throws Exception {
        for (ZipEntry e : java.util.Collections.list(zip.entries())) {
            // ruleid: sentinel-zip-slip
            File saida = new File(destino, e.getName());
        }
    }

    void zipSlipComCaminhoCanonicoConferido(ZipEntry entry, File destino) throws Exception {
        // ok: sentinel-zip-slip
        File saida = new File(destino, entry.getName()).getCanonicalFile();
        if (!saida.toPath().startsWith(destino.getCanonicalFile().toPath())) {
            throw new SecurityException("fora do destino");
        }
    }

    void redirectComCondicao(jakarta.servlet.http.HttpServletResponse resp, String next) throws Exception {
        // ok: sentinel-open-redirect
        resp.sendRedirect(next != null && next.startsWith("/") && !next.startsWith("//") ? next : "/inicio");
    }

    void redirectComValidacaoAntes(jakarta.servlet.http.HttpServletResponse resp, String next) throws Exception {
        if (next.startsWith("/") && !next.startsWith("//")) {
            // ok: sentinel-open-redirect
            resp.sendRedirect(next);
        }
    }

    String redirectSpringDestinoEmPortugues(String destino) {
        // ruleid: sentinel-open-redirect-spring
        return "redirect:" + destino;
    }

    String redirectSpringRetornoEmPortugues(String retorno) {
        // ruleid: sentinel-open-redirect-spring
        return "redirect:" + retorno;
    }

    String redirectSpringDestinoValidado(String destino) {
        if (destino.startsWith("/") && !destino.startsWith("//")) {
            // ok: sentinel-open-redirect-spring
            return "redirect:" + destino;
        }
        return "redirect:/inicio";
    }

    java.io.InputStream ssrfDiretoDaRequisicao(jakarta.servlet.http.HttpServletRequest req) throws Exception {
        // ruleid: sentinel-ssrf-url-do-parametro
        return new URL(req.getParameter("url")).openStream();
    }

    java.io.InputStream urlFixaNaoEUmSsrf() throws Exception {
        // ok: sentinel-ssrf-url-do-parametro
        return new URL("https://api.parceiro.com.br/cotacao").openStream();
    }

    java.sql.Connection jdbcSenhaNoCodigo() throws Exception {
        // ruleid: sentinel-jdbc-senha-fixa
        return java.sql.DriverManager.getConnection("jdbc:mysql://localhost/app", "admin", "1234");
    }

    java.sql.Connection jdbcSenhaDoAmbiente() throws Exception {
        // ok: sentinel-jdbc-senha-fixa
        return java.sql.DriverManager.getConnection("jdbc:mysql://localhost/app", "admin", System.getenv("DB_PASSWORD"));
    }

    java.sql.Connection jdbcSemSenhaLiteral(String senha) throws Exception {
        // ok: sentinel-jdbc-senha-fixa
        return java.sql.DriverManager.getConnection("jdbc:mysql://localhost/app", "admin", senha);
    }

    java.sql.Connection jdbcSenhaVazia() throws Exception {
        // ok: sentinel-jdbc-senha-fixa
        return java.sql.DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
    }

    void oauthSegredoDoClienteNoCodigo(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory()
            .withClient("app")
            // ruleid: sentinel-spring-oauth-segredo-fixo
            .secret("s3cr3t")
            .authorizedGrantTypes("authorization_code");
    }

    void oauthSegredoCodificado(ClientDetailsServiceConfigurer clients, PasswordEncoder enc) throws Exception {
        clients.inMemory()
            .withClient("app")
            // ok: sentinel-spring-oauth-segredo-fixo
            .secret(enc.encode(System.getenv("CLIENT_SECRET")))
            .authorizedGrantTypes("authorization_code");
    }

    void usuarioEmMemoriaComSenhaFixa(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .withUser("admin")
            // ruleid: sentinel-spring-usuario-memoria-senha-fixa
            .password("admin123")
            .roles("ADMIN");
    }

    void usuarioEmMemoriaSenhaDoAmbiente(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .withUser("admin")
            // ok: sentinel-spring-usuario-memoria-senha-fixa
            .password(System.getenv("ADMIN_PASSWORD"))
            .roles("ADMIN");
    }
}

// Volta 36 (terracotta-bank): regras gerais de servlet e de SQL montado com literal.
class ServletVolta36 {

    void redirecionaParametro(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String relay = request.getParameter("relay");
        if (relay == null || relay.isEmpty()) {
            response.sendRedirect(request.getContextPath());
        } else {
            // ruleid: sentinel-open-redirect-requisicao
            response.sendRedirect(relay);
        }
    }

    void redirecionaFixo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String alvo = request.getParameter("x");
        // ok: sentinel-open-redirect-requisicao
        response.sendRedirect(request.getContextPath() + "/index.jsp");
        // ok: sentinel-open-redirect-requisicao
        response.sendRedirect(response.encodeRedirectURL("/home"));
    }

    Object consultaMontada(String username, String senha) {
        // ruleid: sentinel-sql-literal-concatenado
        return runQuery("SELECT * FROM users WHERE username = '" + username + "'", null);
    }

    void atualizaMontada(String id, String nome) {
        // ruleid: sentinel-sql-literal-concatenado
        runUpdate("UPDATE users SET name = '" + nome + "' WHERE id = '" + id + "'");
        // ruleid: sentinel-sql-literal-concatenado
        runUpdate("DELETE FROM users WHERE username = '" + nome + "'");
    }

    void semVariavel(String nome) {
        // ok: sentinel-sql-literal-concatenado
        runUpdate("DELETE FROM users WHERE " + "id = 1");
        // ok: sentinel-sql-literal-concatenado
        log.info("select the best from " + nome);
        // ok: sentinel-sql-literal-concatenado
        runUpdate("UPDATE users SET active = 1");
        // ok: sentinel-sql-literal-concatenado
        System.out.println("Update the " + nome + " set");
    }

    void cookieDeSessao(Object tomcat, Cookie c) {
        // ruleid: sentinel-httponly-desligado
        tomcat.setUseHttpOnly(false);
        // ruleid: sentinel-httponly-desligado
        c.setHttpOnly(false);
        // ok: sentinel-httponly-desligado
        c.setHttpOnly(true);
        // ok: sentinel-httponly-desligado
        tomcat.setUseHttpOnly(true);
    }

    void spelContextoSimples(String entrada, SpelExpressionParser spelParser) {
        SimpleEvaluationContext contexto = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        // ok: sentinel-spel-injection
        spelParser.parseExpression(entrada).getValue(contexto);
    }

    void zipSlipCaminhoEmVariavelConferido(ZipEntry entry, File destino) throws Exception {
        // ok: sentinel-zip-slip
        File saida = new File(destino, entry.getName());
        String caminho = saida.getCanonicalPath();
        if (!caminho.startsWith(destino.getCanonicalPath() + File.separator)) {
            throw new SecurityException("fora do destino");
        }
    }

    void zipSlipCaminhoEmVariavelSemConferir(ZipEntry entry, File destino) throws Exception {
        // ruleid: sentinel-zip-slip
        File saida = new File(destino, entry.getName());
        boolean fora = !saida.getCanonicalPath().startsWith(destino.getCanonicalPath());
    }

    void transformerComAcessoExternoDesligado() throws Exception {
        // ok: sentinel-xxe-parser-xml
        javax.xml.transform.TransformerFactory f = javax.xml.transform.TransformerFactory.newInstance();
        f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    }

    Object xmlDecoder(String xml) throws Exception {
        // ruleid: sentinel-xmldecoder-entrada
        XMLDecoder d = new XMLDecoder(new java.io.ByteArrayInputStream(xml.getBytes()));
        return d.readObject();
    }

    void xmlEncoderNaoDecodifica(java.io.OutputStream saida, Object valor) {
        // ok: sentinel-xmldecoder-entrada
        XMLEncoder e = new XMLEncoder(saida);
        e.writeObject(valor);
    }

    Object xstreamSemLista(String xml) {
        XStream xs = new XStream();
        xs.alias("pessoa", Object.class);
        // ruleid: sentinel-xstream-sem-lista-de-tipos
        return xs.fromXML(xml);
    }

    Object xstreamComLista(String xml) {
        XStream xs = new XStream();
        xs.allowTypes(new Class[] {String.class});
        // ok: sentinel-xstream-sem-lista-de-tipos
        return xs.fromXML(xml);
    }
}
