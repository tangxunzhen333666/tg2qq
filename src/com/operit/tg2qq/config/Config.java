package com.operit.tg2qq.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.util.Xml;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

/* JADX INFO: loaded from: classes2.dex */
public class Config {
    public static final String PKG = "com.operit.tg2qq";
    private static final String PREFS_NAME = "tg_forward";
    private static final long PUB_TTL_MS = 3000;
    private static final String SETTINGS_KEY = "tg2qq_cfg";
    private static volatile Context appContext;
    private static volatile SharedPreferences prefs;
    private static final String[] PUBLIC_CANDIDATES = {"/sdcard/Android/media/com.operit.tg2qq/tg_forward.xml", "/data/user/0/com.operit.tg2qq/shared_prefs/tg_forward.xml"};
    private static volatile Map<String, String> pubValues = new HashMap();
    private static volatile long pubLoadedAt = 0;
    private static volatile long pubFailAt = 0;
    private static volatile long[] targetIdsCache = new long[0];
    private static volatile String targetIdsKey = "";
    private static volatile Route[] routesCache = new Route[0];
    private static volatile String routesKey = "";

    public static void init(Context context) {
        if (appContext == null) {
            Context ac = context.getApplicationContext();
            appContext = ac != null ? ac : context;
        }
        xlog("Config.init called, appContext=" + (appContext != null));
        pubLoadedAt = 0L;
        pubFailAt = 0L;
        if (prefs != null) {
            return;
        }
        try {
            prefs = context.createPackageContext(PKG, 2).getSharedPreferences(PREFS_NAME, 0);
        } catch (Throwable th) {
            prefs = context.getSharedPreferences(PREFS_NAME, 0);
        }
    }

    private static SharedPreferences get() {
        if (prefs != null) {
            return prefs;
        }
        if (appContext == null) {
            appContext = currentApplication();
        }
        if (appContext != null) {
            return appContext.getSharedPreferences(PREFS_NAME, 0);
        }
        return null;
    }

    private static Context currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            return (Context) at.getMethod("currentApplication", new Class[0]).invoke(null, new Object[0]);
        } catch (Throwable th) {
            return null;
        }
    }

    public static void savePublic() {
        try {
            SharedPreferences sp = get();
            File f = new File(PUBLIC_CANDIDATES[0]);
            File dir = f.getParentFile();
            if (dir == null || dir.exists() || dir.mkdirs()) {
                FileOutputStream fos = new FileOutputStream(f);
                XmlSerializer xs = Xml.newSerializer();
                xs.setOutput(fos, "utf-8");
                xs.startDocument("utf-8", true);
                xs.startTag(null, "map");
                writeEntry(xs, "enabled", String.valueOf(sp != null ? sp.getBoolean("enabled", true) : isEnabled()));
                writeEntry(xs, "log_enabled", String.valueOf(sp != null ? sp.getBoolean("log_enabled", true) : isLogEnabled()));
                writeEntry(xs, "forward_media", String.valueOf(sp != null ? sp.getBoolean("forward_media", true) : isForwardMedia()));
                writeEntry(xs, "chat_id", sp != null ? sp.getString("chat_id", "") : getString("chat_id", ""));
                writeEntry(xs, "group_id", sp != null ? sp.getString("group_id", "") : getString("group_id", ""));
                writeEntry(xs, "napcat_url", sp != null ? sp.getString("napcat_url", "http://127.0.0.1:3001") : getString("napcat_url", "http://127.0.0.1:3001"));
                writeEntry(xs, "interval_ms", String.valueOf(sp != null ? sp.getInt("interval_ms", 2000) : getInt("interval_ms", 2000)));
                writeEntry(xs, "dl_timeout_sec", String.valueOf(sp != null ? sp.getInt("dl_timeout_sec", 120) : getInt("dl_timeout_sec", 120)));
                writeEntry(xs, "prefix", sp != null ? sp.getString("prefix", "") : getString("prefix", ""));
                writeEntry(xs, "suffix", sp != null ? sp.getString("suffix", "") : getString("suffix", ""));
                writeEntry(xs, "keywords", sp != null ? sp.getString("keywords", "") : getString("keywords", ""));
                writeEntry(xs, "routes", sp != null ? sp.getString("routes", "") : getString("routes", ""));
                writeEntry(xs, "max_age_sec", String.valueOf(sp != null ? sp.getInt("max_age_sec", 86400) : getInt("max_age_sec", 86400)));
                writeEntry(xs, "strip_hashtags", String.valueOf(sp != null ? sp.getBoolean("strip_hashtags", true) : isStripHashtags()));
                xs.endTag(null, "map");
                xs.endDocument();
                fos.flush();
                fos.close();
                pubValues = parsePublicFile(f);
                pubLoadedAt = System.currentTimeMillis();
            }
        } catch (Throwable th) {
        }
    }

    private static void writeEntry(XmlSerializer xs, String key, String value) throws Exception {
        xs.startTag(null, "string").attribute(null, "name", key).text(value).endTag(null, "string");
    }

    private static void refreshPublic() {
        try {
            long now = System.currentTimeMillis();
            for (String p : PUBLIC_CANDIDATES) {
                File cand = new File(p);
                if (cand.exists() && cand.canRead()) {
                    Map<String, String> m = parsePublicFile(cand);
                    if (!m.isEmpty()) {
                        pubValues = m;
                        pubLoadedAt = now;
                        xlog("public config loaded from " + p);
                        return;
                    }
                }
            }
            if (pubLoadedAt == 0 || now - pubLoadedAt >= PUB_TTL_MS) {
                if (pubFailAt != 0 && now - pubFailAt < 500) {
                    return;
                }
                Map<String, String> pm = queryProvider();
                if (pm == null || pm.isEmpty()) {
                    pm = readFromSettings();
                }
                if (pm != null && !pm.isEmpty()) {
                    pubValues = pm;
                    pubLoadedAt = now;
                    xlog("public config loaded size=" + pm.size());
                } else {
                    pubFailAt = now;
                    xlog("provider+settings both empty, size=" + (pm == null ? -1 : pm.size()));
                }
            }
        } catch (Throwable t) {
            xlog("public config load failed: " + t);
        }
    }

    public static boolean saveToSettings() {
        try {
            SharedPreferences sp = get();
            JSONObject o = new JSONObject();
            o.put("enabled", sp != null ? sp.getBoolean("enabled", true) : isEnabled());
            o.put("log_enabled", sp != null ? sp.getBoolean("log_enabled", true) : isLogEnabled());
            o.put("forward_media", sp != null ? sp.getBoolean("forward_media", true) : isForwardMedia());
            o.put("chat_id", sp != null ? sp.getString("chat_id", "") : getString("chat_id", ""));
            o.put("group_id", sp != null ? sp.getString("group_id", "") : getString("group_id", ""));
            o.put("napcat_url", sp != null ? sp.getString("napcat_url", "http://127.0.0.1:3001") : getString("napcat_url", "http://127.0.0.1:3001"));
            o.put("interval_ms", sp != null ? sp.getInt("interval_ms", 2000) : getInt("interval_ms", 2000));
            o.put("dl_timeout_sec", sp != null ? sp.getInt("dl_timeout_sec", 120) : getInt("dl_timeout_sec", 120));
            o.put("prefix", sp != null ? sp.getString("prefix", "") : getString("prefix", ""));
            o.put("suffix", sp != null ? sp.getString("suffix", "") : getString("suffix", ""));
            o.put("keywords", sp != null ? sp.getString("keywords", "") : getString("keywords", ""));
            o.put("routes", sp != null ? sp.getString("routes", "") : getString("routes", ""));
            o.put("max_age_sec", sp != null ? sp.getInt("max_age_sec", 86400) : getInt("max_age_sec", 86400));
            o.put("strip_hashtags", sp != null ? sp.getBoolean("strip_hashtags", true) : isStripHashtags());
            Context ctx = appContext != null ? appContext : currentApplication();
            if (ctx != null) {
                boolean ok = Settings.System.putString(ctx.getContentResolver(), SETTINGS_KEY, o.toString());
                xlog("saveToSettings result=" + ok);
                return ok;
            }
            xlog("saveToSettings: no context");
            return false;
        } catch (Throwable t) {
            xlog("saveToSettings failed: " + t);
            return false;
        }
    }

    private static Map<String, String> readFromSettings() {
        String json;
        try {
            Context ctx = appContext != null ? appContext : currentApplication();
            if (ctx != null && (json = Settings.System.getString(ctx.getContentResolver(), SETTINGS_KEY)) != null && !json.isEmpty()) {
                JSONObject o = new JSONObject(json);
                Map<String, String> m = new HashMap<>();
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    m.put(k, o.optString(k));
                }
                return m;
            }
            return null;
        } catch (Throwable t) {
            xlog("settings read failed: " + t);
            return null;
        }
    }

    private static Map<String, String> queryProvider() {
        try {
            Context ctx = appContext != null ? appContext : currentApplication();
            if (ctx == null) {
                boolean z = true;
                StringBuilder sbAppend = new StringBuilder().append("queryProvider: no context (appContext=").append(appContext != null).append(", currentApp=");
                if (currentApplication() == null) {
                    z = false;
                }
                xlog(sbAppend.append(z).append(")").toString());
                return null;
            }
            Cursor c = ctx.getContentResolver().query(Uri.parse("content://com.operit.tg2qq.config/get"), null, null, null, null);
            if (c == null) {
                xlog("queryProvider: null cursor");
                return null;
            }
            Map<String, String> m = new HashMap<>();
            while (c.moveToNext()) {
                String k = c.getString(c.getColumnIndex("_key"));
                String v = c.getString(c.getColumnIndex("_value"));
                if (k != null && v != null) {
                    m.put(k, v);
                }
            }
            c.close();
            return m;
        } catch (Throwable t) {
            xlog("provider query failed: " + t);
            return null;
        }
    }

    private static void xlog(String msg) {
        try {
            XposedBridge.log("[TG2QQ][D] " + msg);
        } catch (Throwable th) {
        }
    }

    private static Map<String, String> parsePublicFile(File f) throws Exception {
        Map<String, String> m = new HashMap<>();
        FileInputStream fis = new FileInputStream(f);
        try {
            XmlPullParser xp = Xml.newPullParser();
            xp.setInput(fis, "utf-8");
            while (true) {
                int type = xp.next();
                if (type != 1) {
                    if (type == 2) {
                        String tag = xp.getName();
                        if ("string".equals(tag) || "boolean".equals(tag) || "int".equals(tag) || "long".equals(tag) || "float".equals(tag)) {
                            String name = xp.getAttributeValue(null, "name");
                            if (name != null) {
                                m.put(name, xp.nextText());
                            }
                        }
                    }
                } else {
                    return m;
                }
            }
        } finally {
            fis.close();
        }
    }

    private static String getString(String key, String def) {
        try {
            refreshPublic();
            if (pubValues.containsKey(key)) {
                return pubValues.get(key);
            }
            SharedPreferences sp = get();
            return sp != null ? sp.getString(key, def) : def;
        } catch (Throwable th) {
            return def;
        }
    }

    private static int getInt(String key, int def) {
        try {
            refreshPublic();
            if (pubValues.containsKey(key)) {
                return Integer.parseInt(pubValues.get(key));
            }
            SharedPreferences sp = get();
            return sp != null ? sp.getInt(key, def) : def;
        } catch (Throwable th) {
            return def;
        }
    }

    private static boolean getBool(String key, boolean def) {
        try {
            refreshPublic();
            if (pubValues.containsKey(key)) {
                return Boolean.parseBoolean(pubValues.get(key));
            }
            SharedPreferences sp = get();
            return sp != null ? sp.getBoolean(key, def) : def;
        } catch (Throwable th) {
            return def;
        }
    }

    public static boolean isEnabled() {
        return getBool("enabled", true);
    }

    public static boolean isLogEnabled() {
        return getBool("log_enabled", true);
    }

    public static boolean isForwardMedia() {
        return getBool("forward_media", true);
    }

    private static long[] targetIds() {
        String raw = getString("chat_id", "").trim().replace((char) 65292, ',');
        if (!raw.equals(targetIdsKey)) {
            targetIdsKey = raw;
            String[] parts = raw.split(",");
            long[] arr = new long[parts.length];
            int n = 0;
            for (String p : parts) {
                try {
                    long v = Long.parseLong(p.trim());
                    if (v != 0) {
                        int n2 = n + 1;
                        try {
                            arr[n] = v;
                            n = n2;
                        } catch (Exception e) {
                            n = n2;
                        }
                    }
                } catch (Exception e2) {
                }
            }
            targetIdsCache = Arrays.copyOf(arr, n);
        }
        return targetIdsCache;
    }

    public static long getTargetDialogId() {
        long[] ids = targetIds();
        if (ids.length > 0) {
            return ids[0];
        }
        return 0L;
    }

    public static boolean isTargetDialog(long dialogId) {
        for (long t : targetIds()) {
            if (t == dialogId) {
                return true;
            }
        }
        return false;
    }

    public static String getTargetDialogSummary() {
        long[] ids = targetIds();
        StringBuilder sb = new StringBuilder();
        for (long t : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t);
        }
        return sb.length() > 0 ? sb.toString() : "0";
    }

    public static long getGroupId() {
        try {
            return Long.parseLong(getString("group_id", "").trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    public static class Route {
        public final long[] chats;
        public final long group;

        Route(long[] chats, long group) {
            this.chats = chats;
            this.group = group;
        }
    }

    private static Route[] routes() {
        int eq;
        String raw = getString("routes", "").trim().replace((char) 65292, ',');
        if (!raw.equals(routesKey)) {
            routesKey = raw;
            List<Route> list = new ArrayList<>();
            String[] strArrSplit = raw.split("\n");
            int length = strArrSplit.length;
            int i = 0;
            int i2 = 0;
            while (i2 < length) {
                String line = strArrSplit[i2].trim();
                if (!line.isEmpty() && (eq = line.indexOf(61)) > 0 && eq != line.length() - 1) {
                    try {
                        long group = Long.parseLong(line.substring(eq + 1).trim());
                        if (group != 0) {
                            String[] parts = line.substring(i, eq).trim().split(",");
                            long[] chats = new long[parts.length];
                            int length2 = parts.length;
                            int n = 0;
                            for (int i3 = i; i3 < length2; i3++) {
                                String p = parts[i3];
                                try {
                                    long v = Long.parseLong(p.trim());
                                    if (v != 0) {
                                        int n2 = n + 1;
                                        try {
                                            chats[n] = v;
                                            n = n2;
                                        } catch (Exception e) {
                                            n = n2;
                                        }
                                    }
                                } catch (Exception e2) {
                                }
                            }
                            if (n != 0) {
                                list.add(new Route(Arrays.copyOf(chats, n), group));
                            }
                        }
                    } catch (Exception e3) {
                    }
                }
                i2++;
                i = 0;
            }
            routesCache = (Route[]) list.toArray(new Route[0]);
        }
        return routesCache;
    }

    public static long getGroupForDialog(long dialogId) {
        for (Route r : routes()) {
            for (long c : r.chats) {
                if (c == dialogId) {
                    return r.group;
                }
            }
        }
        if (isTargetDialog(dialogId)) {
            return getGroupId();
        }
        return 0L;
    }

    public static String getNapcatUrl() {
        return getString("napcat_url", "http://127.0.0.1:3001").replaceAll("/+$", "");
    }

    public static int getIntervalMs() {
        return Math.max(300, getInt("interval_ms", 800));
    }

    public static int getDownloadTimeoutSec() {
        return Math.max(10, getInt("dl_timeout_sec", 120));
    }

    public static String getPrefix() {
        return getString("prefix", "");
    }

    public static String getSuffix() {
        return getString("suffix", "");
    }

    public static String getKeywords() {
        return getString("keywords", "");
    }

    public static int getMaxAgeSec() { return getInt("max_age_sec", 86400); }
    public static boolean isStripHashtags() {
        return getBool("strip_hashtags", true);
    }

    private static List<String> parseKeywords() {
        List<String> kws = new ArrayList<>();
        String keywords = getKeywords();
        if (keywords.isEmpty()) return kws;
        if (keywords.contains("【")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("【([^】]+)】").matcher(keywords);
            while (m.find()) {
                String kw = m.group(1).trim();
                if (!kw.isEmpty()) kws.add(kw);
            }
        }
        if (kws.isEmpty()) {
            for (String kw : keywords.split(",")) {
                String k = kw.trim();
                if (!k.isEmpty()) kws.add(k);
            }
        }
        return kws;
    }
    public static String removeKeywords(String text) {
        String keywords = getKeywords();
        if (keywords.isEmpty() || text == null || text.isEmpty()) {
            return text;
        }
        List<String> kws = parseKeywords();
        if (kws.isEmpty()) {
            return text;
        }
        Collections.sort(kws, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.compare(o2.length(), o1.length());
            }
        });
        int cut = -1;
        for (String kw3 : kws) {
            int pos = indexOfLoose(text, kw3);
            if (pos >= 0 && (cut < 0 || pos < cut)) {
                cut = pos;
            }
        }
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        return text.replaceAll("[ \\t]{2,}", " ").trim();
    }

    /** 宽松匹配关键词（忽略空白与标点），返回关键词在原始文本中的起始位置；未找到返回 -1 */
    private static int indexOfLoose(String text, String kw) {
        String nk = normForMatch(kw);
        if (nk.isEmpty()) {
            return -1;
        }
        String nt = normForMatch(text);
        int idx = nt.indexOf(nk);
        if (idx < 0) {
            return -1;
        }
        int ni = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!isIgnorable(text.charAt(i))) {
                if (ni == idx) {
                    return i;
                }
                ni++;
            }
        }
        return -1;
    }

    private static String normForMatch(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!isIgnorable(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isIgnorable(char c) {
        return Character.isWhitespace(c) || !Character.isLetterOrDigit(c);
    }
}
