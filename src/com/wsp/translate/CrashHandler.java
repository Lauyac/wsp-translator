package com.wsp.translate;

import android.content.Context;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃日志本地捕获：默认 UncaughtExceptionHandler 的前置处理器。
 *
 * - 崩溃堆栈写入应用专属外部目录 files/crash/（无需任何权限，卸载即清除）
 * - 仅保留最近 10 份，超出自动清理最旧的
 * - 记录完成后链回系统默认处理器，不影响系统原本的崩溃行为
 * - 日志仅保存在本机，应用不上传、不读取，用户可随时手动删除
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String DIR = "crash";
    private static final int KEEP = 10;
    private static final Object LOCK = new Object();
    private static volatile boolean installed = false;

    private final Context ctx;
    private final Thread.UncaughtExceptionHandler prev;

    private CrashHandler(Context appCtx) {
        this.ctx = appCtx;
        this.prev = Thread.getDefaultUncaughtExceptionHandler();
    }

    /** 幂等安装；建议在各组件（Activity/Service）的 onCreate 中调用。 */
    public static void install(Context c) {
        if (installed) return;
        synchronized (LOCK) {
            if (installed) return;
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(c.getApplicationContext()));
            installed = true;
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            write(t, e);
        } catch (Throwable ignore) {
            // 崩溃路径中绝不能再抛出异常
        }
        if (prev != null) prev.uncaughtException(t, e);
    }

    private void write(Thread t, Throwable e) {
        File dir = ctx.getExternalFilesDir(DIR);
        if (dir == null) dir = new File(ctx.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();

        String ts = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(new Date());
        File out = new File(dir, "crash-" + ts + ".txt");

        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));

        String ver;
        try {
            ver = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception ex) {
            ver = "unknown";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("time: ").append(ts).append('\n')
          .append("version: ").append(ver).append('\n')
          .append("device: ").append(android.os.Build.MANUFACTURER).append(' ')
          .append(android.os.Build.MODEL).append(" (API ")
          .append(android.os.Build.VERSION.SDK_INT).append(")\n")
          .append("thread: ").append(t.getName()).append('\n')
          .append("stacktrace:\n")
          .append(sw.toString());

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(out, "UTF-8");
            pw.write(sb.toString());
            pw.flush();
        } catch (Exception ignore) {
        } finally {
            if (pw != null) pw.close();
        }

        // 只保留最近 KEEP 份
        File[] files = dir.listFiles();
        if (files != null && files.length > KEEP) {
            Arrays.sort(files, new Comparator<File>() {
                @Override public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified()); // 新的在前
                }
            });
            for (int i = KEEP; i < files.length; i++) files[i].delete();
        }
    }
}
