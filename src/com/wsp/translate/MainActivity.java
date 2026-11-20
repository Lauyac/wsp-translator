package com.wsp.translate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    // ---- 设计规范（现代蓝 · Slate 中性色 · 规范化 v2）----
    // 配色：页面/卡片/主色/文字四级/描边/禁用
    private static final int C_PAGE   = 0xFFF6F7FB;  // 页面底色（更柔和）
    private static final int C_CARD   = 0xFFFFFFFF;  // 卡片
    private static final int C_PRIMARY= 0xFF2563EB;  // 主色 蓝
    private static final int C_PRIMARY_DARK = 0xFF1E40AF;
    private static final int C_PRIMARY_PRESSED = 0xFF1D4ED8;  // 按下态
    private static final int C_PRIMARY_LIGHT = 0xFFEFF6FF;
    private static final int C_TEXT   = 0xFF0F172A;  // 主文字
    private static final int C_SUB    = 0xFF475569;  // 次级文字（加深提升可读性）
    private static final int C_HINT   = 0xFF94A3B8;  // 辅助文字/占位/禁用文字
    private static final int C_STROKE = 0xFFE8EDF4;  // 卡片描边（更轻）
    private static final int C_INPUT_STROKE = 0xFFCBD5E1;
    private static final int C_DISABLED_BG = 0xFFE2E8F0;  // 禁用背景
    private static final int C_OK     = 0xFF16A34A;
    private static final int C_ERR    = 0xFFDC2626;
    private static final int C_WARN   = 0xFFD97706;
    // 圆角体系（dp）：卡片 / 组件 / 胶囊
    private static final float R_CARD = 20;
    private static final float R_COMP = 14;
    private static final float R_PILL = 999;
    // 分节图标着色体系（前景色 / 浅底色），保持同一饱和度档位，风格统一
    private static final int T_BLUE   = 0xFF2563EB; private static final int T_BLUE_BG   = 0xFFEFF6FF;
    private static final int T_VIOLET = 0xFF7C3AED; private static final int T_VIOLET_BG = 0xFFF5F3FF;
    private static final int T_GREEN  = 0xFF059669; private static final int T_GREEN_BG  = 0xFFECFDF5;
    private static final int T_AMBER  = 0xFFB45309; private static final int T_AMBER_BG  = 0xFFFFFBEB;
    private static final int T_CYAN   = 0xFF0E7490; private static final int T_CYAN_BG   = 0xFFECFEFF;
    private static final int T_ROSE   = 0xFFBE123C; private static final int T_ROSE_BG   = 0xFFFFF1F2;
    private static final int T_INDIGO = 0xFF4F46E5; private static final int T_INDIGO_BG = 0xFFEEF2FF;
    private static final int T_SLATE  = 0xFF475569; private static final int T_SLATE_BG  = 0xFFF1F5F9;
    private static final int T_OK_BG  = 0xFFECFDF5;
    private static final int T_ERR_BG = 0xFFFEF2F2;

    private Settings st;
    private Handler ui;
    /** 宽屏时内容区最大宽度（px），0 表示不限。 */
    private int maxCardW = 0;
    private EditText etKey, etBase;
    private Spinner spDraft, spIncoming, spModel;
    /** 模型预设列表（按展示顺序）。 */
    private static final String[] MODEL_PRESETS = {
        "deepseek-flash", "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4.1-flash"};
    /** 模型下拉的「自定义」入口项：选中后弹出输入框。 */
    private static final String ITEM_CUSTOM = "＋ 自定义模型…";
    /** 历史保存的自定义模型名（非预设时自动追加到列表末尾）。 */
    private String customModel = "";
    private CheckBox cbShowKey;
    private Switch swNotif, swBubble, swAutoHide, swSkipZh;
    private TextView tvResult, tvStatToday, tvStatTotal;
    /** 权限条目状态行（tag 上挂对应的「去开启」按钮）。 */
    private TextView tvPermN, tvPermA, tvPermO, tvPermP;
    private LinearLayout updatePanel, updateActions;
    private TextView tvUpdateTitle, tvUpdateNotes, tvUpdate;
    private ProgressBar pbUpdate;
    private Button btnCheck, btnDownload, btnCancel;
    private Updater.Info pendingInfo;
    /** 备份导出/恢复的系统文件选择器请求码。 */
    private static final int REQ_EXPORT_BACKUP = 101;
    private static final int REQ_IMPORT_BACKUP = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.install(this);
        st = new Settings(this);
        ui = new Handler(Looper.getMainLooper());
        setContentView(buildUi());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPerm();
        if (tvStatToday != null && tvStatTotal != null) refreshStats();
    }

    // ==================== UI 构建 ====================

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_PAGE);

        // ---- 可滚动内容区（底部操作栏固定在滚动区之外）----
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        sv.addView(page);
        root.addView(sv, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 响应式留白：宽屏（平板/横屏）加大页边距，并限制内容列宽度居中
        int pagePad = getResources().getConfiguration().screenWidthDp >= 600 ? dp(28) : dp(16);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        maxCardW = (screenW - 2 * pagePad) > dp(600) ? dp(600) : 0;
        LinearLayout content = column(page, pagePad, dp(12), pagePad, dp(20));

        // ---- 顶部渐变 Hero ----
        View hero = buildHero();
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (maxCardW > 0) { hp.width = maxCardW; hp.gravity = Gravity.CENTER_HORIZONTAL; }
        hp.setMargins(0, dp(4), 0, dp(4));
        hero.setLayoutParams(hp);
        content.addView(hero);

        // ---- ① 连接与凭据 ----
        LinearLayout c1 = card(content, dp(20));
        sectionTitle(c1, "连接与凭据", "1", "填写翻译服务密钥；修改后点底部「保存设置」生效", "🔑", T_BLUE, T_BLUE_BG);
        c1.addView(fieldLabel("API Key", "必填 · 在 DeepSeek 开放平台申请，仅保存在本机并加密存储"));
        etKey = input("粘贴 API Key（sk-…）", true);
        c1.addView(etKey);
        cbShowKey = check("显示 Key 明文，方便核对是否输入正确");
        cbShowKey.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                etKey.setInputType(checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                etKey.setSelection(etKey.getText().length());
            }
        });
        c1.addView(cbShowKey);
        c1.addView(fieldLabel("API 地址", "留空使用官方地址；兼容 OpenAI 格式网关与私有代理"));
        etBase = input("默认 https://api.deepseek.com", false);
        c1.addView(etBase);

        // ---- ② 模型选择 ----
        LinearLayout cModel = card(content, dp(20));
        sectionTitle(cModel, "模型选择", "2", "一般保持默认即可；修改立即生效", "✨", T_VIOLET, T_VIOLET_BG);
        cModel.addView(fieldLabel("翻译使用的模型", null));
        spModel = modelSpinner();
        spModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (spModel.getSelectedItem() == null) return;
                String sel = String.valueOf(spModel.getSelectedItem());
                if (ITEM_CUSTOM.equals(sel)) {
                    promptCustomModel();
                } else if (!sel.isEmpty()) {
                    st.put("model", sel);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        cModel.addView(spinnerWrap(spModel));
        cModel.addView(caption("预设推荐项；选最后一项「＋ 自定义模型…」可输入任意模型名，自定义值会保留在列表末尾"));

        // ---- ③ 权限开启（逐项：状态 + 去开启）----
        LinearLayout cPerm = card(content, dp(20));
        sectionTitle(cPerm, "权限开启", "3", "开启翻译方式对应的权限；只用收信翻译，开第一项即可", "🛡", T_GREEN, T_GREEN_BG);
        tvPermN = permRow(cPerm, "通知使用权",
            "收信翻译必需 · 监听聊天应用的新消息", "🔔",
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
            });
        tvPermA = permRow(cPerm, "无障碍服务",
            "悬浮翻译必需 · 让悬浮球读取当前屏幕", "♿",
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            });
        tvPermO = permRow(cPerm, "悬浮窗权限",
            "悬浮球显示必需 · 允许在其他应用上层显示悬浮球", "▣",
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                }
            });
        if (Build.VERSION.SDK_INT >= 33) {
            tvPermP = permRow(cPerm, "通知显示权限（Android 13+）",
                "译文通知显示必需 · 未授予时看不到翻译结果", "💬",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                    }
                });
        }

        // ---- ④ 翻译偏好（即时生效）----
        LinearLayout cPref = card(content, dp(20));
        sectionTitle(cPref, "翻译偏好", "4", "修改立即生效，无需手动保存", "⚙", T_AMBER, T_AMBER_BG);
        cPref.addView(fieldLabel("收到的外语消息，翻译成", "默认简体中文"));
        spIncoming = spinner();
        bindSpinner(spIncoming, "incomingLang");
        cPref.addView(spinnerWrap(spIncoming));
        cPref.addView(fieldLabel("我写的中文草稿，翻译成", "回复时把中文译成对方语言"));
        spDraft = spinner();
        bindSpinner(spDraft, "draftLang");
        cPref.addView(spinnerWrap(spDraft));
        swNotif = switchRow(cPref, "自动翻译收到的消息",
            "新消息自动弹出「译 ·」通知（需通知使用权）");
        bindSwitch(swNotif, "notifEnabled");
        divider(cPref);
        swBubble = switchRow(cPref, "悬浮球即点即译",
            "聊天界面点悬浮球翻译当前屏幕（需无障碍 + 悬浮窗权限）");
        bindSwitch(swBubble, "bubbleEnabled");
        divider(cPref);
        swAutoHide = switchRow(cPref, "悬浮球仅在聊天应用内显示",
            "离开 WhatsApp 自动隐藏、进入时恢复，避免悬浮球打扰其他应用");
        bindSwitch(swAutoHide, "bubbleAutoHide");
        divider(cPref);
        swSkipZh = switchRow(cPref, "对方已发中文时跳过翻译",
            "避免中文消息被重复翻译，节省流量");
        bindSwitch(swSkipZh, "skipChineseIncoming");

        // ---- ⑤ 使用统计（本地聚合，返回页面时刷新）----
        LinearLayout cStat = card(content, dp(20));
        sectionTitle(cStat, "使用统计", "5", "按本机翻译记录聚合，仅保存在手机上", "📊", T_CYAN, T_CYAN_BG);
        tvStatToday = new TextView(this);
        tvStatToday.setTextColor(C_SUB);
        tvStatToday.setTextSize(13);
        cStat.addView(tvStatToday);
        tvStatTotal = new TextView(this);
        tvStatTotal.setTextColor(C_SUB);
        tvStatTotal.setTextSize(13);
        tvStatTotal.setPadding(0, dp(6), 0, 0);
        cStat.addView(tvStatTotal);
        LinearLayout statBtns = row();
        Button btnHistory = outlineBtn("翻译历史", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        statBtns.addView(btnHistory, half());
        View statGap = new View(this);
        statBtns.addView(statGap, new LinearLayout.LayoutParams(dp(10), 1));
        Button btnGlossary = outlineBtn("术语速查", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GlossaryActivity.class));
            }
        });
        statBtns.addView(btnGlossary, half());
        cStat.addView(withMargin(statBtns, 0, dp(12), 0, 0));

        // ---- ⑥ 应用更新 ----
        LinearLayout cUp = card(content, dp(20));
        sectionTitle(cUp, "应用更新", "6", null, "⬆", T_ROSE, T_ROSE_BG);

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
        tvVerBadge.setBackground(interactiveBg(
            bg(C_PRIMARY_LIGHT, dp(10), 0),
            bg(0xFFDBEAFE, dp(10), 0),
            bg(C_PRIMARY_LIGHT, dp(10), 0),
            0x142563EB));
        tvVerBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        // 点击徽章查看当前版本的更新内容
        tvVerBadge.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showVersionNotes(); }
        });
        verRow.addView(tvVerBadge);
        cUp.addView(verRow);

        TextView verHint = new TextView(this);
        verHint.setText("点右侧版本号可查看本版本更新内容");
        verHint.setTextColor(C_SUB);
        verHint.setTextSize(11);
        verHint.setPadding(dp(2), dp(6), dp(2), 0);
        cUp.addView(verHint);

        // 检查按钮（与其他卡片的按钮保持同一节奏：上 12dp）
        btnCheck = primaryBtn("检查更新", new View.OnClickListener() {
            @Override public void onClick(View v) { checkUpdate(); }
        });
        cUp.addView(withMargin(btnCheck, 0, dp(12), 0, 0));

        // 更新状态行（与卡片内文字左对齐，去掉多余内边距）
        tvUpdate = new TextView(this);
        tvUpdate.setTextSize(13);
        tvUpdate.setTextColor(C_SUB);
        tvUpdate.setPadding(dp(2), dp(10), dp(2), dp(2));
        tvUpdate.setVisibility(View.GONE);
        cUp.addView(tvUpdate);

        // 新版本详情面板（发现新版本时显示）
        updatePanel = new LinearLayout(this);
        updatePanel.setOrientation(LinearLayout.VERTICAL);
        updatePanel.setBackground(bgStroke(C_PRIMARY_LIGHT, 0xFFBFDBFE, dp(R_COMP)));
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

        cUp.addView(withMargin(updatePanel, 0, dp(10), 0, 0));
        updatePanel.setVisibility(View.GONE);

        // 下载进度条（确认下载后显示，与面板同宽同节奏）
        pbUpdate = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbUpdate.setMax(100);
        pbUpdate.getProgressDrawable().setColorFilter(C_PRIMARY, android.graphics.PorterDuff.Mode.SRC_IN);
        cUp.addView(withMargin(pbUpdate, 0, dp(10), 0, 0));
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
        cUp.addView(updateActions);
        updateActions.setVisibility(View.GONE);

        // ---- ⑦ 备份与恢复（JSON 文件，不含 API Key）----
        LinearLayout cBackup = card(content, dp(20));
        sectionTitle(cBackup, "备份与恢复", "7",
            "换机或重装时导出/恢复应用设置；API Key 属敏感信息不包含在备份内，恢复后需单独填写", "💾", T_INDIGO, T_INDIGO_BG);
        LinearLayout backupBtns = row();
        Button btnExport = outlineBtn("导出设置", new View.OnClickListener() {
            @Override public void onClick(View v) { exportBackup(); }
        });
        backupBtns.addView(btnExport, half());
        View backupGap = new View(this);
        backupBtns.addView(backupGap, new LinearLayout.LayoutParams(dp(10), 1));
        Button btnImport = outlineBtn("恢复设置", new View.OnClickListener() {
            @Override public void onClick(View v) { importBackup(); }
        });
        backupBtns.addView(btnImport, half());
        cBackup.addView(backupBtns);

        // ---- ⑧ 使用说明 ----
        LinearLayout cHelp = card(content, dp(20));
        sectionTitle(cHelp, "使用说明", "8", "三种翻译方式按需使用；不用悬浮翻译可不开启对应权限", "📖", T_SLATE, T_SLATE_BG);
        cHelp.addView(helpItem("收信翻译",
            "保持本应用在后台运行；聊天应用收到新消息后，自动发出以「译 ·」开头的通知，同时展示原文与译文。翻译失败时也会发出失败通知并说明原因。", "🔔"));
        cHelp.addView(helpItem("悬浮翻译",
            "打开聊天界面后点悬浮球「译」，自动读取当前屏幕内容并翻译显示。", "🎯"));
        cHelp.addView(helpItem("草稿翻译",
            "在悬浮面板输入中文，点「翻译并填入」把译文写入聊天输入框；请核对后手动发送，本应用绝不自动发送消息。", "⌨"));
        cHelp.addView(helpItem("隐私与数据",
            "聊天内容仅作为翻译请求发送给你配置的 API 服务端，不在本应用保存或上传；译文缓存仅存内存，退出即清空。API Key 本机加密存储；崩溃日志仅保存在本机。完整说明见仓库 PRIVACY.md。", "🔒"));

        TextView foot = new TextView(this);
        foot.setText("WSP Translator · 开源 · GitHub @YaChiLau");
        foot.setTextSize(11);
        foot.setTextColor(C_HINT);
        foot.setGravity(Gravity.CENTER);
        content.addView(withMargin(foot, 0, dp(16), 0, dp(4)));

        // ---- 底部固定操作栏：结果反馈 + 测试连接 / 保存设置 ----
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(C_CARD);
        barBg.setCornerRadii(new float[]{
            dp(R_CARD), dp(R_CARD), dp(R_CARD), dp(R_CARD), 0, 0, 0, 0});
        bar.setBackground(barBg);
        bar.setElevation(dp(6));
        bar.setPadding(pagePad, dp(12), pagePad, dp(12));

        tvResult = new TextView(this);
        tvResult.setTextSize(12);
        tvResult.setTextColor(C_TEXT);
        tvResult.setLineSpacing(dp(3), 1f);
        tvResult.setVisibility(View.GONE);
        bar.addView(tvResult);

        LinearLayout barBtns = row();
        barBtns.addView(outlineBtn("测试连接", new View.OnClickListener() {
            @Override public void onClick(View v) { test(); }
        }), half());
        View gap = new View(this);
        barBtns.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        barBtns.addView(primaryBtn("保存设置", new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        }), half());
        bar.addView(barBtns);

        // 操作栏与内容区之间的发丝分隔线，强化固定层的层次感
        View hair = new View(this);
        hair.setBackground(bg(C_STROKE, 0, 0));
        root.addView(hair, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        root.addView(bar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    /** 顶部渐变横幅：应用图标位 + 名称简介 + 功能胶囊 + 版本胶囊。 */
    private View buildHero() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF60A5FA, C_PRIMARY, 0xFF1E3A8A});
        g.setCornerRadius(dp(24));
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(g);
        hero.setElevation(dp(4));
        hero.setPadding(dp(22), dp(22), dp(22), dp(22));

        // 上半部分：图标位 + 名称/简介
        LinearLayout top = row();
        TextView icon = new TextView(this);
        icon.setText("译");
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(20);
        icon.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(bg(0x30FFFFFF, dp(14), dp(1)));
        top.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText("WSP 翻译助手");
        name.setTextColor(Color.WHITE);
        name.setTextSize(21);
        name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        name.setLetterSpacing(-0.01f);
        titles.addView(name);
        TextView sub = new TextView(this);
        sub.setText("WhatsApp 聊天实时翻译");
        sub.setTextColor(0xFFBFDBFE);
        sub.setTextSize(13);
        sub.setPadding(0, dp(3), 0, 0);
        titles.addView(sub);
        top.addView(withMargin(titles, dp(14), 0, 0, 0));
        hero.addView(top);

        // 下半部分：三个功能胶囊 + 版本胶囊
        LinearLayout chips = row();
        chips.setPadding(0, dp(16), 0, 0);
        String[] feats = {"通知翻译", "悬浮翻译", "草稿翻译"};
        for (String f : feats) {
            TextView chip = new TextView(this);
            chip.setText(f);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(11);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setBackground(bg(0x24FFFFFF, dp(R_PILL), 0));
            chip.setPadding(dp(11), dp(4), dp(11), dp(4));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(8);
            chips.addView(chip, cp);
        }
        TextView ver = new TextView(this);
        ver.setText("v" + versionName());
        ver.setTextColor(0xFFBFDBFE);
        ver.setTextSize(11);
        ver.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.leftMargin = dp(2);
        chips.addView(ver, vp);
        hero.addView(chips);

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

    /** 白色圆角卡片：轻描边 + 微投影 + 宽屏限宽居中。 */
    private LinearLayout card(ViewGroup parent, int pad) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(R_CARD));
        bg.setStroke(dp(1), C_STROKE);
        v.setBackground(bg);
        v.setElevation(dp(2));
        v.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (maxCardW > 0) { p.width = maxCardW; p.gravity = Gravity.CENTER_HORIZONTAL; }
        v.setLayoutParams(p);
        parent.addView(withMargin(v, 0, dp(14), 0, 0));
        return v;
    }

    /** 卡片内分节标题：着色图标位 + 加粗标题 + 序号胶囊 + 可选说明行。 */
    private void sectionTitle(ViewGroup card, String title, String no, String caption,
                              String glyph, int tintFg, int tintBg) {
        LinearLayout row = row();
        TextView tile = new TextView(this);
        tile.setText(glyph);
        tile.setTextColor(tintFg);
        tile.setTextSize(13);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(bg(tintBg, dp(8), 0));
        row.addView(tile, new LinearLayout.LayoutParams(dp(27), dp(27)));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_TEXT);
        t.setTextSize(16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(10), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView n = new TextView(this);
        n.setText(no);
        n.setTextColor(tintFg);
        n.setTextSize(11);
        n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        n.setBackground(bg(tintBg, dp(R_PILL), 0));
        n.setPadding(dp(9), dp(3), dp(9), dp(3));
        row.addView(n);

        card.addView(withMargin(row, 0, 0, 0, dp(6)));

        if (caption != null && !caption.isEmpty()) {
            TextView c = new TextView(this);
            c.setText(caption);
            c.setTextColor(C_HINT);
            c.setTextSize(12);
            c.setPadding(dp(37), 0, dp(2), 0);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, dp(12));
            card.addView(c, cp);
        } else {
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
            sp.setMargins(0, 0, 0, dp(10));
            card.addView(new View(this), sp);
        }
    }

    /** 卡片内细分隔线：区分同卡片内的并列条目。 */
    private View divider(ViewGroup parent) {
        View v = new View(this);
        v.setBackground(bg(C_STROKE, 0, 0));
        parent.addView(withMargin(v, dp(2), dp(4), dp(2), dp(4)),
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return v;
    }

    /** 字段标签：主标签 + 一行说明。 */
    private LinearLayout fieldLabel(String t, String hint) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(2), dp(10), dp(2), dp(6));
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(C_TEXT);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.addView(tv);
        if (hint != null && hint.length() > 0) {
            TextView h = new TextView(this);
            h.setText(hint);
            h.setTextColor(C_HINT);
            h.setTextSize(11);
            h.setPadding(0, dp(3), 0, 0);
            v.addView(h);
        }
        return v;
    }

    private TextView caption(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(C_HINT);
        v.setTextSize(12);
        v.setPadding(dp(2), 0, dp(2), dp(10));
        return v;
    }

    /** 输入框背景：默认/聚焦双态（聚焦加深描边并加粗）。 */
    private StateListDrawable inputBg() {
        GradientDrawable def = bgStroke(C_CARD, C_INPUT_STROKE, dp(R_COMP));
        GradientDrawable foc = bgStroke(C_CARD, C_PRIMARY, dp(R_COMP));
        foc.setStroke(dp(2), C_PRIMARY);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, foc);
        s.addState(new int[]{}, def);
        return s;
    }

    private EditText input(String hint, boolean key) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(C_TEXT);
        e.setHintTextColor(C_HINT);
        e.setTextSize(15);
        e.setBackground(inputBg());
        e.setPadding(dp(14), dp(13), dp(14), dp(13));
        if (key) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    /** 下拉框容器：右侧下拉箭头；交互态绘制在 Spinner 自身背景上（保证按压态生效）。 */
    private View spinnerWrap(Spinner s) {
        LinearLayout w = new LinearLayout(this);
        w.setGravity(Gravity.CENTER_VERTICAL);
        w.setPadding(dp(8), 0, dp(12), 0);
        s.setBackground(spinnerBg());
        s.setPadding(dp(12), 0, dp(6), 0);
        s.setElevation(dp(1));
        w.addView(s, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextColor(C_HINT);
        arrow.setTextSize(12);
        arrow.setGravity(Gravity.CENTER);
        arrow.setClickable(false);
        w.addView(arrow, new LinearLayout.LayoutParams(
            dp(16), ViewGroup.LayoutParams.MATCH_PARENT));
        return w;
    }

    /** 下拉框背景：默认描边 → 按下浅蓝底 → 聚焦主色描边加粗。 */
    private StateListDrawable spinnerBg() {
        GradientDrawable def = bgStroke(C_CARD, C_INPUT_STROKE, dp(R_COMP));
        GradientDrawable prs = bgStroke(C_PRIMARY_LIGHT, C_PRIMARY, dp(R_COMP));
        GradientDrawable foc = bgStroke(C_CARD, C_PRIMARY, dp(R_COMP));
        foc.setStroke(dp(2), C_PRIMARY);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, prs);
        s.addState(new int[]{android.R.attr.state_focused}, foc);
        s.addState(new int[]{}, def);
        return s;
    }

    /** 下拉弹层底：圆角白底卡片 + 轻描边。 */
    private GradientDrawable popupBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_CARD);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), C_STROKE);
        return g;
    }

    /** 统一下拉弹层样式：条目带按压波纹，选中项主色加粗并打勾；闭合态为深色中字重。
     *  仅改样式，选项数据与选中逻辑不变。 */
    private void styleDropdown(final Spinner s, String[] items) {
        s.setPopupBackgroundDrawable(popupBg());
        s.setAdapter(new ArrayAdapter<String>(this, 0, items) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                TextView v = (TextView) cv;
                if (v == null) {
                    v = new TextView(MainActivity.this);
                    v.setTextColor(C_TEXT);
                    v.setTextSize(14);
                    v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    v.setGravity(Gravity.CENTER_VERTICAL);
                    v.setSingleLine(true);
                    v.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    v.setPadding(dp(12), 0, dp(4), 0);
                }
                v.setText(getItem(pos));
                return v;
            }
            @Override public View getDropDownView(int pos, View cv, ViewGroup parent) {
                TextView v = (TextView) cv;
                if (v == null) {
                    v = new TextView(MainActivity.this);
                    v.setTextSize(14);
                    v.setGravity(Gravity.CENTER_VERTICAL);
                    v.setSingleLine(true);
                    v.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    v.setPadding(dp(14), dp(11), dp(14), dp(11));
                }
                boolean sel = pos == s.getSelectedItemPosition();
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(10));
                g.setColor(sel ? C_PRIMARY_LIGHT : 0x00000000);
                GradientDrawable mask = new GradientDrawable();
                mask.setCornerRadius(dp(10));
                mask.setColor(0xFFFFFFFF);
                v.setBackground(new RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x142563EB), g, mask));
                v.setTextColor(sel ? C_PRIMARY : C_TEXT);
                v.setTypeface(Typeface.create("sans-serif",
                    sel ? Typeface.BOLD : Typeface.NORMAL));
                v.setText((sel ? "✓  " : "      ") + getItem(pos));
                return v;
            }
        });
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        String[] labels = new String[Langs.ITEMS.length];
        for (int i = 0; i < Langs.ITEMS.length; i++) labels[i] = Langs.ITEMS[i][1];
        styleDropdown(s, labels);
        return s;
    }

    /** 模型下拉框：预设 4 项 + 自定义入口；历史保存的自定义模型名追加在入口之前。 */
    private Spinner modelSpinner() {
        Spinner s = new Spinner(this);
        rebuildModelAdapter(s);
        return s;
    }

    /** 组装模型选项列表并选中当前保存值。 */
    private void rebuildModelAdapter(Spinner s) {
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String m : MODEL_PRESETS) items.add(m);
        int selIdx = -1;
        String saved = st.model();
        if (saved != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).equals(saved)) { selIdx = i; break; }
            }
        }
        if (selIdx < 0) {
            // 非预设：视为历史自定义模型，保留在自定义入口之前
            customModel = (saved == null || saved.trim().isEmpty()) ? "" : saved.trim();
            if (!customModel.isEmpty()) {
                items.add(customModel);
                selIdx = items.size() - 1;
            }
        } else {
            customModel = "";
        }
        items.add(ITEM_CUSTOM);
        styleDropdown(s, items.toArray(new String[0]));
        s.setSelection(selIdx < 0 ? 1 : selIdx); // 默认 deepseek-v4-flash
    }

    /** 「自定义模型」输入弹窗：保存后立即生效并刷新列表；取消则恢复原选中项。 */
    private void promptCustomModel() {
        final int restoreIdx = spModel.getSelectedItemPosition();
        final EditText et = new EditText(this);
        et.setHint("输入模型名，如 deepseek-reasoner");
        et.setText(customModel);
        et.setSelection(et.getText().length());
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        int hpad = dp(20);
        wrap.setPadding(hpad, dp(4), hpad, 0);
        wrap.addView(et, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        new android.app.AlertDialog.Builder(this)
            .setTitle("自定义模型")
            .setMessage("将使用该模型名发起翻译请求，请确认 API 服务端支持它。")
            .setView(wrap)
            .setPositiveButton("使用此模型", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    String v = et.getText().toString().trim();
                    if (v.isEmpty()) {
                        Toast.makeText(MainActivity.this, "模型名不能为空，已恢复原选项", Toast.LENGTH_SHORT).show();
                        spModel.setSelection(Math.max(0, restoreIdx));
                        return;
                    }
                    customModel = v;
                    st.put("model", v);
                    rebuildModelAdapter(spModel); // 选中项恢复为刚保存的自定义名
                }
            })
            .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    // 回到取消前的选项（避免停留在「自定义…」入口项上）
                    spModel.setSelection(Math.max(0, restoreIdx));
                }
            })
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                @Override public void onCancel(android.content.DialogInterface d) {
                    spModel.setSelection(Math.max(0, restoreIdx));
                }
            })
            .show();
    }

    private CheckBox check(String t) {
        CheckBox c = new CheckBox(this);
        c.setText(t);
        c.setTextSize(13);
        c.setTextColor(C_TEXT);
        c.setPadding(dp(4), dp(6), dp(4), dp(6));
        c.setMinimumHeight(dp(40)); // 保证触控目标高度
        return c;
    }

    /** 开关行：左侧标题+说明，右侧 Switch（拇指/轨道按选中态着色）。 */
    private Switch switchRow(ViewGroup parent, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_TEXT);
        t.setTextSize(14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        left.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(C_HINT);
        d.setTextSize(11);
        d.setPadding(0, dp(2), 0, 0);
        left.addView(d);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        android.content.res.ColorStateList thumb = new android.content.res.ColorStateList(
            new int[][]{{android.R.attr.state_checked}, {}},
            new int[]{C_PRIMARY, 0xFFCBD5E1});
        android.content.res.ColorStateList track = new android.content.res.ColorStateList(
            new int[][]{{android.R.attr.state_checked}, {}},
            new int[]{0xFFB7CDF8, 0xFFE2E8F0});
        sw.setThumbTintList(thumb);
        sw.setTrackTintList(track);
        row.addView(sw, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(row);
        return sw;
    }

    /** 权限条目行：左侧图标位+名称+用途+状态胶囊，右侧「去开启」按钮（开启后隐藏）。
     *  返回状态 TextView（tag 上挂按钮，供 refreshPerm 更新）。 */
    private TextView permRow(ViewGroup parent, String title, String desc, String glyph,
                             View.OnClickListener open) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(bg(C_PAGE, dp(R_COMP), dp(1)));
        row.setPadding(dp(12), dp(11), dp(12), dp(11));

        TextView tile = new TextView(this);
        tile.setText(glyph);
        tile.setTextColor(T_GREEN);
        tile.setTextSize(15);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(bgStroke(C_CARD, C_STROKE, dp(10)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(32), dp(32));
        tp.rightMargin = dp(10);
        row.addView(tile, tp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_TEXT);
        t.setTextSize(14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        left.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(C_HINT);
        d.setTextSize(11);
        d.setPadding(0, dp(2), 0, 0);
        left.addView(d);
        TextView s = new TextView(this);
        s.setTextSize(11);
        s.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        // 胶囊底色由 setPermState 按状态着色；先给默认占位
        s.setBackground(bg(T_ERR_BG, dp(R_PILL), 0));
        s.setPadding(dp(9), dp(3), dp(9), dp(3));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        left.addView(s, sp);

        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button b = new Button(this);
        b.setText("去开启");
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setTextColor(textColor(C_PRIMARY, C_HINT));
        b.setBackground(interactiveBg(
            bgStroke(C_PRIMARY_LIGHT, C_PRIMARY, dp(R_COMP)),
            bgStroke(0xFFDBEAFE, C_PRIMARY, dp(R_COMP)),
            bgStroke(C_PAGE, C_STROKE, dp(R_COMP)),
            0x1A2563EB));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setOnClickListener(open);
        s.setTag(b);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(dp(10), 0, 0, 0);
        row.addView(b, bp);

        parent.addView(withMargin(row, 0, dp(10), 0, 0));
        return s;
    }

    /** 使用说明条目：图标位 + 标题 + 说明段。 */
    private View helpItem(String title, String desc, String glyph) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setPadding(dp(2), dp(2), dp(2), dp(10));

        TextView tile = new TextView(this);
        tile.setText(glyph);
        tile.setTextColor(C_PRIMARY);
        tile.setTextSize(14);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(bg(C_PRIMARY_LIGHT, dp(9), 0));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(30), dp(30));
        tp.rightMargin = dp(10);
        v.addView(tile, tp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_TEXT);
        t.setTextSize(13);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(C_SUB);
        d.setTextSize(12);
        d.setLineSpacing(dp(3), 1f);
        d.setPadding(0, dp(4), 0, 0);
        col.addView(d);
        v.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    /** Spinner 即时保存：选中即写入设置。 */
    private void bindSpinner(Spinner s, final String key) {
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < Langs.ITEMS.length) st.put(key, Langs.ITEMS[pos][0]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }

    /** Switch 即时保存；悬浮球开关额外通知服务实时增删悬浮窗。 */
    private void bindSwitch(final Switch sw, final String key) {
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                st.put(key, checked);
                if ("bubbleEnabled".equals(key)) {
                    FloatService.applyBubbleEnabled(MainActivity.this, checked);
                }
            }
        });
    }

    private Button primaryBtn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setLetterSpacing(0.02f);
        b.setAllCaps(false);
        b.setStateListAnimator(null); // 去系统投影，按压反馈交给波纹
        b.setTextColor(textColor(Color.WHITE, C_HINT));
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF3B82F6, C_PRIMARY});
        grad.setCornerRadius(dp(R_COMP));
        GradientDrawable gradPressed = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF2563EB, C_PRIMARY_PRESSED});
        gradPressed.setCornerRadius(dp(R_COMP));
        b.setBackground(interactiveBg(
            grad,
            gradPressed,
            bg(C_DISABLED_BG, dp(R_COMP), 0),
            0x2E2563EB));
        b.setPadding(0, dp(14), 0, dp(14));
        b.setOnClickListener(l);
        return b;
    }

    private Button outlineBtn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setTextColor(textColor(C_PRIMARY, C_HINT));
        b.setBackground(interactiveBg(
            bgStroke(C_CARD, C_PRIMARY, dp(R_COMP)),
            bgStroke(C_PRIMARY_LIGHT, C_PRIMARY, dp(R_COMP)),
            bgStroke(C_PAGE, C_STROKE, dp(R_COMP)),
            0x1A2563EB));
        b.setPadding(0, dp(13), 0, dp(13));
        b.setOnClickListener(l);
        return b;
    }

    // ==================== 绘制工具 ====================

    /** 三态背景（禁用/按下/默认）+ 按压波纹，统一交互反馈。 */
    private Drawable interactiveBg(Drawable def, Drawable pressed, Drawable disabled, int rippleColor) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, def);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(R_COMP));
        mask.setColor(0xFFFFFFFF);
        return new RippleDrawable(
            android.content.res.ColorStateList.valueOf(rippleColor), s, mask);
    }

    /** 文字颜色状态表：禁用变灰。 */
    private android.content.res.ColorStateList textColor(int defColor, int disabledColor) {
        return new android.content.res.ColorStateList(
            new int[][]{{-android.R.attr.state_enabled}, {}},
            new int[]{disabledColor, defColor});
    }

    private GradientDrawable bg(int color, float radius, int stroke) {
        return Ui.fill(this, color, radius, stroke);
    }

    private GradientDrawable bgStroke(int color, int strokeColor, float radius) {
        return Ui.strokeRect(this, color, strokeColor, radius);
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

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ==================== 业务逻辑（保持不变） ====================

    private void load() {
        etKey.setText(st.apiKey());
        etBase.setText(st.baseUrl());
        spDraft.setSelection(Langs.indexOf(st.draftLang()));
        spIncoming.setSelection(Langs.indexOf(st.incomingLang()));
        swSkipZh.setChecked(st.skipChineseIncoming());
        swNotif.setChecked(st.notifEnabled());
        swBubble.setChecked(st.bubbleEnabled());
        swAutoHide.setChecked(st.bubbleAutoHide());
        refreshStats();
    }

    /** 从本地翻译记录聚合使用统计（次数/字符/成功率），轻查询直接在主线程执行。 */
    private void refreshStats() {
        HistoryStore.Stats s = HistoryStore.stats(this);
        tvStatToday.setText("今日: " + s.todayCount + " 次翻译 · 约 " + s.todayChars + " 字符");
        tvStatTotal.setText("累计: " + s.totalCount + " 次翻译 · 约 " + s.totalChars
            + " 字符 · 成功率 " + s.successRate() + "%");
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

    /** 点击版本徽章：弹窗展示当前版本在 GitHub Releases 中的更新说明。 */
    private void showVersionNotes() {
        final String ver = versionName();
        final TextView tv = new TextView(this);
        tv.setTextSize(13);
        tv.setTextColor(C_TEXT);
        tv.setLineSpacing(dp(3), 1f);
        tv.setPadding(dp(20), dp(16), dp(20), dp(16));
        tv.setText("正在获取 v" + ver + " 的更新说明…");

        ScrollView sc = new ScrollView(this);
        sc.addView(tv);
        new android.app.AlertDialog.Builder(this)
            .setTitle("v" + ver + " 更新内容")
            .setView(sc)
            .setPositiveButton("知道了", null)
            .show();

        // 更新检查走工具池，不与翻译队列互相阻塞
        Translator.submitUtil(new Runnable() {
            @Override
            public void run() {
                final Updater.Result r = Updater.fetchNotes(ver);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (r.ok) {
                            tv.setText(formatNotes(r.message));
                        } else {
                            tv.setText(r.message
                                + "\n\n说明文本托管在 GitHub Releases，需要网络可用；请稍后重试。");
                            tv.setTextColor(C_ERR);
                        }
                    }
                });
            }
        });
    }

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
        Translator.submitUtil(new Runnable() {
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
        Translator.submitUtil(new Runnable() {
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
        String key = etKey.getText().toString().trim();
        if (!st.setApiKey(key)) {
            // AndroidKeyStore 不可用：拒绝明文落盘
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_ERR);
            tvResult.setText("✗ API Key 加密失败（本机安全存储不可用），已放弃保存以避免明文存储；请重启手机后再试");
            Toast.makeText(this, "API Key 加密失败，未保存", Toast.LENGTH_LONG).show();
            return;
        }
        String base = etBase.getText().toString().trim();
        if (base.startsWith("http://")) {
            // Android 9+ 禁止明文流量，http 地址必然连接失败，直接阻止
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_ERR);
            tvResult.setText("✗ API 地址不能使用 http:// 明文连接（系统已禁止明文流量），请改用 https:// 地址");
            Toast.makeText(this, "API 地址不能使用 http://，请改用 https://", Toast.LENGTH_LONG).show();
            return;
        }
        st.put("baseUrl", base.isEmpty() ? "https://api.deepseek.com" : base);
        st.put("model", String.valueOf(spModel.getSelectedItem()));
        st.put("draftLang", Langs.ITEMS[spDraft.getSelectedItemPosition()][0]);
        st.put("incomingLang", Langs.ITEMS[spIncoming.getSelectedItemPosition()][0]);
        st.put("skipChineseIncoming", swSkipZh.isChecked());
        st.put("notifEnabled", swNotif.isChecked());
        st.put("bubbleEnabled", swBubble.isChecked());
        tvResult.setVisibility(View.VISIBLE);
        if (etKey.getText().toString().trim().isEmpty()) {
            tvResult.setTextColor(C_WARN);
            tvResult.setText("已保存，但 API Key 为空，翻译功能暂不可用");
            Toast.makeText(this, "已保存（API Key 为空）", Toast.LENGTH_LONG).show();
        } else {
            tvResult.setTextColor(C_OK);
            tvResult.setText("✓ 已保存");
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        }
    }

    /** 测试连接：只用输入框现值发起请求，不写入任何配置（需点「保存设置」才会持久化）。 */
    private void test() {
        final String key = etKey.getText().toString().trim();
        final String base = etBase.getText().toString().trim();
        if (key.isEmpty()) {
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_WARN);
            tvResult.setText("请先填写 API Key 再测试");
            return;
        }
        if (base.startsWith("http://")) {
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_ERR);
            tvResult.setText("✗ API 地址不能使用 http:// 明文连接（系统已禁止明文流量），请改用 https:// 地址");
            return;
        }
        final String model = String.valueOf(spModel.getSelectedItem());
        final String target = Langs.ITEMS[spDraft.getSelectedItemPosition()][0];
        final String sample = "你好，请问这款产品的最低起订量是多少？";
        tvResult.setVisibility(View.VISIBLE);
        tvResult.setText("正在测试连接…（使用输入框当前值，不会自动保存）");
        final Settings s = new Settings(this);
        Translator.submitUtil(new Runnable() {
            @Override
            public void run() {
                final Translator.Result r = Translator.translate(s, sample, target, key, base, model);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvResult.setText(r.ok
                            ? ("✓ 连接成功（" + (r.cached ? "缓存命中" : "网络请求") + "）\n示例译文: " + r.text
                                + "\n配置未自动保存，如需保留请点「保存设置」")
                            : "✗ 连接失败: " + r.text);
                        tvResult.setTextColor(r.ok ? C_OK : C_ERR);
                    }
                });
            }
        });
    }

    // ==================== 备份与恢复 ====================

    private static final String BACKUP_TYPE = "wsp-backup";

    /** 系统文件选择器：创建 JSON 导出文件。 */
    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "wsp-backup-" + versionName() + ".json");
        try {
            startActivityForResult(i, REQ_EXPORT_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件选择器: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 系统文件选择器：选择此前导出的 JSON 文件。 */
    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件选择器: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_BACKUP) {
            writeBackup(uri);
        } else if (requestCode == REQ_IMPORT_BACKUP) {
            readBackup(uri);
        }
    }

    /** 导出设置（白名单键，API Key 不写入）；成功后给出结果提示。 */
    private void writeBackup(Uri uri) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("type", BACKUP_TYPE);
            o.put("version", 1);
            o.put("appVersion", versionName());
            o.put("baseUrl", st.baseUrl());
            o.put("model", st.model());
            o.put("draftLang", st.draftLang());
            o.put("incomingLang", st.incomingLang());
            o.put("notifEnabled", st.notifEnabled());
            o.put("bubbleEnabled", st.bubbleEnabled());
            o.put("bubbleAutoHide", st.bubbleAutoHide());
            o.put("skipChineseIncoming", st.skipChineseIncoming());
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) throw new IllegalStateException("目标文件不可写");
            os.write(o.toString(2).getBytes("UTF-8"));
            os.close();
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_OK);
            tvResult.setText("✓ 设置已导出（不含 API Key）");
            Toast.makeText(this, "设置已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_ERR);
            tvResult.setText("✗ 导出失败: " + e.getMessage());
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 读取备份 JSON 并应用（仅接受白名单键，非法结构整体拒绝）。 */
    private void readBackup(Uri uri) {
        try {
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) throw new IllegalStateException("文件不可读");
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) > 0) buf.write(b, 0, n);
            is.close();
            org.json.JSONObject o = new org.json.JSONObject(buf.toString("UTF-8"));
            if (!BACKUP_TYPE.equals(o.optString("type"))) {
                throw new IllegalArgumentException("不是本应用导出的设置备份文件");
            }
            if (o.has("baseUrl") && !o.optString("baseUrl").trim().isEmpty()) {
                st.put("baseUrl", o.optString("baseUrl"));
            }
            if (o.has("model") && !o.optString("model").trim().isEmpty()) {
                st.put("model", o.optString("model"));
            }
            if (o.has("draftLang")) st.put("draftLang", o.optString("draftLang"));
            if (o.has("incomingLang")) st.put("incomingLang", o.optString("incomingLang"));
            if (o.has("notifEnabled")) st.put("notifEnabled", o.optBoolean("notifEnabled", true));
            if (o.has("bubbleEnabled")) st.put("bubbleEnabled", o.optBoolean("bubbleEnabled", true));
            if (o.has("bubbleAutoHide")) st.put("bubbleAutoHide", o.optBoolean("bubbleAutoHide", true));
            if (o.has("skipChineseIncoming")) st.put("skipChineseIncoming", o.optBoolean("skipChineseIncoming", true));
            load(); // 立即回填界面，开关回调会同步服务实时生效
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_OK);
            tvResult.setText("✓ 设置已恢复（API Key 需单独填写）");
            Toast.makeText(this, "设置已恢复", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setTextColor(C_ERR);
            tvResult.setText("✗ 恢复失败: " + e.getMessage());
            Toast.makeText(this, "恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

        setPermState(tvPermN, notif, "收信翻译可用", "收信翻译将不可用");
        setPermState(tvPermA, a11y, "悬浮翻译可用", "悬浮翻译将不可用");
        setPermState(tvPermO, overlay, "悬浮球可显示", "悬浮球无法显示");
        if (Build.VERSION.SDK_INT >= 33)
            setPermState(tvPermP, postNotif, "译文通知可显示", "看不到译文通知");
    }

    /** 更新权限条目状态胶囊：已开启（绿）/ 未开启（红），已开启时隐藏「去开启」按钮。 */
    private void setPermState(TextView status, boolean ok, String okHint, String noHint) {
        if (status == null) return;
        Button b = (Button) status.getTag();
        if (ok) {
            status.setText("已开启 · " + okHint);
            status.setTextColor(C_OK);
            status.setBackground(bg(T_OK_BG, dp(R_PILL), 0));
            if (b != null) b.setVisibility(View.GONE);
        } else {
            status.setText("未开启 · " + noHint);
            status.setTextColor(C_ERR);
            status.setBackground(bg(T_ERR_BG, dp(R_PILL), 0));
            if (b != null) b.setVisibility(View.VISIBLE);
        }
    }
}
