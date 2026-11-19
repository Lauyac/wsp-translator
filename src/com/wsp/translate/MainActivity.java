package com.wsp.translate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Settings st;
    private Handler ui;
    private EditText etKey, etBase, etModel;
    private Spinner spDraft, spIncoming;
    private CheckBox cbNotif, cbBubble, cbSkipZh, cbShowKey;
    private TextView tvResult, tvPerm, tvUpdate;

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

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);

        root.addView(section("① 连接与凭据"));
        etKey = input("DeepSeek API Key（sk-…）", true);
        root.addView(etKey);
        cbShowKey = check("显示 API Key（输入完成后可勾选检查是否正确）");
        cbShowKey.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                etKey.setInputType(checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                etKey.setSelection(etKey.getText().length());
            }
        });
        root.addView(cbShowKey);
        etBase = input("API 地址（默认 https://api.deepseek.com，兼容 OpenAI 网关/私有代理）", false);
        etModel = input("模型（默认 deepseek-chat）", false);
        root.addView(etBase);
        root.addView(etModel);

        root.addView(section("② 翻译行为"));
        root.addView(label("回复目标语言（草稿中文 → 外语）"));
        spDraft = spinner();
        root.addView(spDraft);
        root.addView(label("收信译文语言（外语 → 目标语言，默认简体中文）"));
        spIncoming = spinner();
        root.addView(spIncoming);
        cbSkipZh = check("收信已是大段中文时跳过翻译");
        cbNotif = check("收信自动翻译（通知使用权）");
        cbBubble = check("悬浮球翻译（需无障碍 + 悬浮窗权限）");
        root.addView(cbSkipZh);
        root.addView(cbNotif);
        root.addView(cbBubble);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button save = new Button(this);
        save.setText("保存设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        Button test = new Button(this);
        test.setText("测试连接");
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { test(); }
        });
        btnRow.addView(save, half());
        btnRow.addView(test, half());
        root.addView(btnRow);

        tvResult = new TextView(this);
        tvResult.setTextSize(13);
        tvResult.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(tvResult);

        root.addView(section("③ 权限开启（首次安装必做）"));
        tvPerm = new TextView(this);
        tvPerm.setTextSize(13);
        root.addView(tvPerm);

        root.addView(permBtn("开启通知使用权（收信翻译）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        }));
        root.addView(permBtn("开启无障碍服务（悬浮翻译）", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        root.addView(permBtn("授予悬浮窗权限", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }));
        if (Build.VERSION.SDK_INT >= 33) {
            root.addView(permBtn("授予通知显示权限（Android 13+）", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
            }));
        }

        root.addView(section("④ 应用更新"));
        TextView tvVer = new TextView(this);
        tvVer.setTextSize(13);
        tvVer.setText("当前版本: " + versionName()
            + "（versionCode " + versionCode() + "）\n"
            + "更新包不入仓库；检查更新通过 GitHub Releases API 获取，"
            + "版本判断基于 versionCode，下载后强制 SHA-256 校验，校验失败不会安装。");
        root.addView(tvVer);
        tvUpdate = new TextView(this);
        tvUpdate.setTextSize(13);
        tvUpdate.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(tvUpdate);
        root.addView(permBtn("检查更新（GitHub Releases）", new View.OnClickListener() {
            @Override public void onClick(View v) { checkUpdate(); }
        }));

        root.addView(section("⑤ 使用说明"));
        TextView help = new TextView(this);
        help.setTextSize(13);
        help.setText("· 收信翻译：保持本应用在后台运行，聊天应用（如 WhatsApp）收到新消息后会自动发出一条「译 · 」开头的本地通知，展示原文与译文；翻译失败时也会发出失败通知并说明原因。\n"
            + "· 悬浮翻译：打开聊天应用（如 WhatsApp）的聊天界面 → 点悬浮球「译」→ 自动读取屏幕并翻译。\n"
            + "· 草稿翻译：在悬浮面板输入中文 → 点「翻译并填入」→ 译文写入聊天应用输入框，请核对后手动发送（本应用绝不自动发送消息）。\n"
            + "· 译文缓存仅存本机内存，退出应用即清空；聊天内容不会被保存或上传，仅作为翻译请求发送给你配置的 API 服务端。");
        root.addView(help);

        return sv;
    }

    private TextView section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(16);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(dp(4), dp(16), dp(4), dp(6));
        return v;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setPadding(dp(4), dp(8), dp(4), dp(2));
        return v;
    }

    private EditText input(String hint, boolean key) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        if (key) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        String[] labels = new String[Langs.ITEMS.length];
        for (int i = 0; i < Langs.ITEMS.length; i++) labels[i] = Langs.ITEMS[i][1];
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        return s;
    }

    private CheckBox check(String t) {
        CheckBox c = new CheckBox(this);
        c.setText(t);
        c.setTextSize(14);
        return c;
    }

    private Button permBtn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams half() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

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

    /** 检查更新：后台执行 Updater.check()，成功且通过校验则进入安装流程。 */
    private void checkUpdate() {
        tvUpdate.setText("正在检查更新…");
        final MainActivity act = this;
        Translator.submit(new Runnable() {
            @Override
            public void run() {
                final Updater.Result r = Updater.check(act);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvUpdate.setText(r.message);
                        if (r.apkFile != null) installUpdate(r.apkFile);
                    }
                });
            }
        });
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

        tvPerm.setText("通知使用权: " + (notif ? "✓ 已开启" : "✗ 未开启（收信翻译不可用）")
            + "\n无障碍服务: " + (a11y ? "✓ 已开启" : "✗ 未开启（悬浮翻译不可用）")
            + "\n悬浮窗权限: " + (overlay ? "✓ 已授予" : "✗ 未授予（悬浮球不可用）")
            + "\n通知显示权限: " + (postNotif ? "✓ 已授予" : "✗ 未授予（看不到译文通知）"));
    }
}
