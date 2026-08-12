package com.operit.tg2qq.config;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.operit.tg2qq.network.NapcatClient;
import com.operit.tg2qq.network.OneBotSegments;

/* JADX INFO: loaded from: classes2.dex */
public class SettingsActivity extends Activity {
    private static final int C_CARD = -1;
    private static final String PREFS = "tg_forward";
    private EditText etChatId;
    private EditText etDlTimeout;
    private EditText etGroupId;
    private EditText etInterval;
    private EditText etKeywords;
    private EditText etMaxAge;
    private EditText etNapcat;
    private EditText etPrefix;
    private EditText etRoutes;
    private EditText etSuffix;
    private SharedPreferences prefs;
    private Switch swEnabled;
    private Switch swLog;
    private Switch swMedia;
    private Switch swStrip;
    private static final int C_PRIMARY = Color.parseColor("#1A73E8");
    private static final int C_PRIMARY_DARK = Color.parseColor("#1557B0");
    private static final int C_BG = Color.parseColor("#F2F3F7");
    private static final int C_INPUT_BG = Color.parseColor("#F8FAFC");
    private static final int C_TEXT = Color.parseColor("#1F2937");
    private static final int C_LABEL = Color.parseColor("#64748B");
    private static final int C_BORDER = Color.parseColor("#E5E7EB");

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Config.init(this);
        this.prefs = getSharedPreferences(PREFS, 0);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(C_BG);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        scrollView.addView(linearLayout, new FrameLayout.LayoutParams(C_CARD, -2));
        LinearLayout header = card();
        header.setPadding(dp(20), dp(18), dp(20), dp(18));
        TextView tvTitle = new TextView(this);
        tvTitle.setText("TG2QQ 转发模块");
        tvTitle.setTextSize(22.0f);
        tvTitle.setTextColor(C_PRIMARY);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(tvTitle, matchWrap());
        TextView tvSub = new TextView(this);
        tvSub.setText("Telegram 频道 → QQ 群  ·  批量合并 · 多群路由 · #标签过滤");
        tvSub.setTextSize(12.0f);
        tvSub.setTextColor(C_LABEL);
        tvSub.setPadding(0, dp(4), 0, 0);
        header.addView(tvSub, matchWrap());
        linearLayout.addView(header);
        LinearLayout card1 = card();
        sectionTitle(card1, "转发目标（单群模式）");
        this.etChatId = addEdit(card1, "目标频道 ID（-100 开头，多个用英文逗号分隔）", "-1001234567890,-1000987654321", null);
        this.etGroupId = addEdit(card1, "QQ 群号", "123456789", "number");
        linearLayout.addView(card1);
        LinearLayout card2 = card();
        sectionTitle(card2, "转发规则（多群，可选）");
        TextView tvHint = new TextView(this);
        tvHint.setText("每行一条：频道ID1,频道ID2=QQ群号\n规则命中优先于上方单群模式；留空则全部转发到上方群号。");
        tvHint.setTextSize(12.0f);
        tvHint.setTextColor(C_LABEL);
        tvHint.setPadding(0, dp(2), 0, dp(4));
        card2.addView(tvHint, matchWrap());
        this.etRoutes = addEditMulti(card2, "", "-1001234567890,-1009876543210=123456789\n-100111222333=987654321");
        linearLayout.addView(card2);
        LinearLayout card3 = card();
        sectionTitle(card3, "服务与发送节奏");
        this.etNapcat = addEdit(card3, "OneBot HTTP 地址（不含末尾斜杠）", "http://127.0.0.1:3001", null);
        this.etInterval = addEdit(card3, "消息间隔毫秒（防风控，建议 ≥2000）", "2000", "number");
        this.etDlTimeout = addEdit(card3, "媒体下载超时秒数", "120", "number");
        this.etMaxAge = addEdit(card3, "旧消息容忍秒数（断网补发；0=不过滤，默认86400=24小时）", "86400", "number");
        linearLayout.addView(card3);
        LinearLayout card4 = card();
        sectionTitle(card4, "文本修饰与过滤");
        this.etPrefix = addEdit(card4, "转发前缀（可空）", "", null);
        this.etSuffix = addEdit(card4, "转发后缀（可空）", "", null);
        this.etKeywords = addEdit(card4, "黑名单（用【】包裹每个词条，如【广告】【推广】；消息含任一词条整条不转发）", "【广告】【推广】", null);
        linearLayout.addView(card4);
        LinearLayout card5 = card();
        sectionTitle(card5, "开关");
        this.swEnabled = addSwitch(card5, "总开关（关闭后停止监听转发）");
        this.swMedia = addSwitch(card5, "转发媒体消息的文字说明（媒体文件不下载，关闭则整条跳过）");
        this.swLog = addSwitch(card5, "日志开关（写入 LSPosed/Vector 日志）");
        this.swStrip = addSwitch(card5, "过滤 # 话题标签（#xxx 不参与转发）");
        linearLayout.addView(card5);
        loadPrefs();
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(0);
        btnRow.setPadding(0, dp(4), 0, dp(8));
        Button btnSave = button("保存配置", true);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$onCreate$0(view);
            }
        });
        btnRow.addView(btnSave, new LinearLayout.LayoutParams(0, dp(48), 1.0f));
        Button btnTest = button("发送测试消息", false);
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$onCreate$1(view);
            }
        });
        LinearLayout.LayoutParams lpTest = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        lpTest.leftMargin = dp(12);
        btnRow.addView(btnTest, lpTest);
        linearLayout.addView(btnRow);
        setContentView(scrollView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$0(View v) {
        save();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$1(View v) {
        testNapcat();
    }

    private void loadPrefs() {
        this.etChatId.setText(this.prefs.getString("chat_id", ""));
        this.etGroupId.setText(this.prefs.getString("group_id", ""));
        this.etNapcat.setText(this.prefs.getString("napcat_url", "http://127.0.0.1:3001"));
        this.etInterval.setText(String.valueOf(this.prefs.getInt("interval_ms", 2000)));
        this.etDlTimeout.setText(String.valueOf(this.prefs.getInt("dl_timeout_sec", 120)));
        this.etMaxAge.setText(String.valueOf(this.prefs.getInt("max_age_sec", 86400)));
        this.etPrefix.setText(this.prefs.getString("prefix", ""));
        this.etSuffix.setText(this.prefs.getString("suffix", ""));
        this.etKeywords.setText(this.prefs.getString("keywords", ""));
        this.etRoutes.setText(this.prefs.getString("routes", ""));
        this.swEnabled.setChecked(this.prefs.getBoolean("enabled", true));
        this.swMedia.setChecked(this.prefs.getBoolean("forward_media", true));
        this.swLog.setChecked(this.prefs.getBoolean("log_enabled", true));
        this.swStrip.setChecked(this.prefs.getBoolean("strip_hashtags", true));
    }

    private void save() {
        this.prefs.edit().putString("chat_id", this.etChatId.getText().toString().trim()).putString("group_id", this.etGroupId.getText().toString().trim()).putString("napcat_url", this.etNapcat.getText().toString().trim().replaceAll("/+$", "")).putInt("interval_ms", parseInt(this.etInterval.getText().toString(), 2000)).putInt("dl_timeout_sec", parseInt(this.etDlTimeout.getText().toString(), 120)).putInt("max_age_sec", parseInt(this.etMaxAge.getText().toString(), 86400)).putString("prefix", this.etPrefix.getText().toString()).putString("suffix", this.etSuffix.getText().toString()).putString("keywords", this.etKeywords.getText().toString().trim()).putString("routes", this.etRoutes.getText().toString().trim()).putBoolean("enabled", this.swEnabled.isChecked()).putBoolean("forward_media", this.swMedia.isChecked()).putBoolean("log_enabled", this.swLog.isChecked()).putBoolean("strip_hashtags", this.swStrip.isChecked()).apply();
        Config.savePublic();
        boolean cfgOk = Config.saveToSettings();
        if (!cfgOk) {
            new Thread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    SettingsActivity.lambda$save$2();
                }
            }).start();
        }
        Toast.makeText(this, cfgOk ? "已保存并同步到转发进程" : "已保存（系统同步稍后自动完成）", 0).show();
    }

    static /* synthetic */ void lambda$save$2() {
        try {
            Thread.sleep(3000L);
            Config.saveToSettings();
        } catch (Throwable th) {
        }
    }

    private void testNapcat() {
        final long groupId = parseLong(this.etGroupId.getText().toString(), 0L);
        if (groupId == 0) {
            Toast.makeText(this, "请先填写 QQ 群号", 0).show();
        } else {
            Toast.makeText(this, "正在发送测试消息...", 0).show();
            new Thread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    SettingsActivity.this.lambda$testNapcat$4(groupId);
                }
            }).start();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$testNapcat$4(long groupId) {
        final boolean ok = NapcatClient.sendGroupMessage(groupId, OneBotSegments.text("[TG2QQ] 测试消息，OneBot 连通正常"));
        runOnUiThread(new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.lambda$testNapcat$3(ok);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$testNapcat$3(boolean ok) {
        Toast.makeText(this, ok ? "发送成功" : "发送失败，请检查地址/端口/服务是否在线", 1).show();
    }

    private LinearLayout card() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(1);
        ll.setBackground(bg(C_CARD, dp(16), 0));
        ll.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(C_CARD, -2);
        lp.bottomMargin = dp(12);
        ll.setLayoutParams(lp);
        return ll;
    }

    private void sectionTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13.0f);
        tv.setTextColor(C_PRIMARY);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, dp(6));
        parent.addView(tv, matchWrap());
    }

    private EditText addEdit(LinearLayout parent, String label, String hint, String inputType) {
        if (label != null && !label.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(13.0f);
            tv.setTextColor(C_LABEL);
            tv.setPadding(0, dp(2), 0, dp(2));
            parent.addView(tv, matchWrap());
        }
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextColor(C_TEXT);
        et.setHintTextColor(C_BORDER);
        et.setBackground(bg(C_INPUT_BG, dp(10), 1));
        et.setPadding(dp(12), 0, dp(12), 0);
        if ("number".equals(inputType)) {
            et.setInputType(2);
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(8);
        parent.addView(et, lp);
        return et;
    }

    private EditText addEditMulti(LinearLayout parent, String label, String hint) {
        if (label != null && !label.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(13.0f);
            tv.setTextColor(C_LABEL);
            tv.setPadding(0, dp(2), 0, dp(2));
            parent.addView(tv, matchWrap());
        }
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setMinLines(4);
        et.setGravity(8388659);
        et.setInputType(131073);
        et.setTextColor(C_TEXT);
        et.setHintTextColor(C_BORDER);
        et.setBackground(bg(C_INPUT_BG, dp(10), 1));
        et.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(8);
        parent.addView(et, lp);
        return et;
    }

    private Switch addSwitch(LinearLayout parent, String label) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(C_TEXT);
        sw.setGravity(16);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(4);
        parent.addView(sw, lp);
        return sw;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15.0f);
        if (primary) {
            b.setBackground(bg(C_PRIMARY, dp(12), 0));
            b.setTextColor(C_CARD);
        } else {
            b.setBackground(bg(C_CARD, dp(12), 1));
            b.setTextColor(C_PRIMARY);
        }
        return b;
    }

    private GradientDrawable bg(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (stroke > 0) {
            g.setStroke(dp(stroke), C_BORDER);
        }
        return g;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(C_CARD, -2);
    }

    private int dp(int v) {
        return (int) ((v * getResources().getDisplayMetrics().density) + 0.5f);
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
