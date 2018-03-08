package sonyxperiadev.extendedsettings;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.os.SystemProperties;
import android.util.Log;

import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
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
            DevicePolicyManager polMan = (DevicePolicyManager)
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            UserManager userMan = UserManager.get(context);
            int encryptionStatus = polMan.getStorageEncryptionStatus();

            /* If the device is being encrypted, do NOT touch ANYTHING */
            if (encryptionStatus == ENCRYPTION_STATUS_ACTIVATING)
                return;

            /*
             * If we are at a relatively early Android boot stage, check if the
             * device is encrypted. If it is, bail out, as we cannot read system
             * properties right now (and we cannot write to sysfs).
             */
            if (!userMan.isUserUnlocked() &&
                    encryptionStatus != ENCRYPTION_STATUS_INACTIVE &&
                    encryptionStatus != ENCRYPTION_STATUS_UNSUPPORTED)
                return;

            String sysPref = SystemProperties.get(ExtendedSettingsActivity.PREF_DISPCAL_SETTING);
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
