package sonyxperiadev.extendedsettings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;
import android.support.v14.preference.PreferenceFragment;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;


/**
 * A {@link PreferenceFragment} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class ExtendedSettingsFragment extends PreferenceFragment {

    private static final String TAG = "ExtendedSettings";

    private static final String[] SYSFS_GLOVE_MODE_PATHS =
            new String[]{"lge_touch/glove_mode", "clearpad/glove"};
    private static final String SYSFS_TOUCH_GLOVE_MODE = "/sys/devices/virtual/input/%s";

    static final String PREF_ADB_NETWORK_COM = "vendor.adb.network.port.es";
    private static final String PREF_ADB_NETWORK_READ = "service.adb.tcp.port";
    private static final String mADBOverNetworkSwitchPref = "adbon_switch";
    static final String mGloveModeSwitchPref = "glove_mode_switch";

    private static FragmentManager mFragmentManager;
    static PreferenceFragment mFragment;
    private SharedPreferences.Editor mPrefEditor;
    private UserManager mUserManager;


    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private Preference.OnPreferenceChangeListener mPreferenceListener =
            (preference, value) -> {
                switch (preference.getKey()) {
                    case mADBOverNetworkSwitchPref:
                        if ((Boolean) value) {
                            confirmEnablingADBON();
                        } else {
                            SystemProperties.set(PREF_ADB_NETWORK_COM, "-1");
                            updateADBSummary(false);
                        }
                        break;
                    case mGloveModeSwitchPref:
                        // This function decides whether to update the state of the preference:
                        return onGloveModePreferenceChanged((Boolean) value);
                    default:
                        break;
                }
                return true;
            };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setupActionBar();
        mFragment = this;
        addPreferencesFromResource(R.xml.pref_general);

        findPreference(mADBOverNetworkSwitchPref).setOnPreferenceChangeListener(
                mPreferenceListener);
        mFragmentManager = getFragmentManager();
        mPrefEditor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

        final SwitchPreference gloveModeSwitchPref = (SwitchPreference) findPreference(
                mGloveModeSwitchPref);
        if (hasGloveMode()) {
            gloveModeSwitchPref.setOnPreferenceChangeListener(mPreferenceListener);
        } else {
            getPreferenceScreen().removePreference(gloveModeSwitchPref);
        }

        mUserManager = mFragment.getContext().getSystemService(UserManager.class);
        if (mUserManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)) {
            SystemProperties.set(PREF_ADB_NETWORK_COM, "-1");
            getPreferenceScreen().removePreference(findPreference(mADBOverNetworkSwitchPref));
        } else {
            boolean adbNB = SystemProperties.getInt(PREF_ADB_NETWORK_READ, 0) > 0;
            updateADBSummary(adbNB);
        }
        mPrefEditor.apply();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private static void confirmEnablingADBON() {
        DialogFragment newFragment = new EnableADBONDialog();
        newFragment.show(mFragmentManager, "adb");
    }

    protected static void updateADBSummary(boolean enabled) {
        SwitchPreference mAdbOverNetwork =
                (SwitchPreference) ExtendedSettingsFragment.mFragment.findPreference(
                        mADBOverNetworkSwitchPref);

        if (enabled) {
            WifiManager wifiManager = mFragment.getContext().getSystemService(WifiManager.class);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

            // Convert little-endian to big-endian if needed
            if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                ipAddress = Integer.reverseBytes(ipAddress);
            }

            byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

            String ipAddressString;
            try {
                ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
            } catch (UnknownHostException ex) {
                Log.e("WIFIIP", "Unable to get host address.");
                ipAddressString = null;
            }
            if (ipAddressString != null) {
                mAdbOverNetwork.setSummary(
                        ipAddressString + ":" + SystemProperties.getInt(PREF_ADB_NETWORK_READ, 0));
                // Set the switch state accordingly to the Preference
                mAdbOverNetwork.setChecked(true);
            } else {
                mAdbOverNetwork.setSummary(R.string.error_connect_to_wifi);
                SystemProperties.set(PREF_ADB_NETWORK_COM, "-1");
                // Set the switch state accordingly to the Preference
                mAdbOverNetwork.setChecked(false);
            }
        } else {
            mAdbOverNetwork.setSummary(R.string.pref_description_adbonswitch);
            // Set the switch state accordingly to the Preference
            mAdbOverNetwork.setChecked(false);
        }
    }

    private boolean onGloveModePreferenceChanged(boolean enabled) {
        final int message_text_id =
                enabled ? R.string.dialog_enable_glove_mode : R.string.dialog_disable_glove_mode;
        final SwitchPreference gloveModePref = (SwitchPreference) findPreference(
                mGloveModeSwitchPref);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message_text_id)
                .setTitle(R.string.warning)
                .setPositiveButton(android.R.string.ok, (DialogInterface dialog, int id) -> {
                    if (performGloveMode(enabled)) {
                        // Enable SHOW_TOUCHES to make the user aware of this
                        // feature being enabled.
                        Settings.System.putInt(getContext().getContentResolver(),
                                Settings.System.SHOW_TOUCHES, enabled ? 1 : 0);

                        gloveModePref.setChecked(enabled);
                    }
                })
                // Show cancel button:
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        // Never update the preference; the handler above will do so when
        // the user agrees and there are no errors applying it.
        return false;
    }

    static boolean performGloveMode(boolean enabled) {
        for (String glovePath : SYSFS_GLOVE_MODE_PATHS) {
            try (FileWriter sysfsFile = new FileWriter(
                    String.format(SYSFS_TOUCH_GLOVE_MODE, glovePath));
                 BufferedWriter writer = new BufferedWriter(sysfsFile)) {

                Log.i(TAG, "Setting glove mode to " + enabled);

                final String enabledString = enabled ? "1" : "0";

                writer.write(enabledString + '\n');
                return true;
            } catch (FileNotFoundException ignored) {
                // Ignored: Try next file
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static boolean hasGloveMode() {
        for (String glovePath : SYSFS_GLOVE_MODE_PATHS) {
            final File sysfsFile = new File(String.format(SYSFS_TOUCH_GLOVE_MODE, glovePath));
            if (sysfsFile.isFile()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
