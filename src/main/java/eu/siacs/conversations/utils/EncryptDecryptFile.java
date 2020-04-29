package eu.siacs.conversations.utils;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;

public class EncryptDecryptFile {
    private static String cipher_mode = "AES/CBC/PKCS5Padding";

    public static void encrypt(FileInputStream iFile, FileOutputStream oFile, String iKey) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        byte[] key = iKey.getBytes("UTF-8");
        Log.d(Config.LOGTAG, "Cipher key: " + Arrays.toString(key));
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        Log.d(Config.LOGTAG, "Cipher sha: " + sha.toString());
        key = sha.digest(key);
        Log.d(Config.LOGTAG, "Cipher sha key: " + Arrays.toString(key));
        key = Arrays.copyOf(key, 16); // use only first 128 bit
        Log.d(Config.LOGTAG, "Cipher sha key 16 bytes: " + Arrays.toString(key));
        SecretKeySpec sks = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(cipher_mode);
        cipher.init(Cipher.ENCRYPT_MODE, sks);
        Log.d(Config.LOGTAG, "Cipher IV: " + Arrays.toString(cipher.getIV()));
        CipherOutputStream cos = new CipherOutputStream(oFile, cipher);
        Log.d(Config.LOGTAG, "Encryption with: " + cos.toString());
        int b;
        byte[] d = new byte[8];
        while ((b = iFile.read(d)) != -1) {
            cos.write(d, 0, b);
        }
        cos.flush();
        cos.close();
        iFile.close();
    }

    public static void decrypt(FileInputStream iFile, FileOutputStream oFile, String iKey) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        byte[] key = iKey.getBytes("UTF-8");
        Log.d(Config.LOGTAG, "Cipher key: " + Arrays.toString(key));
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        Log.d(Config.LOGTAG, "Cipher sha: " + sha.toString());
        key = sha.digest(key);
        Log.d(Config.LOGTAG, "Cipher sha key: " + Arrays.toString(key));
        key = Arrays.copyOf(key, 16); // use only first 128 bit
        Log.d(Config.LOGTAG, "Cipher sha key 16 bytes: " + Arrays.toString(key));
        SecretKeySpec sks = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(cipher_mode);
        cipher.init(Cipher.DECRYPT_MODE, sks);
        Log.d(Config.LOGTAG, "Cipher IV: " + Arrays.toString(cipher.getIV()));
        CipherInputStream cis = new CipherInputStream(iFile, cipher);
        Log.d(Config.LOGTAG, "Decryption with: " + cis.toString());
        int b;
        byte[] d = new byte[8];
        while ((b = cis.read(d)) != -1) {
            oFile.write(d, 0, b);
        }
        oFile.flush();
        oFile.close();
        cis.close();
    }
}
