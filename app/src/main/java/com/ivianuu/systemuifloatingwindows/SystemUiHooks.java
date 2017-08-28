package com.ivianuu.systemuifloatingwindows;

import android.annotation.SuppressLint;
import android.app.Activity;
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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static com.ivianuu.systemuifloatingwindows.XLogger.log;
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
    private static final String FIXED_SIZE_IMAGE_VIEW = "com.android.systemui.recents.views.FixedSizeImageView";
    private static final String INTERPOLATORS = "com.android.systemui.Interpolators";
    private static final String NOTIFICATION_CLICKER = "com.android.systemui.statusbar.BaseStatusBar$NotificationClicker";
    private static final String NOTIFICATION_CLICKER_RUNNABLE = "com.android.systemui.statusbar.BaseStatusBar$NotificationClicker$1";
    private static final String NOTIFICATION_LONG_CLICKER = "com.android.systemui.statusbar.BaseStatusBar$13";
    private static final String QS_FOOTER = "com.android.systemui.qs.QSFooter";
    private static final String QS_TILE = "com.android.systemui.qs.QSTile";
    private static final String TASK_VIEW_HEADER = "com.android.systemui.recents.views.TaskViewHeader";

    private static Object baseStatusBar;
    @SuppressLint("StaticFieldLeak")
    private static View clickedView;

    /**
     * Hooks the system ui
     */
    static void hook(@NonNull XC_LoadPackage.LoadPackageParam lpparam) {
        // heads up clicks
        hookHeadsUpClick(lpparam);

        // notification long click
        hookNotificationLongClick(lpparam);

        // quick settings
        hookQuickSettings(lpparam);

        // recents
        hookRecents(lpparam);

        // settings click
        hookSettingsClick(lpparam);
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
                        log("opening floating window from heads up");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private static void hookNotificationLongClick(final XC_LoadPackage.LoadPackageParam lpparam) {
        // get the class
        Class<?> notificationLongClickerClass
                = findClass(NOTIFICATION_LONG_CLICKER, lpparam.classLoader);

        // hook long press
        // this will called on long press obviously
        hookAllMethods(notificationLongClickerClass, "onLongPress", new XC_MethodReplacement() {
            @SuppressLint("WrongConstant")
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
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
                        return true;
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                }

                return false;
            }
        });
    }

    private static void hookQuickSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> qsTileClass = findClass(QS_TILE, lpparam.classLoader);

        hookAllMethods(qsTileClass, "handleLongClick", new XC_MethodReplacement() {
            @SuppressLint("WrongConstant")
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent
                        = (Intent) callMethod(param.thisObject, "getLongClickIntent");

                intent.addFlags(Flags.FLAG_FLOATING_WINDOW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                Object host = getObjectField(param.thisObject, "mHost");

                Context context = (Context) getObjectField(host, "mContext");

                context.startActivity(intent);
                context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

                return null;
            }
        });
    }

    private static final int FLOATING_BUTTON_ID = 1;
    private static void hookRecents(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> taskViewHeaderClass = findClass(TASK_VIEW_HEADER, lpparam.classLoader);

        final Class<?> fixedSizeImageViewClass = findClass(FIXED_SIZE_IMAGE_VIEW, lpparam.classLoader);

        hookAllMethods(taskViewHeaderClass, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                Context context
                        = (Context) callMethod(param.thisObject, "getContext");

                ImageView floatingButton
                        = (ImageView) newInstance(fixedSizeImageViewClass, context);
                floatingButton.setId(FLOATING_BUTTON_ID);
                int padding = Util.convertDpToPixel(context, 12);
                floatingButton.setPadding(padding, padding, padding, padding);

                TypedValue outValue = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                floatingButton.setBackgroundResource(outValue.resourceId);

                Context packageContext
                        = context.createPackageContext("com.ivianuu.systemuifloatingwindows", 0);
                floatingButton.setImageDrawable(packageContext.getDrawable(R.drawable.ic_recents_floating_light));

                int size = Util.convertDpToPixel(context, 48);
                FrameLayout.LayoutParams lp
                        = new FrameLayout.LayoutParams(size, size);

                lp.setMarginEnd(size);
                lp.gravity = Gravity.END | Gravity.CENTER;

                floatingButton.setVisibility(View.INVISIBLE);

                final FrameLayout thisObject = (FrameLayout) param.thisObject;
                thisObject.addView(floatingButton, lp);

                floatingButton.setOnClickListener(new View.OnClickListener() {
                    @SuppressLint("WrongConstant")
                    @Override
                    public void onClick(View view) {
                        // get intent
                        Object task = getObjectField(thisObject, "mTask");
                        Object key = getObjectField(task, "key");
                        final Intent baseIntent = (Intent) getObjectField(key, "baseIntent");

                        // dismiss recents
                        final Activity recentsActivity = (Activity) thisObject.getContext();
                        recentsActivity.finish();

                        thisObject.post(new Runnable() {
                            @Override
                            public void run() {
                                // Launch task in floating mode
                                baseIntent.setFlags(Flags.FLAG_FLOATING_WINDOW);
                                recentsActivity.startActivity(baseIntent);
                                log("launch floating from recents");
                            }
                        });
                    }
                });
            }
        });

        hookAllMethods(taskViewHeaderClass, "bindToTask", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                boolean useLight
                        = (boolean) getObjectField(param.args[0], "useLightOnPrimaryColor");
                Context context
                        = (Context) getObjectField(param.thisObject, "mContext");
                FrameLayout thisObject
                        = (FrameLayout) param.thisObject;
                ImageView floatingButton = thisObject.findViewById(FLOATING_BUTTON_ID);

                Context packageContext
                        = context.createPackageContext("com.ivianuu.systemuifloatingwindows", 0);

                Drawable drawable =
                        packageContext.getDrawable(
                                useLight ? R.drawable.ic_recents_floating_light : R.drawable.ic_recents_floating_dark);

                floatingButton.setImageDrawable(drawable);
            }
        });

        final Class<?> interpolatorsClass = findClass(INTERPOLATORS, lpparam.classLoader);

        hookAllMethods(taskViewHeaderClass, "startNoUserInteractionAnimation", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                FrameLayout thisObject
                        = (FrameLayout) param.thisObject;
                ImageView floatingButton = thisObject.findViewById(FLOATING_BUTTON_ID);
                floatingButton.setVisibility(View.VISIBLE);
                floatingButton.setClickable(true);
                if (floatingButton.getVisibility() == View.VISIBLE) {
                    int id = thisObject.getResources().getIdentifier(
                            "recents_task_enter_from_app_duration", "integer", "com.android.systemui");
                    int duration = thisObject.getResources().getInteger(id);

                    Interpolator interpolator
                            = (Interpolator) getStaticObjectField(interpolatorsClass, "FAST_OUT_LINEAR_IN");

                    floatingButton.animate().cancel();
                    floatingButton.animate()
                            .alpha(1f)
                            .setDuration(duration)
                            .setInterpolator(interpolator)
                            .start();
                } else {
                    floatingButton.setAlpha(1f);
                }
            }
        });

        hookAllMethods(taskViewHeaderClass, "setNoUserInteractionState", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                FrameLayout thisObject
                        = (FrameLayout) param.thisObject;
                ImageView floatingButton = thisObject.findViewById(FLOATING_BUTTON_ID);
                floatingButton.setVisibility(View.VISIBLE);
                floatingButton.animate().cancel();
                floatingButton.setAlpha(1f);
                floatingButton.setClickable(true);
            }
        });

        hookAllMethods(taskViewHeaderClass, "resetNoUserInteractionState", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                FrameLayout thisObject
                        = (FrameLayout) param.thisObject;
                ImageView floatingButton = thisObject.findViewById(FLOATING_BUTTON_ID);
                floatingButton.setVisibility(View.INVISIBLE);
                floatingButton.setAlpha(0f);
                floatingButton.setClickable(false);
            }
        });
    }

    private static void hookSettingsClick(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> qsFooterClass = findClass(QS_FOOTER, lpparam.classLoader);

        hookAllMethods(qsFooterClass, "startSettingsActivity", new XC_MethodReplacement() {
            @SuppressLint("WrongConstant")
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                // create intent
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Flags.FLAG_FLOATING_WINDOW);

                // start floating
                Object activityStarter = getObjectField(param.thisObject, "mActivityStarter");
                callMethod(activityStarter, "startActivity", intent, true);

                log("launch settings activity floating");
                return null;
            }
        });
    }
}
