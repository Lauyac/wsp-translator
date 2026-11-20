package com.wsp.translate;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 悬浮球 + 悬浮翻译面板（无障碍服务）。
 * - 读取 WhatsApp 当前屏幕文字（按屏幕位置排序、清洗界面噪声）→ 译为「收信译文语言」
 * - 中文草稿 → 译为「回复目标语言」→ 填入 WhatsApp 输入框（不自动发送）
 */
public class FloatService extends AccessibilityService {

    private static final String WA = "com.whatsapp";
    private static final String WA_BIZ = "com.whatsapp.w4b";

    // 面板配色
    private static final int C_SUB = 0xFF8899AA;
    private static final int C_SRC = 0xFFB0BEC5;
    private static final int C_OK = 0xFF25D366;
    private static final int C_ERR = 0xFFFF6B6B;
    private static final int C_WARN = 0xFFFFC107;

    /** 悬浮球直径（dp）。拖拽偏移、位置恢复、吸附边界均以此推导，避免硬编码不同步。 */
    private static final int BUBBLE_DP = 36;

    /** 胶囊圆角（dp）。 */
    private static final int R_PILL = 999;

    // WhatsApp 界面固定文案（精确匹配即丢弃，避免按钮/菜单文字混入译文）
    private static final Set<String> UI_CHROME = new HashSet<>(Arrays.asList(
        "输入消息", "键入消息", "输入一条消息", "消息", "WhatsApp", "WhatsApp Web",
        "更多选项", "返回", "发送", "相机", "附件", "麦克风", "语音消息", "表情", "贴纸",
        "语音通话", "视频通话", "通话", "搜索", "菜单", "已读", "已送达", "正在输入",
        "正在输入…", "在线", "上次在线", "新聊天", "置顶聊天", "查看联系人", "导出聊天",
        "清空聊天", "静音", "壁纸", "回复", "转发", "复制", "删除", "收藏", "举报",
        "端到端加密", "所有媒体", "今天", "昨天", "未读消息", "加密提示", "详情",
        "管理", "了解更多", "Learn more", "Manage",
        "Type a message", "Send", "Back", "More options", "Search", "Voice call",
        "Video call", "Camera", "Attach", "Microphone", "Sticker", "Emoji", "Reply",
        "Forward", "Copy", "Delete", "Report", "Online", "typing…", "Read", "Delivered",
        "Today", "Yesterday", "Unread messages"));

    private WindowManager wm;
    private Handler ui;
    private View bubble;
    private LinearLayout panel;
    private TextView tvLang, tvStatus, tvSource, tvResult;
    private Button btnRead, btnCopy, btnDraft, btnFill;
    private EditText etDraft;
    private String lastResult = "";
    private boolean lastResultOk = false;
    private boolean busy = false;
    private AccessibilityNodeInfo lastWaRoot;
    /** 当前服务实例，供主界面开关悬浮球时实时生效。 */
    private static volatile FloatService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        CrashHandler.install(this);
        ui = new Handler(Looper.getMainLooper());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        instance = this;
        // 智能显隐开启时按当前真实前台决定是否显示：服务重启/开机时若不在聊天应用内不显示，
        // 避免悬浮球先常驻、等第一次离开事件才被隐藏
        Settings st = new Settings(this);
        if (st.bubbleEnabled() && (!st.bubbleAutoHide() || activeWindowIsWa())) addBubble();
    }

    @Override
    public void onDestroy() {
        instance = null;
        removeBubble();
        if (panel != null && panel.getParent() != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override
    public void onInterrupt() { }

    private static boolean isWa(String pkg) {
        return WA.equals(pkg) || WA_BIZ.equals(pkg);
    }

    /** 是否为本应用自己的窗口事件/窗口（翻译面板持有焦点时，活动窗口是自身覆盖窗）。 */
    private boolean isSelf(String pkg) {
        return getPackageName().equals(pkg);
    }

    /** 面板版本胶囊展示的版本号；读包信息失败时返回空串（胶囊隐藏）。 */
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkgCs = e == null ? null : e.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        // 自己悬浮窗（翻译面板持有输入焦点时）产生的窗口事件不参与显隐判断：
        // 面板打开 → 焦点移到本应用覆盖窗 → 自身 WINDOW_STATE_CHANGED 若按普通应用处理，
        // 会安排隐藏并在复核时把「自己」当成非聊天前台，导致面板约 1 秒后被自动收起
        if (isSelf(pkg)) return;
        boolean wa = isWa(pkg);

        // 悬浮球智能显隐：WhatsApp 的任意事件立即显示并取消待执行的隐藏；
        // 其他真实应用的窗口切换只「安排」延迟隐藏，真正隐藏与否由 pendingHide
        // 执行时按当前活动窗口复核——事件时序（冷启动迟滞、转场乱序）不可靠，
        // 以 getRootInActiveWindow() 的真实前台为准。
        Settings st = new Settings(this);
        if (st.bubbleEnabled() && st.bubbleAutoHide()) {
            if (wa) {
                lastWaSeenMs = SystemClock.elapsedRealtime();
                ui.removeCallbacks(pendingHide);
                if (bubble == null || bubble.getParent() == null) addBubble();
            } else if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isRealApp(pkg)) {
                if (activeWindowIsWa()) {
                    // 转场期事件乱序：事件包名已变成别的应用，但前台仍是 WhatsApp，不安排隐藏
                    lastWaSeenMs = SystemClock.elapsedRealtime();
                } else {
                    ui.removeCallbacks(pendingHide);
                    ui.postDelayed(pendingHide, HIDE_DELAY_MS);
                }
            }
        }

        if (!wa) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        if (isWa(root.getPackageName().toString())) lastWaRoot = root;
    }

    /** 延迟隐藏：执行时按「当前活动窗口」的真实前台复核，而不是依赖事件到达时序。 */
    private final Runnable pendingHide = new Runnable() {
        @Override public void run() {
            Settings s = new Settings(FloatService.this);
            if (!s.bubbleEnabled() || !s.bubbleAutoHide()) return;
            String active = activeWindowPkg();
            if (active != null) {
                if (isWa(active)) {
                    // 前台仍是 WhatsApp：只是转场/事件乱序，取消隐藏并刷新活动时间
                    lastWaSeenMs = SystemClock.elapsedRealtime();
                    return;
                }
                if (isSelf(active)) {
                    // 焦点在本应用悬浮面板上：面板打开期间不做隐藏判定，
                    // 等用户离开（焦点移到真实前台）后的事件再触发隐藏
                    return;
                }
                // 前台确认是其他应用（桌面/真实 App）→ 执行隐藏
            } else if (SystemClock.elapsedRealtime() - lastWaSeenMs < HIDE_GUARD_MS) {
                // 拿不到活动窗口（瞬间转场）时退回时间防抖兜底，避免误隐藏
                return;
            }
            boolean attached = bubble != null && bubble.getParent() != null;
            if (attached) removeBubble();
        }
    };

    private static final long HIDE_DELAY_MS = 500;   // 非聊天应用窗口切换后的延迟隐藏
    private static final long HIDE_GUARD_MS = 1500;  // 拿不到活动窗口时的时间防抖兜底
    private long lastWaSeenMs = 0L;

    /** 当前活动窗口（真实前台）的包名；拿不到时返回 null。 */
    private String activeWindowPkg() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null) return null;
            return root.getPackageName().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean activeWindowIsWa() {
        String p = activeWindowPkg();
        return p != null && isWa(p);
    }

    /** 是否为可启动的真实应用（有桌面入口）；systemui、输入法等系统组件返回 false。 */
    private boolean isRealApp(String pkg) {
        try {
            return pkg != null && getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 悬浮球 ----------

    /** 主界面开关悬浮球时实时生效；服务未运行时仅写偏好，下次服务启动按设置加载。 */
    public static void applyBubbleEnabled(Context c, boolean enabled) {
        new Settings(c).put("bubbleEnabled", enabled);
        final FloatService s = instance;
        if (s == null || s.wm == null) return;
        s.ui.post(new Runnable() {
            @Override public void run() {
                if (enabled) {
                    // 智能显隐开启且前台不是聊天应用时不立即显示，交给事件流在进入聊天应用时显示
                    Settings st = new Settings(s);
                    if (st.bubbleAutoHide() && !s.activeWindowIsWa()) return;
                    s.addBubble();
                } else {
                    s.removeBubble();
                }
            }
        });
    }

    private void addBubble() {
        if (bubble != null && bubble.getParent() != null) return; // 已在显示，避免重复添加
        int size = dp(BUBBLE_DP);
        TextView tv = new TextView(this);
        tv.setText("译");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bubble_bg);

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // 恢复上次吸附的位置；无记录或屏幕变化时回落到默认位置
        Settings ps = new Settings(this);
        int px = ps.bubbleX(), py = ps.bubbleY();
        Point scr = realSize();
        if (px >= 0 && py >= 0 && px + size <= scr.x && py + size <= scr.y) {
            lp.x = px;
            lp.y = py;
        } else {
            lp.x = dp(6);
            lp.y = dp(280);
        }

        final int sz = size;
        final float[] down = new float[2];   // ACTION_DOWN 时的 raw 坐标（判断点击 vs 拖拽）
        final float[] grab = new float[2];   // 手指相对悬浮球左上角的偏移（保证拖拽跟手不跳动）
        final boolean[] moved = new boolean[1];
        tv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        down[0] = ev.getRawX();
                        down[1] = ev.getRawY();
                        grab[0] = ev.getRawX() - lp.x;
                        grab[1] = ev.getRawY() - lp.y;
                        moved[0] = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = ev.getRawX() - down[0];
                        float dy = ev.getRawY() - down[1];
                        if (!moved[0] && dx * dx + dy * dy < dp(10) * dp(10)) return true; // 消除静止误抖
                        moved[0] = true;
                        lp.x = Math.round(ev.getRawX() - grab[0]);
                        lp.y = Math.round(ev.getRawY() - grab[1]);
                        clampToScreen(lp, sz);
                        try { wm.updateViewLayout(v, lp); } catch (Exception ignored) { }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        float dx = ev.getRawX() - down[0];
                        float dy = ev.getRawY() - down[1];
                        if (!moved[0] && dx * dx + dy * dy < dp(10) * dp(10)) {
                            togglePanel();
                        } else {
                            snapBubble(v, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        // 系统/父级取消手势（如来电、权限弹窗）：若已拖动则吸附归位，避免悬停在半空
                        if (moved[0]) snapBubble(v, lp);
                        return true;
                    default:
                        return false;
                }
            }
        });

        try { wm.addView(tv, lp); bubble = tv; } catch (Exception ignored) { }
    }

    private void removeBubble() {
        if (bubble != null && bubble.getParent() != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) { }
        }
        bubble = null;
        hidePanel();
    }

    /** 把悬浮球窗口坐标钳制在屏幕（物理全屏）范围内，拖拽与吸附共用。 */
    private void clampToScreen(WindowManager.LayoutParams lp, int size) {
        Point scr = realSize();
        if (lp.x < 0) lp.x = 0;
        if (lp.y < 0) lp.y = 0;
        if (lp.x > scr.x - size) lp.x = Math.max(0, scr.x - size);
        if (lp.y > scr.y - size) lp.y = Math.max(0, scr.y - size);
    }

    /** 物理（含状态栏/导航栏）屏幕尺寸；悬浮窗可覆盖全屏，按可用区域钳制会到不了屏幕边缘。 */
    private Point realSize() {
        Point p = new Point();
        try {
            Display d = wm.getDefaultDisplay();
            if (d != null) d.getRealSize(p);
        } catch (Exception ignored) { }
        if (p.x <= 0 || p.y <= 0) {
            android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            p.set(m.widthPixels, m.heightPixels);
        }
        return p;
    }

    /** 拖动结束后把悬浮球吸附到最近的屏幕左/右边缘，纵向钳制在屏幕内；位置持久化。 */
    private void snapBubble(View v, WindowManager.LayoutParams lp) {
        int size = dp(BUBBLE_DP);
        int margin = dp(6);
        Point scr = realSize();
        int fromLeft = lp.x;
        int fromRight = scr.x - lp.x - size;
        lp.x = fromLeft <= fromRight ? margin : scr.x - size - margin;
        clampToScreen(lp, size);
        try { wm.updateViewLayout(v, lp); } catch (Exception ignored) { }
        // 记住吸附位置，服务重启/开机后悬浮球回到用户放的位置
        Settings ps = new Settings(this);
        ps.put("bubbleX", lp.x);
        ps.put("bubbleY", lp.y);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 旋转/分屏后屏幕尺寸变化：立即重新吸附，防止悬浮球留在屏幕外
        if (bubble != null && bubble.getParent() != null && wm != null) {
            try {
                WindowManager.LayoutParams lp =
                    (WindowManager.LayoutParams) bubble.getLayoutParams();
                snapBubble(bubble, lp);
            } catch (Exception ignored) { }
        }
    }

    private void togglePanel() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (panel == null) buildPanel();
                if (panel.getParent() != null) {
                    wm.removeView(panel);
                } else {
                    try {
                        wm.addView(panel, panelLp());
                        refreshLangInfo();
                        autoRead();
                    } catch (Exception e) {
                        toast("无法显示悬浮窗: 请先到应用设置授予「悬浮窗权限」");
                    }
                }
            }
        });
    }

    /** 关闭悬浮面板（若已显示）。悬浮球被关闭时同步收起，避免面板失去入口后悬空。 */
    private void hidePanel() {
        if (panel != null && panel.getParent() != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
        }
    }

    private WindowManager.LayoutParams panelLp() {
        int w = Math.min(dp(340),
            (int) (getResources().getDisplayMetrics().widthPixels * 0.92f));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            w, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    /** 面板顶部显示当前翻译方向，让用户确认目标语言是否符合预期。 */
    private void refreshLangInfo() {
        if (tvLang == null) return;
        Settings st = new Settings(this);
        tvLang.setText("收信 → " + Langs.label(st.incomingLang())
            + "　回复 → " + Langs.label(st.draftLang()));
    }

    // ---------- 悬浮面板 ----------

    private void buildPanel() {
        // 深色渐变底 + 微描边，增强悬浮卡片的层次与质感
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF20303F, 0xFF141F2B});
        pbg.setCornerRadius(dp(20));
        pbg.setStroke(dp(1), 0x26FFFFFF);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(pbg);
        panel.setElevation(dp(10));
        panel.setPadding(dp(14), dp(12), dp(14), dp(14));

        // 标题栏：图标位 + 应用名 + 关闭按钮
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new TextView(this);
        badge.setText("译");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(14);
        badge.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF60A5FA, 0xFF2563EB});
        bbg.setCornerRadius(dp(10));
        badge.setBackground(bbg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(30), dp(30));
        blp.rightMargin = dp(10);
        header.addView(badge, blp);
        TextView title = new TextView(this);
        title.setText("翻译助手");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        // 版本号胶囊：与语言方向胶囊同款式，动态读取应用真实版本
        TextView ver = new TextView(this);
        ver.setText("v" + versionName());
        ver.setTextColor(C_SUB);
        ver.setTextSize(10);
        ver.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        ver.setBackground(Ui.fill(this, 0x14FFFFFF, dp(R_PILL), 0));
        ver.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.rightMargin = dp(6);
        header.addView(ver, vlp);
        if (ver.getText().length() <= 1) ver.setVisibility(View.GONE); // 读不到版本号时不显示空胶囊
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(C_SRC);
        close.setTextSize(13);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.fill(this, 0x1AFFFFFF, dp(R_PILL), 0));
        close.setPadding(dp(10), dp(6), dp(10), dp(6));
        close.setClickable(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePanel(); }
        });
        header.addView(close);
        panel.addView(header);

        // 语言方向：胶囊样式，让用户确认目标语言是否符合预期
        tvLang = new TextView(this);
        tvLang.setTextColor(C_SRC);
        tvLang.setTextSize(11);
        tvLang.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        tvLang.setBackground(Ui.fill(this, 0x14FFFFFF, dp(R_PILL), 0));
        tvLang.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        langLp.topMargin = dp(10);
        panel.addView(tvLang, langLp);

        // 处理状态行（灰=进行中/未开始，绿=成功，红=失败，黄=警告）
        tvStatus = new TextView(this);
        tvStatus.setTextColor(C_SUB);
        tvStatus.setTextSize(12);
        tvStatus.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        panel.addView(tvStatus, colLp(dp(4)));

        // 可滚动内容区（整体限高，适配小屏/横屏）
        ScrollView outer = new ScrollView(this) {
            @Override
            protected void onMeasure(int wspec, int hspec) {
                int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.78f);
                super.onMeasure(wspec,
                    View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
            }
        };
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        outer.addView(content);
        panel.addView(outer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- 区域一：收信翻译（屏幕识别 → 译文）----
        content.addView(sectionLabel("收信翻译", "自动读取当前聊天文字并翻译", C_OK), colLp(dp(6)));
        content.addView(smallLabel("原文（屏幕识别）", C_SRC), colLp(dp(4)));
        tvSource = makeTextCard(12, C_SRC, false);
        ScrollView svSrc = new ScrollView(this);
        svSrc.addView(tvSource);
        LinearLayout.LayoutParams srcL = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        content.addView(svSrc, srcL);

        content.addView(smallLabel("译文", C_OK), colLp(dp(6)));
        tvResult = makeTextCard(14, Color.WHITE, true);
        ScrollView svOut = new ScrollView(this);
        svOut.addView(tvResult);
        LinearLayout.LayoutParams outL = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        content.addView(svOut, outL);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        btnRead = panelBtn("翻译当前屏幕", true);
        btnRead.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { readAndTranslate(); }
        });
        btnCopy = panelBtn("复制译文", false);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copyResult(); }
        });
        row1.addView(btnRead, rowLp());
        row1.addView(btnCopy, rowLp());
        content.addView(row1, colLp(dp(8)));

        // ---- 区域二：回复草稿（中文 → 对方语言 → 填入输入框）----
        content.addView(sectionLabel("回复草稿",
            "中文译成对方语言后写入聊天输入框，不会自动发送，请核对后手动发送", 0xFF5B9BF6), colLp(dp(10)));
        etDraft = new EditText(this);
        etDraft.setHint("输入中文草稿…");
        etDraft.setTextColor(Color.WHITE);
        etDraft.setHintTextColor(C_SUB);
        etDraft.setTextSize(14);
        etDraft.setMinLines(2);
        etDraft.setGravity(Gravity.TOP);
        // 默认浅底描边 → 聚焦高亮描边，输入位置一目了然
        GradientDrawable dDef = new GradientDrawable();
        dDef.setColor(0x14FFFFFF);
        dDef.setCornerRadius(dp(10));
        dDef.setStroke(dp(1), 0x3DFFFFFF);
        GradientDrawable dFoc = new GradientDrawable();
        dFoc.setColor(0x1FFFFFFF);
        dFoc.setCornerRadius(dp(10));
        dFoc.setStroke(dp(1), 0xFF5B9BF6);
        StateListDrawable dsl = new StateListDrawable();
        dsl.addState(new int[]{android.R.attr.state_focused}, dFoc);
        dsl.addState(new int[]{}, dDef);
        etDraft.setBackground(dsl);
        etDraft.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        content.addView(etDraft, colLp(dp(4)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        btnDraft = panelBtn("翻译并填入", true);
        btnDraft.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { translateDraft(); }
        });
        btnFill = panelBtn("填入上方译文", false);
        btnFill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fillFromResult(); }
        });
        row2.addView(btnDraft, rowLp());
        row2.addView(btnFill, rowLp());
        content.addView(row2, colLp(dp(6)));
    }

    /** 统一样式的文本卡：圆角浅色底、同字体族、固定字号、统一行距与左对齐。 */
    private TextView makeTextCard(float sizeSp, int color, boolean selectable) {
        TextView v = new TextView(this);
        v.setTextColor(color);
        v.setTextSize(sizeSp);
        v.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        v.setGravity(Gravity.START);
        v.setLineSpacing(dp(2), 1.1f);
        v.setIncludeFontPadding(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x16FFFFFF);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), 0x1FFFFFFF);
        v.setBackground(bg);
        v.setPadding(dp(10), dp(8), dp(10), dp(8));
        if (selectable) v.setTextIsSelectable(true);
        return v;
    }

    /**
     * 展示排版规则：
     * 1) 清除不可见字符；2) 行内压缩空白；3) 空行识别为段落分隔（渲染为空一行）；
     * 4) 去掉首尾空行，让长文本、多段译文层级清晰、间距一致。
     */
    private String formatForDisplay(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = cleanText(raw);
        if (s.isEmpty()) return "";
        String[] lines = s.split("\n", -1);
        StringBuilder b = new StringBuilder();
        boolean paraBreak = false;
        for (String line0 : lines) {
            String l = line0.replace('\t', ' ').trim().replaceAll(" {2,}", " ");
            if (l.isEmpty()) {
                if (b.length() > 0) paraBreak = true;
                continue;
            }
            if (b.length() > 0) b.append(paraBreak ? "\n\n" : "\n");
            b.append(l);
            paraBreak = false;
        }
        return b.toString();
    }

    private TextView smallLabel(String t, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(11);
        v.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams colLp(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    /** 区域标题：彩色圆点 + 标题 + 一行说明，用于划分面板的两个功能区。 */
    private View sectionLabel(String title, String desc, int dotColor) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setColor(dotColor);
        dg.setCornerRadius(dp(3));
        row.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        t.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        t.setPadding(dp(7), 0, 0, 0);
        row.addView(t);
        v.addView(row);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(C_SUB);
        d.setTextSize(10);
        d.setPadding(dp(13), dp(2), 0, 0);
        v.addView(d);
        return v;
    }

    /** 面板按钮：主按钮（蓝底白字）/ 次按钮（描边浅底），统一按下与禁用态。 */
    private Button panelBtn(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setPadding(0, dp(9), 0, dp(9));

        GradientDrawable def = new GradientDrawable();
        def.setCornerRadius(dp(12));
        GradientDrawable pr = new GradientDrawable();
        pr.setCornerRadius(dp(12));
        GradientDrawable dis = new GradientDrawable();
        dis.setCornerRadius(dp(12));
        int textColor, textDisabled;
        if (primary) {
            GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3B82F6, 0xFF1D4ED8});
            grad.setCornerRadius(dp(12));
            GradientDrawable gradPr = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2563EB, 0xFF1E40AF});
            gradPr.setCornerRadius(dp(12));
            def = grad;
            pr = gradPr;
            dis.setColor(0xFF2A3947);
            dis.setCornerRadius(dp(12));
            textColor = Color.WHITE;
            textDisabled = 0x55FFFFFF;
        } else {
            def.setColor(0x1AFFFFFF);
            def.setStroke(dp(1), 0x3DFFFFFF);
            pr.setColor(0x2EFFFFFF);
            pr.setStroke(dp(1), 0x3DFFFFFF);
            dis.setColor(0x14FFFFFF);
            dis.setStroke(dp(1), 0x22FFFFFF);
            textColor = 0xFFE6EDF3;
            textDisabled = 0x44FFFFFF;
        }
        b.setTextColor(new android.content.res.ColorStateList(
            new int[][]{{-android.R.attr.state_enabled}, {}},
            new int[]{textDisabled, textColor}));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, dis);
        s.addState(new int[]{android.R.attr.state_pressed}, pr);
        s.addState(new int[]{}, def);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(12));
        mask.setColor(0xFFFFFFFF);
        b.setBackground(new RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFFFFF), s, mask));
        return b;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(4);
        return lp;
    }

    /** 翻译期间禁用操作按钮，防止重复提交，并给出一致的忙碌反馈。 */
    private void setBusy(boolean b) {
        busy = b;
        View[] btns = {btnRead, btnCopy, btnDraft, btnFill};
        for (View v : btns) if (v != null) v.setEnabled(!b);
    }

    private void setStatus(String s, int color) {
        if (tvStatus != null) {
            tvStatus.setText(s);
            tvStatus.setTextColor(color);
        }
    }

    /** 状态行尾部提示：本次跳过了多少条语音/图片等非文本内容。 */
    private String skipSuffix(int skipped) {
        return skipped > 0 ? " · 跳过 " + skipped + " 项非文本" : "";
    }

    // ---------- 屏幕读取：清洗、分段、排序 ----------

    private static class TextItem {
        final String text;
        final int top;
        final int left;
        TextItem(String text, int top, int left) {
            this.text = text; this.top = top; this.left = left;
        }
    }

    /** 采集结果：可翻译文本 + 被跳过的非文本条目数（语音/图片/表情等）。 */
    private static class ScreenScan {
        final String text;
        final int skipped;
        ScreenScan(String text, int skipped) {
            this.text = text; this.skipped = skipped;
        }
    }

    /** 语音、图片、贴纸等媒体条目的判定逻辑已共享至 TextFilter（读屏与通知两条链路口径一致）。 */

    /**
     * 读取屏幕文字并清洗：
     * 1) 去控制符/零宽字符/方向符，避免乱码；
     * 2) 按节点屏幕坐标排序（先上下后左右），保证消息顺序与视觉一致；
     * 3) 过滤界面噪声（按钮文案、时间戳、未读徽标、纯表情、已读标记等）；
     * 4) 跳过语音/图片/贴纸等无文字媒体条目及按钮无障碍说明，不送入翻译；
     * 5) 全局去重 + 相邻去重，压缩重复行。
     * 6) 预算截断时保留屏幕底部（最新）消息、丢弃最旧内容；
     *    整体防异常：节点失效/未密封时返回空结果，不崩溃。
     */
    private ScreenScan scanScreen() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null
                || !isWa(root.getPackageName().toString())) {
                root = lastWaRoot; // 兜底：最近一次捕获的 WhatsApp 窗口（可能陈旧，整体 try/catch 保护）
            }
            if (root == null) return new ScreenScan("", 0);

            List<TextItem> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int nodes = 0;
            int skipped = 0;
            Rect r = new Rect();
            while (!queue.isEmpty() && nodes < 600) {
                AccessibilityNodeInfo n = queue.poll();
                if (n == null) continue;
                nodes++;
                CharSequence t = n.getText();
                CharSequence d = n.getContentDescription();
                boolean fromText = t != null && t.length() > 0;
                CharSequence cs = fromText ? t : d;
                if (n.getChildCount() > 0) {
                    for (int i = 0; i < n.getChildCount(); i++) {
                        AccessibilityNodeInfo c = n.getChild(i);
                        if (c != null) queue.add(c);
                    }
                }
                String s = cleanText(cs);
                if (s.isEmpty()) continue;

                if (!fromText) {
                    // 按钮/输入框/图片的无障碍说明不是聊天内容；过长的说明文字同样丢弃
                    if (n.isClickable() || n.isEditable() || s.length() > 30) { skipped++; continue; }
                    // 语音、图片、贴纸等媒体条目：无文字可译，跳过且不混入原文
                    if (isMediaEntry(s)) { skipped++; continue; }
                } else if (n.isEditable()) {
                    // 输入框里的草稿走「草稿翻译」通道，不并入屏幕原文
                    skipped++;
                    continue;
                } else if (isMediaOnly(s)) {
                    // 文本节点形式的媒体条目（如"语音消息 0:07"）：无文字可译，跳过
                    skipped++;
                    continue;
                }
                if (isA11yHint(s)) { skipped++; continue; }
                // WhatsApp 系统提示条（如"此聊天源自 Meta 广告…"）：非聊天内容，不进入翻译区
                if (isSystemNotice(s)) continue;
                if (isNoise(s) || seen.contains(s)) continue;

                try {
                    n.getBoundsInScreen(r); // 窗口切换瞬间节点可能未密封
                } catch (Exception ge) {
                    continue;
                }
                if (r.width() <= 0 || r.height() <= 0) continue;
                seen.add(s);
                items.add(new TextItem(s, r.top, r.left));
            }

            // 按屏幕位置排序：自上而下、自左而右（与用户看到的顺序一致）
            Collections.sort(items, new Comparator<TextItem>() {
                @Override public int compare(TextItem a, TextItem b) {
                    if (a.top != b.top) return a.top - b.top;
                    return a.left - b.left;
                }
            });

            // 条目数上限同样从末尾起算：超过 80 条时保留最新的 80 条
            if (items.size() > 80) {
                items = items.subList(items.size() - 80, items.size());
            }

            // 字符预算截断从末尾（屏幕底部=最新消息）向前装：超限丢弃的是最旧内容
            java.util.LinkedList<String> kept = new java.util.LinkedList<>();
            int budget = 3500;
            for (int i = items.size() - 1; i >= 0; i--) {
                TextItem it = items.get(i);
                String s = it.text;
                if (!kept.isEmpty() && kept.getFirst().equals(s)) continue; // 相邻去重
                if (budget < s.length()) break; // 更旧的内容放不下，直接停止
                kept.addFirst(s);
                budget -= s.length() + 1; // +1 换行
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < kept.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(kept.get(i));
            }
            return new ScreenScan(sb.toString(), skipped);
        } catch (Exception e) {
            // 陈旧节点（窗口切换/未密封）等异常：返回空结果，由调用方给出引导提示
            return new ScreenScan("", 0);
        }
    }

    /** 语音消息、图片、贴纸等媒体条目判定（无文字内容，无需翻译）。 */
    private boolean isMediaEntry(String s) {
        return TextFilter.isMediaEntry(s);
    }

    /** 文本节点形式的媒体条目：去掉时长/标点后仅剩媒体词（如"语音消息 0:07"），才判定为媒体。 */
    private boolean isMediaOnly(String s) {
        return TextFilter.isMediaOnly(s);
    }

    /** 按钮的无障碍操作说明（"双击并按住可录制…松开以发送"之类），不是聊天内容。 */
    private boolean isA11yHint(String s) {
        if (s.length() < 8 || !s.contains("按钮")) return false;
        return s.contains("双击") || s.contains("滑动") || s.contains("长按")
            || s.contains("松开") || s.contains("点按");
    }

    /**
     * WhatsApp 系统提示条判定（广告来源声明、功能开启提示等）。
     * 这类提示是系统插入的说明文案，不属于聊天内容，不应出现在翻译区。
     */
    private boolean isSystemNotice(String s) {
        if (s.length() < 6) return false;
        // 广告来源声明："此聊天源自 Meta 广告。顾客相关活动数据分享功能已开启。管理"
        if (s.startsWith("此聊天源自") || s.startsWith("此聊天来自")
            || s.startsWith("This chat started from") || s.startsWith("This chat came from")) {
            return true;
        }
        if (s.contains("顾客相关活动数据分享功能") || s.contains("customer activity data")) {
            return true;
        }
        // 兜底：提到 Meta 广告且带结尾管理入口/已开启提示，仍视为系统提示
        return (s.contains("Meta 广告") || s.contains("Meta ad"))
            && (s.endsWith("管理") || s.contains("已开启") || s.contains("分享功能"));
    }


    /** 去掉控制符、零宽字符、双向方向符等不可见字符（乱码的主要来源之一）。 */
    private String cleanText(CharSequence cs) {
        if (cs == null) return "";
        StringBuilder b = new StringBuilder(cs.length());
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if ((Character.isISOControl(c) && c != '\n') || c == '\u200B' || c == '\u200C'
                || c == '\u200D' || c == '\u200E' || c == '\u200F' || c == '\u202A'
                || c == '\u202B' || c == '\u202C' || c == '\uFEFF' || c == '\uFE0F') {
                continue;
            }
            b.append(c);
        }
        return b.toString().trim();
    }

    private boolean isNoise(String s) {
        if (s == null || s.length() < 2) return true;
        if (UI_CHROME.contains(s)) return true;
        // 纯时间戳（12:34 / 12:34 PM）
        if (s.matches("\\d{1,2}:\\d{2}(\\s?(AM|PM|am|pm))?")) return true;
        // 未读徽标等短数字（1~3 位）
        if (s.matches("\\d{1,3}")) return true;
        // 纯符号/表情（无任何字母、数字、汉字，且较短）
        if (s.length() <= 8 && !hasLetterOrDigit(s)) return true;
        return false;
    }

    private boolean hasLetterOrDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) return true;
        }
        return false;
    }

    private void autoRead() {
        readAndTranslate();
    }

    private void readAndTranslate() {
        if (busy) { toast("正在翻译中，请稍候…"); return; }
        if (lastWaRoot == null && getRootInActiveWindow() == null) {
            setStatus("无法读取屏幕: 无障碍服务未连接", C_ERR);
            tvResult.setText("请回到应用设置，重新开启本应用的无障碍服务后再试。");
            tvSource.setText("");
            return;
        }
        final ScreenScan scan = scanScreen();
        final String txt = scan.text;
        if (txt.isEmpty()) {
            setStatus(scan.skipped > 0
                ? "未读取到文字消息（已跳过 " + scan.skipped + " 项语音/图片等非文本内容）"
                : "未读取到聊天文字", C_ERR);
            tvResult.setText(scan.skipped > 0
                ? "当前屏幕只有语音/图片/表情等非文本内容，无需翻译。\n可上滑查看含文字的消息后再试。"
                : "请先打开 WhatsApp 的聊天界面，再点「翻译当前屏幕」重试。\n提示: 上下滑动聊天窗口后重试，可读取更多消息。");
            tvSource.setText("");
            return;
        }
        final Settings st = new Settings(this);
        if (st.apiKey().trim().isEmpty()) {
            setStatus("缺少配置", C_ERR);
            tvResult.setText("尚未配置 API Key: 请回到应用设置填写，保存后再试。");
            tvSource.setText(formatForDisplay(txt));
            return;
        }
        setBusy(true);
        setStatus("正在翻译屏幕内容（→ " + Langs.label(st.incomingLang()) + "）…", C_SUB);
        tvSource.setText(formatForDisplay(txt));
        tvResult.setText("");
        Translator.submit(new Runnable() {
            @Override
            public void run() {
                final Translator.Result r = Translator.translate(st, txt, st.incomingLang());
                HistoryStore.record(FloatService.this, HistoryStore.TYPE_SCREEN,
                    "当前屏幕", txt, r.text, r.ok);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false);
                        if (r.ok) {
                            final String out = cleanText(r.text);
                            lastResult = out;
                            lastResultOk = true;
                            tvResult.setText(formatForDisplay(out));
                            setStatus("翻译完成（" + (r.cached ? "缓存" : "网络")
                                + "，→ " + Langs.label(st.incomingLang()) + "）"
                                + skipSuffix(scan.skipped), C_OK);
                        } else {
                            lastResultOk = false;
                            tvResult.setText(r.text + "\n\n可点「翻译当前屏幕」重试。");
                            setStatus("翻译失败", C_ERR);
                            toast(r.text);
                        }
                    }
                });
            }
        });
    }

    private void copyResult() {
        if (!lastResultOk || lastResult.isEmpty()) {
            toast("暂无可复制的译文"); return;
        }
        copyText(lastResult);
    }

    /** 直接复制指定文本（不依赖结果状态，供残留回退等场景使用）。 */
    private void copyText(String text) {
        if (text == null || text.isEmpty()) { toast("暂无可复制的内容"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("translation", text));
        toast("已复制到剪贴板");
    }

    // ---------- 草稿翻译与填入 ----------

    private void translateDraft() {
        if (busy) { toast("正在翻译中，请稍候…"); return; }
        final String draft = etDraft == null ? "" : etDraft.getText().toString().trim();
        if (draft.isEmpty()) { toast("请先输入中文草稿"); return; }
        final Settings st = new Settings(this);
        if (st.apiKey().trim().isEmpty()) { toast("尚未配置 API Key，请回应用设置填写"); return; }
        setBusy(true);
        setStatus("正在翻译草稿（→ " + Langs.label(st.draftLang()) + "）…", C_SUB);
        tvSource.setText(draft);
        tvResult.setText("");
        Translator.submit(new Runnable() {
            @Override
            public void run() {
                final Translator.Result r = Translator.translate(st, draft, st.draftLang());
                HistoryStore.record(FloatService.this, HistoryStore.TYPE_DRAFT,
                    "", draft, r.text, r.ok);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false);
                        if (!r.ok) {
                            lastResultOk = false;
                            tvResult.setText(r.text + "\n\n可修正草稿后重新点「翻译并填入」。");
                            setStatus("翻译失败", C_ERR);
                            toast(r.text);
                            return;
                        }
                        final String out = cleanText(r.text);
                        lastResult = out;
                        // 中文残留检查（checks.js 移植）：失败则只复制不填入
                        if (Langs.residueBlock(draft, out)) {
                            copyText(out);
                            tvResult.setText(formatForDisplay(out));
                            setStatus("译文疑似未翻译（中文残留），已复制", C_WARN);
                            toast("译文疑似未翻译（中文残留），已复制到剪贴板，请人工核对");
                            return;
                        }
                        lastResultOk = true;
                        tvResult.setText(formatForDisplay(out));
                        boolean filled = fillEditable(out);
                        if (filled) {
                            setStatus("已填入输入框，请核对后手动发送", C_OK);
                            toast("已填入聊天应用输入框，请核对后手动发送");
                        } else {
                            copyText(out);
                            setStatus("未找到输入框: 译文已复制", C_WARN);
                            toast("未找到输入框: 请确认当前处于聊天界面；译文已复制到剪贴板");
                        }
                    }
                });
            }
        });
    }

    private void fillFromResult() {
        if (!lastResultOk || lastResult.isEmpty()) {
            toast("暂无可填入的译文"); return;
        }
        if (fillEditable(lastResult)) {
            setStatus("已填入输入框，请核对后手动发送", C_OK);
            toast("已填入输入框，请核对后手动发送");
        } else {
            copyResult();
            setStatus("未找到输入框: 译文已复制", C_WARN);
            toast("未找到聊天应用输入框，译文已复制");
        }
    }

    private boolean fillEditable(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
            || !isWa(root.getPackageName().toString())) {
            root = lastWaRoot; // 兜底: 使用最近一次捕获的 WhatsApp 窗口
        }
        if (root == null || root.getPackageName() == null) return false;
        if (!isWa(root.getPackageName().toString())) return false;

        AccessibilityNodeInfo target = null;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f != null && f.isEditable()) target = f;
        if (target == null) target = findFirstEditable(root, 0);
        if (target == null) return false;

        try {
            // 保留输入框已有内容：非空且与译文不同时拼接填入，不覆盖用户已打的字。
            // 注意：WhatsApp 等应用输入框为空时，无障碍 getText() 会返回占位提示
            //（中文界面为「消息」），必须识别为空框，否则每次填入都会多出这两个字。
            String finalText = text;
            try {
                CharSequence cur = target.getText();
                if (cur != null && cur.length() > 0) {
                    String c = cur.toString().trim();
                    boolean placeholder = isInputPlaceholder(target, c);
                    if (!c.isEmpty() && !placeholder && !c.equals(text.trim())) {
                        finalText = c + "\n" + text;
                    }
                }
            } catch (Exception ignored) { }
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText);
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Exception e) {
            return false;
        }
    }

    /** WhatsApp 输入框的常见占位提示；部分版本空框的无障碍文本即提示词。 */
    private static final String[] INPUT_PLACEHOLDERS = {
        "消息", "键入消息", "输入消息", "输入一条消息",
        "Message", "Type a message", "Type messages"
    };

    /** 当前文本是否为该输入框的占位提示（与 hint 相等，或命中常见占位词）。 */
    private static boolean isInputPlaceholder(AccessibilityNodeInfo target, String cur) {
        try {
            CharSequence hint = target.getHintText();
            if (hint != null && cur.equals(hint.toString().trim())) return true;
        } catch (Exception ignored) { }
        for (String p : INPUT_PLACEHOLDERS) {
            if (cur.equals(p)) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 25) return null;
        if (node.isEditable() && node.isEnabled()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            AccessibilityNodeInfo r = findFirstEditable(c, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
