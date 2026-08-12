package com.operit.tg2qq.core;

/* JADX INFO: loaded from: classes2.dex */
public class ParsedMessage {
    public static final int MEDIA_CONTACT = 5;
    public static final int MEDIA_DOCUMENT = 2;
    public static final int MEDIA_GEO = 4;
    public static final int MEDIA_PHOTO = 1;
    public static final int MEDIA_POLL = 6;
    public static final int MEDIA_WEBPAGE = 3;
    public int date;
    public long dialogId;
    public long id;
    public Object media;
    public MediaInfo mediaInfo;
    public boolean out;
    public String text;

    public static class MediaInfo {
        public String ext;
        public int kind;
        public Object location;
        public Object parent;
        public Object photoSize;
        public String qqType;
        public String textFallback;
        public String title;
        public String url;

        public MediaInfo(int kind, Object location, Object parent, String ext, String qqType) {
            this.kind = kind;
            this.location = location;
            this.parent = parent;
            this.ext = ext;
            this.qqType = qqType;
        }

        public MediaInfo(int kind, String textFallback, String url, String title) {
            this.kind = kind;
            this.textFallback = textFallback;
            this.url = url;
            this.title = title;
            this.qqType = "text";
        }

        public boolean isTextual() {
            return "text".equals(this.qqType);
        }
    }
}
