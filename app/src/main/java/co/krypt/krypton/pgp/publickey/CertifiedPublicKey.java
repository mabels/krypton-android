package co.krypt.krypton.pgp.publickey;

import android.util.Log;

import androidx.core.util.Pair;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

import co.krypt.krypton.pgp.packet.InvalidPacketTagException;
import co.krypt.krypton.pgp.packet.InvalidUTF8Exception;
import co.krypt.krypton.pgp.packet.PacketHeader;
import co.krypt.krypton.pgp.packet.PacketType;
import co.krypt.krypton.pgp.packet.SignedSignatureAttributes;
import co.krypt.krypton.pgp.packet.UnsupportedHashAlgorithmException;
import co.krypt.krypton.pgp.packet.UnsupportedNewFormatException;
import co.krypt.krypton.pgp.packet.UnsupportedOldPacketLengthTypeException;
import co.krypt.krypton.pgp.packet.UnsupportedSignatureVersionException;
import co.krypt.krypton.pgp.packet.UserIDPacket;
import co.krypt.krypton.pgp.subpacket.DuplicateSubpacketException;
import co.krypt.krypton.pgp.subpacket.InvalidSubpacketLengthException;
import co.krypt.krypton.pgp.subpacket.UnsupportedCriticalSubpacketTypeException;

/**
 * Created by Kevin King on 6/13/17.
 * Copyright 2017. KryptCo, Inc.
 */

public class CertifiedPublicKey {
    public final PublicKeyPacket publicKeyPacket;
    public final List<Pair<UserIDPacket, List<SignedSignatureAttributes>>> identities;

    public CertifiedPublicKey(PublicKeyPacket publicKeyPacket, List<Pair<UserIDPacket, List<SignedSignatureAttributes>>> identities) {
        this.publicKeyPacket = publicKeyPacket;
        this.identities = identities;
    }

    public static CertifiedPublicKey parse(DataInputStream in) throws InvalidPacketTagException, UnsupportedOldPacketLengthTypeException, UnsupportedNewFormatException, UnsupportedPublicKeyAlgorithmException, UnsupportedPublicKeyVersionException, InvalidEd25519PublicKeyFormatException, IOException, InvalidUTF8Exception, DuplicateSubpacketException, NoSuchAlgorithmException, UnsupportedHashAlgorithmException, InvalidSubpacketLengthException, UnsupportedCriticalSubpacketTypeException, UnsupportedSignatureVersionException {
        PublicKeyPacket publicKeyPacket = null;
        boolean lastPacketUserIDOrSignature = false;
        List<Pair<UserIDPacket, List<SignedSignatureAttributes>>> identities = new LinkedList<>();
        while (true) {
            try {
                PacketHeader header = PacketHeader.parse(in);
                Log.d("PGP", "found packet with type " + header.tag.packetType.toString());
                switch (header.tag.packetType) {
                    case SIGNATURE:
                        SignedSignatureAttributes signaturePacket = SignedSignatureAttributes.parse(header, in);
                        if (lastPacketUserIDOrSignature && identities.size() > 0) {
                            identities.get(identities.size() - 1).second.add(signaturePacket);
                        }
                        break;
                    case PUBLIC_KEY:
                        if (publicKeyPacket != null) {
                            //  only accept first public key packet
                            in.skip(header.length.bodyLength);
                            continue;
                        }
                        publicKeyPacket = PublicKeyPacket.parse(
                                header,
                                in);
                        break;
                    case USER_ID:
                        identities.add(new Pair<UserIDPacket, List<SignedSignatureAttributes>>(UserIDPacket.parse(header, in), new LinkedList<SignedSignatureAttributes>()));
                        break;
                    default:
                        in.skip(header.length.bodyLength);
                        break;
                }
                lastPacketUserIDOrSignature = header.tag.packetType == PacketType.USER_ID || header.tag.packetType == PacketType.SIGNATURE;
            } catch (EOFException e) {
                break;
            }
        }
        return new CertifiedPublicKey(
                publicKeyPacket,
                identities
        );
    }
}
