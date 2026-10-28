import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

class Exemplo {

    private final Object lock = new Object();
    private boolean pronto;

    void interrupcao() {
        // ruleid: sentinel-cert-interrupted-engolida
        try { Thread.sleep(10); } catch (InterruptedException e) { LOG.warn("acordou"); }

        // ok: sentinel-cert-interrupted-engolida
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // ok: sentinel-cert-interrupted-engolida
        try { Thread.sleep(10); } catch (InterruptedException e) { throw new IllegalStateException(e); }
    }

    void npe(Object o) {
        // ruleid: sentinel-cert-catch-npe
        try { o.toString(); } catch (NullPointerException e) { LOG.warn("nulo"); }

        // ok: sentinel-cert-catch-npe
        try { o.toString(); } catch (IllegalStateException e) { LOG.warn("estado"); }
    }

    void excecoes() {
        // ruleid: sentinel-cert-throw-generico
        throw new RuntimeException("falhou");
    }

    void excecaoGenerica() throws Exception {
        // ruleid: sentinel-cert-throw-generico
        throw new Exception("falhou");
    }

    void excecaoEspecifica() {
        // ok: sentinel-cert-throw-generico
        throw new IllegalStateException("falhou");
    }

    void threads(Runnable r) {
        Thread t = new Thread(r);
        // ruleid: sentinel-cert-thread-run-direto
        t.run();
        // ruleid: sentinel-cert-thread-run-direto
        new Thread(r).run();
        // ok: sentinel-cert-thread-run-direto
        t.start();

        // ruleid: sentinel-cert-thread-stop
        t.stop();
        // ruleid: sentinel-cert-thread-stop
        t.suspend();
        // ok: sentinel-cert-thread-stop
        t.interrupt();
    }

    void espera() throws InterruptedException {
        synchronized (lock) {
            // ruleid: sentinel-cert-wait-fora-de-loop
            lock.wait();
        }
        synchronized (lock) {
            while (!pronto) {
                // ok: sentinel-cert-wait-fora-de-loop
                lock.wait();
            }
        }
    }

    void travas(String nome, Integer numero) {
        // ruleid: sentinel-cert-sync-em-string-ou-boxed
        synchronized (nome) {
            pronto = true;
        }
        // ruleid: sentinel-cert-sync-em-string-ou-boxed
        synchronized (numero) {
            pronto = true;
        }
        // ok: sentinel-cert-sync-em-string-ou-boxed
        synchronized (lock) {
            pronto = true;
        }
        // ruleid: sentinel-cert-sync-em-string-ou-boxed
        synchronized ("trava") {
            pronto = true;
        }
        // ruleid: sentinel-cert-sync-em-string-ou-boxed
        synchronized (Boolean.TRUE) {
            pronto = true;
        }
        // ok: sentinel-cert-sync-em-string-ou-boxed
        synchronized (this) {
            pronto = true;
        }
    }

    void arquivos(File arquivo) throws Exception {
        // ruleid: sentinel-cert-retorno-ignorado-arquivo
        arquivo.delete();
        // ruleid: sentinel-cert-retorno-ignorado-arquivo
        arquivo.mkdirs();
        // ok: sentinel-cert-retorno-ignorado-arquivo
        if (!arquivo.delete()) {
            LOG.warn("não apagou");
        }
        // ok: sentinel-cert-retorno-ignorado-arquivo
        boolean criado = arquivo.createNewFile();
        // ok: sentinel-cert-retorno-ignorado-arquivo
        criado = arquivo.mkdir();
    }

    boolean apagar(File arquivo) {
        // ok: sentinel-cert-retorno-ignorado-arquivo
        return arquivo.delete();
    }

    void textos(String s, byte[] bytes, java.io.InputStream in, java.io.OutputStream out) {
        // ruleid: sentinel-cert-locale-implicito
        String a = s.toLowerCase();
        // ruleid: sentinel-cert-locale-implicito
        String b = s.toUpperCase();
        // ok: sentinel-cert-locale-implicito
        String c = s.toLowerCase(java.util.Locale.ROOT);

        // ruleid: sentinel-cert-charset-implicito
        byte[] d = s.getBytes();
        // ruleid: sentinel-cert-charset-implicito
        String e = new String(bytes);
        // ruleid: sentinel-cert-charset-implicito
        InputStreamReader f = new InputStreamReader(in);
        // ruleid: sentinel-cert-charset-implicito
        OutputStreamWriter g = new OutputStreamWriter(out);
        // ok: sentinel-cert-charset-implicito
        byte[] h = s.getBytes(StandardCharsets.UTF_8);
        // ok: sentinel-cert-charset-implicito
        InputStreamReader i = new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    void aleatorio(byte[] semente) {
        // ruleid: sentinel-cert-securerandom-semente-fixa
        SecureRandom a = new SecureRandom(semente);
        SecureRandom b = new SecureRandom();
        // ruleid: sentinel-cert-securerandom-semente-fixa
        b.setSeed(42L);
        // ok: sentinel-cert-securerandom-semente-fixa
        b.setSeed(a.generateSeed(16));
        // ok: sentinel-cert-securerandom-semente-fixa
        SecureRandom c = new SecureRandom();
    }
}
