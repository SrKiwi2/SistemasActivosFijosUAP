package com.usic.SistemasActivosFijosUAP.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Encriptar {
    private static final String SECRET_KEY = "MiClaveSecreta23"; // Cambia por tu propia clave secreta

    public static String encrypt(String url) throws Exception {
        SecretKeySpec secretKey = generateSecretKey();
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encryptedBytes = cipher.doFinal(url.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedUrl) throws Exception {
        SecretKeySpec secretKey = generateSecretKey();
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        byte[] encryptedBytes = Base64.getUrlDecoder().decode(encryptedUrl);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec generateSecretKey() {
        byte[] keyBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
