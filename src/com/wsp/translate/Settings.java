package com.wsp.translate;

import android.content.Context;
import android.content.SharedPreferences;

/** 本机配置存储。键名与浏览器扩展 core.js 的 DEFAULTS 对齐（简化版）。 */
public class Settings {
    private final SharedPreferences p;

    public Settings(Context c) {
        p = c.getSharedPreferences("wsp", Context.MODE_PRIVATE);
    }

    public String apiKey()      { return p.getString("apiKey", ""); }
    public String baseUrl()     { return p.getString("baseUrl", "https://api.deepseek.com"); }
    public String model()       { return p.getString("model", "deepseek-chat"); }
    public String draftLang()   { return p.getString("draftLang", "en"); }
    public String incomingLang(){ return p.getString("incomingLang", "zh-CN"); }
    public boolean notifEnabled()   { return p.getBoolean("notifEnabled", true); }
    public boolean bubbleEnabled()  { return p.getBoolean("bubbleEnabled", true); }
    public boolean skipChineseIncoming() { return p.getBoolean("skipChineseIncoming", true); }

    public void put(String k, String v)  { p.edit().putString(k, v).apply(); }
    public void put(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
}
