import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Random;
import javax.crypto.Cipher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// Casos sintéticos escritos para o Sentinel (não copiados de nenhum projeto).
class ServletCases extends HttpServlet {

    void caminho(HttpServletRequest request) throws IOException {
        String nome = request.getParameter("arquivo");
        // ruleid: sentinel-servlet-path-traversal
        File f = new File("/dados/" + nome);
        // ok: sentinel-servlet-path-traversal
        File seguro = new File("/dados/fixo.txt");
        // ok: sentinel-servlet-path-traversal
        File soNome = new File("/dados/" + new File(nome).getName());
    }

    void sessao(HttpServletRequest request) {
        String valor = request.getParameter("usuario");
        // ruleid: sentinel-servlet-trust-boundary
        request.getSession().setAttribute("usuario", valor);
        // ruleid: sentinel-servlet-trust-boundary
        request.getSession().putValue(valor, "1");
        // ok: sentinel-servlet-trust-boundary
        request.getSession().setAttribute("usuario", "fixo");
    }

    void comando(HttpServletRequest request) throws IOException {
        String arg = request.getParameter("host");
        // ruleid: sentinel-command-injection-requisicao
        Runtime.getRuntime().exec(arg, new String[]{"A=1"});
        // ok: sentinel-command-injection-requisicao
        Runtime.getRuntime().exec("ls", new String[]{"A=1"});
    }

    void xss(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String texto = request.getParameter("q");
        // ruleid: sentinel-xss-response-write
        response.getWriter().println(texto);
        // ok: sentinel-xss-response-write
        response.getWriter().println(org.owasp.esapi.ESAPI.encoder().encodeForHTML(texto));
        // ok: sentinel-xss-response-write
        response.getWriter().println(org.apache.commons.text.StringEscapeUtils.escapeHtml4(texto));
    }

    void cookies(HttpServletResponse response) {
        // ruleid: sentinel-cookie-sem-secure
        javax.servlet.http.Cookie sem = new javax.servlet.http.Cookie("a", "1");
        response.addCookie(sem);
        Cookie ok = new Cookie("b", "2");
        // ok: sentinel-cookie-sem-secure
        ok.setSecure(true);
        // ruleid: sentinel-cookie-secure-desligado
        ok.setSecure(false);
    }

    void cripto() throws Exception {
        // ruleid: sentinel-crypto-hash-fraco
        MessageDigest.getInstance("SHA1", "SUN");
        // ruleid: sentinel-crypto-hash-fraco
        java.security.MessageDigest.getInstance("md5");
        // ok: sentinel-crypto-hash-fraco
        MessageDigest.getInstance("SHA-512", "SUN");
        // ruleid: sentinel-crypto-cifra-fraca
        Cipher.getInstance("DESede/CBC/PKCS5Padding");
        // ruleid: sentinel-crypto-cifra-fraca
        Cipher.getInstance("AES");
        // ruleid: sentinel-crypto-cifra-fraca
        Cipher.getInstance("AES/ECB/PKCS5Padding");
        // ok: sentinel-crypto-cifra-fraca
        Cipher.getInstance("AES/GCM/NoPadding");
        // ok: sentinel-crypto-cifra-fraca
        Cipher.getInstance("AES/CCM/NoPadding");
    }

    void aleatorio() {
        // ruleid: sentinel-crypto-random-servidor
        int a = new Random().nextInt();
        // ruleid: sentinel-crypto-random-servidor
        double b = Math.random();
        // ok: sentinel-crypto-random-servidor
        java.security.SecureRandom s = new java.security.SecureRandom();
    }
}
