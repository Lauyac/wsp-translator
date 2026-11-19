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
        // 旧版明文：回读明文并立即以密文覆写
        p.edit().putString("apiKey", Crypto.encrypt(raw)).apply();
        return raw;
    }

    /** 保存 API Key（加密落盘）。 */
    public void setApiKey(String v) {
        p.edit().putString("apiKey", Crypto.encrypt(v == null ? "" : v.trim())).apply();
    }
    public String baseUrl()     { return p.getString("baseUrl", "https://api.deepseek.com"); }
    public String model()       { return p.getString("model", "deepseek-v4-flash"); }
    public String draftLang()   { return p.getString("draftLang", "en"); }
    public String incomingLang(){ return p.getString("incomingLang", "zh-CN"); }
    public boolean notifEnabled()   { return p.getBoolean("notifEnabled", true); }
    public boolean bubbleEnabled()  { return p.getBoolean("bubbleEnabled", true); }
    public boolean skipChineseIncoming() { return p.getBoolean("skipChineseIncoming", true); }

    public void put(String k, String v)  { p.edit().putString(k, v).apply(); }
    public void put(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
}
