import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.io.*;
import java.nio.file.*;

public class GenKey {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        KeyPair kp = gen.generateKeyPair();
        String testDir = args[0];
        byte[] privBytes = kp.getPrivate().getEncoded();
        String privB64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privBytes);
        String privPem = "-----BEGIN PRIVATE KEY-----\n" + privB64 + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(Paths.get(testDir, "privateKey.pem"), privPem);
        byte[] pubBytes = kp.getPublic().getEncoded();
        String pubB64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pubBytes);
        String pubPem = "-----BEGIN PUBLIC KEY-----\n" + pubB64 + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(Paths.get(testDir, "publicKey.pem"), pubPem);
        System.out.println("Keys generated OK");
    }
}