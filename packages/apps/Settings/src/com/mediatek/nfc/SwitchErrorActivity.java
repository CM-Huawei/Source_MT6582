package com.mediatek.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.settings.R;
import com.mediatek.xlog.Xlog;

public class SwitchErrorActivity extends AlertActivity {
    private static final String TAG = "SwitchErrorActivity";
    
    private static final String FAIL_DIALOG_ACTION = "android.nfc.action.SWITCH_FAIL_DIALOG_REQUEST";
    private static final String NOT_SUPPORT_PROMPT_DIALOG_ACTION = "android.nfc.action.NOT_NFC_SIM_DIALOG_REQUEST";
    private static final String NOT_SUPPORT_PROMPT_TWO_SIM_DIALOG_ACTION = "android.nfc.action.NOT_NFC_TWO_SIM_DIALOG_REQUEST";
    private static final String EXTRA_WHAT_SIM = "android.nfc.extra.WHAT_SIM";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String action = i.getAction();
        if(FAIL_DIALOG_ACTION.equals(action)) {
            String mode = i.getStringExtra("mode");
            Xlog.d(TAG, "switch fail mode is " + mode);
            showErrorDialog(mode);
        } else if(NOT_SUPPORT_PROMPT_DIALOG_ACTION.equals(action)) {
            String sim = i.getStringExtra(EXTRA_WHAT_SIM);
            Xlog.d(TAG, "show not support dialog, sim is " + sim);
            showNotSupportDialog(sim);
        } else if(NOT_SUPPORT_PROMPT_TWO_SIM_DIALOG_ACTION.equals(action)) {
            Xlog.d(TAG, "show not support dialog for SIM1 and SIM2");
            showTwoSimNotSupportDialog();
        } else {
            Xlog.e(TAG, "Error: this activity may be started only with intent "
                  + "android.nfc.action.SWITCH_FAIL_DIALOG_REQUEST " + action);
            finish();
            return;
        }

    }
    
    private void showErrorDialog(String errorMode) {
        final AlertController.AlertParams p = mAlertParams;
        p.mIconId = android.R.drawable.ic_dialog_alert;
        p.mTitle = getString(R.string.card_emulation_switch_error_title);
        p.mMessage = getString(R.string.card_emulation_switch_error_message, errorMode);
        p.mPositiveButtonText = getString(android.R.string.ok);
        setupAlert();
    }
    
    private void showNotSupportDialog(String simDescription) {
        final AlertController.AlertParams p = mAlertParams;
        p.mIconId = android.R.drawable.ic_dialog_alert;
        p.mTitle = getString(R.string.card_emulation_switch_error_title);
        p.mMessage = getString(R.string.card_emulation_sim_not_supported_message, simDescription);
        p.mPositiveButtonText = getString(android.R.string.ok);
        setupAlert();
    }

    private void showTwoSimNotSupportDialog() {
        final AlertController.AlertParams p = mAlertParams;
        p.mIconId = android.R.drawable.ic_dialog_alert;
        p.mTitle = getString(R.string.card_emulation_switch_error_title);
        p.mMessage = getString(R.string.card_emulation_two_sim_not_supported_message);
        p.mPositiveButtonText = getString(android.R.string.ok);
        setupAlert();
    }

}
