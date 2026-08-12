package com.operit.tg2qq.network;

import com.operit.tg2qq.config.Config;
import com.operit.tg2qq.util.Logger;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/* JADX INFO: loaded from: classes4.dex */
public class NapcatClient {
    public static boolean sendGroupMessage(long groupId, String messageJson) {
        if (groupId == 0) {
            Logger.w("group_id not configured");
            return false;
        }
        String base = Config.getNapcatUrl();
        String body = "{\"group_id\":" + groupId + ",\"message\":" + messageJson + "}";
        for (int i = 1; i <= 2; i++) {
            String resp = post(base + "/send_group_msg", body);
            if (resp != null && (resp.contains("\"status\":\"ok\"") || resp.contains("\"retcode\":0"))) {
                return true;
            }
            if (i == 1) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private static String post(String urlStr, String body) {
        Socket socket = null;
        try {
            URL url = new URL(urlStr);
            int port = url.getPort() > 0 ? url.getPort() : 80;
            String host = url.getHost();
            String path = url.getPath().isEmpty() ? "/" : url.getPath();
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(10000);
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            StringBuilder head = new StringBuilder();
            head.append("POST ").append(path).append(" HTTP/1.1\r\n");
            head.append("Host: ").append(host).append(':').append(port).append("\r\n");
            head.append("Content-Type: application/json\r\n");
            head.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            head.append("Connection: close\r\n\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            out.write(bodyBytes);
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (true) {
                int n = in.read(buf);
                if (n <= 0) break;
                bos.write(buf, 0, n);
            }
            String raw = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            int idx = raw.indexOf("\r\n\r\n");
            String header = idx >= 0 ? raw.substring(0, idx) : raw;
            String respBody = idx >= 0 ? raw.substring(idx + 4) : "";
            boolean ok2xx = header.contains(" 200 ") || header.contains(" 201 ");
            Logger.d("napcat resp " + (ok2xx ? "200" : header.split("\r\n")[0]) + ": " + truncate(respBody, 200));
            if (ok2xx) return respBody;
            Logger.w("napcat http error: " + header.split("\r\n")[0]);
            return null;
        } catch (Throwable t) {
            Logger.e("post failed: " + urlStr, t);
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max) + "...";
    }
}
