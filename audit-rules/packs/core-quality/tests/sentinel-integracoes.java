import java.net.http.*;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.*;

class Integracoes {

    void clientes() throws Exception {
        // ruleid: sentinel-int-httpclient-sem-timeout
        HttpClient a = HttpClient.newHttpClient();

        // ruleid: sentinel-int-httpclient-sem-timeout
        HttpClient b = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        // ok: sentinel-int-httpclient-sem-timeout
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        // ok: sentinel-int-httpclient-sem-timeout
        HttpClient d = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    }

    void requisicoes(URI uri) {
        // ruleid: sentinel-int-httprequest-sem-timeout
        HttpRequest a = HttpRequest.newBuilder(uri).GET().build();

        // ok: sentinel-int-httprequest-sem-timeout
        HttpRequest b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
    }

    void rest() {
        // ruleid: sentinel-int-resttemplate-sem-timeout
        RestTemplate t = new RestTemplate();

        // ok: sentinel-int-resttemplate-sem-timeout
        RestTemplate u = builder.setConnectTimeout(Duration.ofSeconds(2)).build();
    }

    void conexao(URL url) throws Exception {
        // ruleid: sentinel-int-urlconnection-sem-timeout
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
    }

    void conexaoOk(URL url) throws Exception {
        // ok: sentinel-int-urlconnection-sem-timeout
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
    }

    void banco() throws Exception {
        // ruleid: sentinel-int-jdbc-driver-manager
        Object c = DriverManager.getConnection("jdbc:postgresql://db/x");
    }

    void pools() {
        // ruleid: sentinel-int-pool-ilimitado
        ExecutorService a = Executors.newCachedThreadPool();

        // ok: sentinel-int-pool-ilimitado
        ExecutorService b = Executors.newFixedThreadPool(4);

        // ok: sentinel-int-pool-ilimitado
        ExecutorService c = Executors.newVirtualThreadPerTaskExecutor();
    }

    void urls() {
        // ruleid: sentinel-int-http-sem-tls
        String a = "http://api.parceiro.com.br/v1";

        // ok: sentinel-int-http-sem-tls
        String b = "https://api.parceiro.com.br/v1";

        // ok: sentinel-int-http-sem-tls
        String c = "http://localhost:8080/api";

        // ok: sentinel-int-http-sem-tls
        String d = "http://127.0.0.1:9000";

        // ok: sentinel-int-http-sem-tls
        String e = "http://postgres.svc.cluster.local:5432";

        // ok: sentinel-int-http-sem-tls
        String f = "http://apache.org/xml/features/disallow-doctype-decl";

        // ok: sentinel-int-http-sem-tls
        String g = "http://adaptivecards.io/schemas/adaptive-card.json";
    }
}
