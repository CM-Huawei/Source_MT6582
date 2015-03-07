package com.mediatek.nfc.addon;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.util.Log;
import android.provider.Settings;
import android.content.ContentResolver;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.nfc.R;

public class SecureElementConfirmActivity extends AlertActivity {
	private static final String TAG = "SecureElementConfirmActivity";
	
	public static final String EXTRA_TITLE = "com.mediatek.nfc.addon.confirm.title";
	public static final String EXTRA_MESSAGE = "com.mediatek.nfc.addon.confirm.message";
	public static final String EXTRA_FIRSTSE = "com.mediatek.nfc.addon.confirm.firstse";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
		final String title = intent.getStringExtra(EXTRA_TITLE);
		final String message = intent.getStringExtra(EXTRA_MESSAGE);
		final String firstSe = intent.getStringExtra(EXTRA_FIRSTSE);
		if (firstSe.equals("")) {
			/*
			new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}	
			})
			.show();
			*/
			final AlertController.AlertParams p = mAlertParams;
			p.mIconId = android.R.drawable.ic_dialog_alert;
			p.mTitle = title;
			p.mMessage = message;
			p.mPositiveButtonText = getString(android.R.string.ok);
			setupAlert();		
			
		} else {
			/*
			new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(getString(R.string.mtk_yes), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					Settings.Global.putString(SecureElementConfirmActivity.this.getContentResolver(), SecureElementSelector.NFC_MULTISE_ACTIVE, firstSe);
					finish();
				}	
			})
			.setNegativeButton(getString(R.string.mtk_no), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			})
			.show();
			*/
			
			final AlertController.AlertParams p = mAlertParams;
			p.mIconId = android.R.drawable.ic_dialog_alert;
			p.mTitle = title;
			p.mMessage = message;
			p.mPositiveButtonText = getString(android.R.string.yes);
			p.mNegativeButtonText = getString(android.R.string.no);
			p.mPositiveButtonListener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					Settings.Global.putString(SecureElementConfirmActivity.this.getContentResolver(), SecureElementSelector.NFC_MULTISE_ACTIVE, firstSe);
					finish();
				}
			};
			setupAlert();		
		}
    }
	
}
