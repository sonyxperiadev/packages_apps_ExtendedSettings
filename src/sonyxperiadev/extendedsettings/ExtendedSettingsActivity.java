package sonyxperiadev.extendedsettings;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceControl;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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

    protected static final String SYSFS_FB_MODES = "/sys/devices/virtual/graphics/fb0/modes";
    protected static final String SYSFS_FB_MODESET = "/sys/devices/virtual/graphics/fb0/mode";

    protected static final String PREF_8MP_23MP_ENABLED = "persist.camera.8mp.config";
    protected static final String PREF_ADB_NETWORK_COM = "adb.network.port.es";
    private static final String PREF_ADB_NETWORK_READ = "service.adb.tcp.port";
    private static final String PREF_CAMERA_ALT_ACT = "persist.camera.alt.act";
    private static final String m8MPSwitchPref = "8mp_switch";
    private static final String mCameraAltAct = "alt_act_switch";
    protected static final String mADBOverNetworkSwitchPref = "adbon_switch";
    protected static final String mDynamicResolutionSwitchPref = "dynres_list_switch";

    private static final int BUILT_IN_DISPLAY_ID_MAIN = 0;

    private static FragmentManager mFragmentManager;
    protected static AppCompatPreferenceActivity mActivity;
    private SharedPreferences.Editor mPrefEditor;
    private static boolean drsInited = false;

    private static final class DisplayParameters {
        private int height;
        private int width;
        private int refreshRate;

        private DisplayParameters(int height, int width, int refreshRate) {
            this.height = height;
            this.width = width;
            this.refreshRate = refreshRate;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DisplayParameters))
                return false;
            if (obj == this)
                return true;

            DisplayParameters dp = (DisplayParameters) obj;
            if (this.width == dp.width &&
                    this.height == dp.height &&
                    this.refreshRate == dp.refreshRate)
                return true;

            return false;
        }
    }

    private static LinkedList<DisplayParameters> thisDp = new LinkedList<>();

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
                case mDynamicResolutionSwitchPref:
                    confirmPerformDRS(Integer.parseInt((String)value));
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

        initializeDRSListPreference();
        findPreference(mDynamicResolutionSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);

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

    /*
     * getAvailableResolutions - Query SurfaceFlinger for getting display resolutions
     *
     * Note: This is the "legal" Android way to get the available display resolutions.
     *       Use only when the HWC2 implementation will be complete.
     */
    protected static String getAvailableResolutions(int dispId) {
        IBinder dispHandle = SurfaceControl.getBuiltInDisplay(dispId);
        SurfaceControl.PhysicalDisplayInfo[] displayCfgs =
                SurfaceControl.getDisplayConfigs(dispHandle);
        int currentCfgNo = SurfaceControl.getActiveConfig(dispHandle);
        String ret;

        SurfaceControl.PhysicalDisplayInfo currentCfg = displayCfgs[currentCfgNo];
        Log.d(TAG, "Current resolution: " + currentCfg.width + "x" +
                    currentCfg.height + " @ " + currentCfg.refreshRate +
                    "hz, " + currentCfg.density + "DPI");
        ret = currentCfg.width + "x" + currentCfg.height + "@" +
                currentCfg.refreshRate + "hz";
        return ret;
    }

    /*
     * sysfs_readResolutions - Read resolutions from sysfs framebuffer node
     *
     * The modes sysfs file outputs one string per line, formatted like this:
     * U:WxH-Hz --- For example: U:2160x3840p-60
     *
     * Note: This implementation is temporary, used only until we get a
     *       finished HWC2 implementation for the QC display HAL.
     */
    protected static int sysfs_readResolutions() {
        try {
            FileInputStream sysfsFile = new FileInputStream(SYSFS_FB_MODES);
            BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(sysfsFile));
            String line = fileReader.readLine();
            int temp = 0;

            Pattern reg = Pattern.compile("^[A-Z]:(\\d+)x(\\d+)p-(\\d+)$");
            while (line != null) {
                Matcher pat = reg.matcher(line);
                pat.find();
                DisplayParameters curParm = new DisplayParameters(
                        Integer.parseInt(pat.group(1)),
                        Integer.parseInt(pat.group(2)),
                        Integer.parseInt(pat.group(3))
                );
                thisDp.add(curParm);

                temp++;
                line = fileReader.readLine();
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    protected static DisplayParameters sysfs_getCurrentResolution(int fbId) {
        try {
            FileInputStream sysfsFile = new FileInputStream(SYSFS_FB_MODESET);
            BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(sysfsFile));
            String line = fileReader.readLine();
            DisplayParameters dispParms;

            Matcher pat = Pattern.compile("^[A-Z]:(\\d+)x(\\d+)p-(\\d+)$").matcher(line);
            pat.find();
            dispParms = new DisplayParameters(
                    Integer.parseInt(pat.group(1)),
                    Integer.parseInt(pat.group(2)),
                    Integer.parseInt(pat.group(3))
            );

            return dispParms;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected static String sysfs_getCurrentResolutionString(int fbId) {
        try {
            FileInputStream sysfsFile = new FileInputStream(SYSFS_FB_MODESET);
            BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(sysfsFile));
            String line = fileReader.readLine();

            return line;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected static String formatResolutionForUI(int entry) {
        DisplayParameters curParms = thisDp.get(entry);
        String ret = curParms.height + "p" + " @" + curParms.refreshRate + "Hz";

        return ret;
    }

    protected static int resolutionToEntry(DisplayParameters myRes) {
        int i = 0;
        boolean found = false;

        while (!found && i < thisDp.size()) {
            DisplayParameters tempDp = thisDp.get(i);
            if (thisDp.get(i).equals(myRes))
                found = true;
            else i++;
        }

        if (!found)
            return -1;

        return i;
    }
    
    protected void initializeDRSListPreference() {
        DisplayParameters currentRes = sysfs_getCurrentResolution(0);
        ListPreference resPref = (ListPreference)findPreference(mDynamicResolutionSwitchPref);
        int i, curResVal;

        sysfs_readResolutions();

        CharSequence[] entries = new CharSequence[thisDp.size()];
        CharSequence[] entryValues = new CharSequence[thisDp.size()];

        for (i = 0; i < thisDp.size(); i++) {
            entries[i] = formatResolutionForUI(i);
            entryValues[i] = Integer.toString(i);
        }

        if (!drsInited) {
            resPref.setEntries(entries);
            resPref.setEntryValues(entryValues);
            drsInited = true;
        }

        curResVal = resolutionToEntry(currentRes);
        resPref.setDefaultValue(Integer.toString(curResVal));
        resPref.setValueIndex(curResVal);

        resPref.setSummary(sysfs_getCurrentResolutionString(0));
    }

    protected static void performDRS(int resId) {
        IBinder displayHandle = SurfaceControl.getBuiltInDisplay(BUILT_IN_DISPLAY_ID_MAIN);
        int width, height;

        Log.e(TAG, "Performing DRS for mode " + resId);

        if (displayHandle == null) {
            Log.e(TAG, "Cannot get an handle to the current display.");
            return;
        }

        int curMode = SurfaceControl.getActiveConfig(displayHandle);
        SurfaceControl.PhysicalDisplayInfo[] displayCfgs =
                SurfaceControl.getDisplayConfigs(displayHandle);

        width = displayCfgs[resId].width;
        height = displayCfgs[resId].height;

        /* This is an hack for incomplete HWC2 implementation */
        if ((resId == curMode) && (resId < 1)) {
            SurfaceControl.openTransaction();
            SurfaceControl.setActiveConfig(displayHandle, curMode + 1);
            SurfaceControl.closeTransaction();
        }
        /* END */

        SurfaceControl.openTransaction();

        SurfaceControl.setActiveConfig(displayHandle, resId);
        SurfaceControl.setDisplaySize(displayHandle, width, height);

        SurfaceControl.closeTransaction();

        setSystemProperty("debug.sf.nobootanimation", "1");
        setSystemProperty("ctl.restart", "surfaceflinger");
        /* ToDo: Set nobootanimation back to 0 after SF restart */
    }

    private static void confirmPerformDRS(int resId) {
        DialogFragment newFragment = new performDRSDialog();
        Bundle fragArgs = new Bundle();
        fragArgs.putInt(performDRSDialog.DRS_RESOLUTION_ID, resId);

        newFragment.setArguments(fragArgs);
        newFragment.show(mFragmentManager, "drs");
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
