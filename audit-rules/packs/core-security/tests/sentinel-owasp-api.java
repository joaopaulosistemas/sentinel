import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.File;
import java.nio.file.Files;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.X509TrustManager;

class ApiCases {

    String ssrf(@QueryParam("url") String destino) throws Exception {
        // ruleid: sentinel-api-ssrf
        return new URL(destino).getHost();
    }

    String ssrfSeguro(@QueryParam("id") String id) throws Exception {
        // ok: sentinel-api-ssrf
        return new URL("https://api.interna/itens/1").getHost();
    }

    File traversal(@PathParam("arquivo") String arquivo) {
        // ruleid: sentinel-api-path-traversal
        return new File("/dados/" + arquivo);
    }

    Path traversalSeguro(@PathParam("arquivo") String arquivo) {
        // ok: sentinel-api-path-traversal
        return Paths.get("/dados").resolve(Paths.get(arquivo).getFileName());
    }

    void cors(javax.ws.rs.core.Response.ResponseBuilder r, javax.servlet.http.HttpServletResponse resp) {
        // ruleid: sentinel-api-cors-wildcard
        resp.setHeader("Access-Control-Allow-Origin", "*");
        // ok: sentinel-api-cors-wildcard
        resp.setHeader("Access-Control-Allow-Origin", "https://app.exemplo.com");
    }

    // ruleid: sentinel-api-cors-wildcard
    @CrossOrigin("*")
    void corsAnotacao() { }

    void csrf(Object http) throws Exception {
        // ruleid: sentinel-api-csrf-disabled
        http.csrf().disable();
        // ok: sentinel-api-csrf-disabled
        http.csrf().and();
    }

    void tls() {
        // ruleid: sentinel-api-tls-validation-disabled
        HttpsURLConnection.setDefaultHostnameVerifier((host, sessao) -> true);
        // ruleid: sentinel-api-tls-validation-disabled
        HostnameVerifier v = new HostnameVerifier() {
            public boolean verify(String h, javax.net.ssl.SSLSession s) { return true; }
        };
        // ok: sentinel-api-tls-validation-disabled
        HostnameVerifier ok = new HostnameVerifier() {
            public boolean verify(String h, javax.net.ssl.SSLSession s) { return h.endsWith(".exemplo.com"); }
        };
    }

    void jwt(String token) {
        // ruleid: sentinel-api-jwt-sem-verificacao
        Jwts.parser().parse(token);
        // ruleid: sentinel-api-jwt-sem-verificacao
        JWT.decode(token);
        // ok: sentinel-api-jwt-sem-verificacao
        Jwts.parser().setSigningKey(chave()).parseClaimsJws(token);
    }

    void jwtChave() {
        // ruleid: sentinel-api-jwt-chave-fixa
        Algorithm.HMAC256("segredo123");
        // ruleid: sentinel-api-jwt-chave-fixa
        Keys.hmacShaKeyFor("chave-fixa-no-codigo".getBytes());
        // ok: sentinel-api-jwt-chave-fixa
        Algorithm.HMAC256(System.getenv("JWT_SECRET"));
    }
}

class JwtEPathNoSpring {
    void parseComChave(String token, String chave) {
        // ruleid: sentinel-api-jwt-sem-verificacao
        Jwts.parser().setSigningKey(chave).parse(token);
        // ok: sentinel-api-jwt-sem-verificacao
        Jwts.parser().setSigningKey(chave).parseClaimsJws(token);
    }

    void decodeSemVerify(String token) {
        // ruleid: sentinel-api-jwt-sem-verificacao
        JWT.decode(token);
    }

    void decodeComVerify(String token, Algorithm algoritmo) {
        var decodificado = JWT.decode(token);
        JWT.require(algoritmo).build().verify(decodificado);
    }

    // ruleid: sentinel-api-jwt-chave-fixa
    public static final String JWT_PASSWORD = TextCodec.BASE64.encode("victory");
    // ok: sentinel-api-jwt-chave-fixa
    public static final String NOME_DO_CAMPO = TextCodec.BASE64.encode("abc");

    byte[] baixar(@RequestParam String nome) throws Exception {
        // ruleid: sentinel-api-path-traversal
        return Files.readAllBytes(new File("/dados", nome).toPath());
    }

    void upload(MultipartFile arquivo, File pasta) throws Exception {
        // ruleid: sentinel-api-path-traversal
        var destino = new File(pasta, arquivo.getOriginalFilename());
        // ok: sentinel-api-path-traversal
        var seguro = new File(pasta, FilenameUtils.getName(arquivo.getOriginalFilename()));
    }

    @GetMapping("/fetch")
    String ssrfParametroSemAnotacao(String url) throws Exception {
        // ruleid: sentinel-api-ssrf
        return new URL(url).getHost();
    }

    @GetMapping("/fetch-ok")
    String ssrfParametroSemAnotacaoConstante(String id) throws Exception {
        // ok: sentinel-api-ssrf
        return new URL("https://api.interna/itens/2").getHost();
    }
}
