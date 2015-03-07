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

package com.mediatek.phone.vt;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.Constants;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import com.mediatek.phone.DualTalkUtils;
import com.mediatek.phone.GeminiConstants;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.PhoneFeatureConstants.FeatureOption;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.recording.PhoneRecorderHandler;
import com.mediatek.phone.wrapper.CallManagerWrapper;
import com.mediatek.settings.VTAdvancedSetting;
import com.mediatek.settings.VTSettingUtils;
import com.mediatek.settings.VTSettingUtils.Listener;
import com.mediatek.telephony.PhoneNumberUtilsEx;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.vt.VTManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public final class VTCallUtils {

    private static final String LOG_TAG = "VTCallUtils";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    /**
     * Video Call will control some resource, such as Camera, Media. So Phone App will broadcast
     * Intent to other APPs before acquire and after release the resource. Intent action: Before -
     * "android.phone.extra.VT_CALL_START" After - "android.phone.extra.VT_CALL_END"
     */
    public static final String VT_CALL_START = "android.phone.extra.VT_CALL_START";
    public static final String VT_CALL_END = "android.phone.extra.VT_CALL_END";

    // "chmod" is a command to change file permission, 6 is for User, 4 is for Group
    private static final String CHANGE_FILE_PERMISSION = "chmod 640 ";

    private static final int BITMAP_COMPRESS_QUALITY = 100;
    //Change Feature: to indicate whether the VT call is dial out with speaker off;
    private static boolean sDialWithSpeakerOff = false;

    private VTCallUtils() {
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    /**
     * check VT image files, create if no files
     * NOTE: asynchronous method
     */
    public static void checkVTFileAsync() {
        if (DBG) {
            log("start checkVTFile() !");
        }
        new Thread(new Runnable() {

            @Override
            public void run() {
                checkDefaultPictureFile();
                int[] slots = GeminiUtils.getSlots();
                for (int slot : slots) {
                    checkUserSelectPictureFile(slot);
                }
                if (DBG) {
                    log("end checkVTFile() ! ");
                }
            }
        }).start();
    }

    private static void checkDefaultPictureFile() {
        if (!(new File(VTAdvancedSetting.getPicPathDefault()).exists())) {
            if (DBG) {
                log("checkVTFile() : the default pic file not exists , create it ! ");
            }
            generateVTDefaultFile(VTAdvancedSetting.getPicPathDefault());
        }
        if (!(new File(VTAdvancedSetting.getPicPathDefault2()).exists())) {
            if (DBG) {
                log("checkVTFile() : the default pic2 file not exists , create it ! ");
            }
            generateVTDefaultFile(VTAdvancedSetting.getPicPathDefault2());
        }
    }

    private static void checkUserSelectPictureFile(final int slotId) {
        if (!(new File(VTAdvancedSetting.getPicPathUserselect(slotId)).exists())) {
            if (DBG) {
                log("checkVTFile() : the default user select pic file not exists , create it ! ");
            }
            generateVTDefaultFile(VTAdvancedSetting.getPicPathUserselect(slotId));
        }

        if (!(new File(VTAdvancedSetting.getPicPathUserselect2(slotId)).exists())) {
            if (DBG) {
                log("checkVTFile() : the default user select pic2 file not exists , create it ! ");
            }
            generateVTDefaultFile(VTAdvancedSetting.getPicPathUserselect2(slotId));
        }

        if (DBG) {
            log("end checkVTFile() ! ");
        }
    }

    /**
     * generate the default VT bitmap under the given path
     * @param path
     */
    private static void generateVTDefaultFile(String path) {
        if (DBG) {
            log("generateVTDefaultFile, path = " + path);
        }
        Bitmap bitmap = BitmapFactory.decodeResource(PhoneGlobals.getInstance().getResources(),
                R.drawable.vt_incall_pic_qcif);
        try {
            VTCallUtils.saveMyBitmap(path, bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(bitmap != null) {
                bitmap.recycle();
                if (DBG) {
                    log(" - Bitmap.isRecycled() : " + bitmap.isRecycled());
                }
            }
        }
    }

    /**
     * Create, compress and change contribute of specified bitmap file
     * @param bitName file name
     * @param bitmap Bitmap object to save
     * @throws IOException file operation exception
     */
    public static void saveMyBitmap(String bitName, Bitmap bitmap) throws IOException {
        if (DBG) {
            log("saveMyBitmap()...");
        }

        File file = new File(bitName);
        file.createNewFile();
        FileOutputStream fOut = null;

        try {
            fOut = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, BITMAP_COMPRESS_QUALITY, fOut);
        try {
            fOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (DBG) {
                log("Change file visit right for mediaserver process");
            }
            // Mediaserver process can only visit the file with group permission,
            // So we change here, or else, hide me function will not work
            String command = CHANGE_FILE_PERMISSION + file.getAbsolutePath();
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            e.printStackTrace();
            if (DBG) {
                log("exception happens when change file permission");
            }
        }

        try {
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * VT needs special timing method for different number: In MT call, the timing method is the
     * same as voice call: starting timing when the call state turn to "ACTIVE". In MO call, because
     * of the multimedia ringtone, the timing method is different which we starting timing when
     * receive the Message - VTManager.VT_MSG_START_COUNTER. Because when the multimedia ringtone is
     * playing, the call state is already "ACTIVE", but then we are connecting with the multimedia
     * ringtone server but not the number we dialing. So we must wait to start timing when connected
     * with the number we dialing. The Message VTManager.VT_MSG_START_COUNTER is to tell us that we
     * have connected with the number we dialing. But it is not to follow this method for all
     * numbers in MO call. Some numbers don't need timing - vtNumbers_none []. Some numbers need
     * timing with the voice call method - vtNumbers_default []. You can UPDATE the numbers in them
     * here.
     */

    private static String[] sNumbersNone = { "12531", "+8612531" };
    private static String[] sNumbersDefault = { "12535", "13800100011", "+8612535", "+8613800100011" };

    public static enum VTTimingMode {
        VT_TIMING_NONE, /* VT_TIMING_SPECIAL, */VT_TIMING_DEFAULT
    }

    /**
     * Check video call time mode according to phone number
     * @param number phone number
     * @return video call time mode
     */
    public static VTTimingMode checkVTTimingMode(String number) {
        if (DBG) {
            log("checkVTTimingMode - number:" + number);
        }

        ArrayList<String> arrayListNone = new ArrayList<String>(Arrays.asList(sNumbersNone));
        ArrayList<String> arrayListDefault = new ArrayList<String>(Arrays.asList(sNumbersDefault));

        if (arrayListNone.indexOf(number) >= 0) {
            if (DBG) {
                log("checkVTTimingMode - return:" + VTTimingMode.VT_TIMING_NONE);
            }
            return VTTimingMode.VT_TIMING_NONE;
        }

        if (arrayListDefault.indexOf(number) >= 0) {
            if (DBG) {
                log("checkVTTimingMode - return:" + VTTimingMode.VT_TIMING_DEFAULT);
            }
            return VTTimingMode.VT_TIMING_DEFAULT;
        }

        return VTTimingMode.VT_TIMING_DEFAULT;
    }

    /**
     * Place video call
     * @param phone Phone object
     * @param number phone number
     * @param contactRef contact reference
     * @param slotId sim id
     * @return result of place video call
     */
    public static int placeVTCall(Phone phone, String number, Uri contactRef, int slotId) {
        int status = PhoneUtils.CALL_STATUS_DIALED;
        try {
            if (DBG) {
                log("placeVTCall: '" + number + "'..." + "slotId: " + slotId);
            }

            if (PhoneConstants.State.IDLE != PhoneGlobals.getInstance().mCM.getState()) {
                return Constants.CALL_STATUS_FAILED;
            }
            if (PhoneNumberUtilsEx.isIdleSsString(number)) {
                if (DBG) {
                    log("the number for VT call is idle ss string");
                }
                return Constants.CALL_STATUS_FAILED;
            }
            // In current stage, video call doesn't support uri number
            if (PhoneNumberUtils.isUriNumber(number) || phone instanceof SipPhone) {
                if (DBG) {
                    log("the number for VT call is idle uri string");
                }
                return Constants.CALL_STATUS_FAILED;
            }

            /// For ALPS01071164. @{
            int csNetType = getCSNetType(slotId);
            /// @}

            if ((1 == csNetType) || (2 == csNetType)) {
                return Constants.CALL_STATUS_DROP_VOICECALL;
            }

            PhoneUtils.placeCallRegister(phone);
            Connection conn = vtDial(phone, number, slotId);
            if (DBG) {
                log("vtDial() returned: " + conn);
            }
            if (conn == null) {
                if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                    // On GSM phones, null is returned for MMI codes
                    if (DBG) {
                        log("dialed MMI code: " + number);
                    }
                    status = PhoneUtils.CALL_STATUS_DIALED_MMI;
                    //Temp Delete For Build Error
                    //PhoneUtils.setMMICommandToService(number);
                } else {
                    status = Constants.CALL_STATUS_FAILED;
                }
            } else {
                PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_OFFHOOK);

                // phone.dial() succeeded: we're now in a normal phone call.
                // attach the URI to the CallerInfo Object if it is there,
                // otherwise just attach the Uri Reference.
                // if the uri does not have a "content" scheme, then we treat
                // it as if it does NOT have a unique reference.
                if ((contactRef != null) && (contactRef.getScheme().equals(ContentResolver.SCHEME_CONTENT))) {
                    Object userDataObject = conn.getUserData();
                    if (userDataObject == null) {
                        conn.setUserData(contactRef);
                    } else {
                        // TODO: This branch is dead code, we have
                        // just created the connection 'cn' which has
                        // no user data (null) by default.
                        if (userDataObject instanceof CallerInfo) {
                            ((CallerInfo) userDataObject).contactRefUri = contactRef;
                        } else {
                            ((PhoneUtils.CallerInfoToken) userDataObject).currentInfo.contactRefUri = contactRef;
                        }
                    }
                }
            }
        } catch (CallStateException ex) {
            Log.w(LOG_TAG, "Exception from vtDial()", ex);
            status = Constants.CALL_STATUS_FAILED;
        }

        /// M: For VT @{
        // if status == Constants.CALL_STATUS_FAILED, 
        if (status != Constants.CALL_STATUS_FAILED) {
            // here means we already dial VT Call successfully, then we can do VT Call related operations.

            // update VTSettings and push them to InCallUI.
            VTSettingUtils.getInstance().pushVTSettingParams(slotId);

            // update VTInCallScreenFlags.
            VTInCallScreenFlags.getInstance().reset();
            if (!VTInCallScreenFlags.getInstance().mVTInControlRes) {
                PhoneGlobals.getInstance().sendBroadcast(new Intent(VTCallUtils.VT_CALL_START));
                VTInCallScreenFlags.getInstance().mVTInControlRes = true;
            }
            VTInCallScreenFlags.getInstance().mVTIsMT = false;
            VTInCallScreenFlags.getInstance().mVTSlotId = slotId;

            VTSettingUtils.getInstance().updateVTEngineerModeValues();

            VTInCallScreenFlags.getInstance().mVTPeerBigger = VTSettingUtils.getInstance().mPeerBigger;

            // set VTOpen() here.
            VTManagerWrapper.getInstance().setVTOpen(PhoneGlobals.getInstance().getBaseContext(), slotId);

            // we should notify InCallUI to update related flags for MO.
            if (VTSettingUtils.getInstance().getListener() != null) {
                VTSettingUtils.getInstance().getListener().dialVTCallSuccess();
            }

            /// DM lock @{
            if (PhoneUtils.isDMLocked()) {
                if (VDBG) {
                    log("- Now DM locked, VTManager.getInstance().lockPeerVideo() start");
                }
                VTManager.getInstance().lockPeerVideo();
                if (VDBG) {
                    log("- Now DM locked, VTManager.getInstance().lockPeerVideo() end");
                }
            }
            /// @}

        }
        /// @}

        return status;
    }

    /**
     * do init operations before answer VT call.
     */
    public static void internalAnwerVTCallPre() {
        log("internalAnwerVTCallPre() ...");

        // refresh VTInCallScreenFlags.
        // Note we should call reset() before mVTShouldCloseVTManager = false, or it will become useless for 1A + 1R.
        VTInCallScreenFlags.getInstance().reset();
        VTInCallScreenFlags.getInstance().mVTIsMT = true;

        if (isVTActive()) {
            // For VT, we must call VTManager.setVTOpen() / setVTReady() / setVTConnect() /
            //         onDisconnect() / setVTClose() sequentially (just only once) for one VT call.
            // So before we answer a new ringing VT call, we must call those uncalled methods for the former active VT call here.
            // But normally we closeVTManager(VTManager.onDisconnect() && VTManager.setVTClose()) in CallNotifier.onDisconnect().
            // So if we called them here already, we must skip call them in CallNotifier.onDisconnect().
            // Flag of mVTShouldCloseVTManager is used for this.
            closeVTManager();
            if (DBG) {
                log("anwerVTCallPre:"
                    + " set VTInCallScreenFlags.getInstance().mVTShouldCloseVTManager = false");
            }
            VTInCallScreenFlags.getInstance().mVTShouldCloseVTManager = false;
            VTInCallScreenFlags.getInstance().resetPartial();
        }

        // hangup all calls except current ringing call.
        Call ringingCall = PhoneGlobals.getInstance().mCM.getFirstActiveRingingCall();
        if (DualTalkUtils.isSupportDualTalk()) {
            DualTalkUtils dt = DualTalkUtils.getInstance();
            if (dt.hasMultipleRingingCall()) {
                ringingCall = dt.getFirstActiveRingingCall();
            }
        }
        PhoneUtils.hangupAllCalls(true, ringingCall);

        // call VTManager's function
        VTManagerWrapper.getInstance().incomingVTCall(1);

        // speaker related operations are handled later in VTManagerWrapper.setVTReady, so no need do it here.

        SimInfoRecord simInfo = PhoneUtils.getSimInfoByCall(ringingCall);
        if (null != simInfo) {
            VTInCallScreenFlags.getInstance().mVTSlotId = simInfo.mSimSlotId;
        } else {
            log("internalAnswerVTCallPre(), accept a incoming call," +
                    " but can not get ring call sim info, sim info is null,  need to check !!!!!");
            return;
        }

        // before really answer VT Call, update VTSettings and push them to InCallUI. 
        VTSettingUtils.getInstance().pushVTSettingParams(simInfo.mSimSlotId);

        VTManagerWrapper.getInstance().setVTOpen(PhoneGlobals.getInstance().getBaseContext(), simInfo.mSimSlotId);

        // set local video or picture based on VTSetting. if selected as "ASK" in VTSetting, will do this in InCallUI.
        if (!"0".equals(VTSettingUtils.getInstance().mShowLocalMT)) {
            updatePicToReplaceLocalVideo();
        }

        /// DM Lock Feature @{
        if (PhoneUtils.isDMLocked()) {
            if (VDBG) {
                log("- Now DM locked, VTManager.getInstance().lockPeerVideo() start");
            }
            VTManager.getInstance().lockPeerVideo();
            if (VDBG) {
                log("- Now DM locked, VTManager.getInstance().lockPeerVideo() end");
            }
        }
        /// @}

        // we are ready to answer VT Call, notify InCallUI to do some init for it.
        if (VTSettingUtils.getInstance().getListener() != null) {
            VTSettingUtils.getInstance().getListener().answerVTCallPre();
        }

    }

    public static void closeVTManager() {
        if (DBG) {
            log("closeVTManager()!");
        }

        if (!PhoneGlobals.getInstance().notifier.isBluetoothAvailable()
                || !PhoneGlobals.getInstance().notifier.isBluetoothAudioConnected()) {
             // while bluetooth audio is connected, we should not call turnOnSpeaker(), which will cancel BT force use;
             // Or when active + Ringing VT, accept Ringing and end active, the audio will come from earpiece, not BT.
             log("bluetooth audio is not connected, turn off speaker");
             PhoneUtils.turnOnSpeaker(PhoneGlobals.getInstance(), false, true);
        }

        // stop record first.
        if (FeatureOption.MTK_PHONE_VOICE_RECORDING) {
            if (PhoneRecorderHandler.getInstance().isVTRecording()) {
                PhoneRecorderHandler.getInstance().stopVideoRecord();
            }
        }

        VTManagerWrapper.getInstance().onDisconnected();

        VTManagerWrapper.getInstance().setVTClose();
    }

    /**
     * The function to judge whether the call is video call
     * @param call Call object
     * @return true yes false no
     */
    public static boolean isVideoCall(Call call) {
        if (null == call) {
            return false;
        }
        if (null == call.getLatestConnection()) {
            return false;
        }
        return call.getLatestConnection().isVideo();
    }

    public static boolean isVTIdle() {
        if (!FeatureOption.MTK_VT3G324M_SUPPORT) {
            return true;
        }
        if (PhoneConstants.State.IDLE == PhoneGlobals.getInstance().mCM.getState()) {
            return true;
        }
        Phone phone = PhoneGlobals.getInstance().phone;
        if (PhoneConstants.State.IDLE == phone.getState()) {
            return true;
        } else if (phone.getForegroundCall().getState().isAlive()) {
            if (phone.getForegroundCall().getLatestConnection().isVideo()) {
                return false;
            }
        } else if (phone.getRingingCall().getState().isAlive()) {
            if (phone.getRingingCall().getLatestConnection().isVideo()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isVTActive() {
        if (!FeatureOption.MTK_VT3G324M_SUPPORT) {
            return false;
        }
        Phone phone = PhoneGlobals.getInstance().phone;
        if (Call.State.ACTIVE == phone.getForegroundCall().getState()) {
            if (phone.getForegroundCall().getLatestConnection().isVideo()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVTCallActive() {
        if (!FeatureOption.MTK_VT3G324M_SUPPORT) {
            return false;
        }
        Phone phone = PhoneGlobals.getInstance().phone;
        if (GeminiUtils.isGeminiSupport()) {
            CallManager cm = PhoneGlobals.getInstance().mCM;
            if (null != cm.getActiveFgCall()) {
                if (Call.State.ACTIVE == cm.getActiveFgCall().getState()) {
                    if (cm.getActiveFgCall().getLatestConnection().isVideo()) {
                        return true;
                    }
                }
            }
        } else {
            if (Call.State.ACTIVE == phone.getForegroundCall().getState()) {
                if (phone.getForegroundCall().getLatestConnection().isVideo()) {
                    return true;
                }
            }
        }
        return false;
    }


    public static boolean isVTRinging() {
        if (!FeatureOption.MTK_VT3G324M_SUPPORT) {
            return false;
        }
        if (PhoneConstants.State.RINGING != PhoneGlobals.getInstance().mCM.getState()) {
            return false;
        }
        Call ringCall = null;
        DualTalkUtils dt = DualTalkUtils.getInstance();
        if (DualTalkUtils.isSupportDualTalk() && dt != null
                && dt.hasMultipleRingingCall()) {
            ringCall = dt.getFirstActiveRingingCall();
        } else {
            ringCall = PhoneGlobals.getInstance().mCM.getFirstActiveRingingCall();
        }
        
        if (!ringCall.isRinging()) {
            return false;
        }
        
        return PhoneUtils.isVideoCall(ringCall);
    }

    /// M:Gemini+ @ {
    private static Connection vtDial(Phone phone, String number, int slotId)
            throws CallStateException {
        Connection conn = CallManagerWrapper.vtDial(phone, number, slotId);
        return conn;
    }
    /// @ }

    /**
     * to indicate whether the VT call is dial out with headset/bluetooth pluged
     *
     * @return sDialWithSpeakerOff
     */
    public static boolean isVTDialWithSpeakerOff() {
        if (!FeatureOption.MTK_VT3G324M_SUPPORT) {
            return false;
        }
        return sDialWithSpeakerOff;
    }

    /**
     * change feature: to set VT is dial out with headset pluged state
     */
    public static void setVTDialWithSpeakerOff(boolean off) {
        sDialWithSpeakerOff = off;
    }

    /**
     * Update the setting value to VTManager.
     * MO: 0 is show video; 1 is show image (show default picture);
     * MT: 0 is show video; 1 is show image; 2 show hide/show Dialog.
     *
     * @see VTManager#setLocalView(int, String)
     */
    public static void updateLocalViewToVTManager() {
        if (!VTInCallScreenFlags.getInstance().mVTIsMT) {
            PhoneLog.d(LOG_TAG, "updateLocalViewToVTManager: MO" + VTSettingUtils.getInstance().mShowLocalMO);
            // Video Call Settings -> Outgoing video call (true-video, false-picture)
            if (VTSettingUtils.getInstance().mShowLocalMO) {
                VTManager.getInstance().setLocalView(0, "");
            } else {
                updatePicToReplaceLocalVideo();
            }
        } else {
            PhoneLog.d(LOG_TAG, "updateLocalViewToVTManager: MT " + VTSettingUtils.getInstance().mShowLocalMT);
            // Video Call Settings -> Video Incoming Call (0-video, 1-ask, 2-picture)
            if (VTSettingUtils.getInstance().mShowLocalMT.equals("0")) {
                VTManager.getInstance().setLocalView(0, "");
            } else if (VTSettingUtils.getInstance().mShowLocalMT.equals("1")) {
                // Show VTInCallScreen#mVTMTAsker, Before user selection, do nothing
            } else {
                updatePicToReplaceLocalVideo();
            }
        }
    }

    /**
     * This method is called when user set Hide Local Video. It will request
     * VTManager to show a picture instead of Video. 
     *
     * Video Call Settings -> Local Video replacement (0-default, 1-freeze me, 2-my picture)
     * 
     * @see VTManager#setLocalView(int, String)
     */
    public static void updatePicToReplaceLocalVideo() {
        PhoneLog.d(LOG_TAG, "updatePicToReplaceLocalVideo: " + VTSettingUtils.getInstance().mPicToReplaceLocal);
        if (VTSettingUtils.getInstance().mPicToReplaceLocal.equals("2")) { // my setting picture
            VTManager.getInstance().setLocalView(
                    1, VTAdvancedSetting.getPicPathUserselect(VTInCallScreenFlags.getInstance().mVTSlotId));
        } else if (VTSettingUtils.getInstance().mPicToReplaceLocal.equals("1")) { // freeze me picture.
            VTManager.getInstance().setLocalView(2, "");
        } else { // default picture
            VTManager.getInstance().setLocalView(1, VTAdvancedSetting.getPicPathDefault());
        }
    }

/**
     * Get the cs network type from SystemProperties.
     * @return cs network type.
     */
    public static int getCSNetType(int slotId) {
        int csNetType = 0; // so,csNetType: 1-GSM, 2-GPRS
        /// M:Gemini+. @{
        final int index = GeminiUtils.getIndexInArray(slotId, GeminiUtils.getSlots());
        if (index != -1) {
            csNetType = SystemProperties.getInt(GeminiConstants.PROPERTY_CS_NETWORK_TYPES[index], -1);
        }
        /// @}
        if (DBG) {
            log("==> getCSNetType(): csNetType: " + csNetType + " index=" + index);
        }
        return csNetType;
    }

    /**
     *  Whether auto drop back this video call.
     * @return If the local is out of 3G Service and AutoDropBack in settings is true, we need return true.
     */
    public static boolean isAutoDropBack(int slotId) {
        int csNetType = getCSNetType(slotId);
        SharedPreferences sp = PhoneGlobals.getInstance().getApplicationContext()
                .getSharedPreferences("com.android.phone_preferences", Context.MODE_PRIVATE);
        if (null == sp) {
            if (DBG) {
                log("isAutoDropBack() : can not find 'com.android.phone_preferences'...");
            }
            return false;
        }
        boolean autoDropBack = sp.getBoolean("button_vt_auto_dropback_key_" + slotId, false);
        if (((1 == csNetType) || (2 == csNetType)) && autoDropBack) {
            log("Need drop voice call!");
            return true;
        }
        return false;
    }

    /**
     * Handle the case of vt call auto drop back.
     *
     * @param slotId the specify slot id used to dial vt call
     * @return true: the video call is not needed drop into voice call, false:
     *         The video call will be replaced by a voice call.
     */
    public static boolean handleVTCallAutoDropBack(int slotId, Intent intent) {
        if (VTCallUtils.isAutoDropBack(slotId)) {
            intent.putExtra(Constants.EXTRA_IS_VIDEO_CALL, false);
            String toastString = PhoneGlobals.getInstance().getApplicationContext().getResources()
                    .getString(R.string.vt_voice_connecting);
            Toast.makeText(PhoneGlobals.getInstance(), toastString, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    public static void makeVoiceReCall(final String number, final int slot) {
        log("makeVoiceReCall(), number is " + number + " slot is " + slot);
        final Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
        intent.putExtra(Constants.EXTRA_SLOT_ID, slot);
        intent.putExtra(Constants.EXTRA_INTERNATIONAL_DIAL_OPTION, Constants.INTERNATIONAL_DIAL_OPTION_IGNORE);
        intent.putExtra(Constants.EXTRA_VT_MAKE_VOICE_RECALL, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PhoneGlobals.getInstance().startActivity(intent);
    }
}
