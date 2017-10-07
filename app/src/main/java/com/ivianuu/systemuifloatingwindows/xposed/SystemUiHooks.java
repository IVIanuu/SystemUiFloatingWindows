package com.ivianuu.systemuifloatingwindows.xposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.ivianuu.systemuifloatingwindows.R;
import com.ivianuu.systemuifloatingwindows.util.Flags;
import com.ivianuu.systemuifloatingwindows.util.PrefKeys;
import com.ivianuu.systemuifloatingwindows.util.Util;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static com.ivianuu.systemuifloatingwindows.util.XLogger.log;
import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;

/**
 * System ui hooks
 */
final class SystemUiHooks {

    private static final String BASE_STATUS_BAR = "com.android.systemui.statusbar.BaseStatusBar";
    private static final String NOTIFICATION_CLICKER = "com.android.systemui.statusbar.BaseStatusBar$NotificationClicker";
    private static final String NOTIFICATION_CLICKER_RUNNABLE = "com.android.systemui.statusbar.BaseStatusBar$NotificationClicker$1";
    private static final String NOTIFICATION_LONG_CLICKER = "com.android.systemui.statusbar.BaseStatusBar$13";
    private static final String QS_TILE = "com.android.systemui.qs.QSTile";

    private static Object baseStatusBar;
    @SuppressLint("StaticFieldLeak")
    private static View clickedView;

    /**
     * Hooks the system ui
     */
    static void hook(@NonNull XC_LoadPackage.LoadPackageParam lpparam) {
        // heads up clicks
        hookHeadsUpClick(lpparam);

        // notification click
        hookNotificationClick(lpparam);

        // notification long click
        hookNotificationLongClick(lpparam);

        // quick settings
        hookQuickSettings(lpparam);
    }

    private static void hookHeadsUpClick(final XC_LoadPackage.LoadPackageParam lpparam) {
        final Class<?> baseStatusBarClass = findClass(BASE_STATUS_BAR, lpparam.classLoader);

        // we need a base status bar reference
        hookAllConstructors(baseStatusBarClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                baseStatusBar = param.thisObject;
            }
        });

        Class<?> notificationClickerClass = findClass(NOTIFICATION_CLICKER, lpparam.classLoader);

        // we need the reference of the clicked view
        hookAllMethods(notificationClickerClass, "onClick", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                clickedView = (View) param.args[0];
            }
        });

        Class<?> notificationClickerRunnableClass = findClass(NOTIFICATION_CLICKER_RUNNABLE, lpparam.classLoader);

        hookAllMethods(notificationClickerRunnableClass, "run", new XC_MethodHook() {
            @SuppressLint("WrongConstant")
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!XposedInit.getPrefs().getBoolean(PrefKeys.HEADS_UP_CLICK, false)) {
                    log("heads up click disabled");
                    return;
                }

                log("heads up click enabled");

                Object headsUpManager = getObjectField(baseStatusBar, "mHeadsUpManager");
                // check if the heads up notification is clicked
                if ((Boolean) callMethod(
                        headsUpManager, "isClickedHeadsUpNotification", clickedView)) {
                    // get sbn
                    final StatusBarNotification sbn
                            = (StatusBarNotification) callMethod(clickedView, "getStatusBarNotification");

                    PendingIntent contentIntent = sbn.getNotification().contentIntent;

                    Context context
                            = (Context) getObjectField(baseStatusBar, "mContext");

                    // add overlay
                    Intent overlay = new Intent();
                    overlay.addFlags(Flags.FLAG_FLOATING_WINDOW);

                    // open notification
                    try {
                        contentIntent.send(context, 0, overlay);
                        param.setResult(null);
                        log("opening floating window from heads up");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private static void hookNotificationClick(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> baseStatusBarClass = findClass(BASE_STATUS_BAR, lpparam.classLoader);

        hookAllMethods(baseStatusBarClass, "inflateViews", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                final Object entry = param.args[0];
                final View row
                        = (View) getObjectField(entry, "row");

                row.setOnClickListener(new View.OnClickListener() {
                    @SuppressLint("WrongConstant")
                    @Override
                    public void onClick(View view) {
                        Object keyguardManager
                                = getObjectField(param.thisObject, "mStatusBarKeyguardViewManager");
                        boolean keyguardShowing
                                = (boolean) callMethod(keyguardManager, "isShowing");
                        if (XposedInit.getPrefs().getBoolean(PrefKeys.NOTIFICATION_CLICK, false)
                                && !keyguardShowing) {
                            StatusBarNotification sbn
                                    = (StatusBarNotification) getObjectField(entry, "notification");
                            Notification notification = sbn.getNotification();
                            PendingIntent contentIntent = notification.contentIntent;
                            if (contentIntent == null) {
                                return;
                            }

                            // add overlay
                            Intent overlay = new Intent();
                            overlay.addFlags(Flags.FLAG_FLOATING_WINDOW);

                            // open notification
                            try {
                                contentIntent.send(view.getContext(), 0, overlay);
                                param.setResult(null);
                                log("opening floating window from notification click");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            // close system dialogs
                            view.getContext().sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
                        } else {
                            // otherwise let the notification clicker handle the click
                            View.OnClickListener notificationClicker
                                    = (View.OnClickListener) getObjectField(param.thisObject, "mNotificationClicker");
                            notificationClicker.onClick(view);
                        }
                    }
                });
            }
        });
    }

    private static void hookNotificationLongClick(final XC_LoadPackage.LoadPackageParam lpparam) {
        // get the class
        Class<?> notificationLongClickerClass
                = findClass(NOTIFICATION_LONG_CLICKER, lpparam.classLoader);

        // hook long press
        // this will called on long press obviously
        hookAllMethods(notificationLongClickerClass, "onLongPress", new XC_MethodHook() {
            @SuppressLint("WrongConstant")
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!XposedInit.getPrefs().getBoolean(PrefKeys.NOTIFICATION_LONG_CLICK, false)) {
                    // not enabled
                    return;
                }

                // the view is the expandable notification row
                View expandableNotificationRow = (View) param.args[0];

                // which has a reference to the notification
                StatusBarNotification sbn
                        = (StatusBarNotification) getObjectField(
                        expandableNotificationRow, "mStatusBarNotification");

                // check if intent is present
                PendingIntent contentIntent = sbn.getNotification().contentIntent;
                if (contentIntent != null) {
                    // open the notification in floating window
                    Intent overlay = new Intent();
                    overlay.addFlags(Flags.FLAG_FLOATING_WINDOW);
                    try {
                        contentIntent.send(expandableNotificationRow.getContext(), 0, overlay);

                        // close system dialogs
                        expandableNotificationRow.getContext().sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

                        log("opening floating window from notification long click");
                        param.setResult(true);
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }

                param.setResult(false);
            }
        });
    }

    private static void hookQuickSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> qsTileClass = findClass(QS_TILE, lpparam.classLoader);

        hookAllMethods(qsTileClass, "handleLongClick", new XC_MethodHook() {
            @SuppressLint("WrongConstant")
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!XposedInit.getPrefs().getBoolean(PrefKeys.QUICK_SETTINGS_LONG_CLICK, false)) {
                    log("quick settings long click disabled");
                    return;
                }

                log("quick settings long click enabled");

                Intent intent
                        = (Intent) callMethod(param.thisObject, "getLongClickIntent");

                intent.addFlags(Flags.FLAG_FLOATING_WINDOW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                Object host = getObjectField(param.thisObject, "mHost");

                Context context = (Context) getObjectField(host, "mContext");

                context.startActivity(intent);
                context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

                param.setResult(null);
            }
        });
    }

}
