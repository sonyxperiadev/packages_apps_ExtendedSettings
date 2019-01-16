package sonyxperiadev.extendedsettings;

import android.os.SystemProperties;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class SleepControl {
    protected static final String PERSIST_NEVERSLEEP_PROP = "persist.vendor.neversleep";
    protected static final String SYSFS_WAKE_LOCK = "/sys/power/wake_lock";
    protected static final String SYSFS_WAKE_UNLOCK = "/sys/power/wake_unlock";

    protected static void setSleep(boolean enable) {
        // Hold or relase a phony wakelock
        try (FileWriter sysfsFile = new FileWriter(enable ? SYSFS_WAKE_UNLOCK : SYSFS_WAKE_LOCK);
            BufferedWriter writer = new BufferedWriter(sysfsFile)) {
            writer.write("shawnofthedead" + '\n');
            writer.close();
            SystemProperties.set(PERSIST_NEVERSLEEP_PROP, enable ? "false" : "true");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
