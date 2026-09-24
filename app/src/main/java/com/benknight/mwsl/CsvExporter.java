package com.benknight.mwsl;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

class CsvExporter {
    CsvExporter() {
    }

    static String export(Context context, Db db) throws Exception {
        String name = "mwsl_trade_ledger_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        ContentResolver cr = context.getContentResolver();
        ContentValues v = new ContentValues();
        v.put("_display_name", name);
        v.put("mime_type", "text/csv");
        v.put("relative_path", Environment.DIRECTORY_DOWNLOADS + "/MemeWalletShadowLab");
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) {
            throw new Exception("Could not create Downloads file");
        }
        try (OutputStream os = cr.openOutputStream(uri);
             Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Cursor c = db.exportCursor()) {
            String[] cols = c.getColumnNames();
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) {
                    w.write(44);
                }
                w.write(csv(cols[i]));
            }
            w.write(10);
            while (c.moveToNext()) {
                for (int i2 = 0; i2 < cols.length; i2++) {
                    if (i2 > 0) {
                        w.write(44);
                    }
                    w.write(csv(c.isNull(i2) ? "" : c.getString(i2)));
                }
                w.write(10);
            }
            w.flush();
        }
        return name;
    }

    private static String csv(String s) {
        return s == null ? "" : '"' + s.replace("\"", "\"\"") + '"';
    }
}
