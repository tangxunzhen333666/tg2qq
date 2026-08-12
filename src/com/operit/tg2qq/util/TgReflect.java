package com.operit.tg2qq.util;

import de.robv.android.xposed.XposedHelpers;

/* JADX INFO: loaded from: classes3.dex */
public class TgReflect {
    public static long getLongFieldSafe(Object obj, String name) {
        try {
            return XposedHelpers.getLongField(obj, name);
        } catch (Throwable th) {
            return 0L;
        }
    }

    public static String getStringFieldSafe(Object obj, String name, String def) {
        try {
            Object v = XposedHelpers.getObjectField(obj, name);
            return v instanceof String ? (String) v : def;
        } catch (Throwable th) {
            return def;
        }
    }
}
