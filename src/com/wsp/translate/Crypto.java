package com.wsp.translate;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * API Key 本机加密存储：AES-256/GCM，密钥由 AndroidKeyStore 生成并保管
 * （密钥不出安全硬件/系统服务，不可被应用外导出）。
 *
 * 密文存储格式："v1:" + Base64(12字节IV) + ":" + Base64(密文+GCM认证标签)。
 * 兼容历史：读到不带 "v1:" 前缀的旧明文时按明文返回，由调用方（Settings）完成懒迁移。
 */
public final class Crypto {

    private static final String KS = "AndroidKeyStore";
    private static final String ALIAS = "wsp_api_key";
    private static final String PREFIX = "v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private Crypto() { }

    /** 解密。入参为空返回空串；入参为旧明文（无前缀）原样返回；解密失败返回空串（宁缺勿崩）。 */
    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        if (!stored.startsWith(PREFIX)) return stored;
        try {
            String[] parts = stored.substring(PREFIX.length()).split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ct = Base64.decode(parts[1], Base64.NO_WRAP);
            if (iv.length != IV_LEN) return "";
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 加密。空串直接返回空串（表示未设置）；失败返回 null（调用方必须拒绝明文落盘）。 */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return PREFIX
                + Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** 取（必要时生成）AndroidKeyStore 中的 AES-256 密钥。 */
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        java.security.Key k = ks.getKey(ALIAS, null);
        if (k instanceof SecretKey) return (SecretKey) k;

        KeyGenerator gen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KS);
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return gen.generateKey();
    }

    // 抑制未使用告警：SecureRandom 备用于未来需要本地生成随机标识的场景
    @SuppressWarnings("unused")
    private static final SecureRandom RANDOM = new SecureRandom();
}
