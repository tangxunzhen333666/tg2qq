package com.operit.tg2qq.core;

import android.os.Environment;
import com.operit.tg2qq.util.Logger;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: classes2.dex */
public class MediaManager {
    private static final String EXPORT_DIR = "Download/tg2qq_media";
    private static final MediaManager INSTANCE = new MediaManager();
    private volatile ClassLoader cl;
    private final Map<String, Long> readyPaths = new ConcurrentHashMap();

    public static MediaManager getInstance() {
        return INSTANCE;
    }

    public void init(ClassLoader classLoader) {
        this.cl = classLoader;
    }

    public void onFileLoaded(String location) {
        if (location != null) {
            this.readyPaths.put(location, Long.valueOf(System.currentTimeMillis()));
        }
    }

    public String ensureDownloaded(ParsedMessage.MediaInfo mi, int account, long timeoutMs) {
        if (mi == null || mi.location == null || this.cl == null) {
            return null;
        }
        try {
            String cached = pathOf(mi, account);
            if (cached == null) {
                return null;
            }
            File f = new File(cached);
            if (isReady(f)) {
                return export(f, mi);
            }
            triggerDownload(mi, account);
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(500L);
                if (isReady(f)) {
                    return export(f, mi);
                }
            }
            Logger.w("media download timeout: " + cached);
            return null;
        } catch (Throwable t) {
            Logger.e("ensureDownloaded failed", t);
            return null;
        }
    }

    private boolean isReady(File f) {
        if (this.readyPaths.containsKey(f.getAbsolutePath())) {
            return true;
        }
        return f.exists() && f.length() > 0;
    }

    private String export(File src, ParsedMessage.MediaInfo mi) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), EXPORT_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                Logger.w("export dir create failed, use cache path");
                return src.getAbsolutePath();
            }
            File dst = new File(dir, System.currentTimeMillis() + "_" + src.getName() + "." + safeExt(mi.ext));
            copy(src, dst);
            Logger.d("media exported: " + dst.getAbsolutePath());
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            Logger.e("media export failed, use cache path", t);
            return src.getAbsolutePath();
        }
    }

    private String safeExt(String ext) {
        if (ext == null || ext.isEmpty()) {
            return "bin";
        }
        return ext.replaceAll("[^a-zA-Z0-9]", "");
    }

    private void copy(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[65536];
                while (true) {
                    int n = in.read(buf);
                    if (n > 0) {
                        out.write(buf, 0, n);
                    } else {
                        out.close();
                        in.close();
                        return;
                    }
                }
            } catch (Throwable th) {
                out.close();
                throw th;
            }
        } catch (Throwable th2) {
            in.close();
            throw th2;
        }
    }

    private String pathOf(ParsedMessage.MediaInfo mi, int account) throws Throwable {
        Object loader = XposedHelpers.callStaticMethod(Class.forName("org.telegram.messenger.FileLoader", false, this.cl), "getInstance", new Object[]{Integer.valueOf(account)});
        File file = (File) XposedHelpers.callMethod(loader, "getPathToAttach", new Object[]{mi.location, Boolean.TRUE});
        if (file == null) {
            return null;
        }
        return file.getAbsolutePath();
    }

    private void triggerDownload(ParsedMessage.MediaInfo mi, int account) throws Throwable {
        Object loader = XposedHelpers.callStaticMethod(Class.forName("org.telegram.messenger.FileLoader", false, this.cl), "getInstance", new Object[]{Integer.valueOf(account)});
        if (mi.kind == 1 && mi.photoSize != null) {
            Object imageLocation = XposedHelpers.callStaticMethod(Class.forName("org.telegram.messenger.ImageLocation", false, this.cl), "getForPhoto", new Object[]{mi.photoSize, mi.parent});
            if (imageLocation != null) {
                XposedHelpers.callMethod(loader, "loadFile", new Object[]{imageLocation, mi.parent, mi.ext, 0, 0});
            }
        } else {
            XposedHelpers.callMethod(loader, "loadFile", new Object[]{mi.location, mi.parent, 0, 0});
        }
        Logger.d("download triggered: " + mi.ext);
    }
}
