package com.mediatek.apn;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.widget.ListView;

import com.android.settings.ApnSettings;
import com.android.settings.R;
import com.android.settings.Utils;

import com.mediatek.settings.ext.IApnSettingsExt;
import com.mediatek.xlog.Xlog;


public class ApnTypePreference extends DialogPreference
            implements DialogInterface.OnMultiChoiceClickListener {

    private int mApnTypeNum;
    private static final String TAG = "ApnTypePreference";
    private boolean[] mCheckState;
    private boolean[] mUiCheckState;
    private String[] mApnTypeArray;        

    
    private String mTypeString;
    
    private ListView mListView;

    IApnSettingsExt mExt;

    public ApnTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);              

        mExt = Utils.getApnSettingsPlugin(context);
        
        String apnType = null;
        if (context instanceof Activity) {
            Intent intent = ((Activity)context).getIntent();
            apnType = intent.getStringExtra(ApnSettings.APN_TYPE);
        }
        
        boolean isTethering = ApnSettings.TETHER_TYPE.equals(apnType); 
        
        mApnTypeArray = mExt.getApnTypeArray(context, R.array.apn_type_generic,isTethering);
        
        if (mApnTypeArray != null) {
            mApnTypeNum = mApnTypeArray.length;
        } 
        mCheckState = new boolean[mApnTypeNum];
    }
    
    public ApnTypePreference(Context context) {
        this(context, null);
    }
    
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        
        builder.setMultiChoiceItems(mApnTypeArray, mCheckState, this);

        mListView = builder.create().getListView();

        mUiCheckState = new boolean[mApnTypeNum];
        //Set UI CheckBox Status:
        for (int i = 0; i < mApnTypeNum; i++) {
            mUiCheckState[i] = mCheckState[i];
            Xlog.i(TAG, "onPrepareDialogBuilder mUiCheckState[" + i + "]=" + mUiCheckState[i]);
        }
    }

    private void updateUiCheckBoxStatus() {
        for (int i = 0; i < mApnTypeNum; i++) {
            mCheckState[i] = mUiCheckState[i];
            Xlog.i(TAG, "updateUiCheckBoxStatus mCheckState[" + i + "]=" + mCheckState[i]);
        }
    }
    

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            updateUiCheckBoxStatus();
            updateRecord();
            callChangeListener(mTypeString);
        } else {
            intCheckState(mTypeString);
        }
    }

    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        mCheckState[which] = isChecked;

        //Set UI CheckBox Status:
        mUiCheckState[which] = isChecked;
    }

    private void updateRecord() {

        if (mListView != null) {
            
            StringBuilder strTemp = new StringBuilder("");
            
            for (int i = 0; i < mApnTypeNum; i++) {

                if (mCheckState[i]) {
                    strTemp.append(mApnTypeArray[i]).append(',');
                }
            }
            
            int length = strTemp.length();
            if (length > 1) {
                mTypeString = strTemp.substring(0, length - 1);
            } else {
                mTypeString = "";
            }
            Xlog.i(TAG, "mTypeString is " + mTypeString);

        }
        
    }


    public void intCheckState(String strType) {
        
        Xlog.d(TAG,"init CheckState: " + strType);
        if (strType == null) {          
            return;
        }
        
        mTypeString = strType;
        
        for (int i = 0; i < mApnTypeNum; i++) {
            mCheckState[i] = strType.contains(mApnTypeArray[i]);
        }
    }   
    
    public String getTypeString() {
        return mTypeString;
    }
}
