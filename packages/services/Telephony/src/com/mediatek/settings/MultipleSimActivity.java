package com.mediatek.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.settings.SelectSimCardActivity;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.gemini.simui.SimCardInfoPreference;
import com.mediatek.gemini.simui.SimInfoViewUtil.WidgetType;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.ext.ICallSettingsConnection;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.vt.VTCallUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;
import com.mediatek.phone.wrapper.TelephonyManagerWrapper;

import java.io.IOException;

public class MultipleSimActivity extends SelectSimCardActivity {

    //Give the sub items type(ListPreference, CheckBoxPreference, Preference)
    public static final String  INTENT_KEY = "ITEM_TYPE";
    public static final String SUB_TITLE_NAME = "sub_title_name";
    
    //Used for ListPreference to initialize the list
    public static final String INIT_ARRAY = "INIT_ARRAY";
    public static final String INIT_ARRAY_VALUE = "INIT_ARRAY_VALUE";
    
    //Most time, we get the sim number by Framework's api, but we firstly check how many sim support a special feature
    //For example, although two sim inserted, but there's only one support the VT
    public static final String INIT_FEATURE_NAME = "INIT_FEATURE_NAME";
    public static final String INIT_BASE_KEY = "INIT_BASE_KEY";

    private int mSimNumbers = 0;
    private int mListTitle;
    private int mListEntries;
    private int mListValues;
    private static final String TAG = "MultipleSimActivity";

    private PreCheckForRunning mPreCheckForRunning;

    private IntentFilter mIntentFilter;
    private final MultipleSimReceiver mReceiver = new MultipleSimReceiver();

    private class MultipleSimReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (PhoneGlobals.NETWORK_MODE_CHANGE_RESPONSE.equals(action)) {
                removeDialog(PROGRESS_DIALOG);
                int slotId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, 0);
                log("BroadcastReceiver  slotId = " + slotId);
                if (!intent.getBooleanExtra(PhoneGlobals.NETWORK_MODE_CHANGE_RESPONSE, true)) {
                    log("BroadcastReceiver: network mode change failed! restore the old value.");
                    int oldMode = intent.getIntExtra(PhoneGlobals.OLD_NETWORK_MODE, 0);
                    android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                            getNetworkModeName(slotId), oldMode);
                    log("BroadcastReceiver, oldMode = " + oldMode);
                    if (NETWORK_MODE_NAME.equals(mFeatureName)) {
                        log("setValue  to oldMode ");
                        for (int i = 0; i < mFragment.getPreferenceScreen().getPreferenceCount(); i++) {
                            Preference p = mFragment.getPreferenceScreen().getPreference(i);
                            if (slotId == ((SimCardInfoPreference)p).getSimSlotId()) {
                                ((ListPreference)p).setValue(Integer.toString(oldMode));
                                break;
                            }
                        }
                    }
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)
                        || Intent.ACTION_DUAL_SIM_MODE_CHANGED.equals(action)
                        || TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED.equals(action)) {
                log("BroadcastReceiver, action = " + action);
                updatePreferenceEnableState();
            } else if (TelephonyIntents.ACTION_SIM_INFO_UPDATE.equals(action)) {
                int count = SimInfoManager.getInsertedSimCount(context);
                if (count < 1) {
                    log("sim size = " + count + ", Activity finished");
                    finish();
                }
            }
        }
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            log("onCallStateChanged ans state is " + state);
            switch(state) {
            case TelephonyManager.CALL_STATE_IDLE:
                updatePreferenceEnableState();
                break;
            default:
                break;
            }
        }
    };
    
    @Override
    public void onCreate(Bundle icicle) {
        mFeatureName = getIntent().getStringExtra(INIT_FEATURE_NAME);
        if (NETWORK_MODE_NAME.equals(mFeatureName)) {
            setTheme(android.R.style.Theme_Holo_DialogWhenLarge);
        }
        super.onCreate(icicle);

        mBaseKey = getIntent().getStringExtra(INIT_BASE_KEY);

        mListTitle = getIntent().getIntExtra(LIST_TITLE, -1);
        mListEntries = getIntent().getIntExtra(INIT_ARRAY, -1);
        mListValues = getIntent().getIntExtra(INIT_ARRAY_VALUE, -1);
        String itemType = getIntent().getStringExtra(INTENT_KEY);

        if ("ListPreference".equals(itemType)) {
            setWidgetViewType(WidgetType.Dialog);
        } else if ("CheckBoxPreference".equals(itemType)){
            setWidgetViewType(WidgetType.CheckBox);
        } else {
            setWidgetViewType(WidgetType.None);
        }
        
        mPhone = PhoneGlobals.getPhone();
        TelephonyManagerWrapper.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE, PhoneWrapper.UNSPECIFIED_SLOT_ID);

        mIntentFilter =
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_EF_CSP_CONTENT_NOTIFY);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mIntentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        }
        if (NETWORK_MODE_NAME.equals(mFeatureName)) {
            mIntentFilter.addAction(PhoneGlobals.NETWORK_MODE_CHANGE_RESPONSE);
        }
        registerReceiver(mReceiver, mIntentFilter);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceEnableState();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
        case android.R.id.home:
            finish();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        log("-----------[onSaveInstanceState]-----------");
        super.onSaveInstanceState(outState);
        outState.putInt(LIST_TITLE, mListTitle);
        outState.putInt(INIT_ARRAY, mListEntries);
        outState.putInt(INIT_ARRAY_VALUE, mListValues);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        log("-----------[onRestoreInstanceState]-----------");
        super.onRestoreInstanceState(savedInstanceState);
        mListTitle = savedInstanceState.getInt(LIST_TITLE, -1);
        mListEntries = savedInstanceState.getInt(INIT_ARRAY, -1);
        mListValues = savedInstanceState.getInt(INIT_ARRAY_VALUE, -1);
    }

    @Override
    protected void setPreference(SimCardInfoPreference p) {
        String key = null;
        p.setPersistent(true);

        if (mBaseKey != null && mBaseKey.endsWith("@")) {
            key = mBaseKey.substring(0, mBaseKey.length() - 1) + "_" + p.getSimSlotId();
            p.setKey(key);
        }
        p.setOnPreferenceChangeListener((Preference.OnPreferenceChangeListener)mFragment);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (mType == WidgetType.CheckBox) {
            p.setChecked(sp.getBoolean(key, !key.startsWith("button_vt_auto_dropback_key")));
        } else {
            if (mListTitle > 0) {
                p.setDialogTitle(mListTitle);
            }
            
            if (mListEntries > 0) {
                p.setEntries(mListEntries);
            }
            if (mListValues > 0) {
                p.setEntryValues(mListValues);
            }
            p.setValue(sp.getString(key, "0"));
            if (NETWORK_MODE_NAME.equals(mFeatureName)) {
                int settingsNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(), getNetworkModeName(p.getSimSlotId()), 0);
                /// M: add for wcdma prefer feature
                if(settingsNetworkMode == Phone.NT_MODE_GSM_UMTS){
                    settingsNetworkMode = Phone.NT_MODE_WCDMA_PREF;
                }
                log("settingsNetworkMode init value = " + settingsNetworkMode);
                p.setValue(Integer.toString(settingsNetworkMode));
            }
        }
    }
    
    @Override
    public Dialog onCreateDialog(int id) {
        log("[onCreateDialog][" + id + "]");
        Dialog dialog = null;
        log("[mBitmap = " + mBitmap + "]");
        log("[mImage = " + mImage + "]");
        if (mBitmap == null || mImage == null) {
            return dialog;
        }

        switch(id) {
        case PROGRESS_DIALOG:
            dialog = new ProgressDialog(this);
            ((ProgressDialog)dialog).setMessage(getText(R.string.updating_settings));
            ((ProgressDialog)dialog).setCancelable(false);
            ((ProgressDialog)dialog).setIndeterminate(true);
            break;
        case ALERT_DIALOG:
            dialog = new AlertDialog.Builder(this)
                .setPositiveButton(R.string.vt_change_my_pic, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
                            intent.setType("image/*");
                            setCropParametersForIntent(intent);
                            startActivityForResult(intent, 
                                    VTAdvancedSettingEx.REQUESTCODE_PICTRUE_PICKED_WITH_DATA);
                        } catch (ActivityNotFoundException e) {
                            log("Pictrue not found , Gallery ActivityNotFoundException !");
                        }
                    }})
                .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }})
                .create();
            ((AlertDialog)dialog).setView(mImage);
            dialog.setTitle(getResources().getString(R.string.vt_pic_replace_local_mypic));
            dialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    mImage.setImageBitmap(null);
                    if (!mBitmap.isRecycled()) {
                        mBitmap.recycle();
                    }
                    removeDialog(ALERT_DIALOG);
                }
            });
            break;
        case ALERT_DIALOG_DEFAULT:
            dialog = new AlertDialog.Builder(this)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }})
                .create();
            ((AlertDialog)dialog).setView(mImage);
            dialog.setTitle(getResources().getString(R.string.vt_pic_replace_local_default));
            dialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    mImage.setImageBitmap(null);
                    if (!mBitmap.isRecycled()) {
                        mBitmap.recycle();
                    }
                    removeDialog(ALERT_DIALOG_DEFAULT);
                }
            });
            break;
        default:
            break;
        }
        dialog.show();
        return dialog;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        log("onActivityResult: requestCode = " + requestCode + ", resultCode = " + resultCode);

        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
        case VTAdvancedSettingEx.REQUESTCODE_PICTRUE_PICKED_WITH_DATA:
            Bitmap result = data.getParcelableExtra("data");
            Uri uri = data.getData();
            if (result == null) {
                // return value is URI
                log("return value is URI, uri = " + uri);
                if (uri != null) {
                    Intent intent = new Intent(VTAdvancedSettingEx.ACTION_CROP);
                    intent.setDataAndType(uri, "image/*");
                    // add permission for this
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    setCropParametersForIntent(intent);
                    try {
                        startActivityForResult(intent, VTAdvancedSettingEx.REQUESTCODE_PICTRUE_CROP);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Crop, ActivityNotFoundException !");
                    }
                } else {
                    Log.e(TAG, "get content data, uri is null!!!~~");
                }
            } else {
                saveBitMap(result);
                showDialogMyPic();
            }
            break;

        case VTAdvancedSettingEx.REQUESTCODE_PICTRUE_CROP:
            Bitmap bitmap = data.getParcelableExtra("data");
            if (bitmap != null) {
                saveBitMap(bitmap);
                showDialogMyPic();
            } else {
                Log.e(TAG, "get crop data, bitmap is null!!!~~");
            }
            break;
        default:
            break;
        }
    }

    private void saveBitMap(Bitmap bitmap) {
        try {
            if (bitmap != null) {
                if (mVTWhichToSave == 0) {
                    VTCallUtils.saveMyBitmap(VTAdvancedSetting.getPicPathUserselect(mVTSimId), bitmap);
                } else {
                    VTCallUtils.saveMyBitmap(VTAdvancedSetting.getPicPathUserselect2(mVTSimId), bitmap);
                }
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showDialogMyPic() {
        if (mVTWhichToSave == 0) {
            showDialogPic(VTAdvancedSetting.getPicPathUserselect(mVTSimId), ALERT_DIALOG);  
        } else {
            showDialogPic(VTAdvancedSetting.getPicPathUserselect2(mVTSimId), ALERT_DIALOG);  
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        TelephonyManagerWrapper.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE,
                PhoneWrapper.UNSPECIFIED_SLOT_ID);
    }
    
    private void updatePreferenceEnableState() {
        PreferenceScreen prefSet = this.mFragment.getPreferenceScreen();
        
        //For single sim or only one sim inserted, we couldn't go here
        boolean isIdle = (TelephonyManagerWrapper.getCallState(PhoneWrapper.UNSPECIFIED_SLOT_ID) == TelephonyManager.CALL_STATE_IDLE);
        for (int i = 0; i < prefSet.getPreferenceCount(); ++i) {
            Preference p = prefSet.getPreference(i);
            int slotId = ((SimCardInfoPreference)p).getSimSlotId();
            if (GeminiUtils.isGeminiSupport()) {
                if (NETWORK_MODE_NAME.equals(mFeatureName)) {
                    p.setEnabled(PhoneWrapper.isRadioOn(mPhone, slotId) && isIdle);
                } else {
                    p.setEnabled(PhoneWrapper.isRadioOn(mPhone, slotId));
                }
                if (!p.isEnabled()) {
                    if (p instanceof ListPreference) {
                        Dialog dialog = ((ListPreference)p).getDialog();
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                    }
                }
            }
        }
    }

    private void setCropParametersForIntent(Intent intent) {
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX",
                getResources().getDimensionPixelSize(R.dimen.qcif_x));
        intent.putExtra("outputY",
                getResources().getDimensionPixelSize(R.dimen.qcif_y));
        intent.putExtra("return-data", true);
        intent.putExtra("scaleUpIfNeeded", true);
    }
}
