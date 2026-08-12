package com.operit.tg2qq.core;

import com.operit.tg2qq.config.Config;
import com.operit.tg2qq.network.NapcatClient;
import com.operit.tg2qq.network.OneBotSegments;
import com.operit.tg2qq.util.Logger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: classes2.dex */
public class ForwardQueue {
    private static final long BATCH_WAIT_MS = 300;
    private static final int DEDUP_MAX = 5000;
    private static final long DEDUP_WINDOW_MS = 60000;
    private static final long HARD_MAX_WAIT_MS = 1500;
    private static final int MAX_BATCH = 10;
    private static final int MAX_TEXT_CHARS = 3000;
    private final Object lock = new Object();
    private final List<Object[]> pending = new ArrayList();
    private boolean processorRunning = false;
    private final Map<String, Long> dedup = new ConcurrentHashMap();

    public void enqueue(int account, Object message) {
        synchronized (this.lock) {
            this.pending.add(new Object[]{Integer.valueOf(account), message, null, Long.valueOf(System.currentTimeMillis())});
            if (!this.processorRunning) {
                this.processorRunning = true;
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ForwardQueue.this.run();
                    }
                }, "tg-forward");
                t.setDaemon(true);
                t.start();
            }
            this.lock.notifyAll();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void run() {
        while (true) {
            try {
                List<Object[]> batch = takeBatch();
                if (batch == null) {
                    synchronized (this.lock) {
                        if (this.pending.isEmpty()) {
                            this.processorRunning = false;
                            return;
                        }
                    }
                } else {
                    sendBatch(batch);
                }
            } catch (Throwable t) {
                Logger.e("forward queue crashed", t);
                synchronized (this.lock) {
                    this.processorRunning = false;
                    return;
                }
            }
        }
    }

    private List<Object[]> takeBatch() {
        synchronized (this.lock) {
            while (true) {
                if (this.pending.isEmpty()) {
                    try {
                        this.lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }
                List<Object[]> batch = new ArrayList<>();
                Long firstDialog = null;
                int i = 0;
                long now = System.currentTimeMillis();
                long lastEnq = now;
                long firstEnq = now;
                while (i < this.pending.size() && batch.size() < MAX_BATCH) {
                    Object[] item = this.pending.get(i);
                    ParsedMessage pm = (ParsedMessage) item[2];
                    if (pm == null) {
                        pm = MessageParser.parse(item[1]);
                        item[2] = pm;
                    }
                    long dlg = pm != null ? pm.dialogId : Long.MIN_VALUE;
                    if (firstDialog == null) {
                        firstDialog = dlg;
                        firstEnq = (Long) item[3];
                        lastEnq = (Long) item[3];
                        batch.add(item);
                    } else if (dlg == firstDialog) {
                        batch.add(item);
                        lastEnq = Math.max(lastEnq, (Long) item[3]);
                    }
                    i++;
                }
                if (batch.isEmpty()) {
                    try {
                        this.lock.wait(50L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }
                long now2 = System.currentTimeMillis();
                boolean ready = batch.size() >= MAX_BATCH
                        || now2 - lastEnq >= BATCH_WAIT_MS
                        || now2 - firstEnq >= HARD_MAX_WAIT_MS;
                if (ready) {
                    this.pending.removeAll(batch);
                    return batch;
                }
                long waitMs = Math.min(BATCH_WAIT_MS - (now2 - lastEnq), HARD_MAX_WAIT_MS - (now2 - firstEnq));
                try {
                    this.lock.wait(Math.max(waitMs, 1L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }
    private void sendBatch(List<Object[]> batch) {
        List<String> lines = new ArrayList<>();
        int forwarded = 0;
        long batchGroup = 0;
        for (Object[] item : batch) {
            ParsedMessage pm = (ParsedMessage) item[2];
            if (batchGroup == 0 && pm != null) {
                batchGroup = Config.getGroupForDialog(pm.dialogId);
            }
            if (collect(item, pm, lines)) {
                forwarded++;
            }
        }
        if (batchGroup == 0) {
            return;
        }
        List<String> chunks = chunkLines(lines, MAX_TEXT_CHARS);
        for (int i = 0; i < chunks.size(); i++) {
            String body = chunks.get(i);
            if (i == 0) {
                body = Config.getPrefix() + body;
            }
            if (i == chunks.size() - 1) {
                body = body + Config.getSuffix();
            }
            String finalText = body.trim();
            boolean ok = NapcatClient.sendGroupMessage(batchGroup, OneBotSegments.build(finalText, null, null));
            Logger.i("forward batch(group=" + batchGroup + ", lines=" + lines.size() + ", total=" + batch.size() + ", forwarded=" + forwarded + ", chunk=" + (i + 1) + "/" + chunks.size() + ") -> " + (ok ? "OK" : "FAILED"));
            sleepInterval();
        }
    }

    private boolean collect(Object[] item, ParsedMessage pm, List<String> lines) {
        String brief;
        String text;
        try {
            if (!Config.isEnabled()) {
                return false;
            }
            if (pm == null) {
                Logger.d("recv unparsable message");
                return false;
            }
            if (pm.text == null || pm.text.length() <= 100) {
                brief = pm.text != null ? pm.text : "[media]";
            } else {
                brief = pm.text.substring(0, 100);
            }
            long targetGroup = Config.getGroupForDialog(pm.dialogId);
            if (pm.out || targetGroup == 0) {
                Logger.i("recv chat=" + pm.dialogId + " id=" + pm.id + " out=" + pm.out + " target=" + Config.getTargetDialogSummary() + " group=" + targetGroup + " (skip) text=" + brief.replace('\n', ' '));
                return false;
            }
            Logger.i("recv chat=" + pm.dialogId + " id=" + pm.id + " MATCH group=" + targetGroup + " text=" + brief.replace('\n', ' '));
            long nowSec = System.currentTimeMillis() / 1000;
            if (pm.date > 0 && Config.getMaxAgeSec() > 0 && nowSec - ((long) pm.date) > Config.getMaxAgeSec()) {
                Logger.d("skip old msg chat=" + pm.dialogId + " id=" + pm.id + " date=" + pm.date);
                return false;
            }
            String key = pm.dialogId + ":" + pm.id;
            if (!markDedup(key)) {
                return false;
            }
            if (pm.text != null) {
                text = pm.text;
            } else {
                text = (pm.mediaInfo == null || pm.mediaInfo.textFallback == null) ? "" : pm.mediaInfo.textFallback;
            }
            if (Config.isStripHashtags()) {
                text = stripHashtags(text);
            }
            String text2 = Config.removeKeywords(text);
            if (text2.trim().isEmpty()) {
                Logger.d("nothing left after keyword strip, skip " + key);
                return false;
            }
            if (pm.mediaInfo != null && !pm.mediaInfo.isTextual()) {
                if (Config.isForwardMedia() && !text2.trim().isEmpty()) {
                    lines.add(text2.trim());
                    return true;
                }
                Logger.d("media skip(only text) " + key + " media=" + pm.mediaInfo.qqType);
                return false;
            }
            if (text2.trim().isEmpty()) {
                Logger.d("nothing to forward, skip " + key);
                return false;
            }
            lines.add(text2.trim());
            return true;
        } catch (Throwable t) {
            Logger.e("forward collect failed", t);
            return false;
        }
    }

    private boolean markDedup(String key) {
        long now = System.currentTimeMillis();
        Long last = this.dedup.putIfAbsent(key, Long.valueOf(now));
        if (last != null && now - last.longValue() < DEDUP_WINDOW_MS) {
            Logger.d("dedup skip " + key);
            return false;
        }
        if (this.dedup.size() > DEDUP_MAX) {
            long cutoff = now - DEDUP_WINDOW_MS;
            Iterator<Map.Entry<String, Long>> it = this.dedup.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().longValue() < cutoff) {
                    it.remove();
                }
            }
            if (this.dedup.size() > DEDUP_MAX) {
                this.dedup.clear();
                return true;
            }
            return true;
        }
        return true;
    }

    private static List<String> chunkLines(List<String> lines, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            if (line.length() > maxChars) {
                if (cur.length() > 0) {
                    chunks.add(cur.toString());
                    cur.setLength(0);
                }
                int i = 0;
                while (i < line.length()) {
                    chunks.add(line.substring(i, Math.min(line.length(), i + maxChars)));
                    i += maxChars;
                }
            } else {
                if (cur.length() > 0 && cur.length() + 2 + line.length() > maxChars) {
                    chunks.add(cur.toString());
                    cur.setLength(0);
                }
                if (cur.length() > 0) {
                    cur.append("\n\n");
                }
                cur.append(line);
            }
        }
        if (cur.length() > 0) {
            chunks.add(cur.toString());
        }
        return chunks;
    }

    private static String stripHashtags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll("#\\S+", "").replaceAll("[ \\t]{2,}", " ").trim();
    }

    private void sleepInterval() {
        try {
            Thread.sleep(Config.getIntervalMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
