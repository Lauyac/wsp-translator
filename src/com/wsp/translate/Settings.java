package com.wsp.translate;

import android.content.Context;
import android.content.SharedPreferences;

/** 本机配置存储。键名与浏览器扩展 core.js 的 DEFAULTS 对齐（简化版）。 */
public class Settings {
    private final SharedPreferences p;

    public Settings(Context c) {
        p = c.getSharedPreferences("wsp", Context.MODE_PRIVATE);
    }

    /**
     * API Key：读取时解密；发现旧版本存的明文则自动迁移为加密存储（懒迁移）。
     */
    public String apiKey() {
        String raw = p.getString("apiKey", "");
        if (raw == null || raw.isEmpty()) return "";
        if (raw.startsWith("v1:")) return Crypto.decrypt(raw);
        // 旧版明文：立即以密文覆写；加密不可用时保留原值、不破坏可用性
        String enc = Crypto.encrypt(raw);
        if (enc != null && !enc.isEmpty()) p.edit().putString("apiKey", enc).apply();
        return raw;
    }

    /**
     * 保存 API Key（加密落盘）。
     * @return false 表示加密不可用（AndroidKeyStore 异常），为避免明文落盘已拒绝保存。
     */
    public boolean setApiKey(String v) {
        String enc = Crypto.encrypt(v == null ? "" : v.trim());
        if (enc == null) return false;
        if (enc.isEmpty()) p.edit().remove("apiKey").apply(); // 空串表示清除
        else p.edit().putString("apiKey", enc).apply();
        return true;
    }
    public String baseUrl()     { return p.getString("baseUrl", "https://api.deepseek.com"); }
    public String model()       { return p.getString("model", "deepseek-v4-flash"); }
    public String draftLang()   { return p.getString("draftLang", "en"); }
    public String incomingLang(){ return p.getString("incomingLang", "zh-CN"); }
    public boolean notifEnabled()   { return p.getBoolean("notifEnabled", true); }
    public boolean bubbleEnabled()  { return p.getBoolean("bubbleEnabled", true); }
    public boolean skipChineseIncoming() { return p.getBoolean("skipChineseIncoming", true); }

    /** 悬浮球吸附后的屏幕位置（像素）；未记录时返回 -1。 */
    public int bubbleX() { return p.getInt("bubbleX", -1); }
    public int bubbleY() { return p.getInt("bubbleY", -1); }

    public void put(String k, String v)  { p.edit().putString(k, v).apply(); }
    public void put(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
    public void put(String k, int v)     { p.edit().putInt(k, v).apply(); }
}
