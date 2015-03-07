package com.mediatek.settings.inputmethod;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.hardware.input.InputManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import java.util.List;

import com.android.settings.R;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

public class InputMethodExts {

    private static final String TAG = "InputMethodAndLanguageSettings";
    private static final String KEY_VOICE_UI_ENTRY = "voice_ui";

    private Context mContext;
    private boolean mIsOnlyImeSettings;
    private PreferenceCategory mVoiceCategory;
    private Preference mVoiceUiPref;
    private Intent mVoiceControlIntent;

    // { @ Smart book mouse/touch pad settings
    private InputManager mIm;
    private ListPreference mPointerPrimayKeyPrefe;
    private PreferenceCategory mPointerSettingsCategory;

    // @ }

    public InputMethodExts(Context context, boolean isOnlyImeSettings, PreferenceCategory voiceCategory,
            PreferenceCategory pointCategory) {
        mContext = context;
        mIsOnlyImeSettings = isOnlyImeSettings;
        mVoiceCategory = voiceCategory;
        mPointerSettingsCategory = pointCategory;
    }

    // init input method extends items
    public void initExtendsItems() {
        // For voice control
        mVoiceUiPref = new Preference(mContext);
        mVoiceUiPref.setKey(KEY_VOICE_UI_ENTRY);
        mVoiceUiPref.setTitle(mContext.getString(R.string.voice_ui_title));
        if (mVoiceCategory != null) {
            mVoiceCategory.addPreference(mVoiceUiPref);
        }
        if (mIsOnlyImeSettings || !FeatureOption.MTK_VOICE_UI_SUPPORT) {
            Xlog.d(TAG, "going to remove voice ui feature ");
            if (mVoiceUiPref != null && mVoiceCategory != null) {
                Xlog.d(TAG, "removed done");
                mVoiceCategory.removePreference(mVoiceUiPref);
            }
        }

        // { @ : Smart book mouse/touch pad settings
        mIm = (InputManager)mContext.getSystemService(Context.INPUT_SERVICE);
        mPointerPrimayKeyPrefe = new ListPreference(mContext);
        mPointerPrimayKeyPrefe.setKey("mouse_primary_button_settings");
        mPointerPrimayKeyPrefe.setTitle(R.string.mouse_primary_button_title);
        mPointerPrimayKeyPrefe.setDialogTitle(R.string.mouse_primary_button_dialog_title);
        mPointerPrimayKeyPrefe.setEntries(R.array.mouse_primary_button_titles);
        mPointerPrimayKeyPrefe.setEntryValues(R.array.mouse_primary_button_values);

        DoubleClickSpeedPreference doubleClickSpeedPrefere = new DoubleClickSpeedPreference(mContext, null);
        doubleClickSpeedPrefere.setKey("double_click_speed");
        doubleClickSpeedPrefere.setTitle(R.string.double_click_title);
        doubleClickSpeedPrefere.setDialogTitle(R.string.double_click_title);

        if (!mIsOnlyImeSettings && FeatureOption.MTK_SMARTBOOK_SUPPORT) {
            if (mPointerSettingsCategory != null) {
                if (mPointerPrimayKeyPrefe != null) {
                    mPointerSettingsCategory.addPreference(mPointerPrimayKeyPrefe);
                    mPointerPrimayKeyPrefe.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){

                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            Xlog.d(TAG, "value: " + newValue);
                            mIm.setPointerPrimaryKey(mContext, Integer.valueOf((String) newValue) != 0);
                            updatePointerPrimaryValue();
                            return false;
                        }
                        
                    });
                }
                if (doubleClickSpeedPrefere != null) {
                    mPointerSettingsCategory.addPreference(doubleClickSpeedPrefere);
                }
            }
        } else {
            if (mPointerSettingsCategory != null) {
                if (mPointerPrimayKeyPrefe != null) {
                    mPointerSettingsCategory.removePreference(mPointerPrimayKeyPrefe);
                }
                if (doubleClickSpeedPrefere != null) {
                    mPointerSettingsCategory.removePreference(doubleClickSpeedPrefere);
                }
            }
        }
        // @ }
    }

    // on resume input method extends items
    public void resumeExtendsItems() {
        // { @ ALPS00823791
        mVoiceControlIntent = new Intent("com.mediatek.voicecommand.VOICE_CONTROL_SETTINGS");
        mVoiceControlIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        List<ResolveInfo> apps = mContext.getPackageManager().queryIntentActivities(mVoiceControlIntent, 0);
        if (apps == null || apps.size() == 0) {
            Xlog.d(TAG, "going to remove voice ui feature ");
            if (mVoiceUiPref != null && mVoiceCategory != null) {
                Xlog.d(TAG, "removed done");
                mVoiceCategory.removePreference(mVoiceUiPref);
            }
        } else {
            if (!mIsOnlyImeSettings && FeatureOption.MTK_VOICE_UI_SUPPORT) {
                Xlog.d(TAG, "going to add voice ui feature ");
                if (mVoiceUiPref != null && mVoiceCategory != null) {
                    mVoiceCategory.addPreference(mVoiceUiPref);
                }
            }
        }
        // @ }

        // { @ M: Smart book mouse/touch pad settings
        if (!mIsOnlyImeSettings && FeatureOption.MTK_SMARTBOOK_SUPPORT) {
            updatePointerPrimaryValue();
        }
        // @ }
    }

    // { @ M: Smart book mouse/touch pad settings
    private void updatePointerPrimaryValue() {
        if (mPointerPrimayKeyPrefe != null) {
            int currentValue = mIm.isPointerPrimaryKeyChanged(mContext) ? 1 : 0;
            mPointerPrimayKeyPrefe.setValueIndex(currentValue);
            mPointerPrimayKeyPrefe.setSummary(mPointerPrimayKeyPrefe.getEntries()[currentValue]);
        }
    }

    // @ }
    /*
     * on resume input method extends items
     * 
     * @param preferKey: clicled preference's key
     */
    public void onClickExtendsItems(String preferKey) {
        if (KEY_VOICE_UI_ENTRY.equals(preferKey)) {
            mContext.startActivity(mVoiceControlIntent);
        }
    }
}
