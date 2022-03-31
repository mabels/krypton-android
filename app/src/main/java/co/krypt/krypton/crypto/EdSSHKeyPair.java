package co.krypt.krypton.crypto;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import org.libsodium.jni.Sodium;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import co.krypt.krypton.exception.CryptoException;
import co.krypt.krypton.exception.SodiumException;
import co.krypt.krypton.pgp.PGPException;
import co.krypt.krypton.pgp.packet.Ed25519Signature;
import co.krypt.krypton.pgp.packet.HashAlgorithm;
import co.krypt.krypton.pgp.packet.MPInt;
import co.krypt.krypton.pgp.packet.Signature;
import co.krypt.krypton.pgp.publickey.Ed25519PublicKeyData;
import co.krypt.krypton.pgp.publickey.PublicKeyAlgorithm;
import co.krypt.krypton.pgp.publickey.PublicKeyData;
import co.krypt.krypton.pgp.publickey.PublicKeyPacketAttributes;

/**
 * Created by Kevin King on 11/30/16.
 * Copyright 2017. KryptCo, Inc.
 */

public class EdSSHKeyPair implements SSHKeyPairI {
    private static final String TAG = "Ed25519SSHKeyPair";
    private final @NonNull
    byte[] pk;
    private final @NonNull byte[] sk;

    //  PGP public key attribute
    public final long created;

    EdSSHKeyPair(@NonNull byte[] pk, @NonNull byte[] sk, long created) {
        this.pk = pk;
        this.sk = sk;
        this.created = created;
    }

    public String publicKeyDERBase64() {
        return Base64.encodeToString(pk, Base64.DEFAULT);
    }

    public byte[] publicKeySSHWireFormat() throws InvalidKeyException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(SSHWire.encode("ssh-ed25519".getBytes()));
        out.write(SSHWire.encode(pk));

        return out.toByteArray();
    }

    public byte[] publicKeyFingerprint() throws CryptoException {
        try {
            return SHA256.digest(publicKeySSHWireFormat());
        } catch (InvalidKeyException | IOException e) {
            e.printStackTrace();
            throw new CryptoException(e);
        }
    }

    @Override
    public byte[] signDigest(String digest, byte[] data) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, CryptoException, NoSuchProviderException, InvalidKeySpecException {
        //  digest part of ed25519 spec
        return signDigest(data);
    }

    @Override
    public byte[] signDigestAppendingPubkey(byte[] data, String algo) throws CryptoException {
        try {
            ByteArrayOutputStream dataWithPubkey = new ByteArrayOutputStream();
            dataWithPubkey.write(data);
            dataWithPubkey.write(SSHWire.encode(publicKeySSHWireFormat()));

            byte[] signaturePayload = dataWithPubkey.toByteArray();

            return signDigest(signaturePayload);
        } catch (IOException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            throw new CryptoException(e);
        }
    }

    @Override
    public boolean verifyDigest(String digest, byte[] signature, byte[] data) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, CryptoException, InvalidKeySpecException, NoSuchProviderException {
        //  digest part of ed25519 spec
        return verifyDigest(signature, data);
    }

    @Override
    public PublicKeyData pgpPublicKeyData() {
        return new Ed25519PublicKeyData(pk);
    }

    @Override
    public PublicKeyPacketAttributes pgpPublicKeyPacketAttributes() {
        return new PublicKeyPacketAttributes(
                created,
                PublicKeyAlgorithm.ED25519
        );
    }

    @Override
    public Signature pgpSign(HashAlgorithm hash, byte[] data) throws PGPException, NoSuchAlgorithmException, CryptoException, SignatureException, NoSuchProviderException, InvalidKeyException, InvalidKeySpecException {
        byte[] signatureBytes = signDigest(
                hash.digest().digest(data)
        );
        if (signatureBytes.length != 64) {
            throw new SodiumException("unexpected signature length");
        }
        byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
        byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
        return new Ed25519Signature(
                new MPInt(
                        r
                ),
                new MPInt(
                        s
                )
        );
    }

    public byte[] signDigest(byte[] data) throws SignatureException {
        long start = System.currentTimeMillis();
        byte[] signature = new byte[Sodium.crypto_sign_ed25519_bytes()];
        int[] signatureLengthPointer = new int[1];
        int result = Sodium.crypto_sign_ed25519_detached(signature, signatureLengthPointer, data, data.length, sk);
        if (result != 0) {
            throw new SignatureException("non-zero sodium return: " + result);
        }
        long stop = System.currentTimeMillis();
        Log.d(TAG, "signature took " + String.valueOf((stop - start) / 1000.0) + " seconds");
        return signature;
    }

    public boolean verifyDigest(byte[] signature, byte[] data) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        return 0 == Sodium.crypto_sign_ed25519_verify_detached(signature, data, data.length, pk);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EdSSHKeyPair that = (EdSSHKeyPair) o;

        return publicKeyDERBase64().equals(that.publicKeyDERBase64());
    }
}
