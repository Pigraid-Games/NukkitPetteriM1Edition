package cn.nukkit.network.connection.util;

import lombok.experimental.UtilityClass;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Utility methods for creating Bedrock network encryption ciphers.
 * Petter uses protocol >= 428 for CTR mode, older protocols use CFB8.
 */
@UtilityClass
public class EncryptionUtils {

    /**
     * Create a Cipher for Bedrock network encryption/decryption.
     *
     * @param useCtr  true for AES/CTR/NoPadding (protocol >= 428), false for AES/CFB8/NoPadding
     * @param encrypt true for encryption, false for decryption
     * @param key     the AES secret key
     * @return initialized Cipher
     */
    public static Cipher createCipher(boolean useCtr, boolean encrypt, SecretKey key) {
        try {
            byte[] iv;
            String transformation;
            if (useCtr) {
                iv = new byte[16];
                System.arraycopy(key.getEncoded(), 0, iv, 0, 12);
                iv[15] = 2;
                transformation = "AES/CTR/NoPadding";
            } else {
                iv = Arrays.copyOf(key.getEncoded(), 16);
                transformation = "AES/CFB8/NoPadding";
            }
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            throw new AssertionError("Unable to initialize required encryption", e);
        }
    }
}
