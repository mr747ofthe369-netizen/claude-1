package com.memetaillab.beta1;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class CsvExporter {
    static String export(Context c, Db db) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        StringBuilder result = new StringBuilder();
        result.append(write(c, "MemeTailBeta1_ledger_" + stamp + ".csv", queryCsv(db, "SELECT * FROM ledger ORDER BY id"))).append("\n");
        result.append(write(c, "MemeTailBeta1_positions_" + stamp + ".csv", queryCsv(db, "SELECT * FROM position ORDER BY id"))).append("\n");
        result.append(write(c, "MemeTailBeta1_signals_" + stamp + ".csv", queryCsv(db, "SELECT * FROM signal ORDER BY id"))).append("\n");
        result.append(write(c, "MemeTailBeta1_equity_" + stamp + ".csv", queryCsv(db, "SELECT * FROM snapshot ORDER BY id")));
        return result.toString();
    }

    private static String queryCsv(Db db, String sql) {
        StringBuilder b = new StringBuilder();
        Cursor c = db.getReadableDatabase().rawQuery(sql, null);
        for (int i = 0; i < c.getColumnCount(); i++) {
            try {
                if (i > 0) {
                    b.append(',');
                }
                b.append(q(c.getColumnName(i)));
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        b.append('\n');
        while (c.moveToNext()) {
            for (int i2 = 0; i2 < c.getColumnCount(); i2++) {
                if (i2 > 0) {
                    b.append(',');
                }
                b.append(q(c.isNull(i2) ? "" : c.getString(i2)));
            }
            b.append('\n');
        }
        if (c != null) {
            c.close();
        }
        return b.toString();
    }

    private static String write(Context c, String name, String body) throws Exception {
        ContentValues v = new ContentValues();
        v.put("_display_name", name);
        v.put("mime_type", "text/csv");
        v.put("relative_path", Environment.DIRECTORY_DOWNLOADS + "/MemeTailBeta1");
        Uri u = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) {
            throw new Exception("MediaStore insert failed");
        }
        OutputStream o = c.getContentResolver().openOutputStream(u);
        try {
            if (o == null) {
                throw new Exception("Output unavailable");
            }
            o.write(body.getBytes(StandardCharsets.UTF_8));
            if (o != null) {
                o.close();
            }
            return name;
        } catch (Throwable th) {
            if (o != null) {
                try {
                    o.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static String q(String s) {
        if (s == null) {
            s = "";
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private CsvExporter() {
    }
}
