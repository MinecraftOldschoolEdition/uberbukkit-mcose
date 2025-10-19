package com.legacyminecraft.poseidon.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/**
 * Crypto helper for modern Mojang authentication
 * Handles RSA key generation, AES secret key generation, and server ID calculation
 */
public class CryptoHelper {
    private static KeyPair serverKeyPair;

    static {
        try {
            // Generate RSA key pair for the server (1024-bit to match legacy protocol expectations)
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(1024);
            serverKeyPair = keyGen.generateKeyPair();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the server's RSA key pair
     */
    public static KeyPair getServerKeyPair() {
        return serverKeyPair;
    }

    /**
     * Generate a new AES secret key
     */
    public static SecretKey generateSecretKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        return keyGen.generateKey();
    }

    /**
     * Generate server ID according to Mojang's specification
     * @param baseServerId Base server ID (usually empty string)
     * @param publicKey Server's RSA public key
     * @param secretKey The symmetric AES secret key
     * @return Server ID as hex string
     */
    public static String generateServerId(String baseServerId, PublicKey publicKey, SecretKey secretKey) throws Exception {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        messageDigest.update(baseServerId.getBytes("ISO_8859_1"));
        messageDigest.update(secretKey.getEncoded());
        messageDigest.update(publicKey.getEncoded());
        byte[] digestData = messageDigest.digest();
        return new BigInteger(digestData).toString(16);
    }

    /**
     * Decrypt data using RSA private key
     */
    public static byte[] decryptRSA(byte[] data, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    /**
     * Create SecretKey from byte array
     */
    public static SecretKey createSecretKey(byte[] keyBytes) {
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Create PublicKey from byte array
     */
    public static PublicKey createPublicKey(byte[] keyBytes) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }
}


