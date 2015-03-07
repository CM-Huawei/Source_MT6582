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

package com.mediatek.incallui.vt;

import android.graphics.Bitmap;
import android.util.Log;

import com.android.services.telephony.common.VTSettingParams;
import com.mediatek.incallui.ext.ExtensionManager;

public final class VTInCallScreenFlags {

    private static final String TAG = "VTInCallScreenFlags";

    public boolean mVTIsMT;
    public boolean mVTHideMeNow;
    public boolean mVTFrontCameraNow;
    public boolean mVTSettingReady;
    public boolean mVTSurfaceChangedL;
    public boolean mVTSurfaceChangedH;
    public boolean mVTVideoReady;
    public boolean mVTHasReceiveFirstFrame;
    public boolean mVTInLocalZoomSetting;
    public boolean mVTInLocalBrightnessSetting;
    public boolean mVTInLocalContrastSetting;
    public boolean mVTInControlRes;
    public boolean mVTInEndingCall;
    public boolean mVTShouldCloseVTManager;
    public VTConnectionStarttime mVTConnectionStarttime;
    public boolean mVTInSnapshot;
    public boolean mVTInSwitchCamera;
    public boolean mVTIsInflate;
    public boolean mVTVoiceAnswer;
    public String mVTVoiceAnswerPhoneNumber;
    public int mVTSlotId;
    /** PHONE_VT_STATUS_INFO, is active or disconnect */
    public boolean mVTStatusActive;

    // Flag comes from VTSettingUtils @{
    public String mVTPicToReplaceLocal;
    public String mVTShowLocalMT;
    public String mVTPicToReplacePeer;
    public boolean mVTEnableBackCamera;
    public boolean mVTPeerBigger;
    public boolean mVTShowLocalMO;
    public boolean mVTAutoDropBack;
    public boolean mVTToReplacePeer;
    public Bitmap mVTReplacePeerBitmap;
    // @}

    public void getVTSettingParams(VTSettingParams params) {
        Log.d(TAG, "getVTSettingParams: " + params.toString());
        mVTPicToReplaceLocal = params.mPicToReplaceLocal;
        mVTShowLocalMT = params.mShowLocalMT;
        mVTPicToReplacePeer = params.mPicToReplacePeer;
        mVTEnableBackCamera = params.mEnableBackCamera;
        mVTPeerBigger = params.mPeerBigger;
        mVTShowLocalMO = params.mShowLocalMO;
        mVTAutoDropBack = params.mAutoDropBack;
        mVTToReplacePeer = params.mToReplacePeer;
    }

    public void updateVTHideMeForMO() {
        Log.d(TAG, "updateVTHideMeForMO()...");
        if (!mVTShowLocalMO) {
            Log.d(TAG, "updateVTHideMeForMO()...true");
            mVTHideMeNow = true;
        }
    }

    public void updateVTHideMeForMT() {
        if (!"0".equals(mVTShowLocalMT)) {
            Log.d(TAG, "updateVTHideMeForMT()...true");
            mVTHideMeNow = true;
        }
    }

    private static VTInCallScreenFlags sVTInCallScreenFlags;

    /**
     * Get the VTInCallScreenFlags instance
     * 
     * @return VTInCallScreenFlags instance
     */
    public static VTInCallScreenFlags getInstance() {
        if (sVTInCallScreenFlags==null){
            sVTInCallScreenFlags = new VTInCallScreenFlags();
        }
        return sVTInCallScreenFlags;
    }

    private VTInCallScreenFlags() {
        mVTConnectionStarttime = new VTConnectionStarttime();
    }

    /**
     * reset all flags
     */
    public void reset() {
        Log.d(TAG, "reset()...");
        mVTIsMT = false;
        mVTHideMeNow = false;
        mVTFrontCameraNow = true;
        mVTSettingReady = false;
        mVTSurfaceChangedL = false;
        mVTSurfaceChangedH = false;
        mVTVideoReady = false;
        mVTHasReceiveFirstFrame = false;
        mVTInLocalZoomSetting = false;
        mVTInLocalBrightnessSetting = false;
        mVTInLocalContrastSetting = false;
        mVTInControlRes = false;
        mVTInEndingCall = false;
        mVTShouldCloseVTManager = true;

        if (mVTConnectionStarttime != null) {
            mVTConnectionStarttime.reset();
        }

        mVTInSnapshot = false;
        mVTInSwitchCamera = false;
        mVTVoiceAnswer = false;
        mVTVoiceAnswerPhoneNumber = null;
        mVTSlotId = 0;

        mVTPicToReplaceLocal = null;
        mVTShowLocalMT = null;
        mVTPicToReplacePeer = null;
        mVTEnableBackCamera = false;
        mVTPeerBigger = true;
        mVTShowLocalMO = false;
        mVTAutoDropBack = false;
        mVTToReplacePeer = false;
        mVTReplacePeerBitmap = null;

        ExtensionManager.getInstance().getVTCallExtension().resetFlags();
    }

    /**
     * reset partial flags
     */
    public void resetPartial() {
        mVTIsMT = false;
        mVTHideMeNow = false;
        mVTFrontCameraNow = true;
        mVTSettingReady = false;
        mVTSurfaceChangedL = false;
        mVTSurfaceChangedH = false;
        mVTVideoReady = false;
        mVTHasReceiveFirstFrame = false;
        mVTInLocalZoomSetting = false;
        mVTInLocalBrightnessSetting = false;
        mVTInLocalContrastSetting = false;
        mVTInControlRes = false;
        mVTInEndingCall = false;

        if (mVTConnectionStarttime != null) {
            mVTConnectionStarttime.reset();
        }

        mVTInSnapshot = false;
        mVTInSwitchCamera = false;
        mVTPeerBigger = true;
        mVTVoiceAnswer = false;
        mVTVoiceAnswerPhoneNumber = null;
        mVTSlotId = 0;
    }

    /**
     * reset time related flags
     */
    public void resetTiming() {
        if (mVTConnectionStarttime != null) {
            mVTConnectionStarttime.reset();
        }
    }

    public class VTConnectionStarttime {
//        public Connection mConnection;
        public long mStarttime;

        /**
         * Constructor function
         */
        public VTConnectionStarttime() {
            reset();
        }

        /**
         * reset start time
         */
        public void reset() {
            Log.d("VTConnectionStarttime", "reset...");
//            mConnection = null;
            mStarttime = -1;
        }
    }
}
