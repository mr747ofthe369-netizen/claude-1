package com.benknight.mwsl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EquityChartView extends View {
    private final Paint axis;
    private List<Snapshot> data;
    private String label;
    private final Paint line;
    private final Paint text;

    public EquityChartView(Context c) {
        super(c);
        this.data = new ArrayList();
        this.line = new Paint(1);
        this.axis = new Paint(1);
        this.text = new Paint(1);
        this.label = "Combined";
        init();
    }

    public EquityChartView(Context c, AttributeSet a) {
        super(c, a);
        this.data = new ArrayList();
        this.line = new Paint(1);
        this.axis = new Paint(1);
        this.text = new Paint(1);
        this.label = "Combined";
        init();
    }

    private void init() {
        this.line.setStyle(Paint.Style.STROKE);
        this.line.setStrokeWidth(dp(2.0f));
        this.line.setColor(-15374912);
        this.axis.setColor(-5592406);
        this.axis.setStrokeWidth(dp(1.0f));
        this.text.setColor(-13421773);
        this.text.setTextSize(dp(12.0f));
        setMinimumHeight((int) dp(220.0f));
    }

    void setSeries(String label, List<Snapshot> xs) {
        this.label = label;
        this.data = xs == null ? new ArrayList<>() : xs;
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas c) {
        EquityChartView equityChartView = this;
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        float l = equityChartView.dp(54.0f);
        float r = w - equityChartView.dp(12.0f);
        float t = equityChartView.dp(28.0f);
        float b = h - equityChartView.dp(30.0f);
        c.drawLine(l, b, r, b, equityChartView.axis);
        c.drawLine(l, t, l, b, equityChartView.axis);
        c.drawText(equityChartView.label + " equity", l, t - equityChartView.dp(8.0f), equityChartView.text);
        if (equityChartView.data.isEmpty()) {
            c.drawText("Waiting for first equity snapshot…", equityChartView.dp(12.0f) + l, (t + b) / 2.0f, equityChartView.text);
            return;
        }
        if (equityChartView.data.size() == 1) {
            double v = equityChartView.data.get(0).equity;
            float y = (t + b) / 2.0f;
            c.drawText(String.format(Locale.US, "$%.2f", Double.valueOf(v)), equityChartView.dp(2.0f), equityChartView.dp(4.0f) + y, equityChartView.text);
            c.drawLine(l, y, r, y, equityChartView.line);
            c.drawText("Starting equity — waiting for first change", equityChartView.dp(12.0f) + l, y - equityChartView.dp(12.0f), equityChartView.text);
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -1.7976931348623157E308d;
        for (Snapshot s : equityChartView.data) {
            min = Math.min(min, s.equity);
            max = Math.max(max, s.equity);
            w = w;
            h = h;
        }
        if (max - min < 1.0E-9d) {
            max += 1.0d;
            min -= 1.0d;
        }
        c.drawText(String.format(Locale.US, "$%.2f", Double.valueOf(max)), equityChartView.dp(2.0f), equityChartView.dp(4.0f) + t, equityChartView.text);
        c.drawText(String.format(Locale.US, "$%.2f", Double.valueOf(min)), equityChartView.dp(2.0f), b, equityChartView.text);
        Path p = new Path();
        int i = 0;
        while (i < equityChartView.data.size()) {
            float x = (((r - l) * i) / (equityChartView.data.size() - 1)) + l;
            Path p2 = p;
            double min2 = min;
            float y2 = (float) (b - (((equityChartView.data.get(i).equity - min) / (max - min)) * (b - t)));
            if (i == 0) {
                p = p2;
                p.moveTo(x, y2);
            } else {
                p = p2;
                p.lineTo(x, y2);
            }
            i++;
            equityChartView = this;
            min = min2;
        }
        c.drawPath(p, this.line);
    }

    private float dp(float x) {
        return getResources().getDisplayMetrics().density * x;
    }
}
