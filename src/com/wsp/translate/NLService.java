package com.wsp.translate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * 收信自动翻译（通知通道）。
 * 监听 WhatsApp / WhatsApp Business 的通知，将消息文本译为「收信译文语言」并发一条本地通知展示。
 * 只读取通知里的标题与文本，不读聊天记录、不上传任何数据；译文仅存本机缓存。
 *
 * 去重与合并：去重键包含联系人标题（不同联系人发相同内容不会互相吞掉）；
 * 同一联系人短时间内连发多条消息会合并为一次翻译请求（500ms 去抖），通知内分行展示。
 */
public class NLService extends NotificationListenerService {

    private static final String CH_ID = "wsp_translations";
    private static final String CH_NAME = "收信翻译";
    private static final long DEDUP_MS = 30000;
    /** 同联系人多条消息的合并窗口：最后一条消息到达后延迟此时长再发起翻译。 */
    private static final long MERGE_MS = 500;
    /** 临时性失败（超时/断网/429/5xx）的自动重试次数与退避间隔。 */
    private static final int RETRY_MAX = 2;
    private static final long[] RETRY_DELAY_MS = {3000, 10000};

    /** 同一联系人等待合并的消息批次。 */
    private static class Pending {
        final StringBuilder text = new StringBuilder();
        boolean scheduled = false;
    }

    private final Map<String, Long> recent = new HashMap<>();
    private final Map<String, Pending> pending = new HashMap<>();
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        ensureChannel(this);
    }

    /** 创建译文通知渠道（服务与快捷操作接收器共用，幂等）。 */
    static void ensureChannel(Context ctx) {
        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH_ID, CH_NAME,
            NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("WhatsApp 收到外语消息后的译文通知");
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String pkg = sbn.getPackageName();
        if (!"com.whatsapp".equals(pkg) && !"com.whatsapp.w4b".equals(pkg)) return;

        Settings st = new Settings(this);
        if (!st.notifEnabled() || st.apiKey().trim().isEmpty()) return;

        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle ex = n.extras;
        CharSequence titleCs = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence bigCs = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence textCs = ex.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bodyCs = (bigCs != null && bigCs.length() > 0) ? bigCs : textCs;
        if (titleCs == null || bodyCs == null) return;

        final String title = titleCs.toString().trim();
        final String body = bodyCs.toString().trim();
        if (title.isEmpty() || body.isEmpty()) return;
        // 群组摘要（"3 条新消息"）不翻译
        if (body.matches(".*\\d+\\s*(条)?\\s*新?消息.*")) return;
        // 语音/图片/贴纸等媒体条目通知没有可翻译文字，跳过（与读屏链路同一口径）
        if (TextFilter.isMediaEntry(body) || TextFilter.isMediaOnly(body)) return;
        // 已是大段中文且收信译文语言为中文时跳过，避免无谓请求
        if (st.skipChineseIncoming()
            && st.incomingLang().startsWith("zh")
            && Langs.cjkRatio(body) >= 0.5) return;

        // 去重键拼入联系人标题：不同联系人发相同内容不会被误吞
        String key = Translator.hash(title + "|" + body + "|" + st.incomingLang());
        long now = SystemClock.elapsedRealtime();
        synchronized (recent) {
            Long last = recent.get(key);
            if (last != null && now - last < DEDUP_MS) return;
            recent.put(key, now);
            if (recent.size() > 200) {
                Iterator<Map.Entry<String, Long>> it = recent.entrySet().iterator();
                long cutoff = now - DEDUP_MS;
                while (it.hasNext()) {
                    Map.Entry<String, Long> e = it.next();
                    if (e.getValue() < cutoff) it.remove();
                }
            }
        }

        // 同联系人消息合并：窗口内追加文本，窗口结束后一次翻译
        synchronized (pending) {
            Pending p = pending.get(title);
            if (p == null) { p = new Pending(); pending.put(title, p); }
            if (p.text.length() > 0) p.text.append('\n');
            p.text.append(body);
            if (!p.scheduled) {
                p.scheduled = true;
                final String t = title;
                final String pkgF = pkg;
                debounce.postDelayed(new Runnable() {
                    @Override public void run() { flush(t, pkgF); }
                }, MERGE_MS);
            }
        }
    }

    /** 合并窗口结束：把该联系人的所有待译消息合并成一次请求；临时性失败自动重试。 */
    private void flush(String title, String pkg) {
        final String combined;
        synchronized (pending) {
            Pending p = pending.remove(title);
            if (p == null || p.text.length() == 0) return;
            combined = p.text.toString();
        }
        translateWithRetry(title, combined, pkg, 0);
    }

    /**
     * 翻译并按需重试：超时/断网/限流/5xx 等临时错误按退避间隔自动重试，
     * 配置类错误（Key 无效、余额不足等）直接失败不重试。
     */
    private void translateWithRetry(final String title, final String combined,
                                    final String pkg, final int attempt) {
        Translator.submit(new Runnable() {
            @Override
            public void run() {
                Settings s = new Settings(NLService.this);
                if (!s.notifEnabled() || s.apiKey().trim().isEmpty()) return;
                Translator.Result r = Translator.translate(s, combined, s.incomingLang());
                if (r.ok) {
                    HistoryStore.record(NLService.this, HistoryStore.TYPE_INCOMING,
                        title, combined, r.text, true);
                    postTranslation(title, combined, r.text, pkg);
                    return;
                }
                if (r.retryable && attempt < RETRY_MAX) {
                    debounce.postDelayed(new Runnable() {
                        @Override public void run() {
                            translateWithRetry(title, combined, pkg, attempt + 1);
                        }
                    }, RETRY_DELAY_MS[Math.min(attempt, RETRY_DELAY_MS.length - 1)]);
                    return;
                }
                HistoryStore.record(NLService.this, HistoryStore.TYPE_INCOMING,
                    title, combined, r.text, false);
                postFailure(title, combined, r.text);
            }
        });
    }

    private void postTranslation(String title, String body, String out, String pkg) {
        postTranslation(this, title, body, out, pkg);
    }

    private void postFailure(String title, String body, String reason) {
        postFailure(this, title, body, reason);
    }

    /** 译文通知：点击跳回聊天；快捷操作「复制译文」直接进剪贴板。 */
    static void postTranslation(Context ctx, String title, String body, String out, String pkg) {
        ensureChannel(ctx);
        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
        PendingIntent pi = null;
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            pi = PendingIntent.getActivity(ctx, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        Intent copy = new Intent(ctx, NotificationActionReceiver.class)
            .setAction(NotificationActionReceiver.ACTION_COPY)
            .putExtra(NotificationActionReceiver.EXTRA_OUT, out);
        PendingIntent pCopy = PendingIntent.getBroadcast(ctx,
            ("copy|" + title + "|" + body).hashCode(), copy,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification b = new Notification.Builder(ctx, CH_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("译 · " + title)
            .setStyle(new Notification.BigTextStyle()
                .bigText("原文: " + body + "\n\n译文: " + out))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(new Notification.Action.Builder(
                R.drawable.ic_stat, "复制译文", pCopy).build())
            .build();
        try {
            // 通知 ID 拼入联系人：不同联系人的相同内容不会互相覆盖
            nm.notify(Translator.hash(title + "|" + body).hashCode(), b);
        } catch (Exception ignored) { }
    }

    /** 翻译失败通知：说明原因并附「重试翻译」快捷操作（手动触发，不自动循环）。 */
    static void postFailure(Context ctx, String title, String body, String reason) {
        ensureChannel(ctx);
        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
        Intent retry = new Intent(ctx, NotificationActionReceiver.class)
            .setAction(NotificationActionReceiver.ACTION_RETRY)
            .putExtra(NotificationActionReceiver.EXTRA_TITLE, title)
            .putExtra(NotificationActionReceiver.EXTRA_BODY, body)
            .putExtra(NotificationActionReceiver.EXTRA_PKG, ctx.getPackageName());
        PendingIntent pRetry = PendingIntent.getBroadcast(ctx,
            ("retry|" + title + "|" + body).hashCode(), retry,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification b = new Notification.Builder(ctx, CH_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("译 · 翻译失败（" + title + "）")
            .setStyle(new Notification.BigTextStyle()
                .bigText("原文: " + body + "\n\n失败原因: " + reason
                    + "\n\n请按提示检查网络或应用设置，也可点下方「重试翻译」。"))
            .setAutoCancel(true)
            .addAction(new Notification.Action.Builder(
                R.drawable.ic_stat, "重试翻译", pRetry).build())
            .build();
        try {
            nm.notify(("fail|" + title + "|" + Translator.hash(body)).hashCode(), b);
        } catch (Exception ignored) { }
    }
}
