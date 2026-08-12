package com.operit.tg2qq.core;

import com.operit.tg2qq.core.ParsedMessage.MediaInfo;
import com.operit.tg2qq.util.Logger;
import com.operit.tg2qq.util.TgReflect;

import de.robv.android.xposed.XposedHelpers;

/** 从 TLRPC.Message 对象中提取文本、实体、媒体、会话 ID（全反射，兼容版本差异） */
public class MessageParser {

    public static ParsedMessage parse(Object message) {
        try {
            ParsedMessage pm = new ParsedMessage();
            pm.id = TgReflect.getLongFieldSafe(message, "id");
            try { pm.date = XposedHelpers.getIntField(message, "date"); } catch (Throwable ignored) {}
            pm.out = XposedHelpers.getBooleanField(message, "out");
            pm.text = readText(message);
            pm.media = getField(message, "media");
            pm.dialogId = calcDialogId(message);
            pm.mediaInfo = parseMedia(pm.media);
            return pm;
        } catch (Throwable t) {
            Logger.e("parse message failed", t);
            return null;
        }
    }

    /** 文本字段名：新版本 message，老版本 message_ */
    private static String readText(Object message) {
        try {
            Object v = XposedHelpers.getObjectField(message, "message");
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {}
        try {
            Object v = XposedHelpers.getObjectField(message, "message_");
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object getField(Object obj, String name) {
        try { return XposedHelpers.getObjectField(obj, name); } catch (Throwable t) { return null; }
    }

    /** 频道/群/私聊 → dialog_id。频道 ID（-100 开头）可直接与 dialog_id 比较 */
    public static long calcDialogId(Object message) {
        // 优先 peer_id（兼容 12.9.x：dialog_id 字段可能是 int，getLongField 会失败）
        Object peer = getField(message, "peer_id");
        if (peer != null) {
            String full = peer.getClass().getName();
            String n = peer.getClass().getSimpleName();
            Logger.d("calcDialogId peer=" + full + " simple=" + n);
            if (full.endsWith("TL_peerChannel")) {
                long ch = TgReflect.getLongFieldSafe(peer, "channel_id");
                if (ch != 0) return -1000000000000L - ch;
                return 0L;
            }
            if (full.endsWith("TL_peerChat")) {
                return -TgReflect.getLongFieldSafe(peer, "chat_id");
            }
            if (full.endsWith("TL_peerUser")) {
                return TgReflect.getLongFieldSafe(peer, "user_id");
            }
        }
        try {
            long d = XposedHelpers.getLongField(message, "dialog_id");
            if (d != 0) {
                Logger.d("calcDialogId via dialog_id=" + d);
                return d;
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /** 媒体对象 → MediaInfo；文本化媒体（网页/位置/联系人/投票）走 textFallback */
    public static MediaInfo parseMedia(Object media) {
        if (media == null) return null;
        String n = media.getClass().getSimpleName();
        try {
            switch (n) {
                case "TL_messageMediaPhoto": {
                    Object photo = getField(media, "photo");
                    if (photo == null) return null;
                    Object size = pickLargestPhotoSize(photo);
                    Object location = getField(size, "location");
                    if (location == null) return null;
                    MediaInfo mi = new MediaInfo(ParsedMessage.MEDIA_PHOTO, location, photo, "jpg", "image");
                    mi.photoSize = size;
                    return mi;
                }
                case "TL_messageMediaDocument": {
                    Object doc = getField(media, "document");
                    if (doc == null) return null;
                    return classifyDocument(doc);
                }
                case "TL_messageMediaWebPage": {
                    Object page = getField(media, "webpage");
                    if (page == null) return null;
                    String url = TgReflect.getStringFieldSafe(page, "url", "");
                    String title = TgReflect.getStringFieldSafe(page, "title", "");
                    String desc = TgReflect.getStringFieldSafe(page, "description", "");
                    String text = (title.isEmpty() ? "" : title + "\n")
                            + (desc.isEmpty() ? "" : desc + "\n") + url;
                    return new MediaInfo(ParsedMessage.MEDIA_WEBPAGE, text, url, title);
                }
                case "TL_messageMediaGeo":
                case "TL_messageMediaGeoLive": {
                    Object geo = getField(media, "geo");
                    if (geo == null) return null;
                    double lat = XposedHelpers.getDoubleField(geo, "lat");
                    double lon = XposedHelpers.getDoubleField(geo, "long");
                    return new MediaInfo(ParsedMessage.MEDIA_GEO,
                            "📍 " + lat + ", " + lon + "\nhttps://maps.google.com/?q=" + lat + "," + lon,
                            null, null);
                }
                case "TL_messageMediaContact": {
                    String name = TgReflect.getStringFieldSafe(media, "first_name", "")
                            + " " + TgReflect.getStringFieldSafe(media, "last_name", "");
                    String phone = TgReflect.getStringFieldSafe(media, "phone_number", "");
                    return new MediaInfo(ParsedMessage.MEDIA_CONTACT,
                            "联系人: " + name.trim() + " " + phone, null, null);
                }
                case "TL_messageMediaPoll": {
                    Object poll = getField(media, "poll");
                    String q = poll == null ? "" : TgReflect.getStringFieldSafe(poll, "question", "");
                    return new MediaInfo(ParsedMessage.MEDIA_POLL, "📊 投票: " + q, null, null);
                }
                default:
                    return null;
            }
        } catch (Throwable t) {
            Logger.e("parse media failed: " + n, t);
            return null;
        }
    }

    /** Photo.sizes 中选最大尺寸（列表按小到大排序，取最后一项） */
    private static Object pickLargestPhotoSize(Object photo) {
        try {
            java.util.List<?> sizes = (java.util.List<?>) XposedHelpers.getObjectField(photo, "sizes");
            if (sizes != null && !sizes.isEmpty()) return sizes.get(sizes.size() - 1);
        } catch (Throwable ignored) {}
        return null;
    }

    private static String extOf(String fileName, String def) {
        if (fileName == null) return def;
        int i = fileName.lastIndexOf('.');
        if (i < 0 || i == fileName.length() - 1) return def;
        String e = fileName.substring(i + 1).toLowerCase();
        return e.isEmpty() ? def : e;
    }

    /** 文档：按 DocumentAttribute 判定类型并映射 OneBot 段类型 */
    private static MediaInfo classifyDocument(Object doc) {
        boolean isVideo = false, isAnimated = false, isSticker = false, isVoice = false;
        String fileName = null, mime = "";
        try {
            java.util.List<?> attrs = (java.util.List<?>) XposedHelpers.getObjectField(doc, "attributes");
            mime = TgReflect.getStringFieldSafe(doc, "mime_type", "");
            if (attrs != null) {
                for (Object a : attrs) {
                    String an = a.getClass().getSimpleName();
                    if ("TL_documentAttributeVideo".equals(an)) isVideo = true;
                    else if ("TL_documentAttributeAnimated".equals(an)) isAnimated = true;
                    else if ("TL_documentAttributeSticker".equals(an)) isSticker = true;
                    else if ("TL_documentAttributeAudio".equals(an)) {
                        try { isVoice = XposedHelpers.getBooleanField(a, "voice"); } catch (Throwable ignored) {}
                    } else if ("TL_documentAttributeFilename".equals(an)) {
                        fileName = TgReflect.getStringFieldSafe(a, "file_name", null);
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (isSticker) return null; // 贴纸默认忽略
        if (isVoice)   return new MediaInfo(ParsedMessage.MEDIA_DOCUMENT, doc, doc, extOf(fileName, "ogg"), "voice");
        if (isVideo)   return new MediaInfo(ParsedMessage.MEDIA_DOCUMENT, doc, doc, extOf(fileName, "mp4"), "video");
        if (isAnimated || "image/gif".equalsIgnoreCase(mime))
                       return new MediaInfo(ParsedMessage.MEDIA_DOCUMENT, doc, doc, "gif", "image");
        if (mime.startsWith("image/"))
                       return new MediaInfo(ParsedMessage.MEDIA_DOCUMENT, doc, doc, extOf(fileName, "jpg"), "image");
        return new MediaInfo(ParsedMessage.MEDIA_DOCUMENT, doc, doc, extOf(fileName, "bin"), "file");
    }
}