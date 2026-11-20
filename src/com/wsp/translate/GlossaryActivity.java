package com.wsp.translate;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 外贸术语速查：内置术语表（缩写/全称/中文说明），支持关键字实时检索。
 * 匹配范围：缩写、英文全称、中文说明（不区分大小写）。
 */
public class GlossaryActivity extends Activity {

    private static final int C_PAGE   = 0xFFF6F7FB;
    private static final int C_CARD   = 0xFFFFFFFF;
    private static final int C_PRIMARY= 0xFF2563EB;
    private static final int C_PRIMARY_LIGHT = 0xFFEFF6FF;
    private static final int C_TEXT   = 0xFF0F172A;
    private static final int C_SUB    = 0xFF475569;
    private static final int C_HINT   = 0xFF94A3B8;
    private static final int C_STROKE = 0xFFE8EDF4;
    private static final int C_INPUT_STROKE = 0xFFCBD5E1;

    private LinearLayout list;
    private EditText etSearch;
    private TextView tvCount;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_PAGE);

        // ---- 顶栏 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(10));
        TextView title = new TextView(this);
        title.setText("外贸术语速查");
        title.setTextColor(C_TEXT);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.addView(title);
        TextView sub = new TextView(this);
        sub.setText("缩写 · 全称 · 中文说明，支持关键字检索");
        sub.setTextColor(C_HINT);
        sub.setTextSize(11);
        sub.setPadding(0, dp(3), 0, 0);
        header.addView(sub);
        root.addView(header);

        // ---- 搜索框 ----
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.VERTICAL);
        searchWrap.setPadding(dp(16), dp(2), dp(16), dp(4));
        etSearch = new EditText(this);
        etSearch.setHint("搜索术语，如 FOB / 提单 / 起订量");
        etSearch.setTextColor(C_TEXT);
        etSearch.setHintTextColor(C_HINT);
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        GradientDrawable def = new GradientDrawable();
        def.setColor(C_CARD);
        def.setCornerRadius(dp(14));
        def.setStroke(dp(1), C_INPUT_STROKE);
        GradientDrawable foc = new GradientDrawable();
        foc.setColor(C_CARD);
        foc.setCornerRadius(dp(14));
        foc.setStroke(dp(2), C_PRIMARY);
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, foc);
        sl.addState(new int[]{}, def);
        etSearch.setBackground(sl);
        etSearch.setPadding(dp(14), dp(11), dp(14), dp(11));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                // 150ms 防抖检索，避免每个字符都重建列表
                if (pendingFilter != null) ui.removeCallbacks(pendingFilter);
                pendingFilter = new Runnable() {
                    @Override public void run() { filter(etSearch.getText().toString()); }
                };
                ui.postDelayed(pendingFilter, 150);
            }
        });
        searchWrap.addView(etSearch, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(searchWrap);

        // ---- 统计行 ----
        LinearLayout countWrap = new LinearLayout(this);
        countWrap.setPadding(dp(16), dp(4), dp(16), dp(4));
        tvCount = new TextView(this);
        tvCount.setTextColor(C_HINT);
        tvCount.setTextSize(11);
        countWrap.addView(tvCount);
        root.addView(countWrap);

        // ---- 列表区 ----
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(4), dp(14), dp(20));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        filter("");
    }

    private void filter(String raw) {
        String q = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        List<String[]> matched = new ArrayList<>();
        for (String[] it : GlossaryData.ITEMS) {
            if (q.isEmpty()
                || it[0].toLowerCase(Locale.ROOT).contains(q)
                || it[1].toLowerCase(Locale.ROOT).contains(q)
                || it[2].toLowerCase(Locale.ROOT).contains(q)) {
                matched.add(it);
            }
        }
        render(matched, q);
    }

    private void render(List<String[]> items, String query) {
        list.removeAllViews();
        if (query.isEmpty()) {
            tvCount.setText("共 " + items.size() + " 条术语");
        } else {
            tvCount.setText("匹配 " + items.size() + " / " + GlossaryData.ITEMS.length + " 条");
        }
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("未找到与「" + query + "」匹配的术语\n\n试试缩写（FOB）、英文名（Bill of Lading）或中文关键词（提单）");
            empty.setTextColor(C_HINT);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(4), 1f);
            empty.setPadding(dp(30), dp(50), dp(30), 0);
            list.addView(empty);
            return;
        }
        for (String[] it : items) {
            list.addView(itemCard(it));
        }
    }

    private View itemCard(final String[] it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView term = new TextView(this);
        term.setText(it[0]);
        term.setTextColor(C_PRIMARY);
        term.setTextSize(15);
        term.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        top.addView(term);
        TextView full = new TextView(this);
        full.setText("　" + it[1]);
        full.setTextColor(C_SUB);
        full.setTextSize(12);
        full.setSingleLine(true);
        full.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(full, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        TextView desc = new TextView(this);
        desc.setText(it[2]);
        desc.setTextColor(C_SUB);
        desc.setTextSize(12);
        desc.setLineSpacing(dp(3), 1.05f);
        desc.setPadding(0, dp(6), 0, 0);
        card.addView(desc);

        // 长按复制术语与释义，方便粘贴给客户
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("term",
                    it[0] + " (" + it[1] + "): " + it[2]));
                android.widget.Toast.makeText(GlossaryActivity.this,
                    "已复制术语说明", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private Drawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_CARD);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), C_STROKE);
        GradientDrawable pr = new GradientDrawable();
        pr.setColor(C_PRIMARY_LIGHT);
        pr.setCornerRadius(dp(16));
        pr.setStroke(dp(1), C_STROKE);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pr);
        s.addState(new int[]{}, g);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(C_CARD);
        mask.setCornerRadius(dp(16));
        return new RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x142563EB), s, mask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 收起键盘，避免返回时残留输入法窗口
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null && getCurrentFocus() != null) {
            im.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
