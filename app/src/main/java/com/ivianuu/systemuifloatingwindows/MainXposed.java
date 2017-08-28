package com.ivianuu.systemuifloatingwindows;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed entry point
 */
public class MainXposed implements IXposedHookLoadPackage {

    private static final String SYSTEM_UI = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(SYSTEM_UI)) {
            SystemUiHooks.hook(lpparam);
        }
    }
}
