package com.operit.tg2qq.core;

import android.app.Application;
import android.content.Context;
import com.operit.tg2qq.config.Config;
import com.operit.tg2qq.util.Logger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.List;

/* JADX INFO: loaded from: classes2.dex */
public class ForwardService {
    private static final ForwardService INSTANCE = new ForwardService();
    private volatile boolean hooked = false;
    private final ForwardQueue queue = new ForwardQueue();

    public static ForwardService getInstance() {
        return INSTANCE;
    }

    public void init(final XC_LoadPackage.LoadPackageParam lpp) throws Throwable {
        if (this.hooked) {
            return;
        }
        this.hooked = true;
        Logger.i("ForwardService: schedule hooking " + lpp.packageName);
        hookAllMethods(Application.class, "attach", new XC_MethodHook() { // from class: com.operit.tg2qq.core.ForwardService.1
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                try {
                    Context context = (Context) param.thisObject;
                    Logger.i("attach callback: mainProcess=" + ForwardService.this.isMainProcess(context, lpp.packageName));
                    if (ForwardService.this.isMainProcess(context, lpp.packageName)) {
                        Config.init(context);
                        ForwardService.this.installHooks(lpp.classLoader);
                        Logger.i("hooks installed OK, targetDialog=" + Config.getTargetDialogSummary() + " group=" + Config.getGroupId());
                    }
                } catch (Throwable t) {
                    Logger.e("install hooks failed", t);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isMainProcess(Context context, String pkg) {
        try {
            String cur = (String) XposedHelpers.callStaticMethod(Class.forName("android.app.ActivityThread"), "currentProcessName", new Object[0]);
            return pkg.equals(cur);
        } catch (Throwable th) {
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void installHooks(ClassLoader cl) throws Throwable {
        Class<?> mc = Class.forName("org.telegram.messenger.MessagesController", false, cl);
        Class<?> nc = Class.forName("org.telegram.messenger.NotificationCenter", false, cl);
        boolean hookedUpdate = hookAllMethods(mc, "processUpdateArray", new XC_MethodHook() { // from class: com.operit.tg2qq.core.ForwardService.2
            protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                try {
                    if (param.args != null && param.args.length != 0) {
                        Object a0 = param.args[0];
                        Logger.i("processUpdateArray called, args=" + param.args.length + " [0]=" + (a0 != null ? a0.getClass().getName() + "/" + a0.getClass().getSimpleName() : "null"));
                        if (a0 instanceof List) {
                            for (Object update : (List) a0) {
                                ForwardService.this.handleUpdate(update, param.thisObject);
                            }
                        } else {
                            Logger.d("processUpdateArray [0] not a List: " + (a0 != null ? a0.getClass().getName() : "null"));
                        }
                        return;
                    }
                    Logger.i("processUpdateArray called, args=null/empty");
                } catch (Throwable t) {
                    Logger.e(t);
                }
            }
        });
        if (!hookedUpdate) {
            Logger.w("processUpdateArray not found, fallback to processUpdates");
            hookAllMethods(mc, "processUpdates", new XC_MethodHook() { // from class: com.operit.tg2qq.core.ForwardService.3
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    try {
                        Object updates = param.args[0];
                        if (updates == null) {
                            return;
                        }
                        List<?> list = null;
                        try {
                            list = (List) XposedHelpers.getObjectField(updates, "updates");
                        } catch (Throwable th) {
                            try {
                                Object single = XposedHelpers.getObjectField(updates, "update");
                                if (single != null) {
                                    ForwardService.this.handleUpdate(single, param.thisObject);
                                }
                            } catch (Throwable th2) {
                            }
                        }
                        if (list != null) {
                            for (Object update : list) {
                                ForwardService.this.handleUpdate(update, param.thisObject);
                            }
                        }
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            });
        }
        try {
            Class<?> ms = Class.forName("org.telegram.messenger.MessagesStorage", false, cl);
            hookAllMethods(ms, "putMessages", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    try {
                        if (param.args == null) return;
                        for (Object a : param.args) {
                            if (!(a instanceof List)) continue;
                            List<?> list = (List) a;
                            if (list.isEmpty()) continue;
                            String cn = list.get(0).getClass().getName();
                            boolean isTgMessage = cn.contains("TL_message")
                                    || cn.endsWith("TLRPC$Message");
                            if (!isTgMessage) continue;
                            Logger.i("putMessages msgs=" + list.size());
                            for (Object msg : list) {
                                ForwardService.this.handleMessage(msg, param.thisObject);
                            }
                            return;
                        }
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            });
        } catch (Throwable t) {
            Logger.w("MessagesStorage hook skipped: " + t.getMessage());
        }
        try {
            final int fileLoaded = XposedHelpers.getStaticIntField(nc, "fileLoaded");
            hookAllMethods(nc, "postNotificationName", new XC_MethodHook() { // from class: com.operit.tg2qq.core.ForwardService.5
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    Object[] extra;
                    try {
                        if (param.args != null && param.args.length >= 2 && (param.args[0] instanceof Integer) && ((Integer) param.args[0]).intValue() == fileLoaded && (extra = (Object[]) param.args[1]) != null && extra.length >= 2 && (extra[0] instanceof String)) {
                            MediaManager.getInstance().onFileLoaded((String) extra[0]);
                        }
                    } catch (Throwable t2) {
                        Logger.e(t2);
                    }
                }
            });
        } catch (Throwable t2) {
            Logger.w("fileLoaded hook skipped: " + t2.getMessage());
        }
        try {
            hookAllMethods(mc, "updateInterfaceWithMessages", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 2) return;
                        Object list = param.args[1];
                        if (!(list instanceof List)) return;
                        Logger.i("uiwms dialog=" + param.args[0] + " msgs=" + ((List) list).size());
                        for (Object mo : (List) list) {
                            if (mo == null) continue;
                            Object msg;
                            try {
                                msg = XposedHelpers.getObjectField(mo, "messageOwner");
                            } catch (Throwable th) {
                                continue;
                            }
                            if (msg == null) continue;
                            long md;
                            try {
                                md = MessageParser.calcDialogId(msg);
                            } catch (Throwable th) {
                                continue;
                            }
                            if (md != 0 && Config.getGroupForDialog(md) != 0) {
                                ForwardService.this.handleMessage(msg, param.thisObject);
                            }
                        }
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            });
        } catch (Throwable t3) {
            Logger.w("updateInterfaceWithMessages hook skipped: " + t3.getMessage());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleMessage(Object message, Object controller) {
        if (message == null) {
            return;
        }
        int account = 0;
        account = XposedHelpers.getIntField(controller, "currentAccount");
        try {
            this.queue.enqueue(account, message);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleUpdate(Object update, Object controller) {
        if (update == null) {
            return;
        }
        try {
            String name = update.getClass().getName();
            if (!name.endsWith("TL_updateNewMessage") && !name.endsWith("TL_updateNewChannelMessage")) {
                Logger.d("update type: " + name);
                return;
            }
            Logger.i("update type: " + name + " -> enqueue");
            Object message = XposedHelpers.getObjectField(update, "message");
            if (message == null) {
                return;
            }
            int account = 0;
            try {
                account = XposedHelpers.getIntField(controller, "currentAccount");
            } catch (Throwable th) {
            }
            this.queue.enqueue(account, message);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static boolean hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook hook) {
        int count = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                try {
                    XposedBridge.hookMethod(m, hook);
                    count++;
                } catch (Throwable t) {
                    Logger.e("hook " + clazz.getSimpleName() + "." + methodName + " failed", t);
                }
            }
        }
        if (count == 0) {
            Logger.w(clazz.getSimpleName() + "." + methodName + " not found in this TG version");
        } else {
            Logger.i("hooked " + clazz.getSimpleName() + "." + methodName + " x" + count);
        }
        return count > 0;
    }
}
