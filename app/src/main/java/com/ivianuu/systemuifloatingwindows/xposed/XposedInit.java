package com.ivianuu.systemuifloatingwindows.xposed;

import com.ivianuu.systemuifloatingwindows.util.PrefKeys;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed entry point
 */
public class XposedInit implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static final String THIS_PACKAGE = "com.ivianuu.systemuifloatingwindows";
    private static final String SYSTEM_UI = "com.android.systemui";

    private static XSharedPreferences prefs;

    static XSharedPreferences getPrefs() {
        prefs.reload();
        return prefs;
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        prefs = new XSharedPreferences(THIS_PACKAGE, PrefKeys.PREF_NAME);
        prefs.makeWorldReadable();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(SYSTEM_UI)) {
            SystemUiHooks.hook(lpparam);
        }
    }
}
