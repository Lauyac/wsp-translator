package com.wsp.translate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
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
 */
public class NLService extends NotificationListenerService {

    private static final String CH_ID = "wsp_translations";
    private static final String CH_NAME = "收信翻译";
    private static final long DEDUP_MS = 30000;

    private final Map<String, Long> recent = new HashMap<>();
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
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
        // 已是大段中文且收信译文语言为中文时跳过，避免无谓请求
        if (st.skipChineseIncoming()
            && st.incomingLang().startsWith("zh")
            && Langs.cjkRatio(body) >= 0.5) return;

        String key = Translator.hash(body + "|" + st.incomingLang());
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

        Translator.submit(new Runnable() {
            @Override
            public void run() {
                Settings s = new Settings(NLService.this);
                Translator.Result r = Translator.translate(s, body, s.incomingLang());
                if (r.ok) postTranslation(title, body, r.text, pkg);
                else postFailure(title, body, r.text);
            }
        });
    }

    private void postTranslation(String title, String body, String out, String pkg) {
        PendingIntent pi = null;
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            pi = PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        Notification b = new Notification.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("译 · " + title)
            .setStyle(new Notification.BigTextStyle()
                .bigText("原文: " + body + "\n\n译文: " + out))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build();
        try {
            nm.notify((Translator.hash(body)).hashCode(), b);
        } catch (Exception ignored) { }
    }

    /** 翻译失败也发通知说明原因，避免"没收到译文"时用户无从排查。 */
    private void postFailure(String title, String body, String reason) {
        Notification b = new Notification.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("译 · 翻译失败（" + title + "）")
            .setStyle(new Notification.BigTextStyle()
                .bigText("原文: " + body + "\n\n失败原因: " + reason
                    + "\n\n请按提示检查网络或应用设置后重试。"))
            .setAutoCancel(true)
            .build();
        try {
            nm.notify(("fail|" + Translator.hash(body)).hashCode(), b);
        } catch (Exception ignored) { }
    }
}
