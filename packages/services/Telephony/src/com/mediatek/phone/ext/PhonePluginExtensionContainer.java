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

package com.mediatek.phone.ext;

import com.mediatek.phone.PhoneLog;

public class PhonePluginExtensionContainer {

    private static final String LOG_TAG = "PhonePluginExtensionContainer";

    private VTInCallScreenFlagsExtensionContainer mVTInCallScreenFlagsExtensionContainer;
    private CallNotifierExtensionContainer mCallNotifierExtensionContainer;
    private PhoneGlobalsExtensionContainer mPhoneGlobalsExtensionContainer;
    private PhoneGlobalsBroadcastReceiverExtensionContainer mPhoneGlobalsBroadcastReceiverExtensionContainer;
    private EmergencyDialerExtensionContainer mEmergencyDialerExtensionContainer;
    private PhoneCallOptionHandlerExtensionContainer mPhoneCallOptionHandlerExtensionContainer;
    private PhoneCallOptionHandlerFactoryExtensionContainer mPhoneCallOptionHandlerFactoryExtensionContainer;
    private OthersSettingsExtensionContainer mOthersSettingsExtensionContainer;
    private SettingsExtensionContainer mSettingsExtensionContainer;
    private MmiCodeExtensionContainer mMmiCodeExtensionContainer;

    public PhonePluginExtensionContainer() {
        mVTInCallScreenFlagsExtensionContainer = new VTInCallScreenFlagsExtensionContainer();
        mCallNotifierExtensionContainer = new CallNotifierExtensionContainer();
        mPhoneGlobalsExtensionContainer = new PhoneGlobalsExtensionContainer();
        mPhoneGlobalsBroadcastReceiverExtensionContainer = new PhoneGlobalsBroadcastReceiverExtensionContainer();
        mEmergencyDialerExtensionContainer = new EmergencyDialerExtensionContainer();
        mPhoneCallOptionHandlerExtensionContainer = new PhoneCallOptionHandlerExtensionContainer();
        mPhoneCallOptionHandlerFactoryExtensionContainer = new PhoneCallOptionHandlerFactoryExtensionContainer();
        mOthersSettingsExtensionContainer = new OthersSettingsExtensionContainer();
        mSettingsExtensionContainer = new SettingsExtensionContainer();
        mMmiCodeExtensionContainer = new MmiCodeExtensionContainer();
    }

    /**
     * 
     * @return VTInCallScreenFlagsExtension
     */
    public VTInCallScreenFlagsExtension getVTInCallScreenFlagsExtension() {
        log("getVTInCallScreenFlagsExtension()");
        return mVTInCallScreenFlagsExtensionContainer;
    }

    /**
     *
     * @return CallNotifierExtension
     */
    public CallNotifierExtension getCallNotifierExtension() {
        log("getCallNotifierExtension()");
        return mCallNotifierExtensionContainer;
    }

    /**
     * Get PhoneGlobalsExtension object
     * @return PhoneGlobalsExtension
     */
    public PhoneGlobalsExtension getPhoneGlobalsExtension() {
        return mPhoneGlobalsExtensionContainer;
    }

    /**
     * Get PhoneGlobalsBroadcastReceiverExtension object
     * @return PhoneGlobalsBroadcastReceiverExtension object
     */
    public PhoneGlobalsBroadcastReceiverExtension getPhoneGlobalsBroadcastReceiverExtension() {
        log("getPhoneGlobalsBroadcastReceiverExtension()");
        return mPhoneGlobalsBroadcastReceiverExtensionContainer;
    }

    public EmergencyDialerExtension getEmergencyDialerExtension() {
        log("getEmergencyDialerExtension()");
        return mEmergencyDialerExtensionContainer;
    }

    public PhoneCallOptionHandlerExtension getPhoneCallOptionHandlerExtension() {
        log("getPhoneCallOptionHandlerExtension()");
        return mPhoneCallOptionHandlerExtensionContainer;
    }

    public PhoneCallOptionHandlerFactoryExtension getPhoneCallOptionHandlerFactoryExtension() {
        log("getPhoneCallOptionHandlerExtension()");
        return mPhoneCallOptionHandlerFactoryExtensionContainer;
    }

    /**
     *
     * @return mOthersSettingsExtensionContainer
     */
    public OthersSettingsExtension getOthersSettingsExtension() {
        log("getOthersSettingsExtension()");
        return mOthersSettingsExtensionContainer;
    }

    /**
     *
     * @return mSettingsExtensionContainer
     */
    public SettingsExtension getSettingsExtension() {
        log("getSettingsExtension()");
        return mSettingsExtensionContainer;
    }

    /**
     * @return mMmiCodeExtensionContainer
     */
    public MmiCodeExtension getMmiCodeExtension() {
        log("getMmiCodeExtension()");
        return mMmiCodeExtensionContainer;
    }

    /**
     *
     * @param phonePlugin 
     */
    public void addExtensions(IPhonePlugin phonePlugin) {
        log("addExtensions, phone plugin object is " + phonePlugin);

        mVTInCallScreenFlagsExtensionContainer.add(phonePlugin.createVTInCallScreenFlagsExtension());
        mPhoneGlobalsExtensionContainer.add(phonePlugin.createPhoneGlobalsExtension());
        mPhoneGlobalsBroadcastReceiverExtensionContainer.add(phonePlugin.createPhoneGlobalsBroadcastReceiverExtension());
        mEmergencyDialerExtensionContainer.add(phonePlugin.createEmergencyDialerExtension());
        mPhoneCallOptionHandlerExtensionContainer.add(phonePlugin.createPhoneCallOptionHandlerExtension());
        mPhoneCallOptionHandlerFactoryExtensionContainer.add(phonePlugin.createPhoneCallOptionHandlerFactoryExtension());
        mCallNotifierExtensionContainer.add(phonePlugin.createCallNotifierExtension());
        mOthersSettingsExtensionContainer.add(phonePlugin.createOthersSettingsExtension());
        mSettingsExtensionContainer.add(phonePlugin.createSettingsExtension());
        mMmiCodeExtensionContainer.add(phonePlugin.createMmiCodeExtension());
    }

    private static void log(String msg) {
        PhoneLog.d(LOG_TAG, msg);
    }
}
