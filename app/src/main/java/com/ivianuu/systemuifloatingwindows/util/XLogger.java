package com.ivianuu.systemuifloatingwindows.util;

import android.support.annotation.NonNull;

import de.robv.android.xposed.XposedBridge;

/**
 * Xposed logger
 */
public final class XLogger {

    /**
     * Logs the message
     */
    public static void log(@NonNull String message, @NonNull Object... args) {
        XposedBridge.log(String.format(message, args));
    }

    /**
     * Logs the error
     */
    public static void throwable(@NonNull Throwable throwable) {
        XposedBridge.log(throwable);
    }
}
