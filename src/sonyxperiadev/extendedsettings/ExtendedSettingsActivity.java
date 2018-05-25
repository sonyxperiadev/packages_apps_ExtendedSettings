package sonyxperiadev.extendedsettings;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ExtendedSettingsActivity extends PreferenceActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new ExtendedSettingsFragment()).commit();
    }
}
