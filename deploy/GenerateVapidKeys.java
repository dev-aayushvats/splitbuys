import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Makes the key pair that identifies this server to browser push services (VAPID, RFC 8292).
 *
 *   java deploy/GenerateVapidKeys.java
 *
 * Put the three printed lines in the backend's environment (deploy/application.env on the server). The public key is
 * not secret; the private key is: never commit it or paste it into a chat. Changing the pair later invalidates every
 * existing subscription, because each was created for the old public key; people would have to turn notifications on again.
 */
public class GenerateVapidKeys {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        ECPrivateKey priv = (ECPrivateKey) pair.getPrivate();

        byte[] point = new byte[65];
        point[0] = 4;
        System.arraycopy(fixed32(pub.getW().getAffineX()), 0, point, 1, 32);
        System.arraycopy(fixed32(pub.getW().getAffineY()), 0, point, 33, 32);

        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        System.out.println("VAPID_PUBLIC_KEY=" + b64.encodeToString(point));
        System.out.println("VAPID_PRIVATE_KEY=" + b64.encodeToString(fixed32(priv.getS())));
        System.out.println("VAPID_SUBJECT=mailto:you@example.com   # a real contact address: push services use it to reach you");
    }

    private static byte[] fixed32(BigInteger n) {
        byte[] raw = n.toByteArray();
        byte[] out = new byte[32];
        int len = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - len, out, 32 - len, len);
        return out;
    }
}
