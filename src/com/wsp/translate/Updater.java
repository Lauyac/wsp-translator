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
 * 基于 GitHub Releases 的在线更新（两阶段）。
 *
 * 阶段一 checkInfo()：获取最新 Release 信息（版本号、versionCode、更新说明），
 *   只读取清单、不下载数据；由界面展示版本信息与变更内容，用户确认后才进入阶段二。
 * 阶段二 download()：用户确认后下载 APK，流式计算 SHA-256 并展示进度；
 *   校验失败：中止更新、删除已下载文件、提示错误，绝不进入安装流程。
 *
 * 版本判断只基于 versionCode，发布流程保证其单调递增。
 */
public final class Updater {

    public static final String UPDATE_REPO = "Lauyac/wsp-translator";

    /** 单个更新包大小上限：100MB，防止异常资产撑爆存储。 */
    private static final long MAX_APK_BYTES = 100L * 1024 * 1024;

    /** 阶段一产物：新版本完整信息。 */
    public static final class Info {
        public final String versionName;
        public final int versionCode;
        public final String notes;      // Release 描述，作为更新说明展示
        public final String apkName;
        public final String apkUrl;
        public final String sha256;
        Info(String v, int c, String n, String a, String u, String s) {
            versionName = v; versionCode = c; notes = n;
            apkName = a; apkUrl = u; sha256 = s;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final Info info;           // 有新版本时非空（阶段一）
        public final File apkFile;        // 校验通过时非空（阶段二）
        private Result(boolean ok, String message, Info info, File apkFile) {
            this.ok = ok; this.message = message; this.info = info; this.apkFile = apkFile;
        }
        static Result ok(String msg, Info info, File f) { return new Result(true, msg, info, f); }
        static Result fail(String msg) { return new Result(false, msg, null, null); }
    }

    /** 下载进度回调（在后台线程上回调）。 */
    public interface Progress {
        void onProgress(long readBytes, long totalBytes);
    }

    /** GitHub API 返回 4xx/5xx 时的专用异常。 */
    private static class HttpCodeException extends Exception {
        final int code;
        HttpCodeException(int code) { super("HTTP " + code); this.code = code; }
    }

    private Updater() { }

    // ==================== 阶段一：检查版本信息 ====================

    /** 检查最新版本信息；有新版本时返回 Info（含更新说明），不下载数据。 */
    public static Result checkInfo(Context ctx) {
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
                return Result.fail("update.json 与 Release 资产不一致，已中止更新检查");
            }

            // 3. 版本判断：只基于 versionCode（发布流程保证其单调递增）
            int cur = ctx.getPackageManager()
                .getPackageInfo(ctx.getPackageName(), 0).versionCode;
            if (newCode <= cur) {
                return Result.ok("已是最新版本（versionCode " + cur + "）", null, null);
            }

            // 4. 更新说明：取 Release 描述（发布时由 release 流程写入）
            String notes = j.optString("body", "").trim();
            if (notes.isEmpty()) notes = "（本次发布未附更新说明）";
            return Result.ok("发现新版本", new Info(newName, newCode, notes, wantApk, apkUrl, sha), null);
        } catch (HttpCodeException e) {
            if (e.code == 404) {
                return Result.fail("暂无可用发布版本（仓库尚未发布任何 Release）");
            }
            return Result.fail("更新检查失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.fail("更新检查失败: " + Translator.friendlyError(e));
        }
    }

    // ==================== 阶段二：确认后下载 ====================

    /**
     * 下载 Info 描述的更新包并流式计算 SHA-256。
     * 校验失败或下载异常时删除半包并返回失败，绝不返回可安装的损坏文件。
     */
    public static Result download(Context ctx, Info info, Progress progress) {
        if (info == null || info.apkUrl == null || info.sha256 == null) {
            return Result.fail("更新信息无效，请重新检查更新");
        }
        try {
            File dir = new File(ctx.getFilesDir(), "update");
            if (!dir.exists()) dir.mkdirs();
            final File f = new File(dir, info.apkName);
            String actual;
            try {
                actual = downloadAndHash(info.apkUrl, f, MAX_APK_BYTES, progress);
            } catch (Exception e) {
                f.delete(); // 下载中断/超限：不留半包
                throw e;
            }
            if (!actual.equalsIgnoreCase(info.sha256)) {
                f.delete();
                return Result.fail("SHA-256 校验失败（期望 " + shortHash(info.sha256)
                    + "，实际 " + shortHash(actual) + "），已中止更新并删除下载文件");
            }
            return Result.ok("已下载 v" + info.versionName + " 并通过 SHA-256 校验，可安装", null, f);
        } catch (Exception e) {
            return Result.fail("下载失败: " + Translator.friendlyError(e));
        }
    }

    // ==================== 网络与工具 ====================

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

    /** 下载到文件并同步计算 SHA-256，超限即中止；进度通过回调上报。 */
    private static String downloadAndHash(String url, File f, long maxBytes, Progress progress) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "wsp-translator-updater");
        c.setRequestProperty("Accept", "application/octet-stream");
        int code = c.getResponseCode();
        if (code >= 400) throw new HttpCodeException(code);
        long total = c.getContentLengthLong();
        InputStream in = c.getInputStream();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileOutputStream out = new FileOutputStream(f);
        byte[] buf = new byte[8192];
        long done = 0;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                done += n;
                if (done > maxBytes) throw new Exception("更新包超出大小限制，已中止下载");
                md.update(buf, 0, n);
                out.write(buf, 0, n);
                if (progress != null && total > 0) progress.onProgress(done, total);
            }
        } finally {
            out.close();
            in.close();
            c.disconnect();
        }
        if (done == 0) throw new Exception("下载内容为空，已中止更新");
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
