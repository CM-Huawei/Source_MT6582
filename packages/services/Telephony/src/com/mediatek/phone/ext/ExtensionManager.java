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

import android.content.Context;

import com.android.phone.PhoneGlobals;

import com.mediatek.phone.PhoneLog;
import com.mediatek.pluginmanager.Plugin;
import com.mediatek.pluginmanager.Plugin.ObjectCreationException;
import com.mediatek.pluginmanager.PluginManager;

import java.util.LinkedList;

public final class ExtensionManager {

    private static final String LOG_TAG = "ExtensionManager";

    private static ExtensionManager sInstance;
    private PhonePluginExtensionContainer mPhonePluginExtContainer;
    private LinkedList<IPhonePlugin> mIPhonePluginList;

    private ExtensionManager() {
        mPhonePluginExtContainer = new PhonePluginExtensionContainer();
        initContainerByPlugin();
    }

    /**
     * 
     * @return ExtensionManager
     */
    public static ExtensionManager getInstance() {
        if (null == sInstance) {
            sInstance = new ExtensionManager();
        }
        return sInstance;
    }

    /**
     * 
     * @return VTInCallScreenFlagsExtension
     */
    public VTInCallScreenFlagsExtension getVTInCallScreenFlagsExtension() {
        log("getVTInCallScreenFlagsExtension()");
        return mPhonePluginExtContainer.getVTInCallScreenFlagsExtension();
    }

    /**
     *
     * @return CallNotifierExtension
     */
    public CallNotifierExtension getCallNotifierExtension() {
        log("getCallNotifierExtension()");
        return mPhonePluginExtContainer.getCallNotifierExtension();
    }

    /**
     * Get PhoneGlobalsBroadcastReceiver extension object
     * @return PhoneGlobalsBroadcastReceiver extension object
     */
    public PhoneGlobalsExtension getPhoneGlobalsExtension() {
        log("PhoneGlobalsBroadcastReceiverExtension()");
        return mPhonePluginExtContainer.getPhoneGlobalsExtension();
    }

    /**
     * Get PhoneGlobalsBroadcastReceiver extension object
     * @return PhoneGlobalsBroadcastReceiver extension object
     */
    public PhoneGlobalsBroadcastReceiverExtension getPhoneGlobalsBroadcastReceiverExtension() {
        log("PhoneGlobalsBroadcastReceiverExtension()");
        return mPhonePluginExtContainer.getPhoneGlobalsBroadcastReceiverExtension();
    }

    /**
     * Get EmergencyDialer extension object
     * @return EmergencyDialer extension object
     */
    public EmergencyDialerExtension getEmergencyDialerExtension() {
        log("getEmergencyDialerExtension()");
        return mPhonePluginExtContainer.getEmergencyDialerExtension();
    }

    /**
     * Get PhoneCallOptionHandlerExtension extension object
     * @return PhoneCallOptionHandlerExtension extension object
     */
    public PhoneCallOptionHandlerExtension getPhoneCallOptionHandlerExtension() {
        log("getPhoneCallOptionHandlerExtension()");
        return mPhonePluginExtContainer.getPhoneCallOptionHandlerExtension();
    }

    /**
     * Get PhoneCallOptionHandlerFactoryExtension extension object
     * @return PhoneCallOptionHandlerFactoryExtension extension object
     */
    public PhoneCallOptionHandlerFactoryExtension getPhoneCallOptionHandlerFactoryExtension() {
        log("getPhoneCallOptionHandlerFactoryExtension()");
        return mPhonePluginExtContainer.getPhoneCallOptionHandlerFactoryExtension();
    }

    /**
     * 
     * @return OthersSettingsExtension
     */
    public OthersSettingsExtension getOthersSettingsExtension() {
        log("getOthersSettingsExtension()");
        return mPhonePluginExtContainer.getOthersSettingsExtension();
    }

    /**
     * 
     * @return SettingsExtension
     */
    public SettingsExtension getSettingsExtension() {
        log("getSettingsExtension()");
        return mPhonePluginExtContainer.getSettingsExtension();
    }

    /**
     * @return MmiCodeExtension
     */
    public MmiCodeExtension getMmiCodeExtension() {
        log("getMmiCodeExtension");
        return mPhonePluginExtContainer.getMmiCodeExtension();
    }

    private void initContainerByPlugin() {
        PluginManager<IPhonePlugin> pm = PluginManager.<IPhonePlugin>create(
                PhoneGlobals.getInstance(), IPhonePlugin.class.getName());

        final int pluginCount = pm.getPluginCount();
        if (pluginCount == 0) {
            log("No Plugin API, use PhonePluginDefault");
            mPhonePluginExtContainer.addExtensions(new PhonePluginDefault());
            return;
        }

        try {
            for (int i = 0; i < pluginCount; ++i) {
                Plugin<IPhonePlugin> plugIn = pm.getPlugin(i);
                if (null != plugIn) {
                    log("create plugin object, number = " + (i + 1));
                    IPhonePlugin phonePluginObject = plugIn.createObject();
                    mPhonePluginExtContainer.addExtensions(phonePluginObject);
                }
            }
        } catch (ObjectCreationException e) {
            log("create plugin object failed");
            e.printStackTrace();
        }
    }

    /**
     * M: create OP09Settings plugin object
     * @param context Context
     * @return IDataConnection
     */
    public static IDataConnection getDataConnectionPlugin(Context context) {
        IDataConnection ext;
        try {
            ext = (IDataConnection)PluginManager.createPluginObject(context,
                IDataConnection.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultDataConnection();
        }
        return ext;
    }
     /**
     * M: create OP09Settings plugin object
     *
     * @param context
     *            Context
     * @return ICallSettingsConnection
     */
    public static ICallSettingsConnection getCallSettingsPlugin(Context context) {
        ICallSettingsConnection ext;
        try {
            ext = (ICallSettingsConnection) PluginManager.createPluginObject(context,
                    ICallSettingsConnection.class.getName());
            } catch (Plugin.ObjectCreationException e) {
            ext = new DefaultCallSettingsConnection();
        }
        return ext;
    }

    /**
     * @return IDefaultSimPreferenceExt
     */
    public static IDefaultSimPreferenceExt getDefaultSimPreferenceExt(Context context) {
        log("getDefaultSimPreferenceExt()");
        IDefaultSimPreferenceExt mIDefaultSimPreferenceExt;
        try {
            mIDefaultSimPreferenceExt = (IDefaultSimPreferenceExt) PluginManager
                    .createPluginObject(context, IDefaultSimPreferenceExt.class.getName());
        } catch (Plugin.ObjectCreationException e) {
            mIDefaultSimPreferenceExt = new DefaultSimPreferenceExtension(context);
        }
        return mIDefaultSimPreferenceExt;
    }

    private static void log(String msg) {
        PhoneLog.d(LOG_TAG, msg);
    }
}
