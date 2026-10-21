import java.io.ObjectInputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;

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
}
