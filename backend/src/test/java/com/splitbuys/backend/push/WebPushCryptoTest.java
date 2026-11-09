package com.splitbuys.backend.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {
    private static byte[] b64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    /** The worked example in RFC 8291 section 5 and appendix A: same inputs must give byte-for-byte the same message. */
    @Test
    void reproducesTheRfc8291TestVector() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = b64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");
        KeyPair sender = new KeyPair(
                WebPushCrypto.publicKey(b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")),
                WebPushCrypto.privateKey(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));

        byte[] message = WebPushCrypto.encrypt(plaintext, uaPublic, auth, sender, salt);

        // the RFC prints the 86-byte header and the ciphertext separately; each is its own base64url string
        byte[] header = b64("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] ciphertext = b64("8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ");
        assertEquals(86, header.length);
        byte[] expected = java.util.Arrays.copyOf(header, header.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, expected, header.length, ciphertext.length);
        assertArrayEquals(expected, message);
    }

    @Test
    void theReceiverCanDecryptWhatWeEncrypt() throws Exception {
        // an independent implementation of the receiving side (RFC 8291 section 3.4), to catch a mistake the vector might share
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair device = gen.generateKeyPair();
        byte[] devicePublic = WebPushCrypto.uncompressed((java.security.interfaces.ECPublicKey) device.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] plaintext = "Asha added Dinner · ₹1,000.00".getBytes(StandardCharsets.UTF_8);

        byte[] message = WebPushCrypto.encrypt(plaintext, devicePublic, auth);

        assertEquals(plaintext.length + 1 + 16 + 86, message.length); // header + data + delimiter + GCM tag
        byte[] salt = java.util.Arrays.copyOfRange(message, 0, 16);
        byte[] senderPublic = java.util.Arrays.copyOfRange(message, 21, 86);
        byte[] ciphertext = java.util.Arrays.copyOfRange(message, 86, message.length);

        var agreement = javax.crypto.KeyAgreement.getInstance("ECDH");
        agreement.init(device.getPrivate());
        agreement.doPhase(WebPushCrypto.publicKey(senderPublic), true);
        byte[] ecdh = agreement.generateSecret();
        byte[] prkKey = hmac(auth, ecdh);
        byte[] keyInfo = cat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), devicePublic, senderPublic);
        byte[] ikm = hmac(prkKey, cat(keyInfo, new byte[] {1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = java.util.Arrays.copyOf(hmac(prk, cat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = java.util.Arrays.copyOf(hmac(prk, cat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);
        var aes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(cek, "AES"), new javax.crypto.spec.GCMParameterSpec(128, nonce));
        byte[] padded = aes.doFinal(ciphertext);

        assertEquals(2, padded[padded.length - 1]);
        assertArrayEquals(plaintext, java.util.Arrays.copyOf(padded, padded.length - 1));
    }

    @Test
    void refusesAMessageTooBigForOnePushAndAKeyThatIsNotAPoint() {
        byte[] auth = new byte[16];
        assertThrows(IllegalArgumentException.class, () -> WebPushCrypto.encrypt(new byte[WebPushCrypto.MAX_PLAINTEXT + 1], new byte[65], auth));
        assertThrows(java.security.GeneralSecurityException.class, () -> WebPushCrypto.publicKey(new byte[65]));
        assertThrows(java.security.GeneralSecurityException.class, () -> WebPushCrypto.publicKey(new byte[10]));
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] cat(byte[]... parts) {
        var out = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }
}
