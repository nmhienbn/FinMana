package com.finmana.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.finmana.app.data.ChartPoint;

import java.util.ArrayList;
import java.util.List;

public class BarChartView extends View {
    private static final int COLOR_INCOME = Color.parseColor("#167D5B");
    private static final int COLOR_EXPENSE = Color.parseColor("#C54848");
    private List<ChartPoint> points = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        textPaint.setColor(COLOR_INCOME);
        textPaint.setTextSize(28f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        labelPaint.setColor(Color.DKGRAY);
        labelPaint.setTextSize(30f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPoints(List<ChartPoint> points) {
        this.points = points != null ? points : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) return;

        float w = getWidth();
        float h = getHeight() - 40f;
        long max = 1;
        for (ChartPoint p : points) {
            max = Math.max(max, Math.max(p.income, p.expense));
        }

        float groupWidth = w / points.size();
        float barWidth = groupWidth * 0.28f;

        for (int i = 0; i < points.size(); i++) {
            ChartPoint p = points.get(i);
            float x = i * groupWidth + groupWidth * 0.18f;

            float incomeHeight = h * p.income / max;
            float expenseHeight = h * p.expense / max;

            barPaint.setColor(COLOR_INCOME);
            canvas.drawRect(x, h - incomeHeight, x + barWidth, h, barPaint);
            barPaint.setColor(COLOR_EXPENSE);
            canvas.drawRect(x + barWidth, h - expenseHeight, x + barWidth * 2, h, barPaint);

            labelPaint.setColor(Color.DKGRAY);
            canvas.drawText(p.label, x + barWidth, h + 30f, labelPaint);
        }
    }
}
