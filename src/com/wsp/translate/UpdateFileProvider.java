package com.wsp.translate;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 更新包只读内容提供器（零依赖替代 androidx FileProvider）。
 *
 * 用途：Android 7.0 起 file:// URI 不能直接传给系统安装器，需经 content:// +
 * FLAG_GRANT_READ_URI_PERMISSION 授予临时读权限。
 *
 * 安全约束：
 * - exported=false：仅本应用显式 grant 的组件可读；
 * - 仅暴露 filesDir/update 目录，文件名取自 URI 最后一段，不允许路径穿越。
 */
public class UpdateFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("\\")
            || name.contains("..")) {
            throw new FileNotFoundException("非法文件名");
        }
        File f = new File(new File(getContext().getFilesDir(), "update"), name);
        if (!f.isFile()) throw new FileNotFoundException("更新包不存在");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) { return null; }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
