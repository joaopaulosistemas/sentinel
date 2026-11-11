import javax.servlet.http.Cookie;

class CookieSeguro {
    void m(Cookie c) {
        // ok: sentinel-cookie-secure-desligado
        c.setSecure(true);
    }

    void encerra() {
        // ok: sentinel-system-exit
        throw new IllegalStateException("falha na inicialização");
    }
}
