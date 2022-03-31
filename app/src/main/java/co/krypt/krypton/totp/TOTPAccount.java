package co.krypt.krypton.totp;

import android.net.Uri;

import com.google.common.io.BaseEncoding;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.net.URISyntaxException;
import java.sql.Timestamp;

import javax.annotation.Nullable;

import co.krypt.krypton.crypto.TOTP;
import co.krypt.krypton.exception.CryptoException;

@DatabaseTable(tableName = "totp_accounts")
public class TOTPAccount {
    @DatabaseField(columnName = "registration_uri", id = true)
    public String uri;

    @DatabaseField(columnName = "label")
    public String label;

    @DatabaseField(columnName = "secret", dataType = DataType.BYTE_ARRAY)
    private byte[] secret;

    @DatabaseField(columnName = "issuer")
    @Nullable
    public String issuer;

    @DatabaseField(columnName = "algorithm")
    @Nullable
    String algorithm;

    @DatabaseField(columnName = "digits")
    @Nullable
    int digits;
    
    @DatabaseField(columnName = "period")
    @Nullable
    public int period;

    @DatabaseField(columnName = "creation_time")
    public Timestamp creationTime;

    protected TOTPAccount() {};

    public TOTPAccount(String uri) throws URISyntaxException {
        this.uri = uri;
        Uri parsedUri = Uri.parse(uri);
        if (!parsedUri.getScheme().equals("otpauth")) {
            throw new URISyntaxException(uri, "The given scheme is not otpauth");
        }
        if (!parsedUri.getAuthority().equals("totp")) {
            throw new URISyntaxException(uri, "The given host is not totp");
        }
        this.label = parsedUri.getPath();

        this.secret = BaseEncoding.base32().decode(parsedUri.getQueryParameter("secret"));
        this.issuer = parsedUri.getQueryParameter("issuer");
        this.algorithm = parsedUri.getQueryParameter("algorithm");
        String digitString = parsedUri.getQueryParameter("digits");
        this.digits = digitString != null ? Integer.decode(digitString) : TOTP.DEFAULT_OTP_LENGTH;
        String periodString = parsedUri.getQueryParameter("period");
        this.period = periodString != null ? Integer.decode(periodString) : TOTP.DEFAULT_PERIOD;
        this.creationTime = new Timestamp(System.currentTimeMillis());
    }

    public String getOtp() throws CryptoException {
        return TOTP.generateOtp(secret, period, digits);
    }

    // Returns the number of seconds that the current OTP has been valid
    public int getOtpAge() {
        return (int) (System.currentTimeMillis() / 1000) % period;
    }

    /**
     * Retrieves the account name from the URI. The label takes one of two forms:
     * "accountname", or "issuer:accountname"
     * https://github.com/google/google-authenticator/wiki/Key-Uri-Format
     * @return
     */
    public String getUsername() {
        String[] parts = label.split(":");
        if(parts.length == 2) {
            return parts[1];
        }
        else {
            return parts[0];
        }
    }
}
