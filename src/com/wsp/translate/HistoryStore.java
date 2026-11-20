package com.wsp.translate;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 翻译历史与用量统计（本机 SQLite，不上传）。
 * 三条翻译链路（收信通知 / 悬浮读屏 / 草稿翻译）统一落一条 history 表：
 * 成功与失败都记录，既供「翻译历史」回看，也支撑「使用统计」的次数/字符/成功率聚合。
 * 只保留最近 MAX_ROWS 条，自动清理更旧记录，避免无限膨胀。
 */
public class HistoryStore {

    public static final String TYPE_INCOMING = "收信";
    public static final String TYPE_SCREEN   = "悬浮";
    public static final String TYPE_DRAFT    = "草稿";

    private static final int MAX_ROWS = 500;

    /** 一条历史记录。 */
    public static class Item {
        public final long ts;
        public final String type;
        public final String contact;
        public final String src;
        public final String dst;
        public final boolean ok;
        Item(long ts, String type, String contact, String src, String dst, boolean ok) {
            this.ts = ts; this.type = type; this.contact = contact;
            this.src = src; this.dst = dst; this.ok = ok;
        }
    }

    /** 用量统计聚合结果。 */
    public static class Stats {
        public long todayCount, todayChars;
        public long totalCount, totalChars, totalFail;
        /** 成功率（百分比整数，累计口径）；无记录时返回 100。 */
        public int successRate() {
            long total = totalCount + totalFail;
            return total == 0 ? 100 : (int) (totalCount * 100 / total);
        }
    }

    private static final Object LOCK = new Object();

    private static class Db extends SQLiteOpenHelper {
        Db(Context c) {
            super(c, "history.db", null, 1);
        }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "ts INTEGER, type TEXT, contact TEXT, src TEXT, dst TEXT, ok INTEGER)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) { }
    }

    /** 追加一条记录；超过上限时清理最旧数据。任何异常静默失败，不影响翻译主流程。 */
    public static void record(Context ctx, String type, String contact,
                              String src, String dst, boolean ok) {
        try {
            synchronized (LOCK) {
                Db h = new Db(ctx.getApplicationContext());
                SQLiteDatabase db = h.getWritableDatabase();
                ContentValues v = new ContentValues();
                v.put("ts", System.currentTimeMillis());
                v.put("type", type);
                v.put("contact", contact == null ? "" : contact);
                v.put("src", src == null ? "" : src);
                v.put("dst", dst == null ? "" : dst);
                v.put("ok", ok ? 1 : 0);
                db.insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                db.execSQL("DELETE FROM history WHERE id NOT IN "
                    + "(SELECT id FROM history ORDER BY id DESC LIMIT " + MAX_ROWS + ")");
                db.close();
                h.close();
            }
        } catch (Exception ignored) { }
    }

    /** 最近 limit 条记录（新→旧）。 */
    public static List<Item> recent(Context ctx, int limit) {
        List<Item> out = new ArrayList<>();
        try {
            synchronized (LOCK) {
                Db h = new Db(ctx.getApplicationContext());
                SQLiteDatabase db = h.getReadableDatabase();
                Cursor c = db.rawQuery(
                    "SELECT ts,type,contact,src,dst,ok FROM history ORDER BY id DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
                while (c.moveToNext()) {
                    out.add(new Item(c.getLong(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getInt(5) == 1));
                }
                c.close();
                db.close();
                h.close();
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 汇总统计：今日口径按本地 0 点起算，成功记录才计入字符数。 */
    public static Stats stats(Context ctx) {
        Stats s = new Stats();
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();
            synchronized (LOCK) {
                Db h = new Db(ctx.getApplicationContext());
                SQLiteDatabase db = h.getReadableDatabase();
                Cursor c = db.rawQuery(
                    "SELECT SUM(ok=1), SUM(ok=0), "
                        + "SUM(CASE WHEN ok=1 THEN LENGTH(src) ELSE 0 END), "
                        + "SUM(CASE WHEN ts>=? AND ok=1 THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN ts>=? AND ok=1 THEN LENGTH(src) ELSE 0 END) "
                        + "FROM history",
                    new String[]{String.valueOf(dayStart), String.valueOf(dayStart)});
                if (c.moveToFirst()) {
                    s.totalCount = c.getLong(0);
                    s.totalFail = c.getLong(1);
                    s.totalChars = c.getLong(2);
                    s.todayCount = c.getLong(3);
                    s.todayChars = c.getLong(4);
                }
                c.close();
                db.close();
                h.close();
            }
        } catch (Exception ignored) { }
        return s;
    }

    /** 清空全部历史。 */
    public static void clear(Context ctx) {
        try {
            synchronized (LOCK) {
                Db h = new Db(ctx.getApplicationContext());
                SQLiteDatabase db = h.getWritableDatabase();
                db.delete("history", null, null);
                db.close();
                h.close();
            }
        } catch (Exception ignored) { }
    }
}
