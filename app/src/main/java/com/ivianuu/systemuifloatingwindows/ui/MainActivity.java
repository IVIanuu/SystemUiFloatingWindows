package com.ivianuu.systemuifloatingwindows.ui;

import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;

import com.ivianuu.systemuifloatingwindows.R;
import com.ivianuu.systemuifloatingwindows.util.PrefKeys;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PreferencesFragment()).commit();
        }
    }

    public static class PreferencesFragment extends PreferenceFragment {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName(PrefKeys.PREF_NAME);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}
