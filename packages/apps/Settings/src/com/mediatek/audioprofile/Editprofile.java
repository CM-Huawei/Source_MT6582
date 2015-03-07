/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.audioprofile;

import android.app.Activity;
import android.content.ContentQueryMap;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.net.sip.SipManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Telephony.SIMInfo;
import android.telephony.TelephonyManager;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.mediatek.audioprofile.AudioProfileManager;
import com.mediatek.audioprofile.AudioProfileManager.Scenario;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.gemini.GeminiUtils;
import com.mediatek.xlog.Xlog;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class Editprofile extends SettingsPreferenceFragment {
    public static final String KEY_VIBRATE = "phone_vibrate";
    public static final String KEY_VOLUME = "ring_volume";
    public static final String KEY_CATEGORY_RINGTONE = "ringtone";
    public static final String KEY_RINGTONE = "phone_ringtone";
    public static final String KEY_VIDEO_RINGTONE = "video_call_ringtone";
    public static final String KEY_SIP_RINGTONE = "sip_call_ringtone";
    public static final String KEY_CATEGORY_NOTIFICATION = "notifications";
    public static final String KEY_NOTIFY = "notifications_ringtone";
    public static final String KEY_DTMF_TONE = "audible_touch_tones";
    public static final String KEY_SOUND_EFFECTS = "audible_selection";
    public static final String KEY_LOCK_SOUNDS = "screen_lock_sounds";
    public static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback";

    private static final String TAG = "Settings/EditProfile";

    private CheckBoxPreference mVibrat;
    private CheckBoxPreference mDtmfTone;
    private CheckBoxPreference mSoundEffects;
    private CheckBoxPreference mHapticFeedback;
    private CheckBoxPreference mLockSounds;

    private RingerVolumePreference mVolumePref;
    private DefaultRingtonePreference mVoiceRingtone;
    private DefaultRingtonePreference mVideoRingtone;
    private DefaultRingtonePreference mSipRingtone;

    private boolean mIsSilentMode;
    private AudioProfileManager mProfileManager;

    private boolean mIsMeetingMode;

    private ContentQueryMap mContentQueryMap;

    private Observer mSettingsObserver;
    private String mKey;

    private long mSimId = -1;
    private int mCurOrientation;
    private TelephonyManager mTeleManager;
    private Cursor mSettingsCursor;
    
    private String mSIMSelectorTitle;
    
    private static final int SINGLE_SIMCARD = 1;
    private int mSelectRingtongType = -1;
    
    public static final int RINGTONE_INDEX = 1;
    public static final int VIDEO_RINGTONE_INDEX = 2;
    public static final int SIP_RINGTONE_INDEX = 3;
    public static final int CONFIRM_FOR_SIM_SLOT_ID_REQUEST = 124;

    /**
     * If Silent Mode, remove all sound selections, include Volume, Ringtone,
     * Notifications, touch tones, sound effects, lock sounds. For Volume,
     * Ringtone and Notifications, need to set the profile's Scenario.
     * 
     * @param icicle
     *            the bundle which passed if the fragment recreated
     */
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.edit_profile_prefs);
        mTeleManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        final PreferenceActivity parentActivity = (PreferenceActivity) getActivity();
        final boolean singlePane = parentActivity.onIsHidingHeaders()
                || !parentActivity.onIsMultiPane();
        Bundle bundle;
        if (singlePane) {
            bundle = parentActivity.getIntent().getBundleExtra(
                    PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        } else {
            bundle = this.getArguments();
        }
        Xlog.d(TAG, "onCreate activity = " + parentActivity + ",singlePane = "
                + singlePane + ",bundle = " + bundle + ",this = " + this);

        mKey = bundle.getString("profileKey");

        mProfileManager = (AudioProfileManager) getSystemService(Context.AUDIOPROFILE_SERVICE);
        Scenario scenario = AudioProfileManager.getScenario(mKey);

        mIsSilentMode = scenario.equals(Scenario.SILENT);
        mCurOrientation = this.getResources().getConfiguration().orientation;
        mIsMeetingMode = scenario.equals(Scenario.MEETING);
        mSIMSelectorTitle = getActivity().getString(R.string.settings_label);
        
        initPreference();
    }

    /**
     * return true if the current device is "voice capable"
     * 
     * @return
     */
    private boolean isVoiceCapable() {
        return mTeleManager != null && mTeleManager.isVoiceCapable();
    }

    /**
     * return true if the current device support sms service
     * 
     * @return
     */
    private boolean isSmsCapable() {
        return mTeleManager != null && mTeleManager.isSmsCapable();
    }

    /**
     * Register a contentObserve for CMCC load to detect whether vibrate in
     * silent profile
     */
    @Override
    public void onStart() {

        super.onStart();
        // listen for vibrate_in_silent settings changes
        mSettingsCursor = getContentResolver().query(
                Settings.System.CONTENT_URI, null,
                "(" + Settings.System.NAME + "=?)",
                new String[] { AudioProfileManager.getVibrationKey(mKey) }, null);
        mContentQueryMap = new ContentQueryMap(mSettingsCursor,
                Settings.System.NAME, true, null);
    }

    /**
     * stop sampling and revert the volume(no save)for RingerVolumePreference
     * when the fragment is paused
     */
    @Override
    public void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        Xlog.d(TAG, "onPause");
        if (mVolumePref != null) {
            Xlog.d(TAG, "pref is not null");
            mVolumePref.stopPlaying();
            mVolumePref.revertVolume();
        }
        if (mSettingsObserver != null) {
            mContentQueryMap.deleteObserver(mSettingsObserver);
        }
        if (mSettingsCursor != null) {
            mSettingsCursor.close();
            mSettingsCursor = null;
        }
    }

    /**
     * Init the preference and remove some in the silent profile
     */
    private void initPreference() {
        PreferenceScreen parent = getPreferenceScreen();

        mVibrat = (CheckBoxPreference) findPreference(KEY_VIBRATE);
        mDtmfTone = (CheckBoxPreference) findPreference(KEY_DTMF_TONE);
        mSoundEffects = (CheckBoxPreference) findPreference(KEY_SOUND_EFFECTS);
        mLockSounds = (CheckBoxPreference) findPreference(KEY_LOCK_SOUNDS);
        mHapticFeedback = (CheckBoxPreference) findPreference(KEY_HAPTIC_FEEDBACK);
        mVolumePref = (RingerVolumePreference) findPreference(KEY_VOLUME);
        PreferenceGroup parentRingtone = (PreferenceGroup) findPreference(KEY_CATEGORY_RINGTONE);
        PreferenceGroup parentNotify = (PreferenceGroup) findPreference(KEY_CATEGORY_NOTIFICATION);
        DefaultRingtonePreference notify = (DefaultRingtonePreference) parentNotify
                .findPreference(KEY_NOTIFY);

        // Silent and Meeting Mode remove the following items
        if (mIsSilentMode || mIsMeetingMode) {
            parent.removePreference(mDtmfTone);
            parent.removePreference(mSoundEffects);
            parent.removePreference(mLockSounds);
            parent.removePreference(mVolumePref);
            parent.removePreference(parentRingtone);
            parent.removePreference(parentNotify);
            mVibrat.setEnabled(false);
            return;
        }

        if (mVolumePref != null) {
            mVolumePref.setProfile(mKey);
        }

        if (notify != null) {
            notify.setStreamType(DefaultRingtonePreference.NOTIFICATION_TYPE);
            notify.setProfile(mKey);
            notify.setRingtoneType(AudioProfileManager.TYPE_NOTIFICATION);
            notify.setmNoNeedSIMSelector(true);
        }

        if (isVoiceCapable()) {
            mVoiceRingtone = (DefaultRingtonePreference) parentRingtone
                    .findPreference(KEY_RINGTONE);
            mVideoRingtone = (DefaultRingtonePreference) parentRingtone
                    .findPreference(KEY_VIDEO_RINGTONE);
            mSipRingtone = (DefaultRingtonePreference) parentRingtone
                    .findPreference(KEY_SIP_RINGTONE);

            if (!FeatureOption.MTK_VT3G324M_SUPPORT) {
                parentRingtone.removePreference(mVideoRingtone);
                mVoiceRingtone.setTitle(R.string.ringtone_title);
                mVoiceRingtone.setSummary(R.string.ringtone_summary);
            }

            if (!FeatureOption.MTK_MULTISIM_RINGTONE_SUPPORT ||
                    !SipManager.isVoipSupported(getActivity())) {
                parentRingtone.removePreference(mSipRingtone);
            } else {
                if (mSipRingtone != null) {
                    mSipRingtone
                            .setStreamType(DefaultRingtonePreference.RING_TYPE);
                    mSipRingtone.setProfile(mKey);
                    mSipRingtone
                            .setRingtoneType(AudioProfileManager.TYPE_SIP_CALL);
                    mSipRingtone.setmNoNeedSIMSelector(true);
                }
            }
            
            if (mVoiceRingtone != null) {
                mVoiceRingtone
                        .setStreamType(DefaultRingtonePreference.RING_TYPE);
                mVoiceRingtone.setProfile(mKey);
                mVoiceRingtone
                        .setRingtoneType(AudioProfileManager.TYPE_RINGTONE);
                if (!FeatureOption.MTK_MULTISIM_RINGTONE_SUPPORT) {
                    mVoiceRingtone.setmNoNeedSIMSelector(true);
                }
            }

            if (mVideoRingtone != null) {
                mVideoRingtone
                        .setStreamType(DefaultRingtonePreference.RING_TYPE);
                mVideoRingtone.setProfile(mKey);
                mVideoRingtone
                        .setRingtoneType(AudioProfileManager.TYPE_VIDEO_CALL);
                if (!FeatureOption.MTK_MULTISIM_RINGTONE_SUPPORT) {
                    mVideoRingtone.setmNoNeedSIMSelector(true);
                }
            }
        } else {
            if (!isSmsCapable()) {
                parent.removePreference(mVibrat);
            }
            parent.removePreference(mDtmfTone);
            parent.removePreference(parentRingtone);
        }
    }

    /**
     * Update the preference checked status from framework in onResume().
     */
    private void updatePreference() {
        mVibrat.setChecked(mProfileManager.getVibrationEnabled(mKey));
        mDtmfTone.setChecked(mProfileManager.getDtmfToneEnabled(mKey));
        mSoundEffects.setChecked(mProfileManager.getSoundEffectEnabled(mKey));
        mLockSounds.setChecked(mProfileManager.getLockScreenEnabled(mKey));
        mHapticFeedback.setChecked(mProfileManager
                .getHapticFeedbackEnabled(mKey));
    }

    /**
     * Update the preference checked status
     */
    @Override
    public void onResume() {
        super.onResume();

        updatePreference();

        if (mIsSilentMode) {
            if (mSettingsObserver == null) {
                mSettingsObserver = new Observer() {
                    public void update(Observable o, Object arg) {
                        Xlog.d(TAG, "update");
                        if (mVibrat != null) {
                            final String name = AudioProfileManager.getVibrationKey(mKey);
                            Xlog.d(TAG,"name " + name);
                            String vibrateEnabled = Settings.System.getString(
                                    getContentResolver(), name);
                            if (vibrateEnabled != null) {
                                mVibrat.setChecked("true"
                                        .equals(vibrateEnabled));
                                Xlog.d(TAG,
                                        "vibrate setting is "
                                                + "true".equals(vibrateEnabled));
                            }

                        }
                    }
                };
                mContentQueryMap.addObserver(mSettingsObserver);
            }
        }
    }

    /**
     * called when the preference is clicked
     * 
     * @param preferenceScreen
     *            the clicked preference which will be attached to
     * @param preference
     *            the clicked preference
     * @return true
     */
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        Xlog.d(TAG, "Key :" + preference.getKey());
        if ((preference.getKey()).equals(KEY_VIBRATE)) {
            boolean isVibrate = mVibrat.isChecked();
            Xlog.d(TAG, "set vibrate" + isVibrate);
            mProfileManager.setVibrationEnabled(mKey, isVibrate);
        } else if ((preference.getKey()).equals(KEY_DTMF_TONE)) {
            mProfileManager.setDtmfToneEnabled(mKey, mDtmfTone.isChecked());
        } else if ((preference.getKey()).equals(KEY_SOUND_EFFECTS)) {
            mProfileManager.setSoundEffectEnabled(mKey,
                    mSoundEffects.isChecked());
        } else if ((preference.getKey()).equals(KEY_LOCK_SOUNDS)) {
            mProfileManager.setLockScreenEnabled(mKey, mLockSounds.isChecked());
        } else if ((preference.getKey()).equals(KEY_HAPTIC_FEEDBACK)) {
            mProfileManager.setHapticFeedbackEnabled(mKey,
                    mHapticFeedback.isChecked());
        } else if ((preference.getKey()).equals(KEY_RINGTONE)) {
            setRingtongTypeAndStartSIMSelector(RINGTONE_INDEX);
        } else if ((preference.getKey()).equals(KEY_VIDEO_RINGTONE)) {
            setRingtongTypeAndStartSIMSelector(VIDEO_RINGTONE_INDEX);
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    
    private void setRingtongTypeAndStartSIMSelector(int keyIndex) {
        Xlog.d(TAG, "Selected ringtone type index = " + keyIndex);
        if (FeatureOption.MTK_MULTISIM_RINGTONE_SUPPORT) {
            // add to get  selected SIM slot                     
            List<SIMInfo> simList = SIMInfo.getInsertedSIMList(getActivity());
            int simNum = simList.size();
            Xlog.d(TAG, "simList.size() == " + simNum);

            if (simNum > SINGLE_SIMCARD) {
                mSelectRingtongType = keyIndex;
                setRingtoneType(keyIndex);
                startSIMCardSelectorActivity();
            }
        }
    }
    
    private void setRingtoneType(int keyIndex) {
        switch(keyIndex){
            case RINGTONE_INDEX:
                mVoiceRingtone.setRingtoneType(AudioProfileManager.TYPE_RINGTONE);
                break;
            case VIDEO_RINGTONE_INDEX:
                mVideoRingtone.setRingtoneType(AudioProfileManager.TYPE_VIDEO_CALL);
                break;
            default:
                break;
        }
    }
    
    private void startSIMCardSelectorActivity() {
        Intent intent = new Intent();
        intent.setAction(GeminiUtils.INTENT_CARD_SELECT);
        startActivityForResult(intent, GeminiUtils.REQUEST_SIM_SELECT);
    }

    /**
     * called when rotate the screen
     * 
     * @param newConfig
     *            the current new config
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Xlog.d(TAG, "onConfigurationChanged: newConfig = " + newConfig
                + ",mCurOrientation = " + mCurOrientation + ",this = " + this);
        super.onConfigurationChanged(newConfig);
        if (newConfig != null && newConfig.orientation != mCurOrientation) {
            mCurOrientation = newConfig.orientation;
        }
        this.getListView().clearScrapViewsIfNeeded();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Xlog.d(TAG, "onActivityResult " + "requestCode " + requestCode + " " + resultCode + "resultCode");
        if (requestCode == GeminiUtils.REQUEST_SIM_SELECT) {
            if (resultCode == Activity.RESULT_OK) {
                mSimId = data.getLongExtra(GeminiUtils.EXTRA_SIMID, -1);
                setRingtoneSIMId(mSimId);
            } 
            Xlog.v(TAG, "Select SIM id = " + mSimId);
        }
    }
    
    private void setRingtoneSIMId(long simId) {
        switch(mSelectRingtongType){
            case RINGTONE_INDEX:
                mVoiceRingtone.setSimId(simId);
                mVoiceRingtone.simSelectorOnClick();
                break;
            case VIDEO_RINGTONE_INDEX:
                mVideoRingtone.setSimId(simId);
                mVideoRingtone.simSelectorOnClick();
                break;
            default:
                break;
        }        
    }
}
