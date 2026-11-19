package com.wsp.translate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DeepSeek（OpenAI 兼容）翻译客户端。
 * 移植自扩展 core.js / providers.js：提示词构造、LRU 缓存（键=原文+语言+端点身份）、错误分类。
 */
public class Translator {

    public static class Result {
        public final boolean ok;
        public final String text;
        public final boolean cached;
        public Result(boolean ok, String text, boolean cached) {
            this.ok = ok; this.text = text; this.cached = cached;
        }
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    /** 内存 LRU 缓存，上限 500 条（对应扩展 cacheByContent 的本地行为）。 */
    private static final Map<String, String> CACHE = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > 500;
        }
    };

    public static void submit(Runnable r) { POOL.execute(r); }

    public static String prompt(String target) {
        return "你是一位专业的外贸翻译助手。把用户发来的内容翻译成" + target
            + "。要求：1. 只输出译文本身，不要任何解释、前缀或引号；"
            + "2. 保留数字、型号、价格、链接、品牌名等关键信息；"
            + "3. 语气自然、符合商务沟通语境；"
            + "4. 如果原文本身就是目标语言或无法翻译，原样输出。";
    }

    /** 供 NLService 做通知去重使用。 */
    public static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    public static Result translate(Settings s, String text, String target) {
        String key = hash(text + "|" + target + "|" + s.baseUrl() + "|" + s.model());
        synchronized (CACHE) {
            String hit = CACHE.get(key);
            if (hit != null) return new Result(true, hit, true);
        }
        try {
            String out = request(s, text, target);
            if (out == null || out.trim().isEmpty()) return new Result(false, "API 返回了空结果，请重试", false);
            synchronized (CACHE) { CACHE.put(key, out); }
            return new Result(true, out, false);
        } catch (Exception e) {
            return new Result(false, friendlyError(e), false);
        }
    }

    /** 错误分类：把底层异常转成用户可操作的中文提示。 */
    static String friendlyError(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) {
            return "请求超时，请检查网络后重试";
        }
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException) {
            return "网络连接失败，请检查手机网络或 API 地址是否正确";
        }
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("HTTP 401") || m.contains("HTTP 403")) {
            return "API Key 无效或无权限，请到应用设置检查 API Key";
        }
        if (m.contains("HTTP 402")) {
            return "API 账户余额不足，请登录 API 平台充值后重试";
        }
        if (m.contains("HTTP 429")) {
            return "请求过于频繁或额度受限，请稍后再试";
        }
        if (m.contains("HTTP 404")) {
            return "API 地址或模型名不存在，请到应用设置检查这两项配置";
        }
        if (m.contains("HTTP 5")) {
            return "API 服务端暂时不可用，请稍后重试";
        }
        return "翻译失败: " + (m.isEmpty() ? "未知错误" : brief(m));
    }

    private static String baseUrl(Settings s) {
        String b = s.baseUrl().trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    public static String request(Settings s, String text, String target) throws Exception {
        HttpURLConnection c = (HttpURLConnection)
            new URL(baseUrl(s) + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + s.apiKey().trim());
        c.setRequestProperty("Content-Type", "application/json");

        JSONObject body = new JSONObject();
        body.put("model", s.model().trim());
        body.put("stream", false);
        JSONArray msgs = new JSONArray();
        msgs.put(msg("system", prompt(target)));
        msgs.put(msg("user", text));
        body.put("messages", msgs);

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String resp = read(is);
        if (code >= 400) throw new Exception("HTTP " + code + " " + brief(resp));
        JSONObject j = new JSONObject(resp);
        if (j.has("error")) {
            String m = j.optJSONObject("error") != null
                ? j.getJSONObject("error").optString("message", brief(resp)) : brief(resp);
            throw new Exception("API 错误: " + m);
        }
        JSONArray choices = j.getJSONArray("choices");
        return choices.getJSONObject(0).getJSONObject("message")
            .getString("content").trim();
    }

    private static JSONObject msg(String role, String content) throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", role);
        o.put("content", content);
        return o;
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String brief(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
