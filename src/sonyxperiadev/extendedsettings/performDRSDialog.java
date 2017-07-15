package sonyxperiadev.extendedsettings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Created by kholk on 17/07/17.
 * Dialog to perform Dynamic Resolution switch.
 * Needed only for incomplete HWC2 implementation.
 * Base on Googles fire missile dialog
 */

public class performDRSDialog extends DialogFragment {
    static String DRS_RESOLUTION_ID = "ResolutionID";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final int resId = getArguments().getInt(DRS_RESOLUTION_ID);

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.pref_description_drsdialog)
                .setTitle(R.string.pref_title_dynres_switch)
                .setCancelable(false)
                .setPositiveButton(R.string.enable, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ExtendedSettingsActivity.performDRS(resId);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        /* Do nothing */
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}

