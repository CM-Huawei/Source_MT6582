
package com.mediatek.incallui.wrapper;

import com.android.incallui.Log;
import com.android.internal.telephony.PhoneConstants;

public class FeatureOptionWrapper {

    private static final String TAG = "FeatureOptionWrapper";
    private FeatureOptionWrapper() {
    }

    /**
     * @see FeatureOption.MTK_GEMINI_SUPPORT
     * @see FeatureOption.MTK_GEMINI_3SIM_SUPPORT
     * @see FeatureOption.MTK_GEMINI_4SIM_SUPPORT
     * @return true if the device has 2 or more slots
     */
    public static boolean isSupportGemini() {
        return PhoneConstants.GEMINI_SIM_NUM >= 2;
    }

    /**
     * @return MTK_PHONE_VOICE_RECORDING
     */
    public static boolean isSupportPhoneVoiceRecording() {
//        return com.mediatek.featureoption.FeatureOption.MTK_PHONE_VOICE_RECORDING;
        return true;
    }


    public static final boolean MTK_HDMI_SUPPORT =
            com.mediatek.common.featureoption.FeatureOption.MTK_HDMI_SUPPORT;
    public static final boolean MTK_SMARTBOOK_SUPPORT =
            com.mediatek.common.featureoption.FeatureOption.MTK_SMARTBOOK_SUPPORT;
    public static final boolean MTK_PHONE_VT_VOICE_ANSWER =
            com.mediatek.common.featureoption.FeatureOption.MTK_PHONE_VT_VOICE_ANSWER;
    public static final boolean MTK_VT3G324M_SUPPORT =
            com.mediatek.common.featureoption.FeatureOption.MTK_VT3G324M_SUPPORT;

    public static boolean isSupportVoiceUI() {
        boolean isSupport = com.mediatek.common.featureoption.FeatureOption.MTK_VOICE_UI_SUPPORT;
        Log.d(TAG, "isSupportVoiceUI: " + isSupport);
        return isSupport;
    }

    public static boolean isSupportVT() {
        boolean isSupport = com.mediatek.common.featureoption.FeatureOption.MTK_VT3G324M_SUPPORT;
        return isSupport;
    }

    public static boolean isSupportVTVoiceAnswer() {
        boolean isSupport = com.mediatek.common.featureoption.FeatureOption.MTK_PHONE_VT_VOICE_ANSWER;
        Log.d(TAG, "isSupportVTVoiceAnswer: " + isSupport);
        return isSupport;
    }

    public static boolean isSupportDualTalk() {
        boolean isSupportDualTalk = com.mediatek.common.featureoption.FeatureOption.MTK_DT_SUPPORT;
        Log.d(TAG, "isSupportDualTalk: " + isSupportDualTalk);
        return isSupportDualTalk;
    }

    public static boolean isSupportPrivacyProtect() {
        boolean isSupportPrivacyProtect = com.mediatek.common.featureoption.FeatureOption.MTK_PRIVACY_PROTECTION_LOCK;
        return isSupportPrivacyProtect;
    }
}

