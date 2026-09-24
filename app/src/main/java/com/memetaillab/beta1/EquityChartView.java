package com.memetaillab.beta1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class EquityChartView extends View {
    private final Paint axis;
    private final Paint fill;
    private final Paint grid;
    private String label;
    private final Paint line;
    private List<double[]> pts;

    EquityChartView(Context c) {
        super(c);
        this.line = new Paint(1);
        this.axis = new Paint(1);
        this.grid = new Paint(1);
        this.fill = new Paint(1);
        this.pts = new ArrayList();
        this.label = "";
        setLayerType(1, null);
        this.line.setStrokeWidth(5.0f);
        this.line.setStrokeCap(Paint.Cap.ROUND);
        this.line.setStrokeJoin(Paint.Join.ROUND);
        this.line.setStyle(Paint.Style.STROKE);
        this.line.setColor(Color.rgb(46, 242, 161));
        this.axis.setColor(Color.rgb(143, 160, 153));
        this.axis.setTextSize(24.0f);
        this.grid.setColor(Color.rgb(32, 53, 45));
        this.grid.setStrokeWidth(1.0f);
        this.fill.setStyle(Paint.Style.FILL);
    }

    void setData(String l, List<double[]> x) {
        this.label = l;
        this.pts = x == null ? new ArrayList<>() : x;
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(0);
        float w = getWidth();
        float h = getHeight();
        float left = 12.0f;
        float right = w - 12.0f;
        float bottom = h - 28.0f;
        int i = 1;
        this.axis.setTypeface(Typeface.create(Typeface.DEFAULT, 1));
        c.drawText(this.label, 14.0f, 24.0f, this.axis);
        for (int i2 = 1; i2 < 4; i2++) {
            float y = 34.0f + (((bottom - 34.0f) * i2) / 4.0f);
            c.drawLine(12.0f, y, right, y, this.grid);
        }
        if (this.pts.size() < 2) {
            this.axis.setTypeface(Typeface.DEFAULT);
            c.drawText("Live equity will appear after market marks.", 14.0f, h / 2.0f, this.axis);
            return;
        }
        double mn = Double.MAX_VALUE;
        double mx = -1.7976931348623157E308d;
        for (double[] x : this.pts) {
            mn = Math.min(mn, x[1]);
            mx = Math.max(mx, x[1]);
            w = w;
        }
        if (mx <= mn) {
            mx = mn + 1.0d;
        }
        Path p = new Path();
        Path area = new Path();
        int i3 = 0;
        while (i3 < this.pts.size()) {
            float x2 = (((right - left) * i3) / (this.pts.size() - i)) + left;
            float h2 = h;
            float left2 = left;
            float y2 = (float) (bottom - (((this.pts.get(i3)[1] - mn) / (mx - mn)) * (bottom - 34.0f)));
            if (i3 == 0) {
                p.moveTo(x2, y2);
                area.moveTo(x2, bottom);
            } else {
                p.lineTo(x2, y2);
            }
            area.lineTo(x2, y2);
            i3++;
            i = 1;
            h = h2;
            left = left2;
        }
        area.lineTo(right, bottom);
        area.close();
        LinearGradient g = new LinearGradient(0.0f, 34.0f, 0.0f, bottom, Color.argb(85, 46, 242, 161), Color.argb(0, 46, 242, 161), Shader.TileMode.CLAMP);
        this.fill.setShader(g);
        c.drawPath(area, this.fill);
        this.fill.setShader(null);
        this.line.setShadowLayer(10.0f, 0.0f, 0.0f, Color.argb(120, 46, 242, 161));
        c.drawPath(p, this.line);
        this.line.clearShadowLayer();
        this.axis.setTypeface(Typeface.DEFAULT);
        this.axis.setTextSize(21.0f);
        c.drawText(String.format(Locale.US, "$%.0f", Double.valueOf(mx)), 14.0f, 34.0f + 18.0f, this.axis);
        c.drawText(String.format(Locale.US, "$%.0f", Double.valueOf(mn)), 14.0f, bottom, this.axis);
    }
}
