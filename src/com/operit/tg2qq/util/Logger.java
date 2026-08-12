package com.operit.tg2qq.util;

import android.util.Log;
import com.operit.tg2qq.config.Config;
import de.robv.android.xposed.XposedBridge;

/* JADX INFO: loaded from: classes3.dex */
public class Logger {
    private static final String TAG = "TG2QQ";

    private static boolean enabled() {
        try {
            return Config.isLogEnabled();
        } catch (Throwable th) {
            return true;
        }
    }

    public static void d(String msg) {
        if (enabled()) {
            log("D", msg);
        }
    }

    public static void i(String msg) {
        if (enabled()) {
            log("I", msg);
        }
    }

    public static void w(String msg) {
        if (enabled()) {
            log("W", msg);
        }
    }

    public static void e(String msg) {
        log("E", msg);
    }

    public static void e(String msg, Throwable t) {
        log("E", msg);
        Log.e(TAG, msg, t);
        try {
            XposedBridge.log(t);
        } catch (Throwable th) {
        }
    }

    public static void e(Throwable t) {
        Log.e(TAG, "error", t);
        try {
            XposedBridge.log(t);
        } catch (Throwable th) {
        }
    }

    private static void log(String level, String msg) {
        Log.i(TAG, "[" + level + "] " + msg);
        try {
            XposedBridge.log("[TG2QQ][" + level + "] " + msg);
        } catch (Throwable th) {
        }
    }
}
