package com.ivianuu.systemuifloatingwindows;

import android.app.Application;

import com.ivianuu.systemuifloatingwindows.util.PrefKeys;
import com.ivianuu.worldreadableprefs.WorldReadablePrefsFix;

/**
 * App
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // fix preference r/w access
        WorldReadablePrefsFix.builder(this)
                .fix(PrefKeys.PREF_NAME)
                .start();
    }
}
