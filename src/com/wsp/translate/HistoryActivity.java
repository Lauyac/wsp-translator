package com.wsp.translate;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 翻译历史页：本机保存的最近 500 条翻译记录（收信 / 悬浮 / 草稿三条链路），
 * 新→旧排列，支持一键清空。样式与主界面同一套设计规范。
 */
public class HistoryActivity extends Activity {

    private static final int C_PAGE   = 0xFFF6F7FB;
    private static final int C_CARD   = 0xFFFFFFFF;
    private static final int C_PRIMARY= 0xFF2563EB;
    private static final int C_PRIMARY_LIGHT = 0xFFEFF6FF;
    private static final int C_TEXT   = 0xFF0F172A;
    private static final int C_SUB    = 0xFF475569;
    private static final int C_HINT   = 0xFF94A3B8;
    private static final int C_STROKE = 0xFFE8EDF4;
    private static final int C_ERR    = 0xFFDC2626;
    private static final int C_ERR_BG = 0xFFFEF2F2;
    private static final int T_GREEN  = 0xFF059669;
    private static final int T_GREEN_BG = 0xFFECFDF5;
    private static final int T_AMBER  = 0xFFB45309;
    private static final int T_AMBER_BG = 0xFFFFFBEB;

    private LinearLayout list;
    private TextView tvCount;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat fmt =
        new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_PAGE);

        // ---- 顶栏：标题 + 记录数 + 清空 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("翻译历史");
        title.setTextColor(C_TEXT);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titles.addView(title);
        tvCount = new TextView(this);
        tvCount.setTextColor(C_HINT);
        tvCount.setTextSize(11);
        tvCount.setPadding(0, dp(3), 0, 0);
        titles.addView(tvCount);
        header.addView(titles, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = new TextView(this);
        clear.setText("清空");
        clear.setTextColor(C_ERR);
        clear.setTextSize(13);
        clear.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clear.setBackground(clearBg());
        clear.setPadding(dp(14), dp(7), dp(14), dp(7));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        header.addView(clear);
        root.addView(header);

        View hair = new View(this);
        hair.setBackgroundColor(C_STROKE);
        root.addView(hair, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        // ---- 列表区 ----
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(12), dp(14), dp(20));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        reload();
    }

    private void reload() {
        tvCount.setText("正在加载…");
        list.removeAllViews();
        Translator.submitUtil(new Runnable() {
            @Override public void run() {
                final List<HistoryStore.Item> items = HistoryStore.recent(HistoryActivity.this, 200);
                ui.post(new Runnable() {
                    @Override public void run() {
                        render(items);
                    }
                });
            }
        });
    }

    private void render(List<HistoryStore.Item> items) {
        list.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有翻译记录\n\n翻译收到的消息、使用悬浮翻译或草稿翻译后，记录会出现在这里（仅保存在本机）");
            empty.setTextColor(C_HINT);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(4), 1f);
            empty.setPadding(dp(30), dp(60), dp(30), 0);
            list.addView(empty);
            tvCount.setText("共 0 条");
            return;
        }
        tvCount.setText("最近 " + items.size() + " 条（最多保留 500 条）");
        String lastDay = "";
        for (HistoryStore.Item it : items) {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date(it.ts));
            if (!day.equals(lastDay)) {
                lastDay = day;
                TextView dayLabel = new TextView(this);
                dayLabel.setText(fmtDay(it.ts));
                dayLabel.setTextColor(C_HINT);
                dayLabel.setTextSize(11);
                dayLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                dayLabel.setPadding(dp(4), dp(14), 0, dp(6));
                list.addView(dayLabel);
            }
            list.addView(itemCard(it));
        }
    }

    private String fmtDay(long ts) {
        long now = System.currentTimeMillis();
        long diff = now - ts;
        long day = 24L * 60 * 60 * 1000;
        if (diff < day) return "今天";
        if (diff < 2 * day) return "昨天";
        return new SimpleDateFormat("M月d日", Locale.getDefault()).format(new Date(ts));
    }

    private View itemCard(HistoryStore.Item it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        // 首行：类型胶囊 + 联系人 + 时间
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView type = new TextView(this);
        type.setText(it.type);
        type.setTextSize(10);
        type.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        boolean incoming = HistoryStore.TYPE_INCOMING.equals(it.type);
        type.setTextColor(incoming ? T_GREEN : C_PRIMARY);
        type.setBackground(Ui.fill(this, incoming ? T_GREEN_BG : C_PRIMARY_LIGHT, dp(999), 0));
        type.setPadding(dp(8), dp(2), dp(8), dp(2));
        top.addView(type);
        TextView who = new TextView(this);
        who.setText("  " + (it.contact == null || it.contact.isEmpty() ? it.type + "翻译" : it.contact));
        who.setTextColor(C_SUB);
        who.setTextSize(12);
        who.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        who.setSingleLine(true);
        who.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(who, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView time = new TextView(this);
        time.setText(fmt.format(new Date(it.ts)));
        time.setTextColor(C_HINT);
        time.setTextSize(11);
        top.addView(time);
        card.addView(top);

        if (it.ok) {
            card.addView(textBlock("原文", it.src, C_HINT, 12, dp(8)));
            card.addView(textBlock("译文", it.dst, C_TEXT, 13, dp(4)));
        } else {
            card.addView(textBlock("原文", it.src, C_HINT, 12, dp(8)));
            LinearLayout fail = new LinearLayout(this);
            fail.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable g = new GradientDrawable();
            g.setColor(C_ERR_BG);
            g.setCornerRadius(dp(10));
            fail.setBackground(g);
            fail.setPadding(dp(10), dp(8), dp(10), dp(8));
            TextView t = new TextView(this);
            t.setText("翻译失败: " + it.dst);
            t.setTextColor(C_ERR);
            t.setTextSize(12);
            t.setLineSpacing(dp(2), 1f);
            fail.addView(t);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fl.topMargin = dp(4);
            card.addView(fail, fl);
        }
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(8);
        card.setLayoutParams(cardLp);
        return card;
    }

    private TextView textBlock(String label, String body, int color, int size, int topMargin) {
        TextView v = new TextView(this);
        v.setText(label + ": " + body);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setLineSpacing(dp(2), 1.05f);
        v.setMaxLines(6);
        v.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        v.setLayoutParams(lp);
        return v;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle("清空翻译历史")
            .setMessage("将删除本机保存的全部翻译记录，此操作不可撤销。")
            .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    HistoryStore.clear(HistoryActivity.this);
                    reload();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ---- 绘制工具（与 MainActivity 同规范）----

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_CARD);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), C_STROKE);
        return g;
    }

    private Drawable clearBg() {
        GradientDrawable def = Ui.fill(this, C_ERR_BG, dp(12), 0);
        GradientDrawable pr = Ui.fill(this, 0xFFFEE2E2, dp(12), 0);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pr);
        s.addState(new int[]{}, def);
        GradientDrawable mask = Ui.fill(this, Color.WHITE, dp(12), 0);
        return new RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x1ADC2626), s, mask);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
