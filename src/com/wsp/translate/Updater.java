package com.wsp.translate;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 基于 GitHub Releases 的在线更新检查。
 *
 * 流程与规则：
 * 1) 调 GitHub Releases API 获取最新 Release 的资产清单；
 * 2) 从资产中的 update.json 读取 versionName / versionCode / APK 文件名 / SHA-256；
 * 3) 版本判断只基于 versionCode，且服务端发布流程必须保证 versionCode 单调递增；
 * 4) 下载 APK 时流式计算 SHA-256，与 update.json 中的值比对；
 * 5) 校验失败：中止更新、删除已下载文件、提示错误，绝不进入安装流程。
 */
public final class Updater {

    /** TODO: 仓库公开后改为实际仓库（"用户名/仓库名"）。 */
    public static final String UPDATE_REPO = "Lauyac/wsp-translator";

    /** 单个更新包大小上限：100MB，防止异常资产撑爆存储。 */
    private static final long MAX_APK_BYTES = 100L * 1024 * 1024;

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final File apkFile;        // 仅在版本更新且 SHA-256 校验通过时非空
        public final String newVersionName;
        private Result(boolean ok, String message, File apkFile, String newVersionName) {
            this.ok = ok; this.message = message;
            this.apkFile = apkFile; this.newVersionName = newVersionName;
        }
        public static Result ok(String msg, File f, String v) { return new Result(true, msg, f, v); }
        public static Result fail(String msg) { return new Result(false, msg, null, null); }
    }

    /** GitHub API 返回 404（仓库无 Release）时的专用异常。 */
    private static class HttpCodeException extends Exception {
        final int code;
        HttpCodeException(int code) { super("HTTP " + code); this.code = code; }
    }

    private Updater() { }

    public static Result check(Context ctx) {
        try {
            // 1. 最新 Release 的资产清单
            String rel = httpGet(
                "https://api.github.com/repos/" + UPDATE_REPO + "/releases/latest", null);
            JSONObject j = new JSONObject(rel);
            JSONArray assets = j.getJSONArray("assets");
            String manifestUrl = null;
            String apkUrl = null;
            String apkName = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String name = a.optString("name", "");
                String url = a.optString("browser_download_url", "");
                if ("update.json".equals(name)) manifestUrl = url;
                if (name.endsWith(".apk")) { apkUrl = url; apkName = name; }
            }
            if (manifestUrl == null) {
                return Result.fail("最新 Release 缺少 update.json 清单，无法安全校验，已中止更新检查");
            }
            if (apkUrl == null) {
                return Result.fail("最新 Release 未附带 APK 更新包，已中止更新检查");
            }

            // 2. update.json 清单（versionName / versionCode / apk / sha256）
            JSONObject m = new JSONObject(httpGet(manifestUrl, null));
            int newCode = m.getInt("versionCode");
            String newName = m.getString("versionName");
            String sha = m.optString("sha256", "").trim().toLowerCase();
            String wantApk = m.optString("apk", apkName);
            if (sha.isEmpty()) {
                return Result.fail("update.json 缺少 SHA-256 校验值，出于安全考虑已中止更新");
            }
            if (!wantApk.equals(apkName)) {
                // 清单声明的 APK 与 Release 中的资产不一致：拒绝更新
                return Result.fail("update.json 与 Release 资产不一致，已中止更新检查");
            }

            // 3. 版本判断：只基于 versionCode（发布流程保证其单调递增）
            int cur = ctx.getPackageManager()
                .getPackageInfo(ctx.getPackageName(), 0).versionCode;
            if (newCode <= cur) {
                return Result.ok("已是最新版本（versionCode " + cur + " ≥ " + newCode + "）", null, null);
            }

            // 4. 下载更新包并流式计算 SHA-256
            File dir = new File(ctx.getFilesDir(), "update");
            if (!dir.exists()) dir.mkdirs();
            final File f = new File(dir, wantApk);
            String actual;
            try {
                actual = downloadAndHash(apkUrl, f, MAX_APK_BYTES);
            } catch (Exception e) {
                f.delete(); // 下载中断/超限：不留半包
                throw e;
            }

            // 5. SHA-256 校验：失败即中止，删除文件，绝不安装
            if (!actual.equalsIgnoreCase(sha)) {
                f.delete();
                return Result.fail("SHA-256 校验失败（期望 " + shortHash(sha)
                    + "，实际 " + shortHash(actual) + "），已中止更新并删除下载文件");
            }
            return Result.ok("已下载 v" + newName + " 并通过 SHA-256 校验，可安装", f, newName);
        } catch (HttpCodeException e) {
            if (e.code == 404) {
                return Result.fail("暂无可用发布版本（仓库尚未发布任何 Release）");
            }
            return Result.fail("更新检查失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail("更新检查失败: " + Translator.friendlyError(e));
        }
    }

    private static String httpGet(String url, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "wsp-translator-updater");
        if (accept != null) c.setRequestProperty("Accept", accept);
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = read(is);
        c.disconnect();
        if (code >= 400) throw new HttpCodeException(code);
        return body;
    }

    /** 下载到文件并同步计算 SHA-256，超限即中止。 */
    private static String downloadAndHash(String url, File f, long maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "wsp-translator-updater");
        c.setRequestProperty("Accept", "application/octet-stream");
        int code = c.getResponseCode();
        if (code >= 400) throw new HttpCodeException(code);
        InputStream in = c.getInputStream();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[8192];
        long total = 0;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new Exception("更新包超出大小限制，已中止下载");
                md.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } finally {
            out.close();
            in.close();
            c.disconnect();
        }
        if (total == 0) throw new Exception("下载内容为空，已中止更新");
        return hex(md.digest());
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String shortHash(String h) {
        return (h == null || h.length() < 12) ? h : h.substring(0, 12) + "…";
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
}
