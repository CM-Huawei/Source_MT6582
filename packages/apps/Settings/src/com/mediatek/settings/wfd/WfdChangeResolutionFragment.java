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

package com.mediatek.settings.wfd;

import static com.mediatek.settings.wfd.WfdSettingsExt.DEVICE_720P_RESOLUTION_LIST;
import static com.mediatek.settings.wfd.WfdSettingsExt.DEVICE_1080P_RESOLUTION_LIST;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.Settings;

import com.android.settings.R;
import com.mediatek.xlog.Xlog;
/**
 * Dialog fragment for setting the discoverability timeout.
 */
public final class WfdChangeResolutionFragment extends DialogFragment
        implements DialogInterface.OnClickListener {
    private static final String TAG = "WfdChangeResolutionFragment";
    
    private int mCurrentResolution = 0;
    private int mWhichIndex = 0;
    private boolean m720PDeviceConfiguration = true;
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mCurrentResolution = Settings.Global.getInt(getActivity().getContentResolver(), 
                Settings.Global.WIFI_DISPLAY_RESOLUTION, 0);
        Xlog.d(TAG, "create dialog, current resolution is " + mCurrentResolution);
        
        m720PDeviceConfiguration = DEVICE_720P_RESOLUTION_LIST.contains(mCurrentResolution);
        int resolutionArray = m720PDeviceConfiguration ? R.array.wfd_720p_resolution_entry :
                R.array.wfd_1080p_resolution_entry;
        int resolutionIndex = m720PDeviceConfiguration ? 
                DEVICE_720P_RESOLUTION_LIST.indexOf(mCurrentResolution) :
                DEVICE_1080P_RESOLUTION_LIST.indexOf(mCurrentResolution);
        mWhichIndex = resolutionIndex;
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wfd_change_resolution_menu_title)
                .setSingleChoiceItems(resolutionArray,
                 resolutionIndex, this)
                .setPositiveButton(android.R.string.ok, this)
                .create();
    }

    public void onClick(DialogInterface dialog, int which) {
        if(which == DialogInterface.BUTTON_POSITIVE) {
            int userChoice = m720PDeviceConfiguration ? DEVICE_720P_RESOLUTION_LIST.get(mWhichIndex) :
                DEVICE_1080P_RESOLUTION_LIST.get(mWhichIndex);
            Xlog.d(TAG, "User click ok button, set resolution as " + userChoice);
            Settings.Global.putInt(getActivity().getContentResolver(), 
                    Settings.Global.WIFI_DISPLAY_RESOLUTION, userChoice);
        } else {
            mWhichIndex = which;
            Xlog.d(TAG, "User select the item " + mWhichIndex);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!DEVICE_720P_RESOLUTION_LIST.contains(mCurrentResolution) && 
                (!DEVICE_1080P_RESOLUTION_LIST.contains(mCurrentResolution))) {
            dismiss();
        }
    }
}
