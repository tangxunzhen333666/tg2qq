package com.operit.tg2qq.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.util.Map;

/* JADX INFO: loaded from: classes2.dex */
public class ConfigProvider extends ContentProvider {
    private static final String[] COLS = {"_key", "_value"};

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor c = new MatrixCursor(COLS);
        try {
            SharedPreferences sp = getContext().getSharedPreferences("tg_forward", 0);
            Map<String, ?> all = sp.getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                c.addRow(new Object[]{e.getKey(), String.valueOf(e.getValue())});
            }
        } catch (Throwable th) {
        }
        return c;
    }

    @Override // android.content.ContentProvider
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.tg2qq.config";
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override // android.content.ContentProvider
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override // android.content.ContentProvider
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
