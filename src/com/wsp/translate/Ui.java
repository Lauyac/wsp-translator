package com.wsp.translate;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/**
 * 跨界面共享的绘制工具（MainActivity 与 FloatService 的公共部分）。
 * 既有两处实例方法保持原签名，内部委托到这里，避免一次性大改引入回归。
 */
public final class Ui {

    private Ui() { }

    /** dp → px（四舍五入）。 */
    public static int dp(Context c, float v) {
        return (int) (c.getResources().getDisplayMetrics().density * v + 0.5f);
    }

    /** 纯色圆角矩形，可选 1dp 描边（stroke > 0 时生效）。 */
    public static GradientDrawable fill(Context c, int color, float radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (stroke > 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** 纯色圆角矩形 + 1dp 描边。 */
    public static GradientDrawable strokeRect(Context c, int color, int strokeColor, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        g.setStroke(dp(c, 1), strokeColor);
        return g;
    }
}
