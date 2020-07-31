package io.vertx.common;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import io.vertx.common.message.QueryMessage;

public class AES256Util {

	private static Logger logger = Logger.getLogger(AES256Util.class);

    private static volatile AES256Util INSTANCE;

    final static String secretKey = "jmlim12345bbbbbaaaaa123456789067"; //32bit
    final static String secretKeySecond = "secondkeyvalueHopeitworks1234567"; //32bit
    static String IV = secretKey.substring(0, 16); //16bit

    public static AES256Util getInstance() {
        if (INSTANCE == null) {
            synchronized (AES256Util.class) {
                if (INSTANCE == null)
                    INSTANCE = new AES256Util();
            }
        }
        return INSTANCE;
    }

    private AES256Util() {
        IV = secretKey.substring(0, 16);
    }

    //암호화
    public static String AES_Encode(String str) throws java.io.UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        byte[] keyData = secretKey.getBytes();

        SecretKey secureKey = new SecretKeySpec(keyData, "AES");

        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, secureKey, new IvParameterSpec(IV.getBytes()));

        byte[] encrypted = c.doFinal(str.getBytes("UTF-8"));
        String enStr = new String(Base64.encodeBase64(encrypted));

        logger.info("encoded success");
        return enStr;
    }

    //복호화
    public static String AES_Decode(String str) throws java.io.UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        byte[] keyData = secretKey.getBytes();
        SecretKey secureKey = new SecretKeySpec(keyData, "AES");
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, secureKey, new IvParameterSpec(IV.getBytes("UTF-8")));

        byte[] byteStr = Base64.decodeBase64(str.getBytes());
        String value = new String(c.doFinal(byteStr), "UTF-8");
        
        //logger.info("decoded success");
        return value;
    }
    
    /**
     * 1. get all list of data from db(or from sharedData)
     * 2. decrypt queryString with first_Secret_Key
     * 3. encrypt all queryString with second_Secret_key
     * 4. ReInsert into db
     * 5. Update sharedData
     */
    private static String AES_Change_Key(String str) throws java.io.UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException{
        //decrypt using the old secret key 
    	byte[] keyData = secretKey.getBytes();
        SecretKey secureKey = new SecretKeySpec(keyData, "AES");
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, secureKey, new IvParameterSpec(IV.getBytes("UTF-8")));
        
        byte[] byteStr = Base64.decodeBase64(str.getBytes());
        String beforeValue = new String(c.doFinal(byteStr), "UTF-8");
        
        //encrypt using the new secret key 
        byte[] keyDataSecond = secretKeySecond.getBytes();

        SecretKey secureKeySecond = new SecretKeySpec(keyDataSecond, "AES");

        Cipher cSecond = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cSecond.init(Cipher.ENCRYPT_MODE, secureKeySecond, new IvParameterSpec(IV.getBytes()));

        byte[] encrypted = cSecond.doFinal(beforeValue.getBytes("UTF-8"));
        String enStr = new String(Base64.encodeBase64(encrypted));
    	return enStr;
    }
}