package com.ivianuu.systemuifloatingwindows;

import android.support.annotation.NonNull;

import de.robv.android.xposed.XposedBridge;

/**
 * Xposed logger
 */
final class XLogger {

    /**
     * Logs the message
     */
    static void log(@NonNull String message, @NonNull Object... args) {
        XposedBridge.log(String.format(message, args));
    }

    /**
     * Logs the error
     */
    static void throwable(@NonNull Throwable throwable) {
        XposedBridge.log(throwable);
    }
}
