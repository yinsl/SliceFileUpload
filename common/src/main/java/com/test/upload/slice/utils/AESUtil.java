package com.test.upload.slice.utils;

import java.security.Key;
import java.security.Security;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * 
 * @ClassName: AESUtil.java
 * @Description: AES128算法，因为java不支持PKCS7Padding，因此使用bouncycastle组件来实现
 * @author 尹顺林
 * @version V1.0
 * @Date 2017年7月29日 上午9:30:45
 */
public class AESUtil {
	// 算法名称
	final String KEY_ALGORITHM = "AES";
	// 加解密算法/模式/填充方式
	final String algorithmStr = "AES/CBC/PKCS7Padding";
	//
	private Key key;
	private Cipher cipher;
	boolean isInited = false;

	byte[] iv = "1234567890123456".getBytes();

	/**
	 * @param length must be equal to 128, 192 or 256
	 * @return
	 * @throws Exception
	 */
	public static byte[] generateKey(int length, String algorithmName) throws Exception {
		// 实例化
		KeyGenerator kgen = KeyGenerator.getInstance(algorithmName);
		// 设置密钥长度
		kgen.init(length);
		// 生成密钥
		SecretKey key = kgen.generateKey();
		// 返回密钥的二进制编码
		return key.getEncoded();
	}

	/**
	 * @param length must be equal to 128, 192 or 256
	 * @return
	 * @throws Exception
	 */
	public static byte[] generateDesKey128() throws Exception {
		// 实例化
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		// 设置密钥长度
		kgen.init(128);
		// 生成密钥
		SecretKey skey = kgen.generateKey();
		// 返回密钥的二进制编码
		return skey.getEncoded();
	}

	public void init(byte[] keyBytes) {

		// 如果密钥不足16位，那么就补足. 这个if 中的内容很重要
		int base = 16;
		if (keyBytes.length % base != 0) {
			int groups = keyBytes.length / base + (keyBytes.length % base != 0 ? 1 : 0);
			byte[] temp = new byte[groups * base];
			Arrays.fill(temp, (byte) 0);
			System.arraycopy(keyBytes, 0, temp, 0, keyBytes.length);
			keyBytes = temp;
		}
		// 初始化
		Security.addProvider(new BouncyCastleProvider());
		// 转化成JAVA的密钥格式
		key = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
		try {
			// 初始化cipher
			cipher = Cipher.getInstance(algorithmStr, "BC");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @Title: encrypt
	 * @Description: 加密 ，采用默认加密向量
	 * @param: @param  content 加密内容
	 * @param: @param  keyBytes 加密key
	 * @param: @return
	 * @return: byte[] 加密后的字节数组
	 * @throws @author zekym
	 * @Date 2017年7月29日 上午9:33:36
	 */
	public byte[] encrypt(String content, byte[] keyBytes) {
		return encrypt(content.getBytes(), keyBytes, iv);
	}

	public byte[] encrypt(String content, byte[] keyBytes, byte[] iv) {
		return encrypt(content.getBytes(), keyBytes, iv);
	}

	public byte[] encrypt(byte[] content, byte[] keyBytes) {
		return encrypt(content, keyBytes, iv);
	}

	public byte[] encrypt(byte[] content, byte[] keyBytes, byte[] iv) {
		byte[] encryptedText = null;
		init(keyBytes);
		try {
			cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
			encryptedText = cipher.doFinal(content);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return encryptedText;
	}

	/**
	 * @Title: decrypt
	 * @Description: 解密方法
	 * @param encryptedData 要解密的字符串
	 * @param keyBytes      解密密钥
	 * @return
	 */
	public byte[] decrypt(byte[] encryptedData, byte[] keyBytes) {
		return decrypt(encryptedData, keyBytes, iv);
	}

	/**
	 * @Title: decrypt
	 * @Description: 解密方法
	 * @param encryptedData
	 * @param keyBytes
	 * @param iv
	 * @return encryptedText
	 */
	public byte[] decrypt(byte[] encryptedData, byte[] keyBytes, byte[] iv) {
		byte[] encryptedText = null;
		init(keyBytes);
		try {
			cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
			encryptedText = cipher.doFinal(encryptedData);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return encryptedText;
	}

	public static void main(String[] args) throws Exception {
		System.out.println("加密密钥：" + new String(generateDesKey128()));
		System.out.println("加密密钥：" + new String(generateDesKey128()));
//		AESUtil aes = new AESUtil();
//		// 加解密 密钥
//		byte[] key = generateDesKey(128);
//		String content = "000A0000000000000A";
//		content = "80010000000000040000000085";
//		// 加密字符串
//		System.out.println("加密前的：" + content);
//		System.out.println("加密密钥：" + new String(key));
//		// 加密方法
//		byte[] enc = aes.encrypt(DatatypeConverter.parseHexBinary(content), key);
//		System.out.println("加密后的内容：" + new String(Hex.encode(enc)));
//		// 解密方法
//		byte[] dec = aes.decrypt(enc, key);
//		System.out.println("解密：" + DatatypeConverter.printHexBinary(dec));
	}

}
