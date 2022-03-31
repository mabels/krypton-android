package co.krypt.krypton;

import static org.junit.Assert.assertTrue;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.libsodium.jni.Sodium;

import java.security.SecureRandom;
import java.util.Arrays;

import co.krypt.krypton.exception.CryptoException;
import co.krypt.krypton.pairing.Pairing;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class SealInstrumentedTest {
    @Test
    public void seal_inverts() throws Exception {
        byte[] pubKey = new byte[Sodium.crypto_box_publickeybytes()];
        byte[] privKey = new byte[Sodium.crypto_box_secretkeybytes()];
        assertTrue(0 == Sodium.crypto_box_seed_keypair(pubKey, privKey, SecureRandom.getSeed(Sodium.crypto_box_seedbytes())));
        Pairing pairing = Pairing.generate(InstrumentationRegistry.getInstrumentation().getContext(), pubKey, "workstation", null, null);
        for (int i = 0; i < 1024; i++) {
            byte[] message = SecureRandom.getSeed(i);
            byte[] ciphertext = pairing.seal(message);
            byte[] unsealed = pairing.unseal(ciphertext);
            assertTrue(Arrays.equals(message, unsealed));
        }
    }

    @Test(expected = CryptoException.class)
    public void sealTamper_fails() throws Exception {
        byte[] pubKey = new byte[Sodium.crypto_box_publickeybytes()];
        byte[] privKey = new byte[Sodium.crypto_box_secretkeybytes()];
        assertTrue(0 == Sodium.crypto_box_seed_keypair(pubKey, privKey, SecureRandom.getSeed(Sodium.crypto_box_seedbytes())));
        Pairing pairing = Pairing.generate(InstrumentationRegistry.getInstrumentation().getContext(), pubKey, "workstation", null, null);
        byte[] message = SecureRandom.getSeed(37);
        byte[] ciphertext = pairing.seal(message);
        ciphertext[17] ^= 0xff;
        byte[] unsealed = pairing.unseal(ciphertext);
    }
    static {
        System.loadLibrary("sodiumjni");
    }
}