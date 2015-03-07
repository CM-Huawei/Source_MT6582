/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2013. All rights reserved.
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

package com.mediatek.phone.wrapper;

import java.util.List;

import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.ServiceState;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

import com.android.internal.telephony.gemini.MTKCallManager;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.phone.PhoneGlobals;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.phone.PhoneLog;
import com.mediatek.phone.gemini.GeminiUtils;

import junit.framework.Assert;

public class CallManagerWrapper {
    private static final String TAG = "CallManagerWrapper";

    /**
     * Register for ServiceState changed.
     *
     * @param phone
     * @param handler
     * @param whats
     */
    public static void registerForServiceStateChanged(Phone phone, Handler handler, int[] whats) {
        Assert.assertNotNull(phone);
        if (GeminiUtils.isGeminiSupport()) {
            GeminiPhone gPhone = (GeminiPhone) phone;
            final int[] geminiSlots = GeminiUtils.getSlots();

            Assert.assertTrue(whats.length >= geminiSlots.length);

            for (int i = 0; i < geminiSlots.length; i++) {
                gPhone.getPhonebyId(geminiSlots[i]).unregisterForServiceStateChanged(handler);
                gPhone.getPhonebyId(geminiSlots[i]).registerForServiceStateChanged(handler, whats[i], null);
            }
        } else {
            // Safe even if not currently registered
            phone.unregisterForServiceStateChanged(handler);
            phone.registerForServiceStateChanged(handler, whats[0], null);
        }
    }

    /**
     * Unregisters for ServiceStateChange notification.
     *
     * @param phone
     * @param handler
     * @param whats
     */
    public static void unregisterForServiceStateChanged(Phone phone, Handler handler, int[] whats) {
        // the method is safe to call even if we haven't set phone yet.
        if (phone != null) {
            if (GeminiUtils.isGeminiSupport()) {
                final int[] geminiSlots = GeminiUtils.getSlots();

                Assert.assertTrue(whats.length >= geminiSlots.length);

                for (int i = 0; i < geminiSlots.length; i++) {
                    handler.removeMessages(whats[i]);
                    // Safe even if not currently registered
                    ((GeminiPhone) phone).getPhonebyId(geminiSlots[i]).unregisterForServiceStateChanged(handler);
                }
            } else {
                // Safe even if not currently registered
                phone.unregisterForServiceStateChanged(handler);
            }
        }

        // Clean up any pending message too
        handler.removeMessages(whats[0]);
    }

    /**
     * @see #registerForNewRingingConnection(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForNewRingingConnection(Handler handler, int what) {
        registerForNewRingingConnection(handler, what, null);
    }

    /**
     * Register for notifies when a new ringing or waiting connection has
     * appeared.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForNewRingingConnection(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForNewRingingConnectionEx(handler, what,
                        obj, geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForNewRingingConnection(handler, what, obj);
        }
    }

    /**
     * Unregisters for new ringing connection notification. Extraneous calls are
     * tolerated silently
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForNewRingingConnection(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForNewRingingConnectionEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForNewRingingConnection(handler);
        }
    }

    /**
     * @see #registerForDisconnect(Handler, int[], Object)
     * @param handler
     * @param what
     */
    public static void registerForDisconnect(Handler handler, int what) {
        final int[] geminiSlots = GeminiUtils.getSlots();
        final int count = geminiSlots.length;
        int[] whats = new int[count];
        for (int i = 0; i < whats.length; i++) {
            whats[i] = what;
        }
        registerForDisconnect(handler, whats, null);
    }

    /**
     * @see #registerForDisconnect(Handler, int[], Object)
     * @param handler
     * @param what
     */
    public static void registerForDisconnect(Handler handler, int what, Object obj) {
        final int[] geminiSlots = GeminiUtils.getSlots();
        final int count = geminiSlots.length;
        int[] whats = new int[count];
        for (int i = 0; i < whats.length; i++) {
            whats[i] = what;
        }
        registerForDisconnect(handler, whats, obj);
    }

    /**
     * see {@link #registerForDisconnect(Handler, int[], Object)}
     * @param handler
     * @param whats
     */
    public static void registerForDisconnect(Handler handler, int[] whats) {
        registerForDisconnect(handler, whats, null);
    }

    /**
     * Register for notifies when a voice connection has disconnected, either
     * due to local or remote hangup or error.
     *
     * @param handler Handler that receives the notification message.
     * @param whats User-defined message codes.
     * @param obj User object.
     */
    public static void registerForDisconnect(Handler handler, int[] whats, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            Assert.assertTrue(whats.length >= geminiSlots.length);

            for (int i = 0; i < geminiSlots.length; i++) {
                CallManager.getInstance().registerForDisconnectEx(handler, whats[i], obj,
                        geminiSlots[i]);
            }
        } else {
            /**
             * M: [ALPS00448197] M: [Rose][JB2][Free Test][Call]The voice of
             * playing song isn't transfered via Bluetooth headset, No any sound
             * after the incoming call is ended by remote side(5/5). @{
             */
            CallManager.getInstance().registerForDisconnect(handler, whats[0], obj);
        }
    }

    /**
     * Unregisters for voice disconnection notification. Extraneous calls are
     * tolerated silently
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForDisconnect(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForDisconnectEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForDisconnect(handler);
        }
    }

    /**
     * @see #registerForPreciseCallStateChanged(Handler, int, Object)
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     */
    public static void registerForPreciseCallStateChanged(Handler handler, int what) {
        registerForPreciseCallStateChanged(handler, what, null);
    }

    /**
     * Register for getting notifications for change in the Call State
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForPreciseCallStateChanged(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForPreciseCallStateChangedEx(handler,
                        what, obj, geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForPreciseCallStateChanged(handler, what, obj);
        }
    }

    /**
     * Unregisters for voice call state change notifications. Extraneous calls
     * are tolerated silently.
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForPreciseCallStateChanged(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForPreciseCallStateChangedEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForPreciseCallStateChanged(handler);
        }
    }

    /**
     * Notifies when a previously untracked non-ringing/waiting connection has
     * appeared.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     */
    public static void registerForUnknownConnection(Handler handler, int what) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForUnknownConnectionEx(handler, what,
                        null, geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForUnknownConnection(handler, what, null);
        }
    }

    /**
     * Unregisters for unknown connection notifications.
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForUnknownConnection(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForUnknownConnectionEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForUnknownConnection(handler);
        }
    }

    /**
     * @see #registerForIncomingRing(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForIncomingRing(Handler handler, int what) {
        registerForIncomingRing(handler, what, null);
    }

    /**
     * Notifies when an incoming call rings.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForIncomingRing(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForIncomingRingEx(handler, what, obj,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForIncomingRing(handler, what, obj);
        }
    }

    /**
     * Unregisters for ring notification. Extraneous calls are tolerated
     * silently
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForIncomingRing(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForIncomingRingEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForIncomingRing(handler);
        }
    }

    /**
     * Notifies when out-band ringback tone is needed.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     */
    public static void registerForRingbackTone(Handler handler, int what) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int gs : geminiSlots) {
                CallManager.getInstance().registerForRingbackToneEx(handler, what, null, gs);
            }
        } else {
            CallManager.getInstance().registerForRingbackTone(handler, what, null);
        }
    }

    /**
     * Unregisters for ringback tone notification.
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForRingbackTone(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForDisconnectEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForRingbackTone(handler);
        }
    }

    /**
     * @see #registerForVtRingInfo(Handler, int, Object)
     * @param handler
     * @param ringInfo
     */
    public static void registerForVtRingInfo(Handler handler, int ringInfo){
        registerForVtRingInfo(handler, ringInfo, null);
    }
    /**
     * Find the 3G slot and register for VT ringInfo
     *
     * @param h Handler that receives the notification message.
     * @param ringInfo User-defined message code.
     * @param obj User object.
     */
    public static void registerForVtRingInfo(Handler handler, int ringInfo, Object obj) {
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            if (GeminiUtils.isGeminiSupport()) {
                // as when register is too early, framework init may not
                // completed, the get3GCapabilitySIM() will return wrong value,
                // so register for every slot
                final int[] geminiSlots = GeminiUtils.getSlots();
                for (int geminiSlot : geminiSlots) {
                    CallManager.getInstance().registerForVtRingInfoEx(handler, ringInfo,
                            obj, geminiSlot);
                }
            } else {
                CallManager.getInstance().registerForVtRingInfo(handler, ringInfo, obj);
            }
        }
    }

    /**
     * Find the 3G slot and unregister VT ringInfo
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForVtRingInfo(Handler handler) {
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            if (GeminiUtils.isGeminiSupport()) {
                final int[] geminiSlots = GeminiUtils.getSlots();
                for (int geminiSlot : geminiSlots) {
                    CallManager.getInstance().unregisterForVtRingInfoEx(handler,
                            geminiSlot);
                }
            } else {
                CallManager.getInstance().unregisterForVtRingInfo(handler);
            }
        }
    }

    /**
     * @see #registerForVtStatusInfo(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForVtStatusInfo(Handler handler, int what) {
        registerForVtStatusInfo(handler, what, null);
    }

    /**
     * Register VT statusInfo
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForVtStatusInfo(Handler handler, int what, Object obj) {
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            if (GeminiUtils.isGeminiSupport()) {
                // as when register is too early, framework init may not
                // completed,the get3GCapabilitySIM() will return wrong value,
                // so register for every slot
                final int[] geminiSlots = GeminiUtils.getSlots();
                for (int geminiSlot : geminiSlots) {
                    CallManager.getInstance().registerForVtStatusInfoEx(handler, what, obj,
                            geminiSlot);
                }
            } else {
                CallManager.getInstance().registerForVtStatusInfo(handler, what, obj);
            }
        }
    }

    /**
     * Unregister VT statusInfo
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForVtStatusInfo(Handler handler) {
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            if (GeminiUtils.isGeminiSupport()) {
                final int[] geminiSlots = GeminiUtils.getSlots();
                for (int geminiSlot : geminiSlots) {
                    CallManager.getInstance().unregisterForVtStatusInfoEx(handler,
                            geminiSlot);
                }
            } else {
                CallManager.getInstance().unregisterForVtStatusInfo(handler);
            }
        }
    }

    /**
     * @see #registerForVtReplaceDisconnect(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForVtReplaceDisconnect(Handler handler, int what) {
        registerForVtReplaceDisconnect(handler, what, null);
    }

    /**
     * Register for notification when VT replace disconnected
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForVtReplaceDisconnect(Handler handler, int what, Object obj) {
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            if (GeminiUtils.isGeminiSupport()) {
                // as when register is too early, framework init may not
                // completed, the get3GCapabilitySIM() will return wrong value,
                // so register for every slot
                final int[] geminiSlots = GeminiUtils.getSlots();
                for (int geminiSlot : geminiSlots) {
                    CallManager.getInstance().registerForVtReplaceDisconnectEx(handler,
                            what, obj, geminiSlot);
                }
            } else {
                CallManager.getInstance().registerForVtReplaceDisconnect(handler, what, obj);
            }
        }
    }

    /**
     * Unregister for notification when VT replace disconnected
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForVtReplaceDisconnect(Handler handler) {
        if (FeatureOption.MTK_VT3G324M_SUPPORT) {
            if (GeminiUtils.isGeminiSupport()) {
                final int[] geminiSlots = GeminiUtils.getSlots();
                for (int geminiSlot : geminiSlots) {
                    CallManager.getInstance().unregisterForVtReplaceDisconnectEx(handler,
                            geminiSlot);
                }
            } else {
                CallManager.getInstance().unregisterForVtReplaceDisconnect(handler);
            }
        }
    }

    /**
     * @see #registerForInCallVoicePrivacyOn(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForInCallVoicePrivacyOn(Handler handler, int what) {
        registerForInCallVoicePrivacyOn(handler, what, null);
    }

    /**
     * Register for notifications when a sInCall VoicePrivacy is enabled
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForInCallVoicePrivacyOn(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForInCallVoicePrivacyOnEx(handler, what,
                        obj, geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForInCallVoicePrivacyOn(handler, what, obj);
        }
    }

    public static void registerForInCallVoicePrivacyOff(Handler handler, int what) {
        registerForInCallVoicePrivacyOff(handler, what, null);
    }

    /**
     * Register for notifications when a sInCall VoicePrivacy is disabled
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForInCallVoicePrivacyOff(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForInCallVoicePrivacyOffEx(handler, what,
                        obj, geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForInCallVoicePrivacyOff(handler, what, obj);
        }
    }

    /**
     * Unregister for notifications when a sInCall VoicePrivacy is enabled
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForInCallVoicePrivacyOn(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForInCallVoicePrivacyOnEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForInCallVoicePrivacyOn(handler);
        }
    }

    /**
     * Unregister for notifications when a sInCall VoicePrivacy is disabled
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForInCallVoicePrivacyOff(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForInCallVoicePrivacyOffEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForInCallVoicePrivacyOff(handler);
        }
    }

    /**
     * Register for notifications that an MMI request has completed its network
     * activity and is in its final state.
     *
     * @paramhandler Handler that receives the notification message.
     * @param whats User-defined message codes.
     */
    public static void registerForMmiComplete(Handler handler, int[] whats) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();

            Assert.assertTrue(whats.length >= geminiSlots.length);

            for (int i = 0; i < geminiSlots.length; i++) {
                CallManager.getInstance().registerForMmiCompleteEx(handler, whats[i], null,
                        geminiSlots[i]);
            }
        } else {
            CallManager.getInstance().registerForMmiComplete(handler, whats[0], null);
        }
    }

    /**
     * Unregisters for MMI complete notification.
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForMmiComplete(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance()
                        .unregisterForMmiCompleteEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForMmiComplete(handler);
        }
    }

    /**
     * Register for notifications of initiation of a new MMI code request.
     *
     * @param handler Handler that receives the notification message.
     * @param whats User-defined message codes.
     */
    public static void registerForMmiInitiate(Handler handler, int[] whats) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();

            Assert.assertTrue(whats.length >= geminiSlots.length);

            for (int i = 0; i < geminiSlots.length; i++) {
                CallManager.getInstance().registerForMmiInitiateEx(handler, whats[i], null,
                        geminiSlots[i]);
            }
        } else {
            CallManager.getInstance().registerForMmiInitiate(handler, whats[0], null);
        }
    }

    /**
     * Unregisters for new MMI initiate notification.
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForMmiInitiate(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance()
                        .unregisterForMmiInitiateEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForMmiInitiate(handler);
        }
    }

    /**
     * @see #registerForCrssSuppServiceNotification(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForCrssSuppServiceNotification(Handler handler, int what) {
        registerForCrssSuppServiceNotification(handler, what, null);
    }

    /**
     * Register for notifications about crss supplementary service
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     */
    public static void registerForCrssSuppServiceNotification(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForCrssSuppServiceNotificationEx(handler,
                        what, obj, geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForCrssSuppServiceNotification(handler, what, obj);
        }
    }

    /**
     * Unregister for notifications about crss supplementary service
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForCrssSuppServiceNotification(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForCrssSuppServiceNotificationEx(
                        handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForCrssSuppServiceNotification(handler);
        }
    }

    /**
     * @see #registerForPostDialCharacter(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForPostDialCharacter(Handler handler, int what) {
        registerForPostDialCharacter(handler, what, null);
    }

    /**
     * Sets an event to be fired when the telephony system processes a post-dial
     * character on an outgoing call
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     */
    public static void registerForPostDialCharacter(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForPostDialCharacterEx(handler, what, obj,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForPostDialCharacter(handler, what, obj);
        }
    }

    /**
     * Unregister for notifications when post dial character
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForPostDialCharacter(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForPostDialCharacterEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForPostDialCharacter(handler);
        }
    }

    /**
     * @see #registerForSuppServiceFailed(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForSuppServiceFailed(Handler handler, int what) {
        registerForSuppServiceFailed(handler, what, null);
    }

    /**
     * Register for notifications when a supplementary service attempt fails.
     * Message.obj will contain an AsyncResult.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForSuppServiceFailed(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForSuppServiceFailedEx(handler, what, obj,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForSuppServiceFailed(handler, what, obj);
        }
    }

    /**
     * Unregister for notifications when a supplementary service attempt fails
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForSuppServiceFailed(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForSuppServiceFailedEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForSuppServiceFailed(handler);
        }
    }

    /**
     * @see #registerForSuppServiceNotification(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForSuppServiceNotification(Handler handler, int what) {
        registerForSuppServiceNotification(handler, what, null);
    }

    /**
     * Register for notifications about supplementary service
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForSuppServiceNotification(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForSuppServiceNotificationEx(handler,
                        what, obj, geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForSuppServiceNotification(handler, what, obj);
        }
    }

    /**
     * Unregister for notifications about supplementary service
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForSuppServiceNotification(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForSuppServiceNotificationEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForSuppServiceNotification(handler);
        }
    }

    /**
     * Register for notifications about speechInfo
     *
     * @param handler Handler that receives the notification message.90.ft-
     * @param whats User-defined message codes.
     */
    public static void registerForSpeechInfo(Phone phone, Handler handler, int[] whats) {
        boolean isSipCall = phone.getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;
        if (GeminiUtils.isGeminiSupport() && !isSipCall) {
            final int[] geminiSlots = GeminiUtils.getSlots();

            Assert.assertTrue(whats.length >= geminiSlots.length);

            for (int i = 0; i < geminiSlots.length; i++) {
                CallManager.getInstance().registerForSpeechInfoEx(handler, whats[i], null,
                        geminiSlots[i]);
            }
        } else {
            CallManager.getInstance().registerForSpeechInfo(handler, whats[0], null);
        }
    }

    /**
     * Unregister for notifications about speechInfo
     *
     * @param handler Handler to be removed from the registrant list.
     * @param slotId specify slot id
     */
    public static void unregisterForSpeechInfo(Handler handler, int slotId) {
        if (GeminiUtils.isGeminiSupport()) {
            if (!GeminiUtils.isValidSlot(slotId)) {
                PhoneLog.i(TAG, "[unregisterForSpeechInfo], invalid slotId = " + slotId);
                return;
            }
            CallManager.getInstance().unregisterForSpeechInfoEx(handler, slotId);
        } else {
            CallManager.getInstance().unregisterForSpeechInfo(handler);
        }
    }

    /**
     * Unregister for notifications about speechInfo
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForSpeechInfo(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForSpeechInfoEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForSpeechInfo(handler);
        }
    }

    /**
     * @see #registerForCdmaOtaStatusChange(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForCdmaOtaStatusChange(Handler handler, int what) {
        registerForCdmaOtaStatusChange(handler, what, null);
    }

    /**
     * Register for notifications when CDMA OTA Provision status change
     *
     * @param h Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForCdmaOtaStatusChange(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForCdmaOtaStatusChangeEx(handler, what, obj,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForCdmaOtaStatusChange(handler, what, obj);
        }
    }

    /**
     * Unregister for notifications when CDMA OTA Provision status change
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForCdmaOtaStatusChange(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForCdmaOtaStatusChangeEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForCdmaOtaStatusChange(handler);
        }
    }

    /**
     * @see #registerForCallWaiting(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForCallWaiting(Handler handler, int what) {
        registerForCallWaiting(handler, what, null);
    }

    /**
     * Register for notifications when CDMA call waiting comes
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForCallWaiting(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForCallWaitingEx(handler, what, obj,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForCallWaiting(handler, what, obj);
        }
    }

    /**
     * Unregister for notifications when CDMA Call waiting comes
     *
     * @param handler to be removed from the registrant list.
     */
    public static void unregisterForCallWaiting(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForCallWaitingEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForCallWaiting(handler);
        }
    }

    /**
     * @see #registerForDisplayInfo(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForDisplayInfo(Handler handler, int what) {
        registerForDisplayInfo(handler, what, null);
    }

    /**
     * Registers for display information notifications.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForDisplayInfo(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForDisplayInfoEx(handler, what, obj,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForDisplayInfo(handler, what, obj);
        }
    }

    /**
     * Unregisters for display information notifications.
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForDisplayInfo(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForDisplayInfoEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForDisplayInfo(handler);
        }
    }

    /**
     * @see #registerForSignalInfo(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForSignalInfo(Handler handler, int what) {
        registerForSignalInfo(handler, what, null);
    }

    /**
     * Register for signal information notifications from the network.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForSignalInfo(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForSignalInfoEx(handler, what, obj,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().registerForSignalInfo(handler, what, obj);
        }
    }

    /**
     * Unregisters for signal information notifications.
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForSignalInfo(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForSignalInfoEx(handler, geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForSignalInfo(handler);
        }
    }

    /**
     * Return the default phone or null if no phone available
     *
     * @param slotId specify slot id
     * @return phone default phone
     */
    public static Phone getDefaultPhone(int slotId) {
        Phone phone = null;
        if (GeminiUtils.isGeminiSupport()) {
            if (!GeminiUtils.isValidSlot(slotId)) {
                PhoneLog.i(TAG, "[getDefaultPhone], invalid slotId = " + slotId);
                return null;
            }
            phone = ((GeminiPhone) MTKCallManager.getInstance().getDefaultPhoneGemini())
                    .getPhonebyId(slotId);
        } else {
            phone = CallManager.getInstance().getDefaultPhone();
        }
        return phone;
    }

    /**
     * Return the default phone or null if no phone available
     *
     * @return phone default phone
     */
    public static Phone getDefaultPhone() {
        Phone phone = null;
        if (GeminiUtils.isGeminiSupport()) {
            phone = ((GeminiPhone) MTKCallManager.getInstance().getDefaultPhoneGemini());
        } else {
            phone = CallManager.getInstance().getDefaultPhone();
        }
        return phone;
    }

    /**
     * Register phone to CallManager
     *
     * @param phone the phone to be register to CallManager
     */
    public static void registerPhone(Phone phone) {
        if (GeminiUtils.isGeminiSupport()) {
            MTKCallManager.getInstance().registerPhoneGemini(phone);
        } else {
            CallManager.getInstance().registerPhone(phone);
        }
    }

    /**
     * @see #registerForServiceStateChanged(Handler, int, Object)
     * @param handler
     * @param what
     */
    public static void registerForServiceStateChanged(Handler handler, int what) {
        registerForServiceStateChanged(handler, what, null);
    }

    /**
     * Register for ServiceState changed.
     *
     * @param handler Handler that receives the notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public static void registerForServiceStateChanged(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().registerForServiceStateChangedEx(handler, what,
                        geminiSlot, geminiSlot);
            }
        } else {
            CallManager.getInstance().getDefaultPhone().registerForServiceStateChanged(handler, what, obj);
        }
    }

    /**
     * Unregisters for ServiceStateChange notification. Extraneous calls are
     * tolerated silently
     *
     * @param handler Handler to be removed from the registrant list.
     */
    public static void unregisterForServiceStateChanged(Handler handler) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int geminiSlot : geminiSlots) {
                CallManager.getInstance().unregisterForServiceStateChangedEx(handler,
                        geminiSlot);
            }
        } else {
            CallManager.getInstance().unregisterForServiceStateChanged(handler);
        }
    }

    /**
     * send burst DTMF tone.
     *
     * @param dtmfString is string representing the dialing digit(s) in the
     *            active call
     * @param on the DTMF ON length in milliseconds, or 0 for default
     * @param off the DTMF OFF length in milliseconds, or 0 for default
     * @param onComplete is the callback message when the action is processed by
     *            BP
     */
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        if (PhoneGlobals.getPhone() instanceof GeminiPhone) {
            final int cdmaSlot = GeminiUtils.getCDMASlot();
            Phone cdmaPhone = ((GeminiPhone) PhoneGlobals.getPhone()).getPhonebyId(cdmaSlot);
            cdmaPhone.sendBurstDtmf(dtmfString, on, off, onComplete);
            PhoneLog.d(TAG, "[sendBurstDtmf], cdmaSlot = " + cdmaSlot);
        } else {
            CallManager.getInstance().sendBurstDtmf(dtmfString, on, off, onComplete);
        }
    }

    /**
     * Make a VT call.
     *
     * @param phone specify which phone to make call.
     * @param dialString phone number to be dialed out.
     * @param slotId specify slot id
     * @return Connection
     */
    public static Connection vtDial(Phone phone, String dialString, int slotId) {
        Assert.assertNotNull(phone);
        Connection conn = null;
        int dialSlotId = slotId;
        try {
            if (GeminiUtils.isGeminiSupport()) {
                if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
                    if (!GeminiUtils.isValidSlot(dialSlotId)) {
                        PhoneLog.i(TAG, "[vtDial], invalid dialSlotId = " + dialSlotId);
                        return conn;
                    }
                } else {
                    dialSlotId = GeminiUtils.getDefaultSlot();
                    PhoneLog.d(TAG, "[vtDial], Only support gemini, dialSlot = " + dialSlotId);
                }
            }
            conn = CallManager.getInstance().vtDial(PhoneWrapper.getPhoneBySlotId(phone, dialSlotId), dialString);
        } catch (CallStateException e) {
            e.printStackTrace();
            conn = null;
        }
        return conn;
    }

    /**
     * Initiate a new voice connection. This happens asynchronously, so you
     * cannot assume the audio path is connected (or a call index has been
     * assigned) until PhoneStateChanged notification has occurred.
     *
     * @param phone specify which phone to make call.
     * @param numberToDial phone number to be dialed out.
     * @param slotId specify slot id
     * @return Connection
     */
    public static Connection dial(Phone phone, String numberToDial, int slotId) throws CallStateException {
        Assert.assertNotNull(phone);
        boolean isSipPhone = phone.getPhoneType() == PhoneConstants.PHONE_TYPE_SIP;
        Connection connection = null;
        int dialSlot = slotId;
        try {
            if (GeminiUtils.isGeminiSupport() && !isSipPhone) {
                if (!GeminiUtils.isValidSlot(dialSlot)) {
                    dialSlot = SystemProperties.getInt(PhoneConstants.GEMINI_DEFAULT_SIM_PROP,
                            PhoneWrapper.UNSPECIFIED_SLOT_ID);
                }
            }
            connection = CallManager.getInstance().dial(PhoneWrapper.getPhoneBySlotId(phone, dialSlot), numberToDial);
        } catch (CallStateException ex) {
            throw new CallStateException("cannot dial, numberToDial:" + numberToDial + ", dialSlot:"
                    + dialSlot);
        }
        return connection;
    }

    public static void registerForCipherIndication(Handler handler, int what, Object obj) {
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int i = 0; i < geminiSlots.length; i++) {
                CallManager.getInstance().registerForCipherIndicationEx(handler, what, obj, geminiSlots[i]);
            }
        } else {
            CallManager.getInstance().registerForCipherIndication(handler, what, obj);
        }
    }

    public static void unregisterForCipherIndication(Handler handler){
        if (GeminiUtils.isGeminiSupport()) {
            final int[] geminiSlots = GeminiUtils.getSlots();
            for (int i = 0; i < geminiSlots.length; i++) {
                CallManager.getInstance().unregisterForCipherIndicationEx(handler, geminiSlots[i]);
            }
        } else {
            CallManager.getInstance().unregisterForCipherIndication(handler);
        }
    }

    public static Object getCallManager(){
        if (GeminiUtils.isGeminiSupport()) {
            return MTKCallManager.getInstance();
        }
        return CallManager.getInstance();
    }
}

