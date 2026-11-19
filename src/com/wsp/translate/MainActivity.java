package com.wsp.translate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    // ---- 设计规范（现代蓝 · Slate 中性色）----
    private static final int C_PAGE   = 0xFFF1F5F9;  // 页面底色
    private static final int C_CARD   = 0xFFFFFFFF;  // 卡片
    private static final int C_PRIMARY= 0xFF2563EB;  // 主色 蓝
    private static final int C_PRIMARY_DARK = 0xFF1E40AF;
    private static final int C_PRIMARY_LIGHT = 0xFFEFF6FF;
    private static final int C_TEXT   = 0xFF0F172A;  // 主文字
    private static final int C_SUB    = 0xFF64748B;  // 次级文字
    private static final int C_STROKE = 0xFFE2E8F0;  // 卡片描边
    private static final int C_INPUT_STROKE = 0xFFCBD5E1;
    private static final int C_OK     = 0xFF16A34A;
    private static final int C_ERR    = 0xFFDC2626;
    private static final int C_WARN   = 0xFFD97706;

    private Settings st;
    private Handler ui;
    private EditText etKey, etBase, etModel;
    private Spinner spDraft, spIncoming;
    private CheckBox cbNotif, cbBubble, cbSkipZh, cbShowKey;
    private TextView tvResult, tvPerm;
    private LinearLayout updatePanel, updateActions;
    private TextView tvUpdateTitle, tvUpdateNotes, tvUpdate;
    private ProgressBar pbUpdate;
    private Button btnCheck, btnDownload, btnCancel;
    private Updater.Info pendingInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        st = new Settings(this);
        ui = new Handler(Looper.getMainLooper());
        setContentView(buildUi());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPerm();
    }

    // ==================== UI 构建 ====================

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(C_PAGE);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(page);

        LinearLayout root = column(page, dp(16), dp(16), dp(16), dp(24));

        // ---- 顶部渐变 Hero ----
        root.addView(buildHero());

        // ---- ① 连接与凭据 ----
        LinearLayout c1 = card(root, dp(20));
        sectionTitle(c1, "连接与凭据", "1");
        etKey = input("DeepSeek API Key（sk-…）", true);
        c1.addView(etKey);
        cbShowKey = check("显示 API Key，方便核对是否输入正确");
        cbShowKey.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                etKey.setInputType(checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                etKey.setSelection(etKey.getText().length());
            }
        });
        c1.addView(cbShowKey);
        etBase = input("API 地址（默认 https://api.deepseek.com）", false);
        etModel = input("模型（默认 deepseek-chat）", false);
        c1.addView(etBase);
        c1.addView(etModel);

        LinearLayout btnRow = row();
        btnRow.addView(primaryBtn("保存设置", new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        }), half());
        btnRow.addView(outlineBtn("测试连接", new View.OnClickListener() {
            @Override public void onClick(View v) { test(); }
        }), half());
        c1.addView(withMargin(btnRow, 0, dp(14), 0, 0));

        tvResult = statusBox(c1, "");
        tvResult.setVisibility(View.GONE);

        // ---- ② 翻译行为 ----
        LinearLayout c2 = card(root, dp(20));
        sectionTitle(c2, "翻译行为", "2");
        c2.addView(label("回复目标语言（草稿中文 → 外语）"));
        spDraft = spinner();
        c2.addView(spinnerWrap(spDraft));
        c2.addView(label("收信译文语言（外语 → 目标语言，默认简体中文）"));
        spIncoming = spinner();
        c2.addView(spinnerWrap(spIncoming));
        cbSkipZh = check("收信已是大段中文时跳过翻译");
        cbNotif = check("收信自动翻译（通知使用权）");
        cbBubble = check("悬浮球翻译（需无障碍 + 悬浮窗权限）");
        c2.addView(cbSkipZh);
        c2.addView(cbNotif);
        c2.addView(cbBubble);

        // ---- ③ 权限开启 ----
        LinearLayout c3 = card(root, dp(20));
        sectionTitle(c3, "权限开启", "3");
        c3.addView(caption("首次安装必做，按用途开启对应权限"));
        tvPerm = new TextView(this);
        tvPerm.setTextSize(13);
        tvPerm.setTextColor(C_TEXT);
        tvPerm.setTypeface(Typeface.MONOSPACE);
        tvPerm.setLineSpacing(dp(4), 1f);
        tvPerm.setBackground(bg(C_PRIMARY_LIGHT, dp(10), 0));
        tvPerm.setPadding(dp(12), dp(10), dp(12), dp(10));
        c3.addView(tvPerm);
        c3.addView(withMargin(permBtn("开启通知使用权（收信翻译）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        }), 0, dp(8), 0, 0));
        c3.addView(withMargin(permBtn("开启无障碍服务（悬浮翻译）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }), 0, dp(8), 0, 0));
        c3.addView(withMargin(permBtn("授予悬浮窗权限", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }), 0, dp(8), 0, 0));
        if (Build.VERSION.SDK_INT >= 33) {
            c3.addView(withMargin(permBtn("授予通知显示权限（Android 13+）", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
            }), 0, dp(8), 0, 0));
        }

        // ---- ④ 应用更新 ----
        LinearLayout c4 = card(root, dp(20));
        sectionTitle(c4, "应用更新", "4");

        // 当前版本行：左侧名称、右侧徽章，单行对齐
        LinearLayout verRow = row();
        TextView tvVerName = new TextView(this);
        tvVerName.setText("当前版本");
        tvVerName.setTextColor(C_SUB);
        tvVerName.setTextSize(13);
        verRow.addView(tvVerName,
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvVerBadge = new TextView(this);
        tvVerBadge.setText("v" + versionName() + " · code " + versionCode());
        tvVerBadge.setTextColor(C_PRIMARY);
        tvVerBadge.setTextSize(12);
        tvVerBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvVerBadge.setBackground(bg(C_PRIMARY_LIGHT, dp(10), 0));
        tvVerBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        verRow.addView(tvVerBadge);
        c4.addView(verRow);

        // 检查按钮（与其他卡片的按钮保持同一节奏：上 12dp）
        btnCheck = primaryBtn("检查更新", new View.OnClickListener() {
            @Override public void onClick(View v) { checkUpdate(); }
        });
        c4.addView(withMargin(btnCheck, 0, dp(12), 0, 0));

        // 更新状态行（与卡片内文字左对齐，去掉多余内边距）
        tvUpdate = new TextView(this);
        tvUpdate.setTextSize(13);
        tvUpdate.setTextColor(C_SUB);
        tvUpdate.setPadding(dp(2), dp(10), dp(2), dp(2));
        tvUpdate.setVisibility(View.GONE);
        c4.addView(tvUpdate);

        // 新版本详情面板（发现新版本时显示）
        updatePanel = new LinearLayout(this);
        updatePanel.setOrientation(LinearLayout.VERTICAL);
        updatePanel.setBackground(bg(C_PRIMARY_LIGHT, dp(12), 0));
        updatePanel.setPadding(dp(14), dp(12), dp(14), dp(14));

        tvUpdateTitle = new TextView(this);
        tvUpdateTitle.setTextColor(C_PRIMARY_DARK);
        tvUpdateTitle.setTextSize(15);
        tvUpdateTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        updatePanel.addView(tvUpdateTitle);

        TextView notesLabel = new TextView(this);
        notesLabel.setText("本次更新内容");
        notesLabel.setTextColor(C_SUB);
        notesLabel.setTextSize(11);
        notesLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        notesLabel.setPadding(0, dp(12), 0, dp(6));
        updatePanel.addView(notesLabel);

        tvUpdateNotes = new TextView(this);
        tvUpdateNotes.setTextColor(C_TEXT);
        tvUpdateNotes.setTextSize(13);
        tvUpdateNotes.setLineSpacing(dp(3), 1f);
        updatePanel.addView(tvUpdateNotes);

        c4.addView(withMargin(updatePanel, 0, dp(10), 0, 0));
        updatePanel.setVisibility(View.GONE);

        // 下载进度条（确认下载后显示，与面板同宽同节奏）
        pbUpdate = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbUpdate.setMax(100);
        pbUpdate.getProgressDrawable().setColorFilter(C_PRIMARY, android.graphics.PorterDuff.Mode.SRC_IN);
        c4.addView(withMargin(pbUpdate, 0, dp(10), 0, 0));
        pbUpdate.setVisibility(View.GONE);

        // 确认 / 取消按钮
        updateActions = row();
        btnDownload = primaryBtn("下载并安装", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmDownload(); }
        });
        btnCancel = outlineBtn("暂不更新", new View.OnClickListener() {
            @Override public void onClick(View v) { dismissUpdate(); }
        });
        updateActions.addView(btnDownload, half());
        updateActions.addView(btnCancel, half());
        c4.addView(updateActions);
        updateActions.setVisibility(View.GONE);

        // ---- ⑤ 使用说明 ----
        LinearLayout c5 = card(root, dp(20));
        sectionTitle(c5, "使用说明", "5");
        TextView help = new TextView(this);
        help.setTextSize(13);
        help.setTextColor(C_TEXT);
        help.setLineSpacing(dp(4), 1f);
        help.setText("· 收信翻译：保持本应用在后台运行，聊天应用（如 WhatsApp）收到新消息后会自动发出一条「译 · 」开头的本地通知，展示原文与译文；翻译失败时也会发出失败通知并说明原因。\n\n"
            + "· 悬浮翻译：打开聊天应用（如 WhatsApp）的聊天界面 → 点悬浮球「译」→ 自动读取屏幕并翻译。\n\n"
            + "· 草稿翻译：在悬浮面板输入中文 → 点「翻译并填入」→ 译文写入聊天应用输入框，请核对后手动发送（本应用绝不自动发送消息）。\n\n"
            + "· 隐私：译文缓存仅存本机内存，退出应用即清空；聊天内容不会被保存或上传，仅作为翻译请求发送给你配置的 API 服务端。");
        c5.addView(help);

        TextView foot = new TextView(this);
        foot.setText("WSP Translator · 开源 · versionCode 单调递增 · SHA-256 校验");
        foot.setTextSize(11);
        foot.setTextColor(C_SUB);
        foot.setGravity(Gravity.CENTER);
        root.addView(withMargin(foot, 0, dp(16), 0, 0));

        return sv;
    }

    /** 顶部渐变横幅：应用名 + 简介 + 版本徽章。 */
    private View buildHero() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{C_PRIMARY, C_PRIMARY_DARK});
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(g);
        hero.setPadding(dp(20), dp(24), dp(20), dp(20));

        TextView name = new TextView(this);
        name.setText("WSP 翻译助手");
        name.setTextColor(Color.WHITE);
        name.setTextSize(22);
        name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hero.addView(name);

        TextView sub = new TextView(this);
        sub.setText("收信翻译 · 悬浮翻译 · 草稿翻译");
        sub.setTextColor(0xFFDBEAFE);
        sub.setTextSize(13);
        sub.setPadding(0, dp(4), 0, 0);
        hero.addView(sub);

        TextView chip = new TextView(this);
        chip.setText("v" + versionName() + "  ·  build " + versionCode());
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(11);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setBackground(bg(0x33FFFFFF, dp(14), 0));
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        hero.addView(withMargin(chip, 0, dp(10), 0, 0));

        return hero;
    }

    // ==================== 组件工厂 ====================

    private LinearLayout column(ViewGroup parent, int l, int t, int r, int b) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(l, t, r, b);
        parent.addView(v);
        return v;
    }

    /** 白色圆角卡片。 */
    private LinearLayout card(ViewGroup parent, int pad) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), C_STROKE);
        v.setBackground(bg);
        v.setPadding(pad, pad, pad, pad);
        parent.addView(withMargin(v, 0, dp(12), 0, 0));
        return v;
    }

    /** 卡片内分节标题：彩色竖条 + 加粗标题。 */
    private void sectionTitle(ViewGroup card, String title, String no) {
        LinearLayout row = row();
        View bar = new View(this);
        GradientDrawable bd = new GradientDrawable();
        bd.setColor(C_PRIMARY);
        bd.setCornerRadius(dp(2));
        bar.setBackground(bd);
        row.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(18)));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_TEXT);
        t.setTextSize(17);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(10), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView n = new TextView(this);
        n.setText(no);
        n.setTextColor(C_PRIMARY);
        n.setTextSize(12);
        n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        n.setBackground(bg(C_PRIMARY_LIGHT, dp(10), 0));
        n.setPadding(dp(8), dp(2), dp(8), dp(2));
        row.addView(n);

        card.addView(withMargin(row, 0, 0, 0, dp(14)));
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(C_SUB);
        v.setTextSize(12);
        v.setPadding(dp(2), dp(10), dp(2), dp(6));
        return v;
    }

    private TextView caption(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(C_SUB);
        v.setTextSize(12);
        v.setPadding(dp(2), 0, dp(2), dp(10));
        return v;
    }

    private EditText input(String hint, boolean key) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(C_TEXT);
        e.setHintTextColor(0xFF94A3B8);
        e.setTextSize(14);
        e.setBackground(bgStroke(C_CARD, C_INPUT_STROKE, dp(12)));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (key) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                GradientDrawable g = (GradientDrawable) v.getBackground();
                g.setStroke(dp(1), has ? C_PRIMARY : C_INPUT_STROKE);
            }
        });
        return e;
    }

    private View spinnerWrap(Spinner s) {
        LinearLayout w = new LinearLayout(this);
        w.setBackground(bgStroke(C_CARD, C_INPUT_STROKE, dp(12)));
        w.setPadding(dp(6), 0, dp(6), 0);
        w.addView(s, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return w;
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        String[] labels = new String[Langs.ITEMS.length];
        for (int i = 0; i < Langs.ITEMS.length; i++) labels[i] = Langs.ITEMS[i][1];
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        s.setBackground(null);
        return s;
    }

    private CheckBox check(String t) {
        CheckBox c = new CheckBox(this);
        c.setText(t);
        c.setTextSize(13);
        c.setTextColor(C_TEXT);
        c.setPadding(dp(4), dp(4), dp(4), dp(4));
        return c;
    }

    private Button primaryBtn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setAllCaps(false);
        b.setBackground(bg(C_PRIMARY, dp(12), 0));
        b.setPadding(0, dp(12), 0, dp(12));
        b.setOnClickListener(l);
        return b;
    }

    private Button outlineBtn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(C_PRIMARY);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setAllCaps(false);
        b.setBackground(bgStroke(C_CARD, C_PRIMARY, dp(12)));
        b.setPadding(0, dp(12), 0, dp(12));
        b.setOnClickListener(l);
        return b;
    }

    private Button permBtn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText("›  " + t);
        b.setTextColor(C_TEXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(bgStroke(C_CARD, C_STROKE, dp(12)));
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setOnClickListener(l);
        return b;
    }

    /** 状态/结果提示框。 */
    private TextView statusBox(ViewGroup parent, String init) {
        TextView v = new TextView(this);
        v.setText(init);
        v.setTextSize(13);
        v.setTextColor(C_TEXT);
        v.setLineSpacing(dp(3), 1f);
        v.setBackground(bg(C_PRIMARY_LIGHT, dp(12), 0));
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        parent.addView(withMargin(v, 0, dp(12), 0, 0));
        return v;
    }

    // ==================== 绘制工具 ====================

    private GradientDrawable bg(int color, float radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (stroke > 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable bgStroke(int color, int strokeColor, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        g.setStroke(dp(1), strokeColor);
        return g;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private View withMargin(View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (p == null) p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout.LayoutParams half() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    // ==================== 业务逻辑（保持不变） ====================

    private void load() {
        etKey.setText(st.apiKey());
        etBase.setText(st.baseUrl());
        etModel.setText(st.model());
        spDraft.setSelection(Langs.indexOf(st.draftLang()));
        spIncoming.setSelection(Langs.indexOf(st.incomingLang()));
        cbSkipZh.setChecked(st.skipChineseIncoming());
        cbNotif.setChecked(st.notifEnabled());
        cbBubble.setChecked(st.bubbleEnabled());
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "未知";
        }
    }

    private int versionCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== 应用更新（两阶段） ====================

    /** 阶段一：检查版本信息，发现新版本时展示版本信息与更新说明，等待用户确认。 */
    private void checkUpdate() {
        tvUpdate.setVisibility(View.VISIBLE);
        tvUpdate.setText("正在检查更新…");
        tvUpdate.setTextColor(C_SUB);
        updatePanel.setVisibility(View.GONE);
        updateActions.setVisibility(View.GONE);
        pbUpdate.setVisibility(View.GONE);
        btnCheck.setEnabled(false);
        final MainActivity act = this;
        Translator.submit(new Runnable() {
            @Override
            public void run() {
                final Updater.Result r = Updater.checkInfo(act);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        btnCheck.setEnabled(true);
                        if (!r.ok) {
                            tvUpdate.setVisibility(View.VISIBLE);
                            tvUpdate.setText(r.message);
                            tvUpdate.setTextColor(C_ERR);
                            return;
                        }
                        if (r.info == null) {
                            // 已是最新版本
                            tvUpdate.setVisibility(View.VISIBLE);
                            tvUpdate.setText("✓ " + r.message);
                            tvUpdate.setTextColor(C_OK);
                            return;
                        }
                        // 发现新版本：展示版本信息与更新说明
                        pendingInfo = r.info;
                        tvUpdateTitle.setText("发现新版本 v" + r.info.versionName
                            + "（versionCode " + r.info.versionCode + "）");
                        tvUpdateNotes.setText(formatNotes(r.info.notes));
                        tvUpdate.setVisibility(View.VISIBLE);
                        tvUpdate.setText("请阅读上方更新内容，确认无误后再下载安装。");
                        tvUpdate.setTextColor(C_SUB);
                        updatePanel.setVisibility(View.VISIBLE);
                        updateActions.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /** 阶段二：用户确认后开始下载（带进度），SHA-256 校验通过后自动进入安装。 */
    private void confirmDownload() {
        if (pendingInfo == null) return;
        btnDownload.setEnabled(false);
        btnCancel.setEnabled(false);
        btnCheck.setEnabled(false);
        updateActions.setVisibility(View.GONE);
        pbUpdate.setVisibility(View.VISIBLE);
        pbUpdate.setProgress(0);
        tvUpdate.setVisibility(View.VISIBLE);
        tvUpdate.setTextColor(C_SUB);
        final MainActivity act = this;
        final Updater.Info info = pendingInfo;
        Translator.submit(new Runnable() {
            @Override
            public void run() {
                final Updater.Result r = Updater.download(act, info, new Updater.Progress() {
                    @Override public void onProgress(final long read, final long total) {
                        ui.post(new Runnable() {
                            @Override public void run() {
                                int pct = total > 0 ? (int) (read * 100 / total) : 0;
                                pbUpdate.setProgress(pct);
                                tvUpdate.setText("正在下载 v" + info.versionName + "… "
                                    + pct + "%（" + humanSize(read) + " / " + humanSize(total) + "）");
                            }
                        });
                    }
                });
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        pbUpdate.setVisibility(View.GONE);
                        btnDownload.setEnabled(true);
                        btnCancel.setEnabled(true);
                        btnCheck.setEnabled(true);
                        if (r.apkFile != null) {
                            tvUpdate.setVisibility(View.VISIBLE);
                            tvUpdate.setText("✓ " + r.message);
                            tvUpdate.setTextColor(C_OK);
                            installUpdate(r.apkFile);
                        } else {
                            tvUpdate.setVisibility(View.VISIBLE);
                            tvUpdate.setText(r.message);
                            tvUpdate.setTextColor(C_ERR);
                            updatePanel.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }

    /** 用户选择暂不更新：收起详情面板。 */
    private void dismissUpdate() {
        pendingInfo = null;
        updatePanel.setVisibility(View.GONE);
        updateActions.setVisibility(View.GONE);
        tvUpdate.setVisibility(View.VISIBLE);
        tvUpdate.setText("已跳过本次更新，可随时再点「检查更新」");
        tvUpdate.setTextColor(C_SUB);
    }

    /** 轻量 Markdown 清理：去标记符号、列表符转圆点，保留段落结构。 */
    private String formatNotes(String md) {
        if (md == null) return "";
        StringBuilder out = new StringBuilder();
        String[] lines = md.split("\n");
        for (String line : lines) {
            String s = line
                .replace("**", "").replace("`", "")
                .replaceFirst("^#+\\s*", "")
                .replaceFirst("^[-*]\\s+", "· ")
                .replaceFirst("^\\d+\\.\\s+", "· ");
            s = s.trim();
            if (s.isEmpty() && (out.length() == 0
                || out.charAt(out.length() - 1) == '\n')) continue;
            out.append(s).append('\n');
        }
        String r = out.toString().trim();
        if (r.length() > 1500) r = r.substring(0, 1500) + "\n…（说明过长已截断，完整内容见 GitHub Release）";
        return r;
    }

    private String humanSize(long b) {
        if (b <= 0) return "0KB";
        if (b < 1024 * 1024) return String.format("%.0fKB", b / 1024f);
        return String.format("%.1fMB", b / 1024f / 1024f);
    }

    /** 仅在 SHA-256 校验通过后被调用；先确认"安装未知应用"权限。 */
    private void installUpdate(java.io.File apk) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            tvUpdate.setText("需要「安装未知应用」权限，请在系统设置中允许后重新检查更新");
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开安装权限设置: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            return;
        }
        Uri uri = Uri.parse("content://com.wsp.translate.update/" + apk.getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            tvUpdate.setText("无法启动安装: " + e.getMessage());
        }
    }

    private void save() {
        st.put("apiKey", etKey.getText().toString().trim());
        String base = etBase.getText().toString().trim();
        st.put("baseUrl", base.isEmpty() ? "https://api.deepseek.com" : base);
        String model = etModel.getText().toString().trim();
        st.put("model", model.isEmpty() ? "deepseek-chat" : model);
        st.put("draftLang", Langs.ITEMS[spDraft.getSelectedItemPosition()][0]);
        st.put("incomingLang", Langs.ITEMS[spIncoming.getSelectedItemPosition()][0]);
        st.put("skipChineseIncoming", cbSkipZh.isChecked());
        st.put("notifEnabled", cbNotif.isChecked());
        st.put("bubbleEnabled", cbBubble.isChecked());
        if (etKey.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "已保存，但 API Key 为空，翻译功能暂不可用", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        }
    }

    private void test() {
        save();
        final String sample = "你好，请问这款产品的最低起订量是多少？";
        tvResult.setVisibility(View.VISIBLE);
        tvResult.setText("正在测试连接…");
        final Settings s = new Settings(this);
        Translator.submit(new Runnable() {
            @Override
            public void run() {
                final Translator.Result r = Translator.translate(s, sample, s.draftLang());
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResult.setText(r.ok
                            ? ("连接成功（" + (r.cached ? "缓存命中" : "网络请求") + "）\n示例译文: " + r.text)
                            : "连接失败: " + r.text);
                    }
                });
            }
        });
    }

    private void refreshPerm() {
        String nls = android.provider.Settings.Secure.getString(getContentResolver(),
            "enabled_notification_listeners");
        boolean notif = nls != null && nls.contains(getPackageName());
        String acc = android.provider.Settings.Secure.getString(getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean a11y = acc != null && acc.contains(getPackageName());
        boolean overlay = android.provider.Settings.canDrawOverlays(this);
        boolean postNotif = Build.VERSION.SDK_INT < 33
            || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        tvPerm.setText("通知使用权    " + (notif ? "✓ 已开启" : "✗ 未开启（收信翻译不可用）")
            + "\n无障碍服务    " + (a11y ? "✓ 已开启" : "✗ 未开启（悬浮翻译不可用）")
            + "\n悬浮窗权限    " + (overlay ? "✓ 已授予" : "✗ 未授予（悬浮球不可用）")
            + "\n通知显示权限  " + (postNotif ? "✓ 已授予" : "✗ 未授予（看不到译文通知）"));
        tvPerm.setTextColor(notif && a11y && overlay && postNotif ? C_OK : C_TEXT);
    }
}
