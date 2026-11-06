import java.io.ObjectInputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class CryptoCases {

    void hash() throws Exception {
        // ruleid: sentinel-crypto-hash-fraco
        MessageDigest.getInstance("MD5");
        // ruleid: sentinel-crypto-hash-fraco
        MessageDigest.getInstance("SHA-1");
        // ok: sentinel-crypto-hash-fraco
        MessageDigest.getInstance("SHA-256");
    }

    void cifra() throws Exception {
        // ruleid: sentinel-crypto-cifra-fraca
        Cipher.getInstance("DES/CBC/PKCS5Padding");
        // ruleid: sentinel-crypto-cifra-fraca
        Cipher.getInstance("AES/ECB/PKCS5Padding");
        // ruleid: sentinel-crypto-cifra-fraca
        Cipher.getInstance("AES");
        // ok: sentinel-crypto-cifra-fraca
        Cipher.getInstance("AES/GCM/NoPadding");
    }

    String gerarToken() {
        // ruleid: sentinel-crypto-random-previsivel
        return Long.toString(new java.util.Random().nextLong());
    }

    double gerarSenhaTemporaria() {
        // ruleid: sentinel-crypto-random-previsivel
        return Math.random();
    }

    int sortearCor() {
        // ok: sentinel-crypto-random-previsivel
        return (int) (Math.random() * 10);
    }

    byte[] gerarSalt() {
        // ok: sentinel-crypto-random-previsivel
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    Object ler(java.io.InputStream in) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(in);
        // ruleid: sentinel-desserializacao-insegura
        return ois.readObject();
    }

    Object lerJson(com.fasterxml.jackson.databind.ObjectMapper m, String json) throws Exception {
        // ok: sentinel-desserializacao-insegura
        return m.readValue(json, Object.class);
    }

    Object lerComRecurso(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            // ruleid: sentinel-desserializacao-insegura
            return ois.readObject();
        }
    }

    Object lerDeParametro(ObjectInputStream entrada) throws Exception {
        // ruleid: sentinel-desserializacao-insegura
        return entrada.readObject();
    }

    // ruleid: sentinel-crypto-chave-fixa-no-codigo
    String chaveAes = "uma chave escrita no codigo 123";
    String nome = "valor qualquer";
    // ok: sentinel-crypto-chave-fixa-no-codigo
    static final String KEY_USER = "user_id";
    // ok: sentinel-crypto-chave-fixa-no-codigo
    static final String SECRET_HEADER = "X-Secret";
    // ok: sentinel-crypto-chave-fixa-no-codigo
    static final String CHAVE_CONFIG = "app.crypto.key";
    // ruleid: sentinel-crypto-chave-fixa-no-codigo
    String segredoSimples = "supersecretkey";
    byte[] iv = new byte[16];

    void chaveFixa(String entrada) throws Exception {
        // ruleid: sentinel-crypto-chave-fixa-no-codigo
        new SecretKeySpec("0123456789abcdef".getBytes("UTF-8"), "AES");
        new SecretKeySpec(chaveAes.getBytes("UTF-8"), "AES");
        // ok: sentinel-crypto-chave-fixa-no-codigo
        new SecretKeySpec(entrada.getBytes("UTF-8"), "AES");
        // ok: sentinel-crypto-chave-fixa-no-codigo
        nome.getBytes("UTF-8");
    }

    void ivFixo() {
        // ruleid: sentinel-crypto-iv-fixo
        new IvParameterSpec(new byte[16]);
        // ruleid: sentinel-crypto-iv-fixo
        new IvParameterSpec("1234567890123456".getBytes());
        // ok: sentinel-crypto-iv-fixo
        byte[] aleatorio = new byte[16];
        new SecureRandom().nextBytes(aleatorio);
        new IvParameterSpec(aleatorio);
    }
}
