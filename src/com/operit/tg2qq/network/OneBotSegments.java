package com.operit.tg2qq.network;

import com.operit.tg2qq.core.ParsedMessage;

/* JADX INFO: loaded from: classes4.dex */
public class OneBotSegments {
    public static String build(String text, ParsedMessage.MediaInfo mediaInfo, String mediaPath) {
        StringBuilder sb = new StringBuilder("[");
        boolean hasText = (text == null || text.isEmpty()) ? false : true;
        if (hasText) {
            sb.append(text(text));
        }
        if (mediaInfo != null && mediaPath != null) {
            String seg = null;
            if ("image".equals(mediaInfo.qqType)) {
                seg = image(mediaPath);
            } else if ("video".equals(mediaInfo.qqType)) {
                seg = video(mediaPath);
            } else if ("voice".equals(mediaInfo.qqType)) {
                seg = voice(mediaPath);
            } else if ("file".equals(mediaInfo.qqType)) {
                seg = file(mediaPath);
            }
            if (seg != null) {
                if (hasText) {
                    sb.append(",");
                }
                sb.append(seg);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static String text(String text) {
        return "{\"type\":\"text\",\"data\":{\"text\":" + quote(text) + "}}";
    }

    public static String image(String path) {
        return "{\"type\":\"image\",\"data\":{\"file\":" + quote("file://" + path) + "}}";
    }

    public static String video(String path) {
        return "{\"type\":\"video\",\"data\":{\"file\":" + quote("file://" + path) + "}}";
    }

    public static String voice(String path) {
        return "{\"type\":\"record\",\"data\":{\"file\":" + quote("file://" + path) + "}}";
    }

    public static String file(String path) {
        return "{\"type\":\"file\",\"data\":{\"file\":" + quote("file://" + path) + "}}";
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", Integer.valueOf(c)));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.append("\"").toString();
    }
}
