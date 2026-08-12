package com.operit.tg2qq;

import com.operit.tg2qq.core.ForwardService;
import com.operit.tg2qq.util.Logger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/* JADX INFO: loaded from: classes4.dex */
public class HookEntry implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("org.telegram.messenger") || lpparam.packageName.equals("org.telegram.plus") || lpparam.packageName.equals("org.telegram.mdgram")) {
            Logger.i("HookEntry: load " + lpparam.packageName + " process=" + lpparam.processName);
            try {
                ForwardService.getInstance().init(lpparam);
                Logger.i("HookEntry: init OK");
            } catch (Throwable t) {
                Logger.e("HookEntry: init FAILED", t);
            }
        }
    }
}
