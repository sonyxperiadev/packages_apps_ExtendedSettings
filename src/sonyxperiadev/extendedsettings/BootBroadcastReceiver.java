package sonyxperiadev.extendedsettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This will be executed after the core system has been initialized,
 * along with providers. This means that any dependency on any system
 * app will not be necessarily satisfied and the app may crash if
 * relying on some.
 *
 * BEWARE!! DO NOT ABUSE of this receiver!
 * -- 07/10/2017 - AngeloGioacchino Del Regno <kholk11@gmail.com>
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
    static final String TAG = "ExtendedSettingsBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String sysPref = ExtendedSettingsActivity.getSystemProperty(
                    ExtendedSettingsActivity.PREF_DISPCAL_SETTING);
            if (sysPref == null || sysPref.length() < 1)
                sysPref = "0"; /* 0 = default calibration */

            ExtendedSettingsActivity.performDisplayCalibration(Integer.parseInt(sysPref));

        } catch (Throwable t) {
            Log.wtf(TAG, "We have crashed. THIS IS AN HORRENDOUS BUG!");
            Log.wtf(TAG, "Please report this error immediately by opening a new issue on GitHub.\n" +
                    "--- https://git.io/vduAF --- Thank you!", t);
        }
    }
}
