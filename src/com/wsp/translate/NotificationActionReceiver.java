package com.wsp.translate;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * 通知快捷操作接收器：译文通知上的「复制译文」、失败通知上的「重试翻译」。
 * Manifest 注册（exported=false），仅接收本应用发出的显式广播，不依赖服务存活。
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_COPY  = "com.wsp.translate.action.COPY";
    public static final String ACTION_RETRY = "com.wsp.translate.action.RETRY";
    public static final String EXTRA_OUT    = "out";
    public static final String EXTRA_TITLE  = "title";
    public static final String EXTRA_BODY   = "body";
    public static final String EXTRA_PKG    = "pkg";

    @Override
    public void onReceive(Context ctx, Intent i) {
        String action = i == null ? null : i.getAction();
        if (ACTION_COPY.equals(action)) {
            String out = i.getStringExtra(EXTRA_OUT);
            if (out == null || out.isEmpty()) {
                Toast.makeText(ctx, "暂无可复制的译文", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("translation", out));
            Toast.makeText(ctx, "译文已复制", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ACTION_RETRY.equals(action)) {
            final String title = i.getStringExtra(EXTRA_TITLE);
            final String body = i.getStringExtra(EXTRA_BODY);
            final String pkg = i.getStringExtra(EXTRA_PKG);
            if (body == null || body.isEmpty()) {
                Toast.makeText(ctx, "没有可重译的内容", Toast.LENGTH_SHORT).show();
                return;
            }
            final String t = title == null ? "" : title;
            Translator.submit(new Runnable() {
                @Override public void run() {
                    Settings s = new Settings(ctx);
                    if (s.apiKey().trim().isEmpty()) {
                        NLService.postFailure(ctx, t, body, "尚未配置 API Key，请到应用设置填写");
                        return;
                    }
                    Translator.Result r = Translator.translate(s, body, s.incomingLang());
                    HistoryStore.record(ctx, HistoryStore.TYPE_INCOMING, t, body, r.text, r.ok);
                    if (r.ok) NLService.postTranslation(ctx, t, body, r.text, pkg);
                    else NLService.postFailure(ctx, t, body, r.text);
                }
            });
            Toast.makeText(ctx, "正在重新翻译…", Toast.LENGTH_SHORT).show();
        }
    }
}
