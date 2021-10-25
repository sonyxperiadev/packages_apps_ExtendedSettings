package sonyxperiadev.extendedsettings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;
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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
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

    private static final String SYSFS_FB_MODES = "/sys/devices/virtual/graphics/fb0/modes";
    private static final String SYSFS_FB_MODESET = "/sys/devices/virtual/graphics/fb0/mode";
    private static final String[] SYSFS_DISPLAY_FOLDERS = new String[]{ "mdss_dsi_panel", "dsi_panel_driver" };
    private static final String SYSFS_PCC_PROFILE = "/sys/devices/%s/pcc_profile";
    private static final String[] SYSFS_GLOVE_MODE_PATHS = new String[]{ "lge_touch/glove_mode", "clearpad/glove" };
    private static final String SYSFS_TOUCH_GLOVE_MODE = "/sys/devices/virtual/input/%s";

    static final String PREF_ADB_NETWORK_COM = "vendor.adb.network.port.es";
    private static final String PREF_ADB_NETWORK_READ = "service.adb.tcp.port";
    private static final String mADBOverNetworkSwitchPref = "adbon_switch";
    private static final String mDynamicResolutionSwitchPref = "dynres_list_switch";
    static final String mDispCalSwitchPref = "dispcal_list_switch";
    static final String mGloveModeSwitchPref = "glove_mode_switch";

    private static final long BUILT_IN_DISPLAY_ID_MAIN = SurfaceControl.getPhysicalDisplayIds()[0];

    private static FragmentManager mFragmentManager;
    static PreferenceFragment mFragment;
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
            return getElementName(elm.val);
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
    private Preference.OnPreferenceChangeListener mPreferenceListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            switch (preference.getKey()) {
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
                        updateDispCalPreference(newDispCal);
                    }
                    break;
                case mGloveModeSwitchPref:
                    // This function decides whether to update the state of the preference:
                    return onGloveModePreferenceChanged((Boolean)value);
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

        findPreference(mADBOverNetworkSwitchPref).setOnPreferenceChangeListener(mPreferenceListener);
        mFragmentManager = getFragmentManager();
        mPrefEditor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

        ListPreference drsSwitchPref = (ListPreference) findPreference(mDynamicResolutionSwitchPref);
        int ret = initializeDRSListPreference(drsSwitchPref);
        if (ret == 0) {
            drsSwitchPref.setOnPreferenceChangeListener(mPreferenceListener);
        } else {
            getPreferenceScreen().removePreference(drsSwitchPref);
        }

        ListPreference dispCalSwitchPref = (ListPreference) findPreference(mDispCalSwitchPref);
        ret = initializeDispCalListPreference(dispCalSwitchPref);
        if (ret == 0) {
            dispCalSwitchPref.setOnPreferenceChangeListener(mPreferenceListener);
        } else {
            getPreferenceScreen().removePreference(dispCalSwitchPref);
        }

        final SwitchPreference gloveModeSwitchPref = (SwitchPreference) findPreference(mGloveModeSwitchPref);
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
        IBinder displayHandle = SurfaceControl.getPhysicalDisplayToken(dispId);
        SurfaceControl.DynamicDisplayInfo displayInfo = SurfaceControl.getDynamicDisplayInfo(displayHandle);
        SurfaceControl.DisplayMode currentCfg = displayInfo.supportedDisplayModes[displayInfo.activeDisplayModeId];
        String ret = currentCfg.width + "x" + currentCfg.height + "@" + currentCfg.refreshRate + "hz";
        Log.d(TAG, "Current resolution: " + ret);
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
        try (FileReader sysfsFile = new FileReader(SYSFS_FB_MODES);
             BufferedReader fileReader = new BufferedReader(sysfsFile)) {
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
        try (FileReader sysfsFile = new FileReader(SYSFS_FB_MODESET);
             BufferedReader fileReader = new BufferedReader(sysfsFile)) {
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

    protected int initializeDRSListPreference(ListPreference resPref) {
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
        try (FileWriter sysfsFile = new FileWriter(SYSFS_FB_MODESET);
             BufferedWriter writer = new BufferedWriter(sysfsFile)) {

            SystemProperties.set("debug.sf.nobootanimation", "1");
            SystemProperties.set("ctl.stop", "surfaceflinger");

            writer.write(modeString + '\n');

            SystemProperties.set("ctl.start", "surfaceflinger");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static void performDRS(int resId) {
        IBinder displayHandle = SurfaceControl.getPhysicalDisplayToken(BUILT_IN_DISPLAY_ID_MAIN);
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

        SurfaceControl.DynamicDisplayInfo displayInfo = SurfaceControl.getDynamicDisplayInfo(displayHandle);

        width = displayInfo.supportedDisplayModes[displayInfo.activeDisplayModeId].width;
        height = displayInfo.supportedDisplayModes[displayInfo.activeDisplayModeId].height;

        /* This is an hack for incomplete HWC2 implementation */
        // if ((resId == curMode) && (resId < 1)) {
        //     SurfaceControl.openTransaction();
        //     // setActiveConfig(displayHandle, curMode + 1);
        //     SurfaceControl.closeTransaction();
        // }
        /* END */

        SurfaceControl.DesiredDisplayModeSpecs specs = SurfaceControl.getDesiredDisplayModeSpecs(displayHandle);
        // TODO: Most likely doesn't work like this, should probably only set
        // refresh rates through this system, and the rest through setDisplaySize?
        specs.defaultMode = resId;

        SurfaceControl.openTransaction();

        SurfaceControl.setDesiredDisplayModeSpecs(displayHandle, specs);
        SurfaceControl.setDisplaySize(displayHandle, width, height);

        SurfaceControl.closeTransaction();

        SystemProperties.set("debug.sf.nobootanimation", "1");

        SystemProperties.set("ctl.restart", "surfaceflinger");
        /* ToDo: Set nobootanimation back to 0 after SF restart */
    }

    protected int initializeDispCalListPreference(ListPreference resPref) {
        for (String displayFolder : SYSFS_DISPLAY_FOLDERS) {
            try (FileReader sysfsFile = new FileReader(String.format(SYSFS_PCC_PROFILE, displayFolder));
                 BufferedReader fileReader = new BufferedReader(sysfsFile)) {
                // "Current" sysfs value is not persisted and will be 0 after a reboot.
                // The file is only checked to make sure pcc profile selection is available.
                // final String curDispCal = fileReader.readLine();

                CharSequence[] entries = new CharSequence[dispCal.lastElement()];
                CharSequence[] entryValues = new CharSequence[dispCal.lastElement()];
                for (int i = 0; i < dispCal.lastElement(); i++) {
                    entries[i] = dispCal.getElementName(i);
                    entryValues[i] = Integer.toString(i);
                }

                resPref.setEntries(entries);
                resPref.setEntryValues(entryValues);

                // Retrieve current value from persisted storage:
                final String curDispCal = resPref.getValue();
                resPref.setSummary(dispCal.getElementName(Integer.parseInt(curDispCal)));

                return 0;
            } catch (FileNotFoundException ignored) {
                // Ignored: Try next file
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    /* WARNING: Be careful! This function is called at PRE_BOOT_COMPLETED stage! */
    protected static boolean performDisplayCalibration(int calId) {
        for (String displayFolder : SYSFS_DISPLAY_FOLDERS) {
            try (FileWriter sysfsFile = new FileWriter(String.format(SYSFS_PCC_PROFILE, displayFolder));
                 BufferedWriter writer = new BufferedWriter(sysfsFile)) {
                String calIdStr = Integer.toString(calId);

                writer.write(calIdStr + '\n');

                return true;
            } catch (FileNotFoundException ignored) {
                // Ignored: Try next file
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
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

    private boolean onGloveModePreferenceChanged(boolean enabled) {
        final int message_text_id = enabled ? R.string.dialog_enable_glove_mode : R.string.dialog_disable_glove_mode;
        final SwitchPreference gloveModePref = (SwitchPreference) findPreference(mGloveModeSwitchPref);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message_text_id)
            .setTitle(R.string.warning)
            .setPositiveButton(android.R.string.ok, (DialogInterface dialog, int id) -> {
                if (performGloveMode(enabled)) {
                    // Enable SHOW_TOUCHES to make the user aware of this
                    // feature being enabled.
                    Settings.System.putInt(getContext().getContentResolver(), Settings.System.SHOW_TOUCHES, enabled ? 1 : 0);

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
            try (FileWriter sysfsFile = new FileWriter(String.format(SYSFS_TOUCH_GLOVE_MODE, glovePath));
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

    static boolean isGloveModeEnabled() {
        for (String glovePath : SYSFS_GLOVE_MODE_PATHS) {
            try (FileReader sysfsFile = new FileReader(String.format(SYSFS_TOUCH_GLOVE_MODE, glovePath));
                    BufferedReader fileReader = new BufferedReader(sysfsFile)) {
                final String line = fileReader.readLine();
                return "1".equals(line);
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
            if (sysfsFile.isFile())
                return true;
        }
        return false;
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
