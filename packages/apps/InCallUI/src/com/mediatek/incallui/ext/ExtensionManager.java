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

package com.mediatek.incallui.ext;

import android.content.Context;

import com.android.incallui.Log;

import com.mediatek.pluginmanager.Plugin;
import com.mediatek.pluginmanager.Plugin.ObjectCreationException;
import com.mediatek.pluginmanager.PluginManager;

import java.util.LinkedList;

public final class ExtensionManager implements IInCallUIPlugin{

    private static ExtensionManager sInstance = null;

    private PluginWrapper mPluginWrapper;

    private ExtensionManager() {
    }

    /**
     * @return ExtensionManager
     */
    public static ExtensionManager getInstance() {
        if (null == sInstance) {
            sInstance = new ExtensionManager();
        }
        return sInstance;
    }

    public void initPlugin(Context context) {
        if (mPluginWrapper == null) {
            mPluginWrapper = new PluginWrapper();

            PluginManager<IInCallUIPlugin> pm = PluginManager.<IInCallUIPlugin> create(
                    context, IInCallUIPlugin.class.getName());

            final int pluginCount = pm.getPluginCount();
            log("InCallUI Plug-in, count=" + pluginCount);
            if (pluginCount == 0) {
                log("initPlugin, no installed Plug-ins, use DEFAULT plugin.");
                mPluginWrapper.addExtensions(new InCallUIPluginDefault());
                return;
            }

            try {
                for (int i = 0; i < pluginCount; ++i) {
                    Plugin<IInCallUIPlugin> plugIn = pm.getPlugin(i);
                    if (null != plugIn) {
                        IInCallUIPlugin phonePluginObject = plugIn.createObject();
                        log("Create Plugin Object: " + phonePluginObject.getClass());
                        mPluginWrapper.addExtensions(phonePluginObject);
                    }
                }
            } catch (ObjectCreationException e) {
                log("create plugin object failed");
                e.printStackTrace();
            }
        }

        log("initPlugin InCallUI Plug-in, size=" + mPluginWrapper.size());
    }

    private void log(String msg) {
        Log.d(this, msg);
    }

    /**
     * Call {@link #initPlugin(Context)} before using this method.
     * @param context
     */
    public CallCardExtension getCallCardExtension() {
        if (mPluginWrapper == null) {
            Log.e(this, "getCallCardExtension failed, mPluginWrapper is null");
            return null;
        }
        return mPluginWrapper.getCallCardExtension();
    }

    /**
     * Call {@link #initPlugin(Context)} before using this method.
     * @param context
     */
    public VTCallExtension getVTCallExtension() {
        if (mPluginWrapper == null) {
            Log.e(this, "getCallCardExtension failed, mPluginWrapper is null");
            return null;
        }
        return mPluginWrapper.getVTCallExtension();
    }

    @Override
    public CallButtonExtension getCallButtonExtension() {
        if (mPluginWrapper == null) {
            Log.e(this, "getCallButtonExtension failed, mPluginWrapper is null");
            return null;
        }
        return mPluginWrapper.getCallButtonExtension();
    }

    @Override
    public InCallUIExtension getInCallUIExtension() {
        if (mPluginWrapper == null) {
            Log.e(this, "getInCallUIExtension failed, mPluginWrapper is null");
            return null;
        }
        return mPluginWrapper.getInCallUIExtension();
    }

    @Override
    public NotificationExtension getNotificationExtension() {
        if (mPluginWrapper == null) {
            Log.e(this, "getNotificationExtension failed, mPluginWrapper is null");
            return null;
        }
        return mPluginWrapper.getNotificationExtension();
    }
}
