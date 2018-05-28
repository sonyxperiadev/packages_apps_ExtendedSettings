package sonyxperiadev.extendedsettings;

import android.app.ActionBar;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserManager;
import android.support.v14.preference.PreferenceFragment;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceControl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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

    protected static final String SYSFS_FB_MODES = "/sys/devices/virtual/graphics/fb0/modes";
    protected static final String SYSFS_FB_MODESET = "/sys/devices/virtual/graphics/fb0/mode";
    protected static final String SYSFS_FB_PCC_PROFILE = "/sys/devices/mdss_dsi_panel/pcc_profile";

    protected static final String PREF_8MP_23MP_ENABLED = "persist.camera.8mp.config";
    protected static final String PREF_DISPCAL_SETTING = "persist.dispcal.setting";
    protected static final String PREF_ADB_NETWORK_COM = "adb.network.port.es";
    private static final String PREF_ADB_NETWORK_READ = "service.adb.tcp.port";
    private static final String PREF_CAMERA_ALT_ACT = "persist.camera.alt.act";
    private static final String m8MPSwitchPref = "8mp_switch";
    private static final String mCameraAltAct = "alt_act_switch";
    protected static final String mADBOverNetworkSwitchPref = "adbon_switch";
    protected static final String mDynamicResolutionSwitchPref = "dynres_list_switch";
    protected static final String mDispCalSwitchPref = "dispcal_list_switch";

    private static final int BUILT_IN_DISPLAY_ID_MAIN = 0;

    private static FragmentManager mFragmentManager;
    protected static PreferenceFragment mFragment;
    private SharedPreferences.Editor mPrefEditor;
    private UserManager mUserManager;

    private enum dispCal {
        PANEL_CALIB_6000K(0),
        PANEL_CALIB_F6(1),
        PANEL_CALIB_D50(2),
        PANEL_CALIB_D65(3),
        PANEL_CALIB_END(4);

        private final int val;

        private dispCal(int value) {
            this.val = value;
        }

        public int getInt() {
            return val;
        }

        public static String getElementName(dispCal elm) {
            switch (elm) {
                case PANEL_CALIB_6000K:
                    return "6000K";
                case PANEL_CALIB_F6:
                    return "F6: 4150K";
                case PANEL_CALIB_D50:
                    return "D50: 5000K";
                case PANEL_CALIB_D65:
                    return "D65: 6500K";
                default:
                    return "ERROR: UNKNOWN";
            }
        }

        public static String getElementName(int elm) {
            switch (elm) {
                case 0:
                    return "6000K";
                case 1:
                    return "F6: 4150K";
                case 2:
                    return "D50: 5000K";
                case 3:
                    return "D65: 6500K";
                default:
                    return "ERROR: UNKNOWN";
            }
        }

        public static int lastElement() {
            return dispCal.PANEL_CALIB_END.getInt();
        }
    }

    private static final class DisplayParameters {
        private int height;
        private int width;
        private int refreshRate;
        private String modeString;

        private DisplayParameters(int height, int width,
                                  int refreshRate, String modeString) {
            this.height = height;
            this.width = width;
            this.refreshRate = refreshRate;
            this.modeString = modeString;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DisplayParameters)) {
                return false;
            }
            if (obj == this) {
                return true;
            }

            DisplayParameters dp = (DisplayParameters) obj;
            if (this.width == dp.width &&
                    this.height == dp.height &&
                    this.refreshRate == dp.refreshRate) {
                return true;
            }

            return false;
        }

        public boolean isOnlyRefreshRateChange(Object obj) {
            if (!(obj instanceof DisplayParameters)) {
                return false;
            }
            if (obj == this) {
                return true;
            }

            DisplayParameters dp = (DisplayParameters) obj;
            if (this.width == dp.width &&
                    this.height == dp.height &&
                    this.refreshRate != dp.refreshRate) {
                return true;
            }

            return false;
        }
    }

    private static LinkedList<DisplayParameters> sDp;

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener mPreferenceListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            switch (preference.getKey()) {
                case m8MPSwitchPref:
                    SystemProperties.set(PREF_8MP_23MP_ENABLED, String.valueOf((Boolean) value));
                    confirmRebootChange();
                    break;
                case mCameraAltAct:
                    SystemProperties.set(PREF_CAMERA_ALT_ACT, String.valueOf((Boolean) value));
                    confirmRebootChange();
                    break;
                case mADBOverNetworkSwitchPref:
                    if ((Boolean) value) {
                        confirmEnablingADBON();
                    } else {
                        SystemProperties.set(PREF_ADB_NETWORK_COM, "-1");
                        updateADBSummary(false);
                    }
                    break;
                case mDynamicResolutionSwitchPref:
                    confirmPerformDRS(Integer.parseInt((String) value));
                    break;
                case mDispCalSwitchPref:
                    int newDispCal = Integer.parseInt((String) value);
                    boolean performed = performDisplayCalibration(newDispCal);
                    if (performed) {
                        SystemProperties.set(PREF_DISPCAL_SETTING, (String) value);
                        updateDispCalPreference(newDispCal);
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setupActionBar();
        mFragment = this;
        addPreferencesFromResource(R.xml.pref_general);

        findPreference(m8MPSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);
        findPreference(mCameraAltAct).setOnPreferenceChangeListener(mPreferenceListener);
        findPreference(mADBOverNetworkSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);
        mFragmentManager = getFragmentManager();
        mPrefEditor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

        loadPref(m8MPSwitchPref, PREF_8MP_23MP_ENABLED);
        loadPref(mCameraAltAct, PREF_CAMERA_ALT_ACT);

        int ret = initializeDRSListPreference();
        if (ret == 0) {
            findPreference(mDynamicResolutionSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);
        } else {
            getPreferenceScreen().removePreference(findPreference(mDynamicResolutionSwitchPref));
        }

        initializeDispCalListPreference();
        findPreference(mDispCalSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);

        mUserManager = getSystemService(UserManager.class);
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

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName);
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
    protected static LinkedList<DisplayParameters> sysfs_readResolutions() {
        LinkedList<DisplayParameters> listDp = new LinkedList<>();
        try {
            FileInputStream sysfsFile = new FileInputStream(SYSFS_FB_MODES);
            BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(sysfsFile));
            String line = fileReader.readLine();

            Pattern reg = Pattern.compile("^[A-Z]:(\\d+)x(\\d+)p-(\\d+)$");
            while (line != null) {
                Matcher pat = reg.matcher(line);
                pat.find();
                DisplayParameters newDp = new DisplayParameters(
                        Integer.parseInt(pat.group(1)),
                        Integer.parseInt(pat.group(2)),
                        Integer.parseInt(pat.group(3)),
                        line
                );
                listDp.add(newDp);

                line = fileReader.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listDp;
    }

    protected static DisplayParameters sysfs_getCurrentResolution() {
        DisplayParameters curDp = null;
        try {
            FileInputStream sysfsFile = new FileInputStream(SYSFS_FB_MODESET);
            BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(sysfsFile));
            String line = fileReader.readLine();

            Pattern reg = Pattern.compile("^[A-Z]:(\\d+)x(\\d+)p-(\\d+)$");
            Matcher pat = reg.matcher(line);
            pat.find();
            curDp = new DisplayParameters(
                    Integer.parseInt(pat.group(1)),
                    Integer.parseInt(pat.group(2)),
                    Integer.parseInt(pat.group(3)),
                    line
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return curDp;
    }

    protected static String formatResolutionForUI(DisplayParameters dp) {
        return dp.height + " x " + dp.width + "p" +" @" + dp.refreshRate + "Hz";
    }

    protected static int resolutionToEntry(DisplayParameters dp) {
        int i = 0;
        boolean found = false;

        while (!found && i < sDp.size()) {
            DisplayParameters tempDp = sDp.get(i);
            if (sDp.get(i).equals(dp)) {
                found = true;
            }
            else i++;
        }

        if (!found) {
            return -1;
        }

        return i;
    }

    protected int initializeDRSListPreference() {
        ListPreference resPref = (ListPreference) findPreference(mDynamicResolutionSwitchPref);

        sDp = sysfs_readResolutions();

        CharSequence[] entries = new CharSequence[sDp.size()];
        CharSequence[] entryValues = new CharSequence[sDp.size()];

        for (int i = 0; i < sDp.size(); i++) {
            entries[i] = formatResolutionForUI((DisplayParameters) sDp.get(i));
            entryValues[i] = Integer.toString(i);
        }

        resPref.setEntries(entries);
        resPref.setEntryValues(entryValues);

        DisplayParameters curDp = sysfs_getCurrentResolution();
        int curVal = resolutionToEntry(curDp);

        if (curVal < 0) {
            Log.e(TAG, "initializeDRSListPreference: Active mode is blank or cannot be detected." +
                    " DRS will be disabled");
            return -1;
        }

        resPref.setValue(Integer.toString(curVal));
        resPref.setSummary(formatResolutionForUI(curDp));

        return 0;
    }

    protected static void performRRS(String modeString) {
        try {
            FileWriter sysfsFile = new FileWriter(SYSFS_FB_MODESET);
            BufferedWriter writer = new BufferedWriter(sysfsFile);

            SystemProperties.set("debug.sf.nobootanimation", "1");
            SystemProperties.set("ctl.stop", "surfaceflinger");

            writer.write(modeString + '\n');
            writer.close();

            SystemProperties.set("ctl.start", "surfaceflinger");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void performDRS(int resId) {
        IBinder displayHandle = SurfaceControl.getBuiltInDisplay(BUILT_IN_DISPLAY_ID_MAIN);
        int width, height;

        Log.e(TAG, "Performing DRS for mode " + resId);

        DisplayParameters dpCur = sysfs_getCurrentResolution();
        DisplayParameters dpNew = sDp.get(resId);
        if (dpNew.isOnlyRefreshRateChange(dpCur)) {
            performRRS(dpNew.modeString);
            return;
        }

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

        SystemProperties.set("debug.sf.nobootanimation", "1");

        SystemProperties.set("ctl.restart", "surfaceflinger");
        /* ToDo: Set nobootanimation back to 0 after SF restart */
    }

    protected void initializeDispCalListPreference() {
        String curDispCal;
        int i;

        try {
            ListPreference resPref = (ListPreference) findPreference(mDispCalSwitchPref);
            FileInputStream sysfsFile = new FileInputStream(SYSFS_FB_PCC_PROFILE);
            BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(sysfsFile));
            curDispCal = fileReader.readLine();

            if (curDispCal == null) {
                curDispCal = new String("Unavailable");
            }

            CharSequence[] entries = new CharSequence[dispCal.lastElement()];
            CharSequence[] entryValues = new CharSequence[dispCal.lastElement()];
            for (i = 0; i < dispCal.lastElement(); i++) {
                entries[i] = dispCal.getElementName(i);
                entryValues[i] = Integer.toString(i);
            }

            resPref.setEntries(entries);
            resPref.setEntryValues(entryValues);

            resPref.setDefaultValue(dispCal.getElementName(Integer.parseInt(curDispCal)));
            resPref.setValueIndex(Integer.parseInt(curDispCal));

            resPref.setSummary(dispCal.getElementName(Integer.parseInt(curDispCal)));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* WARNING: Be careful! This function is called at PRE_BOOT_COMPLETED stage! */
    protected static boolean performDisplayCalibration(int calId) {
        try {
            FileWriter sysfsFile = new FileWriter(SYSFS_FB_PCC_PROFILE);
            BufferedWriter writer = new BufferedWriter(sysfsFile);
            String calIdStr = Integer.toString(calId);

            writer.write(calIdStr + '\n');
            writer.close();

            return true;
        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }
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

    protected static void updateDispCalPreference(int newDispCal) {
        ListPreference resPref = (ListPreference) ExtendedSettingsFragment.mFragment.findPreference(mDispCalSwitchPref);
        if (resPref != null) {
            resPref.setValueIndex(newDispCal);
            resPref.setSummary(dispCal.getElementName(newDispCal));
        }
    }

    protected static void updateADBSummary(boolean enabled) {
        SwitchPreference mAdbOverNetwork = (SwitchPreference) ExtendedSettingsFragment.mFragment.findPreference(mADBOverNetworkSwitchPref);

        if (enabled) {
            WifiManager wifiManager = mFragment.getContext().getSystemService(WifiManager.class);
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
                mAdbOverNetwork.setSummary(ipAddressString + ":" + SystemProperties.getInt(PREF_ADB_NETWORK_READ, 0));
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

    private static boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    private void loadPref(String pref, String key) {
        String pref_st = SystemProperties.get(key);
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
                getActivity().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
