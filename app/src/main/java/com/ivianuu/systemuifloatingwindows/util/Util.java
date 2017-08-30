package com.ivianuu.systemuifloatingwindows.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;

/**
 * Utils
 */
public final class Util {

    /**
     * Converts dp to pixels.
     */
    public static int convertDpToPixel(@NonNull Context context, int dp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float px = dp * ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return (int) px;
    }

}
