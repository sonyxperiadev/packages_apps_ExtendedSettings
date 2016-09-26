package sonyxperiadev.extendedsettings;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.MenuItem;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class ExtendedSettingsActivity extends AppCompatPreferenceActivity {

    private static final String TAG = "ExtendedSettings";
    protected static final String PREF_8MP_23MP_ENABLED = "persist.camera.8mp.config";
    protected static final String PREF_ADB_NETWORK_COM = "adb.network.port.es";
    private static final String PREF_ADB_NETWORK_READ = "service.adb.tcp.port";
    private static final String PREF_CAMERA_ALT_ACT = "persist.camera.alt.act";
    private static final String m8MPSwitchPref = "8mp_switch";
    private static final String mCameraAltAct = "alt_act_switch";
    protected static final String mADBOverNetworkSwitchPref = "adbon_switch";
    private static FragmentManager mFragmentManager;
    protected static AppCompatPreferenceActivity mActivity;
    private SharedPreferences.Editor mPrefEditor;

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener mPreferenceListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            switch (preference.getKey()) {
                case m8MPSwitchPref:
                    setSystemProperty(PREF_8MP_23MP_ENABLED, (Boolean) value ? "true" : "false");
                    confirmRebootChange();
                    break;
                case mCameraAltAct:
                    setSystemProperty(PREF_CAMERA_ALT_ACT, (Boolean) value ? "true" : "false");
                    confirmRebootChange();
                    break;
                case mADBOverNetworkSwitchPref:
                    if ((Boolean) value) {
                        confirmEnablingADBON();
                    } else {
                        setSystemProperty(PREF_ADB_NETWORK_COM, "-1");
                        updateADBSummary(false);
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        mActivity = this;
        addPreferencesFromResource(R.xml.pref_general);

        findPreference(m8MPSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);
        findPreference(mCameraAltAct).setOnPreferenceChangeListener(mPreferenceListener);
        findPreference(mADBOverNetworkSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);
        mFragmentManager = getFragmentManager();
        mPrefEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();

        loadPref(m8MPSwitchPref, PREF_8MP_23MP_ENABLED);
        loadPref(mCameraAltAct, PREF_CAMERA_ALT_ACT);

        String adbN = getSystemProperty(PREF_ADB_NETWORK_READ);
        boolean adbNB = isNumeric(adbN) && (Integer.parseInt(adbN) > 0);
        mPrefEditor.putBoolean(mADBOverNetworkSwitchPref, adbNB);
        SwitchPreference adbon = (SwitchPreference) findPreference(mADBOverNetworkSwitchPref);
        // Set the switch state accordingly to the Preference
        adbon.setChecked(adbNB);
        updateADBSummary(adbNB);
        mPrefEditor.apply();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName);
    }

    protected static String getSystemProperty(String key) {
        String value = null;
        try {
            value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    protected static void setSystemProperty(String key, String value) {
        try {
            Class.forName("android.os.SystemProperties")
                    .getMethod("set", String.class, String.class).invoke(null, key, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void confirmEnablingADBON() {
        DialogFragment newFragment = new EnableADBONDialog();
        newFragment.show(mFragmentManager, "adb");
    }

    private static void confirmRebootChange() {
        DialogFragment newFragment = new confirmRebootChangeDialog();
        newFragment.show(mFragmentManager, "8mp");
    }

    protected static void updateADBSummary(boolean enabled) {

        SwitchPreference mAdbOverNetwork = (SwitchPreference) ExtendedSettingsActivity.mActivity.findPreference(mADBOverNetworkSwitchPref);

        if (enabled) {
            WifiManager wifiManager = (WifiManager) mActivity.getSystemService(WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

            // Convert little-endian to big-endianif needed
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
                mAdbOverNetwork.setSummary(ipAddressString + ":" + getSystemProperty(PREF_ADB_NETWORK_READ));
            } else {
                mAdbOverNetwork.setSummary(R.string.error_connect_to_wifi);
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(mADBOverNetworkSwitchPref, false);
                editor.apply();
                setSystemProperty(PREF_ADB_NETWORK_COM, "-1");
                mAdbOverNetwork.setChecked(false);
            }
        } else {
            mAdbOverNetwork.setSummary(R.string.pref_description_adbonswitch);
        }
    }

    private static boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    private void loadPref(String pref, String key) {
        String pref_st = getSystemProperty(key);
        if (pref_st != null && !pref_st.equals("")) {
            mPrefEditor.putBoolean(pref, pref_st.equals("true"));
            SwitchPreference pref_sw = (SwitchPreference) findPreference(pref);
            // Set the switch state accordingly to the Preference
            pref_sw.setChecked(Boolean.valueOf(pref_st));
        } else {
            getPreferenceScreen().removePreference(findPreference(pref));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
