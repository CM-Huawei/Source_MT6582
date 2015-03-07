package com.android.camera;

import com.android.camera.Camera;

import android.content.Context;
import android.util.AttributeSet;

public class VoiceListPreference extends IconListPreference implements VoiceManager.Listener {
    private static final String TAG = "VoiceListPreference";

    private VoiceManager mVoiceManager;
    private String mDefaultValue;
    private Camera mCamera;
    
    public VoiceListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCamera = (Camera)context;
    }

    @Override
    public String getValue() {
        if (mVoiceManager != null) {
            mValue = mVoiceManager.getVoiceValue();
            if (mValue == null) {
                mValue = getSupportedDefaultValue();
            }
        } else {
            mValue = getSupportedDefaultValue();
        }
        Log.d(TAG, "getValue() return " + mValue);
        return mValue;
    }
    
    private String getSupportedDefaultValue() {
        if (mDefaultValue == null) {
            mDefaultValue = findSupportedDefaultValue();
        }
        return mDefaultValue;
    }
    
    @Override
    public void setValue(String value) {
        Log.i(TAG, "setValue(" + value + ") mValue=" + mValue);
        if (findIndexOfValue(value) < 0) {
            throw new IllegalArgumentException();
        }
        mValue = value;
        persistStringValue(value);
    }
    
    @Override
    protected void persistStringValue(String value) {
        Log.d(TAG, "persistStringValue(" + value + ") mVoiceManager=" + mVoiceManager);
        if (mVoiceManager != null) {
            mVoiceManager.setVoiceValue(value);
        }
    }
    
    @Override
    public boolean isEnabled() {
        Log.d(TAG, "isEnabled() mVoiceManager=" + mVoiceManager);
        if (mVoiceManager == null || mVoiceManager.getVoiceValue() == null) {
            return false;
        }
        return super.isEnabled();
    }
    
    public void setVoiceManager(VoiceManager voiceManager) {
        Log.d(TAG, "setVoiceManager(" + voiceManager + ") mVoiceManager=" + mVoiceManager);
        if (mVoiceManager != voiceManager) {
            if (mVoiceManager != null) {
                mVoiceManager.removeListener(this);
            }
            mVoiceManager = voiceManager;
            if (voiceManager != null) {
                voiceManager.addListener(this);
            }
        }
    }
    
    @Override
    public void onVoiceValueUpdated(String value) {
        Log.d(TAG, "onVoiceValueUpdated(" + value + ")");
        if (value == null) {
            value = getSupportedDefaultValue();
        }
        if (!((mValue == null && value == null) || (mValue != null && mValue.equals(value)))) {
            mValue = value;
            mCamera.getSettingManager().refresh();
        }
    }

    @Override
    public boolean isExtended() {
        return true;
    }
    
    @Override
    public CharSequence[] getExtendValues() {
        return mCamera.getVoiceManager().getVoiceEntryValues();
    }
}
