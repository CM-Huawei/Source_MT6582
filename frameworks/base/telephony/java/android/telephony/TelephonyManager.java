/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telephony;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.Rlog;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyProperties;


//MTK-START [mtk04070][111116][ALPS00093395]MTK used
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.telephony.ITelephonyEx;
import android.os.Message;
import android.provider.Settings;
//MTK-END [mtk04070][111116][ALPS00093395]MTK used

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IOnlyOwnerSimSupport;
/// @}

/**
 * Provides access to information about the telephony services on
 * the device. Applications can use the methods in this class to
 * determine telephony services and states, as well as to access some
 * types of subscriber information. Applications can also register
 * a listener to receive notification of telephony state changes.
 * <p>
 * You do not instantiate this class directly; instead, you retrieve
 * a reference to an instance through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.TELEPHONY_SERVICE)}.
 * <p>
 * Note that access to some telephony information is
 * permission-protected. Your application cannot access the protected
 * information unless it has the appropriate permissions declared in
 * its manifest file. Where permissions apply, they are noted in the
 * the methods through which you access the protected information.
 */
public class TelephonyManager {
    private static final String TAG = "TelephonyManager";

    private static ITelephonyRegistry sRegistry;
    private final Context mContext;

    //MTK-START [mtk04070][111116][ALPS00093395]Add for supporting Gemini
    /* Add for Gemini Phone2 */
    private static final boolean mtkGeminiSupport = FeatureOption.MTK_GEMINI_SUPPORT;
    private static ITelephonyRegistry sRegistry2; 
    private static ITelephonyRegistry sRegistry3; 
    private static ITelephonyRegistry sRegistry4; 
    private static int defaultSimId = PhoneConstants.GEMINI_SIM_1;
    //MTK-END [mtk04070][111116][ALPS00093395]Add for supporting Gemini
    
    /* max sim number */
   private static final int MAX_SIM1_PDP_NUM = 3;
   private static final int MAX_SIM2_PDP_NUM = 1;
   private static final int MAX_PDP_NUM = 3;
   
   /* signal type */
   /** @hide */
   public static final int RADIO_WCDMA = 1;
   /** @hide */
   public static final int RADIO_MISC = 2;

    /* Add band enumeration for adjusting modem radio power to specific band(s). mtk04070, 20120531 */
    /** @hide */
    public static final int SAR_BAND_GSM_GSM850  = 0;
    /** @hide */
    public static final int SAR_BAND_GSM_EGSM900 = 1;
    /** @hide */
    public static final int SAR_BAND_GSM_DCS1800 = 2;
    /** @hide */
    public static final int SAR_BAND_GSM_PCS1900 = 3;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_IMT_2000 = 4;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_PCS_1900 = 5;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_DCS_1800 = 6;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_AWS_1700 = 7;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_CLR_850  = 8;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_800      = 9;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_IMT_E_2600 = 10;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_GSM_900    = 11;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_1800       = 12;
    /** @hide */
    public static final int SAR_BAND_UMTS_WCDMA_1700       = 13;
    /* Add band enumeration for adjusting modem radio power to specific band(s). mtk04070, 20120531 */

    /* SIM related system property start*/
    private String[] PHONE_SUBINFO_SERVICE = {
        "iphonesubinfo",
        "iphonesubinfo2",
        "iphonesubinfo3",
        "iphonesubinfo4",
    };

    private String[] PROPERTY_SIM_STATE = {
        TelephonyProperties.PROPERTY_SIM_STATE,
        TelephonyProperties.PROPERTY_SIM_STATE_2,
        TelephonyProperties.PROPERTY_SIM_STATE_3,
        TelephonyProperties.PROPERTY_SIM_STATE_4,
    };

    /* SIM related system property end*/

    /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT 
    private IOnlyOwnerSimSupport mOnlyOwnerSimSupport = null;
    /** @hide */
    public TelephonyManager(Context context) {
        //MTK-START [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
        	mContext = appContext;
        } else {
        	mContext = context;
        }

        if (sRegistry == null) {
            sRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    "telephony.registry"));
        }

        if (sRegistry2 == null) {
            //MTK-START [mtk04070][111116][ALPS00093395]Add for Gemini Phone2
            sRegistry2 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    "telephony.registry2"));
            //MTK-END [mtk04070][111116][ALPS00093395]Add for Gemini Phone2
        }

        if(PhoneConstants.GEMINI_SIM_NUM >=3){
            sRegistry3 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                "telephony.registry3"));
        }
        
        if(PhoneConstants.GEMINI_SIM_NUM >=4){		
            sRegistry4 = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                "telephony.registry4"));		
        }	
		//MTK-END [mtk04070][111223][ALPS00106134]Merge to ICS 4.0.3
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        mOnlyOwnerSimSupport = MediatekClassFactory.createInstance(IOnlyOwnerSimSupport.class);
        if (mOnlyOwnerSimSupport != null) {
            String actualClassName = mOnlyOwnerSimSupport.getClass().getName();
            Rlog.d(TAG, "initial mOnlyOwnerSimSupport done, actual class name is " + actualClassName);
        } else {
            Rlog.e(TAG, "FAIL! intial mOnlyOwnerSimSupport");
        }
        /// @}
    }

    /** @hide */
    /*  Construction function for TelephonyManager */
    private TelephonyManager() {
        mContext = null;
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        mOnlyOwnerSimSupport = MediatekClassFactory.createInstance(IOnlyOwnerSimSupport.class);
        if (mOnlyOwnerSimSupport != null) {
            String actualClassName = mOnlyOwnerSimSupport.getClass().getName();
            Rlog.d(TAG, "initial mOnlyOwnerSimSupport done, actual class name is " + actualClassName);
        } else {
            Rlog.e(TAG, "FAIL! intial mOnlyOwnerSimSupport");
        }
        /// @}
    }

    /*  Create static instance for TelephonyManager */
    private static TelephonyManager sInstance = new TelephonyManager();

    /** @hide
    /* @deprecated - use getSystemService as described above */
    public static TelephonyManager getDefault() {
        return sInstance;
    }

    /** {@hide} */
    public static TelephonyManager from(Context context) {
        return (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /** @hide */
    public static int getMaxPdpNum(int simId)
    {
        if(FeatureOption.MTK_DT_SUPPORT == true)
        {
            if (simId == PhoneConstants.GEMINI_SIM_1)
            {
                return MAX_SIM1_PDP_NUM;
            }
            else if (simId == PhoneConstants.GEMINI_SIM_2)
            {
                return MAX_SIM2_PDP_NUM;
            }
            else
            {
                return -1;
            }
        }
        else
        {
            return MAX_PDP_NUM;
        }
    }

    /** @hide */
    public static int getRadioType(int simId)
    {
        if(FeatureOption.MTK_DT_SUPPORT == true)
        {
            if (simId == PhoneConstants.GEMINI_SIM_1)
            {
                return RADIO_WCDMA;
            }
            else if (simId == PhoneConstants.GEMINI_SIM_2)
            {
                return RADIO_MISC;
            }
            else
            {
                return -1;
            }
        }
        else
        {
            return RADIO_WCDMA;
        }
    }

    //
    // Broadcast Intent actions
    //

    /**
     * Broadcast intent action indicating that the call state (cellular)
     * on the device has changed.
     *
     * <p>
     * The {@link #EXTRA_STATE} extra indicates the new call state.
     * If the new state is RINGING, a second extra
     * {@link #EXTRA_INCOMING_NUMBER} provides the incoming phone number as
     * a String.
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">
     * This was a {@link android.content.Context#sendStickyBroadcast sticky}
     * broadcast in version 1.0, but it is no longer sticky.
     * Instead, use {@link #getCallState} to synchronously query the current call state.
     *
     * @see #EXTRA_STATE
     * @see #EXTRA_INCOMING_NUMBER
     * @see #getCallState
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PHONE_STATE_CHANGED =
            "android.intent.action.PHONE_STATE";

    /**
     * The Phone app sends this intent when a user opts to respond-via-message during an incoming
     * call. By default, the device's default SMS app consumes this message and sends a text message
     * to the caller. A third party app can also provide this functionality by consuming this Intent
     * with a {@link android.app.Service} and sending the message using its own messaging system.
     * <p>The intent contains a URI (available from {@link android.content.Intent#getData})
     * describing the recipient, using either the {@code sms:}, {@code smsto:}, {@code mms:},
     * or {@code mmsto:} URI schema. Each of these URI schema carry the recipient information the
     * same way: the path part of the URI contains the recipient's phone number or a comma-separated
     * set of phone numbers if there are multiple recipients. For example, {@code
     * smsto:2065551234}.</p>
     *
     * <p>The intent may also contain extras for the message text (in {@link
     * android.content.Intent#EXTRA_TEXT}) and a message subject
     * (in {@link android.content.Intent#EXTRA_SUBJECT}).</p>
     *
     * <p class="note"><strong>Note:</strong>
     * The intent-filter that consumes this Intent needs to be in a {@link android.app.Service}
     * that requires the
     * permission {@link android.Manifest.permission#SEND_RESPOND_VIA_MESSAGE}.</p>
     * <p>For example, the service that receives this intent can be declared in the manifest file
     * with an intent filter like this:</p>
     * <pre>
     * &lt;!-- Service that delivers SMS messages received from the phone "quick response" -->
     * &lt;service android:name=".HeadlessSmsSendService"
     *          android:permission="android.permission.SEND_RESPOND_VIA_MESSAGE"
     *          android:exported="true" >
     *   &lt;intent-filter>
     *     &lt;action android:name="android.intent.action.RESPOND_VIA_MESSAGE" />
     *     &lt;category android:name="android.intent.category.DEFAULT" />
     *     &lt;data android:scheme="sms" />
     *     &lt;data android:scheme="smsto" />
     *     &lt;data android:scheme="mms" />
     *     &lt;data android:scheme="mmsto" />
     *   &lt;/intent-filter>
     * &lt;/service></pre>
     * <p>
     * Output: nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_RESPOND_VIA_MESSAGE =
            "android.intent.action.RESPOND_VIA_MESSAGE";
    
    /**
     * The lookup key used with the {@link #ACTION_PHONE_STATE_CHANGED} broadcast
     * for a String containing the new call state.
     *
     * @see #EXTRA_STATE_IDLE
     * @see #EXTRA_STATE_RINGING
     * @see #EXTRA_STATE_OFFHOOK
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_STATE = PhoneConstants.STATE_KEY;

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_IDLE}.
     */
    public static final String EXTRA_STATE_IDLE = PhoneConstants.State.IDLE.toString();

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_RINGING}.
     */
    public static final String EXTRA_STATE_RINGING = PhoneConstants.State.RINGING.toString();

    /**
     * Value used with {@link #EXTRA_STATE} corresponding to
     * {@link #CALL_STATE_OFFHOOK}.
     */
    public static final String EXTRA_STATE_OFFHOOK = PhoneConstants.State.OFFHOOK.toString();

    /**
     * The lookup key used with the {@link #ACTION_PHONE_STATE_CHANGED} broadcast
     * for a String containing the incoming phone number.
     * Only valid when the new call state is RINGING.
     *
     * <p class="note">
     * Retrieve with
     * {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_INCOMING_NUMBER = "incoming_number";


    //
    //
    // Device Info
    //
    //

    /**
     * Returns the software version number for the device, for example,
     * the IMEI/SV for GSM phones. Return null if the software version is
     * not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getDeviceSoftwareVersion() {
        try {
            //MTK-START [mtk04070][111116][ALPS00093395]Refine for Gemini
            return getSubscriberInfo(getDefaultSim()).getDeviceSvn();
            //MTK-END [mtk04070][111116][ALPS00093395]Refine for Gemini
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns the unique device ID, for example, the IMEI for GSM and the MEID
     * or ESN for CDMA phones. Return null if device ID is not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getDeviceId() {
        try {
            //MTK-START [mtk04070][111116][ALPS00093395]Refine for Gemini
            return getSubscriberInfo(getDefaultSim()).getDeviceId();
            //MTK-END [mtk04070][111116][ALPS00093395]Refine for Gemini
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            ex.printStackTrace();        
            return null;
        }
    }

    /**
     * Returns the current location of the device.
     *<p>
     * If there is only one radio in the device and that radio has an LTE connection,
     * this method will return null. The implementation must not to try add LTE
     * identifiers into the existing cdma/gsm classes.
     *<p>
     * In the future this call will be deprecated.
     *<p>
     * @return Current location of the device or null if not available.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION}.
     */
    public CellLocation getCellLocation() {
        //MTK-START [mtk04070][111116][ALPS00093395] Support Gemini
    	if(mtkGeminiSupport){
    		return getCellLocationGemini(getDefaultSim());
    	}else{
            try {
               Bundle bundle = getITelephony().getCellLocation();
               if (bundle.isEmpty()) return null;
               CellLocation cl = CellLocation.newFromBundle(bundle);
               if (cl.isEmpty())
                   return null;
               return cl;
            } catch (RemoteException ex) {
               ex.printStackTrace();            
               return null;
            } catch (NullPointerException ex) {
               ex.printStackTrace();            
               return null;
            }
        }
        //MTK-END [mtk04070][111116][ALPS00093395] Support Gemini
    }

    /**
     * Enables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     */
    public void enableLocationUpdates() {
        try {
            getITelephony().enableLocationUpdates();
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Disables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     */
    public void disableLocationUpdates() {
        try {
            getITelephony().disableLocationUpdates();
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Returns the neighboring cell information of the device. The getAllCellInfo is preferred
     * and use this only if getAllCellInfo return nulls or an empty list.
     *<p>
     * In the future this call will be deprecated.
     *<p>
     * @return List of NeighboringCellInfo or null if info unavailable.
     *
     * <p>Requires Permission:
     * (@link android.Manifest.permission#ACCESS_COARSE_UPDATES}
     */
    public List<NeighboringCellInfo> getNeighboringCellInfo() {
        try {
            return getITelephony().getNeighboringCellInfo(mContext.getOpPackageName());
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            ex.printStackTrace();        
            return null;
        }
    }

    /** No phone radio. */
    public static final int PHONE_TYPE_NONE = PhoneConstants.PHONE_TYPE_NONE;
    /** Phone radio is GSM. */
    public static final int PHONE_TYPE_GSM = PhoneConstants.PHONE_TYPE_GSM;
    /** Phone radio is CDMA. */
    public static final int PHONE_TYPE_CDMA = PhoneConstants.PHONE_TYPE_CDMA;
    /** Phone is via SIP. */
    public static final int PHONE_TYPE_SIP = PhoneConstants.PHONE_TYPE_SIP;

    /**
     * Returns the current phone type.
     * TODO: This is a last minute change and hence hidden.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     * @see #PHONE_TYPE_SIP
     *
     * {@hide}
     */
    public int getCurrentPhoneType() {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getActivePhoneType();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return getPhoneTypeFromProperty();
            }
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty();
        } catch (NullPointerException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty();
        }
    }

    /**
     * Returns a constant indicating the device phone type.  This
     * indicates the type of radio used to transmit voice calls.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     * @see #PHONE_TYPE_SIP
     */
    public int getPhoneType() {
        if (!isVoiceCapable()) {
            return PHONE_TYPE_NONE;
        }
        return getCurrentPhoneType();
    }

    /* Get current active phone type by system property, GsmPhone or CdmaPhone */
    private int getPhoneTypeFromProperty() {
        int type =
            SystemProperties.getInt(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                    getPhoneTypeFromNetworkType());
        return type;
    }

    /* Get phone type by network type, GsmPhone or CdmaPhone */
    private int getPhoneTypeFromNetworkType() {
        // When the system property CURRENT_ACTIVE_PHONE, has not been set,
        // use the system property for default network type.
        // This is a fail safe, and can only happen at first boot.
        int mode = SystemProperties.getInt("ro.telephony.default_network", -1);
        if (mode == -1)
            return PHONE_TYPE_NONE;
        return getPhoneType(mode);
    }

    
    /**
     * This function returns the type of the phone, depending
     * on the network mode.
     *
     * @param networkMode
     * @return Phone Type
     *
     * @hide
     */
    public static int getPhoneType(int networkMode) {
        switch(networkMode) {
        case RILConstants.NETWORK_MODE_CDMA:
        case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
        case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
            return PhoneConstants.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_WCDMA_PREF:
        case RILConstants.NETWORK_MODE_GSM_ONLY:
        case RILConstants.NETWORK_MODE_WCDMA_ONLY:
        case RILConstants.NETWORK_MODE_GSM_UMTS:
        case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
        case RILConstants.NETWORK_MODE_LTE_WCDMA:
        case RILConstants.NETWORK_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            return PhoneConstants.PHONE_TYPE_GSM;

        // Use CDMA Phone for the global mode including CDMA
        case RILConstants.NETWORK_MODE_GLOBAL:
        case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO:
            return PhoneConstants.PHONE_TYPE_CDMA;

        case RILConstants.NETWORK_MODE_LTE_ONLY:
            if (getLteOnCdmaModeStatic() == PhoneConstants.LTE_ON_CDMA_TRUE) {
                return PhoneConstants.PHONE_TYPE_CDMA;
            } else {
                return PhoneConstants.PHONE_TYPE_GSM;
            }
        default:
            return PhoneConstants.PHONE_TYPE_GSM;
        }
    }

    /**
     * The contents of the /proc/cmdline file
     */
    private static String getProcCmdLine()
    {
        String cmdline = "";
        FileInputStream is = null;
        try {
            is = new FileInputStream("/proc/cmdline");
            byte [] buffer = new byte[2048];
            int count = is.read(buffer);
            if (count > 0) {
                cmdline = new String(buffer, 0, count);
            }
        } catch (IOException e) {
            Rlog.d(TAG, "No /proc/cmdline exception=" + e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        Rlog.d(TAG, "/proc/cmdline=" + cmdline);
        return cmdline;
    }

    /** Kernel command line */
    private static final String sKernelCmdLine = getProcCmdLine();

    /** Pattern for selecting the product type from the kernel command line */
    private static final Pattern sProductTypePattern =
        Pattern.compile("\\sproduct_type\\s*=\\s*(\\w+)");

    /** The ProductType used for LTE on CDMA devices */
    private static final String sLteOnCdmaProductType =
        SystemProperties.get(TelephonyProperties.PROPERTY_LTE_ON_CDMA_PRODUCT_TYPE, "");

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link PhoneConstants#LTE_ON_CDMA_UNKNOWN}, {@link PhoneConstants#LTE_ON_CDMA_FALSE}
     * or {@link PhoneConstants#LTE_ON_CDMA_TRUE}
     *
     * @hide
     */
    public static int getLteOnCdmaModeStatic() {
        int retVal;
        int curVal;
        String productType = "";

        curVal = SystemProperties.getInt(TelephonyProperties.PROPERTY_LTE_ON_CDMA_DEVICE,
                    PhoneConstants.LTE_ON_CDMA_UNKNOWN);
        retVal = curVal;
        if (retVal == PhoneConstants.LTE_ON_CDMA_UNKNOWN) {
            Matcher matcher = sProductTypePattern.matcher(sKernelCmdLine);
            if (matcher.find()) {
                productType = matcher.group(1);
                if (sLteOnCdmaProductType.equals(productType)) {
                    retVal = PhoneConstants.LTE_ON_CDMA_TRUE;
                } else {
                    retVal = PhoneConstants.LTE_ON_CDMA_FALSE;
                }
            } else {
                retVal = PhoneConstants.LTE_ON_CDMA_FALSE;
            }
        }

        Rlog.d(TAG, "getLteOnCdmaMode=" + retVal + " curVal=" + curVal +
                " product_type='" + productType +
                "' lteOnCdmaProductType='" + sLteOnCdmaProductType + "'");
        return retVal;
    }
    



    //
    //
    // Current Network
    //
    //

    /**
     * Returns the alphabetic name of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperatorName() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        return getNetworkOperatorNameGemini(getDefaultSim());
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns the numeric name (MCC+MNC) of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkOperator() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        return getNetworkOperatorGemini(getDefaultSim());
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns true if the device is considered roaming on the current
     * network, for GSM purposes.
     * <p>
     * Availability: Only when user registered to a network.
     */
    public boolean isNetworkRoaming() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.isNetworkRoamingGemini(getDefaultSim());
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return false;
            }
        } catch(RemoteException ex) {
            ex.printStackTrace();        
            return false;
        } catch (NullPointerException ex) {
            ex.printStackTrace();        
            return false;
        }	
        //return isNetworkRoamingGemini(getDefaultSim());
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns the ISO country code equivalent of the current registered
     * operator's MCC (Mobile Country Code).
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     */
    public String getNetworkCountryIso() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        return getNetworkCountryIsoGemini(getDefaultSim());
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /** Network type is unknown */
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    /** Current network is GPRS */
    public static final int NETWORK_TYPE_GPRS = 1;
    /** Current network is EDGE */
    public static final int NETWORK_TYPE_EDGE = 2;
    /** Current network is UMTS */
    public static final int NETWORK_TYPE_UMTS = 3;
    /** Current network is CDMA: Either IS95A or IS95B*/
    public static final int NETWORK_TYPE_CDMA = 4;
    /** Current network is EVDO revision 0*/
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    /** Current network is EVDO revision A*/
    public static final int NETWORK_TYPE_EVDO_A = 6;
    /** Current network is 1xRTT*/
    public static final int NETWORK_TYPE_1xRTT = 7;
    /** Current network is HSDPA */
    public static final int NETWORK_TYPE_HSDPA = 8;
    /** Current network is HSUPA */
    public static final int NETWORK_TYPE_HSUPA = 9;
    /** Current network is HSPA */
    public static final int NETWORK_TYPE_HSPA = 10;
    /** Current network is iDen */
    public static final int NETWORK_TYPE_IDEN = 11;
    /** Current network is EVDO revision B*/
    public static final int NETWORK_TYPE_EVDO_B = 12;
    /** Current network is LTE */
    public static final int NETWORK_TYPE_LTE = 13;
    /** Current network is eHRPD */
    public static final int NETWORK_TYPE_EHRPD = 14;
    /** Current network is HSPA+ */
    public static final int NETWORK_TYPE_HSPAP = 15;

    /**
     * @return the NETWORK_TYPE_xxxx for current data connection.
     */
    public int getNetworkType() {
        return getDataNetworkType();
    }
    
    /**
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device for data transmission.
     * @return the network type
     *
     * @see #NETWORK_TYPE_UNKNOWN
     * @see #NETWORK_TYPE_GPRS
     * @see #NETWORK_TYPE_EDGE
     * @see #NETWORK_TYPE_UMTS
     * @see #NETWORK_TYPE_HSDPA
     * @see #NETWORK_TYPE_HSUPA
     * @see #NETWORK_TYPE_HSPA
     * @see #NETWORK_TYPE_CDMA
     * @see #NETWORK_TYPE_EVDO_0
     * @see #NETWORK_TYPE_EVDO_A
     * @see #NETWORK_TYPE_EVDO_B
     * @see #NETWORK_TYPE_1xRTT
     * @see #NETWORK_TYPE_IDEN
     * @see #NETWORK_TYPE_LTE
     * @see #NETWORK_TYPE_EHRPD
     * @see #NETWORK_TYPE_HSPAP
     *
     * @hide
     */
    public int getDataNetworkType() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
    	if(mtkGeminiSupport){
    		return getNetworkTypeGemini(getDefaultSim());
    	}else{
           try{
               ITelephony telephony = getITelephony();
               if (telephony != null) {
                   return telephony.getDataNetworkType();
               } else {
                   // This can happen when the ITelephony interface is not up yet.
                   return NETWORK_TYPE_UNKNOWN;
               }
           } catch(RemoteException ex) {
               // This shouldn't happen in the normal case
               return NETWORK_TYPE_UNKNOWN;
           } catch (NullPointerException ex) {
               // This could happen before phone restarts due to crashing
               return NETWORK_TYPE_UNKNOWN;
           }
        }
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns the NETWORK_TYPE_xxxx for voice
     *
     * @hide
     */
    public int getVoiceNetworkType() {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getVoiceNetworkType();
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return NETWORK_TYPE_UNKNOWN;
        }
    }

    /** Unknown network class. {@hide} */
    public static final int NETWORK_CLASS_UNKNOWN = 0;
    /** Class of broadly defined "2G" networks. {@hide} */
    public static final int NETWORK_CLASS_2_G = 1;
    /** Class of broadly defined "3G" networks. {@hide} */
    public static final int NETWORK_CLASS_3_G = 2;
    /** Class of broadly defined "4G" networks. {@hide} */
    public static final int NETWORK_CLASS_4_G = 3;

    /**
     * Return general class of network type, such as "3G" or "4G". In cases
     * where classification is contentious, this method is conservative.
     *
     * @hide
     */
    public static int getNetworkClass(int networkType) {
        switch (networkType) {
            case NETWORK_TYPE_GPRS:
            case NETWORK_TYPE_EDGE:
            case NETWORK_TYPE_CDMA:
            case NETWORK_TYPE_1xRTT:
            case NETWORK_TYPE_IDEN:
                return NETWORK_CLASS_2_G;
            case NETWORK_TYPE_UMTS:
            case NETWORK_TYPE_EVDO_0:
            case NETWORK_TYPE_EVDO_A:
            case NETWORK_TYPE_HSDPA:
            case NETWORK_TYPE_HSUPA:
            case NETWORK_TYPE_HSPA:
            case NETWORK_TYPE_EVDO_B:
            case NETWORK_TYPE_EHRPD:
            case NETWORK_TYPE_HSPAP:
                return NETWORK_CLASS_3_G;
            case NETWORK_TYPE_LTE:
                return NETWORK_CLASS_4_G;
            default:
                return NETWORK_CLASS_UNKNOWN;
        }
    }

    /**
     * Returns a string representation of the radio technology (network type)
     * currently in use on the device.
     * @return the name of the radio technology
     *
     * @hide pending API council review
     */
    public String getNetworkTypeName() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
    	if(mtkGeminiSupport){
    		return getNetworkTypeNameGemini(getDefaultSim());
    	}else{
           return getNetworkTypeName(getNetworkType());
        }
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /** {@hide} */
    public static String getNetworkTypeName(int type) {
        switch (type) {
            case NETWORK_TYPE_GPRS:
                return "GPRS";
            case NETWORK_TYPE_EDGE:
                return "EDGE";
            case NETWORK_TYPE_UMTS:
                return "UMTS";
            case NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case NETWORK_TYPE_HSPA:
                return "HSPA";
            case NETWORK_TYPE_CDMA:
                return "CDMA";
            case NETWORK_TYPE_EVDO_0:
                return "CDMA - EvDo rev. 0";
            case NETWORK_TYPE_EVDO_A:
                return "CDMA - EvDo rev. A";
            case NETWORK_TYPE_EVDO_B:
                return "CDMA - EvDo rev. B";
            case NETWORK_TYPE_1xRTT:
                return "CDMA - 1xRTT";
            case NETWORK_TYPE_LTE:
                return "LTE";
            case NETWORK_TYPE_EHRPD:
                return "CDMA - eHRPD";
            case NETWORK_TYPE_IDEN:
                return "iDEN";
            case NETWORK_TYPE_HSPAP:
                return "HSPA+";
            default:
                return "UNKNOWN";
        }
    }

    //
    //
    // SIM Card
    //
    //

    /** SIM card state: Unknown. Signifies that the SIM is in transition
     *  between states. For example, when the user inputs the SIM pin
     *  under PIN_REQUIRED state, a query for sim status returns
     *  this state before turning to SIM_STATE_READY. */
    public static final int SIM_STATE_UNKNOWN = 0;
    /** SIM card state: no SIM card is available in the device */
    public static final int SIM_STATE_ABSENT = 1;
    /** SIM card state: Locked: requires the user's SIM PIN to unlock */
    public static final int SIM_STATE_PIN_REQUIRED = 2;
    /** SIM card state: Locked: requires the user's SIM PUK to unlock */
    public static final int SIM_STATE_PUK_REQUIRED = 3;
    /** SIM card state: Locked: requries a network PIN to unlock */
    public static final int SIM_STATE_NETWORK_LOCKED = 4;
    /** SIM card state: Ready */
    public static final int SIM_STATE_READY = 5;

    /**
     * @return true if a ICC card is present
     */
    public boolean hasIccCard() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        try {
            if(mtkGeminiSupport){
                return getITelephonyEx().hasIccCard(getDefaultSim());
            } else {
                return getITelephony().hasIccCard();
            }
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns a constant indicating the state of the
     * device SIM card.
     *
     * @see #SIM_STATE_UNKNOWN
     * @see #SIM_STATE_ABSENT
     * @see #SIM_STATE_PIN_REQUIRED
     * @see #SIM_STATE_PUK_REQUIRED
     * @see #SIM_STATE_NETWORK_LOCKED
     * @see #SIM_STATE_READY
     */
    public int getSimState() {
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "getSimState return: 3gdatasms  MTK_ONLY_OWNER_SIM_SUPPORT ");
            return SIM_STATE_UNKNOWN;
        }
        /// @}
        
        try {
            return getITelephony().getSimState(getDefaultSim());
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case
            ex.printStackTrace();            
            return android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the MCC+MNC (mobile country code + mobile network code) of the
     * provider of the SIM. 5 or 6 decimal digits.
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     */
    public String getSimOperator() {
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "getSimOperator return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return "";
        }
        /// @}
        
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        return getSimOperatorGemini(getDefaultSim());
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns the Service Provider Name (SPN).
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     */
    public String getSimOperatorName() {
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "getSimOperatorName return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return "";
        }
        /// @}

        try {
            return getITelephony().getSimOperatorName(getDefaultSim());
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case
            ex.printStackTrace();            
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    /**
     * Returns the ISO country code equivalent for the SIM provider's country code.
     */
    public String getSimCountryIso() {
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "getSimCountryIso return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return "";
        }
        /// @}
        
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        return getSimCountryIsoGemini(getDefaultSim());
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns the serial number of the SIM, if applicable. Return null if it is
     * unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getSimSerialNumber() {
        /// M: For MTK multiuser in 3gdatasms:MTK_ONLY_OWNER_SIM_SUPPORT @{ 
        if(!mOnlyOwnerSimSupport.isCurrentUserOwner()){
            Rlog.d(TAG, "getSimSerialNumber return: 3gdatasms MTK_ONLY_OWNER_SIM_SUPPORT ");
            return "";
        }
        /// @}
        
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
    	if(mtkGeminiSupport){
    		return getSimSerialNumberGemini(getDefaultSim());
    	}else{
           try {
               return getSubscriberInfo().getIccSerialNumber();
           } catch (RemoteException ex) {
                ex.printStackTrace();           
               return null;
           } catch (NullPointerException ex) {
               // This could happen before phone restarts due to crashing
                ex.printStackTrace();               
               return null;
           }
        }
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link PhoneConstants#LTE_ON_CDMA_UNKNOWN}, {@link PhoneConstants#LTE_ON_CDMA_FALSE}
     * or {@link PhoneConstants#LTE_ON_CDMA_TRUE}
     *
     * @hide
     */
    public int getLteOnCdmaMode() {
        try {
            return getITelephony().getLteOnCdmaMode();
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        }
    }

    //
    //
    // Subscriber Info
    //
    //

    /**
     * Returns the unique subscriber ID, for example, the IMSI for a GSM phone.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getSubscriberId() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
    	if(mtkGeminiSupport){
            try {
               return getITelephonyEx().getSubscriberId(getDefaultSim());
                //return getSubscriberIdGemini(getDefaultSim());
            } catch (RemoteException ex) {
                ex.printStackTrace();	        
                return null;
            } catch (NullPointerException ex) {
                // This could happen before phone restarts due to crashing
                ex.printStackTrace();	            
                return null;
            }            
    	}else{
	        try {
	            return getSubscriberInfo(getDefaultSim()).getSubscriberId();
	        } catch (RemoteException ex) {
                ex.printStackTrace();	        
	            return null;
	        } catch (NullPointerException ex) {
	            // This could happen before phone restarts due to crashing
                ex.printStackTrace();	            
	            return null;
	        }
    	}
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns the Group Identifier Level1 for a GSM phone.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getGroupIdLevel1() {
        try {
            return getSubscriberInfo().getGroupIdLevel1();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the phone number string for line 1, for example, the MSISDN
     * for a GSM phone. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getLine1Number() {
        try {
            return getSubscriberInfo(getDefaultSim()).getLine1Number();
        } catch (RemoteException ex) {
            ex.printStackTrace();	        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();	            
            return null;
        }
    }

    /**
     * Returns the alphabetic identifier associated with the line 1 number.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     * nobody seems to call this.
     */
    public String getLine1AlphaTag() {
        try {
            return getSubscriberInfo(getDefaultSim()).getLine1AlphaTag();
        } catch (RemoteException ex) {
            ex.printStackTrace();
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Returns the MSISDN string.
     * for a GSM phone. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @hide
     */
    public String getMsisdn() {
        try {
            return getSubscriberInfo().getMsisdn();
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    /**
     * Returns the voice mail number. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getVoiceMailNumber() {
        try {
            return getSubscriberInfo(getDefaultSim()).getVoiceMailNumber();
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    /**
     * Returns the complete voice mail number. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#CALL_PRIVILEGED CALL_PRIVILEGED}
     *
     * @hide
     */
    public String getCompleteVoiceMailNumber() {
        try {
            return getSubscriberInfo().getCompleteVoiceMailNumber();
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    /**
     * Returns the voice mail count. Return 0 if unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     */
    public int getVoiceMessageCount() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
    	if(mtkGeminiSupport){
    		return getVoiceMessageCountGemini(getDefaultSim());
    	}else{
           try {
               return getITelephony().getVoiceMessageCount();
           } catch (RemoteException ex) {
               ex.printStackTrace();           
               return 0;
           } catch (NullPointerException ex) {
               // This could happen before phone restarts due to crashing
                ex.printStackTrace();               
               return 0;
           }
        }
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Retrieves the alphabetic identifier associated with the voice
     * mail number.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getVoiceMailAlphaTag() {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
    	if(mtkGeminiSupport){
    		return getVoiceMailAlphaTagGemini(getDefaultSim());
    	}else{
	        try {
	            return getSubscriberInfo(getDefaultSim()).getVoiceMailAlphaTag();
	        } catch (RemoteException ex) {
                ex.printStackTrace();	        
	            return null;
	        } catch (NullPointerException ex) {
	            // This could happen before phone restarts due to crashing
                ex.printStackTrace();	            
	            return null;
	        }
    	}
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
    }

    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM.
     * @return the IMPI, or null if not present or not loaded
     * @hide
     */
    public String getIsimImpi() {
        try {
            return getSubscriberInfo().getIsimImpi();
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM.
     * @return the IMS domain name, or null if not present or not loaded
     * @hide
     */
    public String getIsimDomain() {
        try {
            return getSubscriberInfo().getIsimDomain();
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM.
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     * @hide
     */
    public String[] getIsimImpu() {
        try {
            return getSubscriberInfo().getIsimImpu();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));
    }


    /** Device call state: No activity. */
    public static final int CALL_STATE_IDLE = 0;
    /** Device call state: Ringing. A new call arrived and is
     *  ringing or waiting. In the latter case, another call is
     *  already active. */
    public static final int CALL_STATE_RINGING = 1;
    /** Device call state: Off-hook. At least one call exists
      * that is dialing, active, or on hold, and no calls are ringing
      * or waiting. */
    public static final int CALL_STATE_OFFHOOK = 2;

    /**
     * Returns a constant indicating the call state (cellular) on the device.
     */
    public int getCallState() {
        try {
            return getITelephony().getCallState();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();            
            return CALL_STATE_IDLE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
            ex.printStackTrace();          
          return CALL_STATE_IDLE;
      }
    }

    /** Data connection activity: No traffic. */
    public static final int DATA_ACTIVITY_NONE = 0x00000000;
    /** Data connection activity: Currently receiving IP PPP traffic. */
    public static final int DATA_ACTIVITY_IN = 0x00000001;
    /** Data connection activity: Currently sending IP PPP traffic. */
    public static final int DATA_ACTIVITY_OUT = 0x00000002;
    /** Data connection activity: Currently both sending and receiving
     *  IP PPP traffic. */
    public static final int DATA_ACTIVITY_INOUT = DATA_ACTIVITY_IN | DATA_ACTIVITY_OUT;
    /**
     * Data connection is active, but physical link is down
     */
    public static final int DATA_ACTIVITY_DORMANT = 0x00000004;

    /**
     * Returns a constant indicating the type of activity on a data connection
     * (cellular).
     *
     * @see #DATA_ACTIVITY_NONE
     * @see #DATA_ACTIVITY_IN
     * @see #DATA_ACTIVITY_OUT
     * @see #DATA_ACTIVITY_INOUT
     * @see #DATA_ACTIVITY_DORMANT
     */
    public int getDataActivity() {
        try {
            return getITelephony().getDataActivity();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();            
            return DATA_ACTIVITY_NONE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
            ex.printStackTrace();          
          return DATA_ACTIVITY_NONE;
      }
    }

    /** Data connection state: Unknown.  Used before we know the state.
     * @hide
     */
    public static final int DATA_UNKNOWN        = -1;
    /** Data connection state: Disconnected. IP traffic not available. */
    public static final int DATA_DISCONNECTED   = 0;
    /** Data connection state: Currently setting up a data connection. */
    public static final int DATA_CONNECTING     = 1;
    /** Data connection state: Connected. IP traffic should be available. */
    public static final int DATA_CONNECTED      = 2;
    /** Data connection state: Suspended. The connection is up, but IP
     * traffic is temporarily unavailable. For example, in a 2G network,
     * data activity may be suspended when a voice call arrives. */
    public static final int DATA_SUSPENDED      = 3;

    /**
     * Returns a constant indicating the current data connection state
     * (cellular).
     *
     * @see #DATA_DISCONNECTED
     * @see #DATA_CONNECTING
     * @see #DATA_CONNECTED
     * @see #DATA_SUSPENDED
     */
    public int getDataState() {
        try {
            return getITelephony().getDataState();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();            
            return DATA_DISCONNECTED;
        } catch (NullPointerException ex) {
            ex.printStackTrace();        
            return DATA_DISCONNECTED;
        }
    }

    private ITelephony getITelephony() {
        return ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
    }

    private ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICEEX));
    }

    //
    //
    // PhoneStateListener
    //
    //

    /**
     * Registers a listener object to receive notification of changes
     * in specified telephony states.
     * <p>
     * To register a listener, pass a {@link PhoneStateListener}
     * and specify at least one telephony state of interest in
     * the events argument.
     *
     * At registration, and when a specified telephony state
     * changes, the telephony manager invokes the appropriate
     * callback method on the listener object and passes the
     * current (udpated) values.
     * <p>
     * To unregister a listener, pass the listener object and set the
     * events argument to
     * {@link PhoneStateListener#LISTEN_NONE LISTEN_NONE} (0).
     *
     * @param listener The {@link PhoneStateListener} object to register
     *                 (or unregister)
     * @param events The telephony state(s) of interest to the listener,
     *               as a bitwise-OR combination of {@link PhoneStateListener}
     *               LISTEN_ flags.
     */
    public void listen(PhoneStateListener listener, int events) {
        //MTK-START [mtk04070][111116][ALPS00093395]Support Gemini
        String pkgForDebug = mContext != null ? mContext.getPackageName() : "<unknown>";
        try {
            Boolean notifyNow = true;
            sRegistry.listen(pkgForDebug, listener.callback, events, notifyNow);

            /* Add by mtk01411 for Data related event */
            if (FeatureOption.MTK_GEMINI_SUPPORT) {           
                if (events == PhoneStateListener.LISTEN_NONE) {
                    /* Unregister previous listened events */
                    sRegistry2.listen(pkgForDebug, listener.callback, events, notifyNow);
                } else if (events == PhoneStateListener.LISTEN_CALL_STATE) {
                    sRegistry2.listen(pkgForDebug, listener.callback, events, notifyNow);
                } else {
                    int data_events = PhoneStateListener.LISTEN_NONE;
                    if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) !=0 ) {
                        data_events |= PhoneStateListener.LISTEN_DATA_CONNECTION_STATE;  
                    }    
                    if ((events & PhoneStateListener.LISTEN_DATA_ACTIVITY) !=0 ) {
                        data_events |= PhoneStateListener.LISTEN_DATA_ACTIVITY;
                    }

                    if (data_events != PhoneStateListener.LISTEN_NONE) {
                        /* For 3rd party application: */
                        /* This solution is only useful if only one PS can be attached for one of two sim cards */
                        /* If PS can be attached on both sim cards at the same time: This solution can't work */
                        /* => Because the same callback may recevie two same events and can't identify which event comes from which sim card */
                        sRegistry2.listen(pkgForDebug, listener.callback, data_events, notifyNow);
                    }
                }
                
            }
        //MTK-END [mtk04070][111116][ALPS00093395]Support Gemini
        } catch (RemoteException ex) {
            // system process dead
            ex.printStackTrace();            
        } catch (NullPointerException ex) {
            // system process dead
            ex.printStackTrace();            
        }
    }

    /**
     * Returns the CDMA ERI icon index to display
     *
     * @hide
     */
    public int getCdmaEriIconIndex() {
        try {
            return getITelephony().getCdmaEriIconIndex();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI icon mode,
     * 0 - ON
     * 1 - FLASHING
     *
     * @hide
     */
    public int getCdmaEriIconMode() {
        try {
            return getITelephony().getCdmaEriIconMode();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI text,
     *
     * @hide
     */
    public String getCdmaEriText() {
        try {
            return getITelephony().getCdmaEriText();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * @return true if the current device is "voice capable".
     * <p>
     * "Voice capable" means that this device supports circuit-switched
     * (i.e. voice) phone calls over the telephony network, and is allowed
     * to display the in-call UI while a cellular voice call is active.
     * This will be false on "data only" devices which can't make voice
     * calls and don't support any in-call UI.
     * <p>
     * Note: the meaning of this flag is subtly different from the
     * PackageManager.FEATURE_TELEPHONY system feature, which is available
     * on any device with a telephony radio, even if the device is
     * data-only.
     *
     * @hide pending API review
     */
    public boolean isVoiceCapable() {
        if (mContext == null) return true;
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
    }

    /**
     * @return true if the current device supports sms service.
     * <p>
     * If true, this means that the device supports both sending and
     * receiving sms via the telephony network.
     * <p>
     * Note: Voicemail waiting sms, cell broadcasting sms, and MMS are
     *       disabled when device doesn't support sms.
     *
     * @hide pending API review
     */
    public boolean isSmsCapable() {
        if (mContext == null) return true;
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sms_capable);
    }

    /**
     * Returns all observed cell information from all radios on the
     * device including the primary and neighboring cells. This does
     * not cause or change the rate of PhoneStateListner#onCellInfoChanged.
     *<p>
     * The list can include one or more of {@link android.telephony.CellInfoGsm CellInfoGsm},
     * {@link android.telephony.CellInfoCdma CellInfoCdma},
     * {@link android.telephony.CellInfoLte CellInfoLte} and
     * {@link android.telephony.CellInfoWcdma CellInfoCdma} in any combination.
     * Specifically on devices with multiple radios it is typical to see instances of
     * one or more of any these in the list. In addition 0, 1 or more CellInfo
     * objects may return isRegistered() true.
     *<p>
     * This is preferred over using getCellLocation although for older
     * devices this may return null in which case getCellLocation should
     * be called.
     *<p>
     * @return List of CellInfo or null if info unavailable.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}
     */
    public List<CellInfo> getAllCellInfo() {
        try {
            return getITelephony().getAllCellInfo();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Sets the minimum time in milli-seconds between {@link PhoneStateListener#onCellInfoChanged
     * PhoneStateListener.onCellInfoChanged} will be invoked.
     *<p>
     * The default, 0, means invoke onCellInfoChanged when any of the reported
     * information changes. Setting the value to INT_MAX(0x7fffffff) means never issue
     * A onCellInfoChanged.
     *<p>
     * @param rateInMillis the rate
     *
     * @hide
     */
    public void setCellInfoListRate(int rateInMillis) {
        try {
            getITelephony().setCellInfoListRate(rateInMillis);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }

    /**
     * Returns the MMS user agent.
     */
    public String getMmsUserAgent() {
        if (mContext == null) return null;
        return mContext.getResources().getString(
                com.android.internal.R.string.config_mms_user_agent);
    }

    /**
     * Returns the MMS user agent profile URL.
     */
    public String getMmsUAProfUrl() {
        if (mContext == null) return null;
        return mContext.getResources().getString(
                com.android.internal.R.string.config_mms_user_agent_profile_url);
    }   

    /**
     * Gemini
     * Returns the current location of the device.
     * Return null if current location is not available.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION}.
     * @hide
     * @deprecated - use getCellLocation(int simId) in TelephonyManagerEx     
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead        
     */
    @Deprecated public CellLocation getCellLocationGemini(int simId) {
        try {
            Bundle bundle = getITelephonyEx().getCellLocation(simId);

            //ALPS00296533
            CellLocation cl = CellLocation.newFromBundle(bundle);
            if (cl.isEmpty())
               return null;
			   
            return cl;
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            ex.printStackTrace();        
            return null;
        }
    }

    /**
     * Gemini
     * Enables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     * @deprecated - use enableLocationUpdates(int simId) in TelephonyManagerEx instead   
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead        
     */
    @Deprecated	public void enableLocationUpdatesGemini(int simId) {
        try {
            getITelephony().enableLocationUpdatesGemini(simId);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }
    
    /**
     * Gemini
     * Disables location update notifications.  {@link PhoneStateListener#onCellLocationChanged
     * PhoneStateListener.onCellLocationChanged} will be called on location updates.
     *
     * <p>Requires Permission: {@link android.Manifest.permission#CONTROL_LOCATION_UPDATES
     * CONTROL_LOCATION_UPDATES}
     *
     * @hide
     * @deprecated - use disableLocationUpdates(int simId) in TelephonyManagerEx instead  
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead        
     */
    @Deprecated	public void disableLocationUpdatesGemini(int simId) {
        try {
            getITelephony().disableLocationUpdatesGemini(simId);
        } catch (RemoteException ex) {
        } catch (NullPointerException ex) {
        }
    }
    
    /**
     * Gemini
     * Returns the neighboring cell information of the device.
     *
     * @return List of NeighboringCellInfo or null if info unavailable.
     *
     * <p>Requires Permission:
     * (@link android.Manifest.permission#ACCESS_COARSE_UPDATES}
     * @hide
     * @deprecated - use getNeighboringCellInfo(int simId) in TelephonyManagerEx instead   
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead        
     */
    @Deprecated	public List<NeighboringCellInfo> getNeighboringCellInfoGemini(int simId) {
        try {
            return getITelephonyEx().getNeighboringCellInfo(mContext.getBasePackageName(), simId);
            //return getITelephony().getNeighboringCellInfoGemini(mContext.getBasePackageName(), simId);
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            ex.printStackTrace();        
            return null;
        }
    }

    /**
     * Gemini
     * Returns the alphabetic name of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     * @hide
     * @deprecated - use getNetworkOperatorName(int simId) in TelephonyManagerEx instead    
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead        
     */
    @Deprecated	public String getNetworkOperatorNameGemini(int simId) {
        if(simId == PhoneConstants.GEMINI_SIM_4)
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_4);
        else if(simId == PhoneConstants.GEMINI_SIM_3)		
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_3);
        else if(simId == PhoneConstants.GEMINI_SIM_2)		
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA_2);
        else					
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ALPHA);			
    }

    /**
     * Gemini
     * Returns the numeric name (MCC+MNC) of current registered operator.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     * @hide
     * @deprecated - use getNetworkOperator(int simId) in TelephonyManagerEx instead      
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead        
     */
    @Deprecated	public String getNetworkOperatorGemini(int simId) {
        if(simId == PhoneConstants.GEMINI_SIM_4)
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_4);
        else if(simId == PhoneConstants.GEMINI_SIM_3)		
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_3);
        else if(simId == PhoneConstants.GEMINI_SIM_2)		
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC_2);
        else					
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC);			    
 		
    }
    
    /**
     * Gemini
     * Returns the ISO country code equivilent of the current registered
     * operator's MCC (Mobile Country Code).
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     * @hide
     * @deprecated - use getNetworkCountryIso(int simId) in TelephonyManagerEx instead          
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead        
     */
    @Deprecated	public String getNetworkCountryIsoGemini(int simId) {
        if(simId == PhoneConstants.GEMINI_SIM_4)
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_4);
        else if(simId == PhoneConstants.GEMINI_SIM_3)		
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_3);
        else if(simId == PhoneConstants.GEMINI_SIM_2)		
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY_2);
        else					
            return SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY);			    		
    }

    /**
     * Gemini 
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device.
     * @return the network type
     *
     * @see #NETWORK_TYPE_UNKNOWN
     * @see #NETWORK_TYPE_GPRS
     * @see #NETWORK_TYPE_EDGE
     * @see #NETWORK_TYPE_UMTS
     * @see #NETWORK_TYPE_HSDPA
     * @see #NETWORK_TYPE_HSUPA
     * @see #NETWORK_TYPE_HSPA
     * @see #NETWORK_TYPE_CDMA
     * @see #NETWORK_TYPE_EVDO_0
     * @see #NETWORK_TYPE_EVDO_A
     * @see #NETWORK_TYPE_1xRTT
     * @hide
     * @deprecated - use getNetworkType(int simId) in TelephonyManagerEx instead
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead     
     */
    @Deprecated	public int getNetworkTypeGemini(int simId) {
        try{
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.getNetworkTypeGemini(simId);
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            ex.printStackTrace();            
            return NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return NETWORK_TYPE_UNKNOWN;
        }
    }
    
    /**
     * Gemini
     * Returns a string representation of the radio technology (network type)
     * currently in use on the device.
     * @return the name of the radio technology
     *
     * @hide
     * @deprecated - use TelephonyManager.getNetworkTypeName(TelephonyManagerEx.getNetworkType(simId)) instead 
     *                       for framework , use function in Itelephony(PhoneInterfaceManager) instead
     */
    @Deprecated	public String getNetworkTypeNameGemini(int simId) {
        switch (getNetworkTypeGemini(simId)) {
            case NETWORK_TYPE_GPRS:
                return "GPRS";
            case NETWORK_TYPE_EDGE:
                return "EDGE";
            case NETWORK_TYPE_UMTS:
                return "UMTS";
            case NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case NETWORK_TYPE_HSPA:
                return "HSPA";
            case NETWORK_TYPE_CDMA:
                return "CDMA";
            case NETWORK_TYPE_EVDO_0:
                return "CDMA - EvDo rev. 0";
            case NETWORK_TYPE_EVDO_A:
                return "CDMA - EvDo rev. A";
            case NETWORK_TYPE_EVDO_B:
                return "CDMA - EvDo rev. B";
            case NETWORK_TYPE_1xRTT:
                return "CDMA - 1xRTT";
            case NETWORK_TYPE_LTE:
                return "LTE";
            case NETWORK_TYPE_EHRPD:
                return "CDMA - eHRPD";
            case NETWORK_TYPE_IDEN:
                return "iDEN";
            case NETWORK_TYPE_HSPAP:
                return "HSPA+";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Gemini
     * Returns the MCC+MNC (mobile country code + mobile network code) of the
     * provider of the SIM. 5 or 6 decimal digits.
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     * @hide
     * @deprecated - use TeleponyManagerEx instead if apk use
     *               use PhoneInterfaceManager instead if framework use
     */
    @Deprecated public String getSimOperatorGemini(int simId) {
        Rlog.d( TAG,"getSimOperator:" + simId);
        try {
            return getITelephony().getSimOperator(simId);
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case
            ex.printStackTrace();            
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    /**
     * Gemini
     * Returns the ISO country code equivalent for the SIM provider's country code.
     * @hide
     * @deprecated - use TeleponyManagerEx instead if apk use
     *               use PhoneInterfaceManager instead if framework use
     */
    // TODO LEGO SIM
    @Deprecated public String getSimCountryIsoGemini(int simId) {
        Rlog.d( TAG,"getSimCountryIso:" + simId);
        try {
            return getITelephony().getSimCountryIso(simId);
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case
            ex.printStackTrace();            
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }


    /**
     * Gemini
     * Returns the serial number of the SIM, if applicable. Return null if it is
     * unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     * @deprecated - use TeleponyManagerEx instead if apk use
     *               use PhoneInterfaceManager instead if framework use
     */
    @Deprecated public String getSimSerialNumberGemini(int simId) {
        try {
            return getSubscriberInfo(simId).getIccSerialNumber();
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }
    
    /**
     * Gemini
     * Returns the voice mail count. Return 0 if unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     * @deprecated - use TeleponyManagerEx instead if apk use
     *               use PhoneInterfaceManager instead if framework use
     */
    @Deprecated public int getVoiceMessageCountGemini(int simId) {
        try {
            return getITelephony().getVoiceMessageCountGemini(simId);
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return 0;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return 0;
        }
    }

    /**
     * Gemini
     * Retrieves the alphabetic identifier associated with the voice
     * mail number.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @hide
     * @deprecated - use TeleponyManagerEx instead if apk use
     *               use PhoneInterfaceManager instead if framework use
     */
    @Deprecated public String getVoiceMailAlphaTagGemini(int simId) {
        try {
            return getSubscriberInfo(simId).getVoiceMailAlphaTag();
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();            
            return null;
        }
    }

    private IPhoneSubInfo getSubscriberInfo(int simId) {
        Rlog.d( TAG,"getSubscriberInfo simId = " + simId);
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService(PHONE_SUBINFO_SERVICE[simId]));
    }

    /**
     * Do sim authentication and return the raw data of result.
     * Returns the hex format string of auth result.
     * <p> random string in hex format
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
    @Deprecated public String simAuth(String strRand) {
        try {
           ITelephony telephony = getITelephony();
           if (telephony != null) {
               return getITelephony().simAuth(strRand);
           }else {
               return null;
           }
        } catch (RemoteException ex) {
            //Rlog.e(TAG, "simAuth RemoteException: " + ex.toString());
            ex.printStackTrace();            
        	return null;
        } 
    }

    /**
     * Do usim authentication and return the raw data of result.
     * Returns the hex format string of auth result.
     * <p> random string in hex format
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
    @Deprecated public String uSimAuth(String strRand, String strAutn) {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return getITelephony().uSimAuth(strRand, strAutn);
            }else {
                return null;
            }
        } catch (RemoteException ex) {
            //Rlog.e(TAG, "uSimAuth RemoteException: " + ex.toString());
            ex.printStackTrace();            
            return null;
        } 
    }    

   /**
   * @hide
   * @deprecated - use PhoneInterfaceManager instead
   */
   @Deprecated public String simAuthGemini(String strRand, int simId)  {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {			
                return getITelephony().simAuthGemini(strRand, simId);
            }else {
			    return null;
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } 
    }

    /**
    * @hide
    * @deprecated - use PhoneInterfaceManager instead
    */
    @Deprecated public String uSimAuthGemini(String strRand, String strAutn, int simId) {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {			
                return getITelephony().uSimAuthGemini(strRand, strAutn, simId);
            }else {
                return null;
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();        
            return null;
        } 
    }

    /*Add by mtk80372 for Barcode number*/
    /**
     * 
     * Get the device information from the type of information
     * @param type  Revision Type, please refer to AT+ EGMR command
     * @param message return information
     *
     * @hide
     * @deprecated - use function in Itelephony(PhoneInterfaceManager) instead
     */    
    @Deprecated public void getMobileRevisionAndImei(int type, Message message) {
        try {
            getITelephony().getMobileRevisionAndImei(type, message);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return ;
        } catch (NullPointerException ex) {
            return ;
        }
    }   
    
    private  int getDefaultSim()
    {	
    	int default_sim = PhoneConstants.GEMINI_SIM_1;
    	
        /* Solve [ALPS00305232]Voicemail can't be dialed out when set SIMB as default SIM, mtk04070, 20120626 */
        try {
            if (!FeatureOption.MTK_BSP_PACKAGE) {
                
                // handle single sim condition
                boolean single_sim = false;
                int first_single_sim = PhoneConstants.GEMINI_SIM_1;
                int counter = 0;
                for (int i = PhoneConstants.GEMINI_SIM_1; i < PhoneConstants.GEMINI_SIM_NUM; i++) {
                    if (getITelephonyEx().hasIccCard(i)) {
                        ++counter;
                        if (counter==1) {
                            single_sim = true;
                            first_single_sim = i;
//                            Rlog.d(TAG, "getDefaultSim first inserted sim: sim" + (i+1));
                        } else if (counter > 1) {
                            single_sim = false;
//                            Rlog.d(TAG, "getDefaultSim more than 1 sim found.");
                            break; // found more than 1 sim, break loop. 
                        }
                    }
                }
                Rlog.d(TAG, "getDefaultSim check single_sim=" + single_sim + ", first_single_sim=" + first_single_sim);
                
                if (single_sim) {
                    default_sim = first_single_sim;
                    
                    Rlog.d(TAG, "getDefaultSim = " + default_sim);
                    return default_sim;
                }
            }
            
            // handle bsp package and non-single sim condition
            default_sim = SystemProperties.getInt(PhoneConstants.GEMINI_DEFAULT_SIM_PROP, -1);
            if (default_sim == -1) {
               // the property didn't exist, set it as SIM1
               SystemProperties.set(PhoneConstants.GEMINI_DEFAULT_SIM_PROP, 
                                 String.valueOf(PhoneConstants.GEMINI_SIM_1));
               default_sim = PhoneConstants.GEMINI_SIM_1;
            }
            Rlog.d(TAG, "getDefaultSim = " + default_sim);
        } catch (Exception ex) {
            Rlog.d(TAG, "getDefaultSim has exception (" + ex.getMessage() + "), set as default PhoneConstants.GEMINI_SIM_1");
            default_sim = PhoneConstants.GEMINI_SIM_1;
        }
        
        return default_sim;
    }

    /**
    * @hide    
    */
    public int getSmsDefaultSim() {
        try {
            return getITelephony().getSmsDefaultSim();
        } catch (RemoteException ex) {
            return PhoneConstants.GEMINI_SIM_1;
        } catch (NullPointerException ex) {
            return PhoneConstants.GEMINI_SIM_1;
        }
    }

   /**
     *send BT SIM profile of Connect SIM
     * @param simId specify which SIM to connect
     * @param btRsp fetch the response data.
     * @return success or error code.
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
   @Deprecated public int btSimapConnectSIM(int simId,  BtSimapOperResponse btRsp) {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.btSimapConnectSIM(simId, btRsp);
            }
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return 1;
   }

   /**
     *send BT SIM profile of Disconnect SIM
     *
     * @return success or error code.
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
   @Deprecated public int btSimapDisconnectSIM() {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.btSimapDisconnectSIM();
            }
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return 1;
   }

   /**
     *Transfer APDU data through BT SAP
     * @param type Indicate which transport protocol is the preferred one
     * @param cmdAPDU APDU data to transfer in hex character format
     * @param btRsp fetch the response data.
     * @return success or error code.
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
   @Deprecated public int btSimapApduRequest(int type, String cmdAPDU, BtSimapOperResponse btRsp) {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.btSimapApduRequest(type, cmdAPDU, btRsp);
            }
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return 1;
   }

    /**
     *send BT SIM profile of Reset SIM
     * @param type Indicate which transport protocol is the preferred one
     * @param btRsp fetch the response data.
     * @return success or error code.
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
   @Deprecated public int btSimapResetSIM(int type, BtSimapOperResponse btRsp) {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.btSimapResetSIM(type, btRsp);
            }
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return 1;
   }

   /**
     *send BT SIM profile of Power On SIM
     * @param type Indicate which transport protocol is the preferred onet
     * @param btRsp fetch the response data.
     * @return success or error code.
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
   @Deprecated public int btSimapPowerOnSIM(int type, BtSimapOperResponse btRsp) {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.btSimapPowerOnSIM(type, btRsp);
            }          
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return 1;
   }

   /**
     *send BT SIM profile of PowerOff SIM
     * @return success or error code.
     * @hide
     * @deprecated - use PhoneInterfaceManager instead
     */
   @Deprecated public int btSimapPowerOffSIM() {
        try {
            ITelephony telephony = getITelephony();
            if (telephony != null) {
                return telephony.btSimapPowerOffSIM();
            }
        } catch (RemoteException ex) {
            // the phone process is restarting.
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return 1;
   }   

    /**
     * Get the count of missed call.
     *
     * @return Return the count of missed call. 
     * @hide
     */
    public int getMissedCallCount() {
    	 int result = 0;
       try {
              ITelephony telephony = getITelephony();
              if (telephony != null) {
                  Rlog.d(TAG, "[TelephonyManager] Call PhoneInterfaceManager - getMissedCallCount "); 
                  result = telephony.getMissedCallCount();
              }
       } catch (RemoteException ex) {
           ex.printStackTrace();
		          /* Do nothing */
       }    	
            
    	 return result;
    }	    

 /**
   *   Description : Adjust modem radio power for Lenovo SAR requirement.
  *       Level scope : 0 ~ 64.
   *   Arthur      : mtk04070
   *   Date        : 2012.01.09
   *   Return value: True for success, false for failure
   * @hide
   * @deprecated use function in Itelephony(PhoneInterfaceManager) instead
    */
   @Deprecated public boolean adjustModemRadioPower(int level_2G, int level_3G) {

	    boolean result = false;

            try {
              ITelephony telephony = getITelephony();
              if (telephony != null) {
                  Rlog.d(TAG, "[TelephonyManager] Call PhoneInterfaceManager - adjustModemRadioPower "); 
                  result = telephony.adjustModemRadioPower(level_2G, level_3G);
              }
            } catch (RemoteException ex) {
		  /* Do nothing */
            }

            Rlog.d(TAG, "[TelephonyManager]adjustModemRadioPower, level = " + level_2G + ", " + level_3G);

            return result;
    }   

/**
   *   Description : Adjust modem radio power by band for Lenovo SAR requirement.
  *       Level scope : 0 ~ 255.
   *   Arthur      : mtk04070
   *   Date        : 2012.05.31
   *   Return value: True for success, false for failure
   * @hide
   * @deprecated use function in Itelephony(PhoneInterfaceManager) instead
   */
   @Deprecated public boolean adjustModemRadioPowerByBand(int band, int level) {
        boolean result = false;
		int rat = 1;
        boolean is3G = (band > SAR_BAND_GSM_PCS1900);
        boolean validValue = ((band >= SAR_BAND_GSM_GSM850) && 
                              (band <= SAR_BAND_UMTS_WCDMA_1700) &&
                              (level >= 0) &&
                              (level <= 255));

        if (validValue) {
           try {
              ITelephony telephony = getITelephony();
              if (telephony != null) {
                  if (is3G) {
                     /* The band value of 3G starts from 0 */
                     band-=SAR_BAND_UMTS_WCDMA_IMT_2000;
					 rat = 2;
                  }
                  Rlog.d(TAG, "[TelephonyManager]adjustModemRadioPowerByBand, band = " + band + ", level = " + level); 
                  result = telephony.adjustModemRadioPowerByBand(rat, band, level);
              }
           } catch (RemoteException ex) {
              /* Do nothing */
           }
        }
		else {
           Rlog.d(TAG, "[TelephonyManager]Invalid band or level value !");
        }
		
        return result;
   }
    
   //MTK-END [mtk04070][111116][ALPS00093395]MTK proprietary methods
}
