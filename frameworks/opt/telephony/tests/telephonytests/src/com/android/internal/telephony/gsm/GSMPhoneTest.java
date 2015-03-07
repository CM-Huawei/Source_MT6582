/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF GSMTestHandler.ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.gsm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.test.AndroidTestCase;
import android.test.PerformanceTestCase;
import android.test.mock.MockContentResolver;
import android.util.Log;
import android.util.TypedValue;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants.State;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.UiccController;


public class GSMPhoneTest extends AndroidTestCase implements PerformanceTestCase {
    private static final String TAG = "GSMPhoneTest";
    private SimulatedRadioControl mRadioControl;
    private GSMPhone mGSMPhone;
    private GSMTestHandler mGSMTestHandler;
    private Handler mHandler;
    private Context mMockContext;
    private static boolean sIsGSMPhoneInit = false;

    private static final int EVENT_PHONE_STATE_CHANGED = 1;
    private static final int EVENT_DISCONNECT = 2;
    private static final int EVENT_RINGING = 3;
    private static final int EVENT_CHANNEL_OPENED = 4;
    private static final int EVENT_POST_DIAL = 5;
    private static final int EVENT_DONE = 6;
    private static final int EVENT_SSN = 7;
    private static final int EVENT_MMI_INITIATE = 8;
    private static final int EVENT_MMI_COMPLETE = 9;
    private static final int EVENT_IN_SERVICE = 10;
    private static final int SUPP_SERVICE_FAILED = 11;
    private static final int SERVICE_STATE_CHANGED = 12;
    private static final int EVENT_OEM_RIL_MESSAGE = 13;
    private static final int EVENT_GET_CURRENT_CALLS = 14;
    public static final int ANY_MESSAGE = -1;

    static final int CSMCC_SETUP_MSG = 0;
    static final int CSMCC_DISCONNECT_MSG = 1;
    static final int CSMCC_ALERT_MSG = 2;
    static final int CSMCC_CALL_PROCESS_MSG = 3;
    static final int CSMCC_SYNC_MSG = 4;
    static final int CSMCC_PROGRESS_MSG = 5;
    static final int CSMCC_CALL_CONNECTED_MSG = 6;
    static final int CSMCC_ALL_CALLS_DISC_MSG = 129;
    static final int CSMCC_MO_CALL_ID_ASSIGN_MSG = 130;
    static final int CSMCC_STATE_CHANGE_HELD = 131;
    static final int CSMCC_STATE_CHANGE_ACTIVE = 132;
    static final int CSMCC_STATE_CHANGE_DISCONNECTED = 133;
    static final int CSMCC_STATE_CHANGE_MO_DISCONNECTING = 134;

	private static final String mDialString = "+13125551212";
	private static final String mIncomingCallString = "18005551212";
	private static final String mIncomingCallString2 = "16505550100";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockContext = new MockContext();
        
        if (sIsGSMPhoneInit == false) {
        	UiccController.make(mMockContext, new SimulatedCommands(), PhoneConstants.GEMINI_SIM_1);
        	
        	sIsGSMPhoneInit = true;
        }

        mGSMTestHandler = new GSMTestHandler(mMockContext);

        mGSMTestHandler.start();
        synchronized (mGSMTestHandler) {
            do {
                mGSMTestHandler.wait();
            } while (mGSMTestHandler.getGSMPhone() == null);
        }

        mGSMPhone = mGSMTestHandler.getGSMPhone();
        mRadioControl = mGSMTestHandler.getSimulatedCommands();

        mHandler = mGSMTestHandler.getHandler();
        mGSMPhone.registerForPreciseCallStateChanged(mHandler, EVENT_PHONE_STATE_CHANGED, null);
        mGSMPhone.registerForNewRingingConnection(mHandler, EVENT_RINGING, null);
        mGSMPhone.registerForDisconnect(mHandler, EVENT_DISCONNECT, null);

        mGSMPhone.setOnPostDialCharacter(mHandler, EVENT_POST_DIAL, null);

        mGSMPhone.registerForSuppServiceNotification(mHandler, EVENT_SSN, null);
        mGSMPhone.registerForMmiInitiate(mHandler, EVENT_MMI_INITIATE, null);
        mGSMPhone.registerForMmiComplete(mHandler, EVENT_MMI_COMPLETE, null);
        mGSMPhone.registerForSuppServiceFailed(mHandler, SUPP_SERVICE_FAILED, null);

        mGSMPhone.registerForServiceStateChanged(mHandler, SERVICE_STATE_CHANGED, null);

        if (mRadioControl instanceof SimulatedCommands) {
            CommandsInterface ci = (CommandsInterface) mRadioControl;
            ci.getBasebandVersion(null);
            ci.setRadioPowerOn(null);
        } else {
            assertFalse("mRadioControl instanceof SimulatedCommands", true);
        }
        mGSMPhone.setRadioPowerOn();
        // wait until we get phone in both voice and data service
        Message msg;
        ServiceState state;

        do {
            msg = mGSMTestHandler.waitForMessage(SERVICE_STATE_CHANGED);
            assertNotNull("Message Time Out", msg);
            state = (ServiceState) ((AsyncResult) msg.obj).result;
        } while (state.getState() != ServiceState.STATE_IN_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        mRadioControl.shutdown();

        mGSMPhone.unregisterForPreciseCallStateChanged(mHandler);
        mGSMPhone.unregisterForNewRingingConnection(mHandler);
        mGSMPhone.unregisterForDisconnect(mHandler);
        mGSMPhone.setOnPostDialCharacter(mHandler, 0, null);
        mGSMPhone.unregisterForSuppServiceNotification(mHandler);
        mGSMPhone.unregisterForMmiInitiate(mHandler);
        mGSMPhone.unregisterForMmiComplete(mHandler);

        mGSMPhone = null;
        mRadioControl = null;
        mHandler = null;
        mGSMTestHandler.cleanup();

        super.tearDown();
    }

    // These test can only be run once.
    public int startPerformance(Intermediates intermediates) {
        return 1;
    }

    public boolean isPerformanceOnly() {
        return false;
    }


    //This test is causing the emulator screen to turn off. I don't understand
    //why, but I'm removing it until we can figure it out.
    public void brokenTestGeneral() throws Exception {
        Connection cn;
        Message msg;
        AsyncResult ar;

        // IDLE state

        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());
        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestCreateTime());
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());
        assertFalse(mGSMPhone.canConference());

        // One DIALING connection

        mRadioControl.setAutoProgressConnectingCall(false);

        mGSMPhone.dial("+13125551212");

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());

        msg = mGSMTestHandler.waitForMessage(EVENT_PHONE_STATE_CHANGED);
        assertNotNull("Message Time Out", msg);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertEquals(Call.State.DIALING, mGSMPhone.getForegroundCall().getState());
        assertTrue(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        /*do {
            mGSMTestHandler.waitForMessage(ANY_MESSAGE);
        } while (mGSMPhone.getForegroundCall().getConnections().size() == 0);*/

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DIALING,
                mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        cn = mGSMPhone.getForegroundCall().getConnections().get(0);
        assertTrue(!cn.isIncoming());
        assertEquals(Connection.PostDialState.NOT_STARTED, cn.getPostDialState());

        assertEquals(Connection.DisconnectCause.NOT_DISCONNECTED, cn.getDisconnectCause());

        assertFalse(mGSMPhone.canConference());

        // One ALERTING connection

        mRadioControl.progressConnectingCallState();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        }
        while (mGSMPhone.getForegroundCall().getState() != Call.State.ALERTING);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertTrue(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ALERTING, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        cn = mGSMPhone.getForegroundCall().getConnections().get(0);
        assertTrue(!cn.isIncoming());
        assertEquals(Connection.PostDialState.NOT_STARTED, cn.getPostDialState());
        assertFalse(mGSMPhone.canConference());

        // One ACTIVE connection

        mRadioControl.progressConnectingCallState();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.ACTIVE);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertTrue(mGSMPhone.getForegroundCall().getEarliestConnectTime() > 0);

        cn = mGSMPhone.getForegroundCall().getConnections().get(0);
        assertTrue(!cn.isIncoming());
        assertEquals(Connection.PostDialState.COMPLETE, cn.getPostDialState());
        assertFalse(mGSMPhone.canConference());

        // One disconnected connection
        mGSMPhone.getForegroundCall().hangup();

        msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        assertNotNull("Message Time Out", msg);

        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());
        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertTrue(mGSMPhone.getForegroundCall().getEarliestConnectTime() > 0);

        assertFalse(mGSMPhone.canConference());

        cn = mGSMPhone.getForegroundCall().getEarliestConnection();

        assertEquals(Call.State.DISCONNECTED, cn.getState());

        // Back to idle state

        mGSMPhone.clearDisconnected();

        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());
        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestCreateTime());
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        assertFalse(mGSMPhone.canConference());

        // cn left over from before phone.clearDisconnected();

        assertEquals(Call.State.DISCONNECTED, cn.getState());

        // One ringing (INCOMING) call

        mRadioControl.triggerRing("18005551212");

        msg = mGSMTestHandler.waitForMessage(EVENT_RINGING);
        assertNotNull("Message Time Out", msg);

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertTrue(mGSMPhone.getRingingCall().isRinging());

        ar = (AsyncResult) msg.obj;
        cn = (Connection) ar.result;
        assertTrue(cn.isRinging());
        assertEquals(mGSMPhone.getRingingCall(), cn.getCall());

        assertEquals(1, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.INCOMING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getRingingCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getRingingCall().getEarliestConnectTime());

        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestCreateTime());
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        cn = mGSMPhone.getRingingCall().getConnections().get(0);
        assertTrue(cn.isIncoming());
        assertEquals(Connection.PostDialState.NOT_STARTED, cn.getPostDialState());

        assertFalse(mGSMPhone.canConference());

        // One mobile terminated active call
        mGSMPhone.acceptCall();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getConnections().size() == 1);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertFalse(mGSMPhone.getRingingCall().isRinging());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE,
                mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertTrue(mGSMPhone.getForegroundCall().getEarliestConnectTime() > 0);

        cn = mGSMPhone.getForegroundCall().getConnections().get(0);
        assertTrue(cn.isIncoming());
        assertEquals(Connection.PostDialState.NOT_STARTED, cn.getPostDialState());

        assertFalse(mGSMPhone.canConference());

        // One disconnected (local hangup) call

        try {
            Connection conn;
            conn = mGSMPhone.getForegroundCall().getConnections().get(0);
            conn.hangup();
        } catch (CallStateException ex) {
            ex.printStackTrace();
            fail("unexpected ex");
        }

        msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        assertNotNull("Message Time Out", msg);

        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());
        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertFalse(mGSMPhone.getRingingCall().isRinging());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DISCONNECTED,
                mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertTrue(mGSMPhone.getForegroundCall().getEarliestConnectTime() > 0);

        cn = mGSMPhone.getForegroundCall().getEarliestConnection();

        assertEquals(Call.State.DISCONNECTED, cn.getState());

        assertEquals(Connection.DisconnectCause.LOCAL, cn.getDisconnectCause());

        assertFalse(mGSMPhone.canConference());

        // Back to idle state

        mGSMPhone.clearDisconnected();

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertFalse(mGSMPhone.getRingingCall().isRinging());

        assertEquals(Connection.DisconnectCause.LOCAL, cn.getDisconnectCause());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestCreateTime());
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        assertFalse(mGSMPhone.canConference());

        // cn left over from before phone.clearDisconnected();

        assertEquals(Call.State.DISCONNECTED, cn.getState());

        // One ringing call

        mRadioControl.triggerRing("18005551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getConnections().isEmpty());

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertTrue(mGSMPhone.getRingingCall().isRinging());

        assertEquals(1, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.INCOMING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getRingingCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getRingingCall().getEarliestConnectTime());

        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestCreateTime());
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        assertFalse(mGSMPhone.canConference());

        // One rejected call
        mGSMPhone.rejectCall();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.IDLE);

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertFalse(mGSMPhone.getRingingCall().isRinging());

        assertEquals(1, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getRingingCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getRingingCall().getEarliestConnectTime());

        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestCreateTime());
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        cn = mGSMPhone.getRingingCall().getEarliestConnection();
        assertEquals(Call.State.DISCONNECTED, cn.getState());

        assertEquals(Connection.DisconnectCause.INCOMING_MISSED, cn.getDisconnectCause());

        assertFalse(mGSMPhone.canConference());

        // Back to idle state

        mGSMPhone.clearDisconnected();

        assertEquals(Connection.DisconnectCause.INCOMING_MISSED, cn.getDisconnectCause());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(0, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestCreateTime());
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        assertFalse(mGSMPhone.canConference());
        assertEquals(Call.State.DISCONNECTED, cn.getState());

        // One ringing call

        mRadioControl.triggerRing("18005551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getConnections().isEmpty());

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertTrue(mGSMPhone.getRingingCall().isRinging());

        cn = mGSMPhone.getRingingCall().getEarliestConnection();

        // Ringing call disconnects

        mRadioControl.triggerHangupForeground();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.IDLE);

        assertEquals(Connection.DisconnectCause.INCOMING_MISSED, cn.getDisconnectCause());

        // One Ringing Call

        mRadioControl.triggerRing("18005551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.RINGING);


        cn = mGSMPhone.getRingingCall().getEarliestConnection();

        // One answered call
        mGSMPhone.acceptCall();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.OFFHOOK);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // one holding call
        mGSMPhone.switchHoldingAndActive();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() == Call.State.IDLE);


        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

        // one active call
        mGSMPhone.switchHoldingAndActive();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        }
        while (mGSMPhone.getBackgroundCall().getState() == Call.State.HOLDING);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // One disconnected call in the foreground slot

        mRadioControl.triggerHangupAll();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.IDLE);

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Connection.DisconnectCause.NORMAL, cn.getDisconnectCause());

        // Test missed calls

        mRadioControl.triggerRing("18005551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.RINGING);

        mGSMPhone.rejectCall();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (msg.what != EVENT_DISCONNECT);

        ar = (AsyncResult) msg.obj;
        cn = (Connection) ar.result;

        assertEquals(Connection.DisconnectCause.INCOMING_MISSED, cn.getDisconnectCause());
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getRingingCall().getState());

        // Test incoming not missed calls

        mRadioControl.triggerRing("18005551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.RINGING);

        cn = mGSMPhone.getRingingCall().getEarliestConnection();

        mGSMPhone.acceptCall();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.OFFHOOK);

        assertEquals(Connection.DisconnectCause.NOT_DISCONNECTED, cn.getDisconnectCause());
        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());

        try {
            mGSMPhone.getForegroundCall().hangup();
        } catch (CallStateException ex) {
            ex.printStackTrace();
            fail("unexpected ex");
        }

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState()
                != Call.State.DISCONNECTED);

        assertEquals(Connection.DisconnectCause.LOCAL, cn.getDisconnectCause());

        //
        // Test held and hangup held calls
        //

        // One ALERTING call
        mGSMPhone.dial("+13125551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.OFFHOOK);

        assertTrue(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        mRadioControl.progressConnectingCallState();
        mRadioControl.progressConnectingCallState();

        // One ACTIVE call

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.ACTIVE);

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        // One ACTIVE call, one ringing call

        mRadioControl.triggerRing("18005551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.RINGING);

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertTrue(mGSMPhone.getRingingCall().isRinging());

        // One HOLDING call, one ACTIVE call
        mGSMPhone.acceptCall();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.OFFHOOK);

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());
        assertTrue(mGSMPhone.canConference());

        // Conference the two
        mGSMPhone.conference();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() != Call.State.IDLE);

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertTrue(mGSMPhone.getForegroundCall().isMultiparty());
        assertFalse(mGSMPhone.canConference());

        // Hold the multiparty call
        mGSMPhone.switchHoldingAndActive();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        }
        while (mGSMPhone.getBackgroundCall().getState() != Call.State.HOLDING);

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertTrue(mGSMPhone.getBackgroundCall().isMultiparty());
        assertFalse(mGSMPhone.canConference());

        // Multiparty call on hold, call waiting added

        mRadioControl.triggerRing("18005558355");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.RINGING);

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertTrue(mGSMPhone.getRingingCall().isRinging());

        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());
        assertTrue(mGSMPhone.getBackgroundCall().isMultiparty());
        assertEquals(Call.State.WAITING, mGSMPhone.getRingingCall().getState());
        assertFalse(mGSMPhone.canConference());

        // Hangup conference call, ringing call still around
        mGSMPhone.getBackgroundCall().hangup();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() != Call.State.DISCONNECTED);

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getBackgroundCall().getState());

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertTrue(mGSMPhone.getRingingCall().isRinging());

        // Reject waiting call
        mGSMPhone.rejectCall();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.IDLE);

        assertFalse(mGSMPhone.getForegroundCall().isDialingOrAlerting());
        assertFalse(mGSMPhone.getRingingCall().isRinging());
    }

    public void testOutgoingCallFailImmediately() throws Exception {
        Message msg;

        // Test outgoing call fail-immediately edge case
        // This happens when a call terminated before ever appearing in a
        // call list
        // This should land the immediately-failing call in the
        // ForegroundCall list as an IDLE call
        mRadioControl.setNextDialFailImmediately(true);

        Connection cn = mGSMPhone.dial("+13125551212");

        progressMoDisconnectCall(1);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.DISCONNECTED);

        // msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        // assertNotNull("Message Time Out", msg);
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        ///[JB Auto Test]Marked temporarily.
        //assertEquals(Connection.DisconnectCause.NORMAL, cn.getDisconnectCause());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());
    }

    public void testHangupOnOutgoing() throws Exception {
        Connection cn;
        Message msg;

        mRadioControl.setAutoProgressConnectingCall(false);

        // Test 1: local hangup in "DIALING" state
        mGSMPhone.dial("+13125551212");
        progressMoConnectingCallState(1, "+13125551212", 0x81, false);

        do {
            mRadioControl.progressConnectingCallState();
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.DIALING);

        cn = mGSMPhone.getForegroundCall().getEarliestConnection();

        mGSMPhone.getForegroundCall().hangup();
        progressMoDisconnectCall(1);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.DISCONNECTED);

        // msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        // assertNotNull("Message Time Out", msg);
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Connection.DisconnectCause.LOCAL, cn.getDisconnectCause());

        // Test 2: local hangup in "ALERTING" state
        mGSMPhone.dial("+13125551212");
        progressMoConnectingCallState(1, "+13125551212", 0x81, false);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.OFFHOOK);

        mRadioControl.progressConnectingCallState();
        progressMoConnectingCallState(1, "+13125551212", 0x81, false);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.ALERTING);

        cn = mGSMPhone.getForegroundCall().getEarliestConnection();

        mGSMPhone.getForegroundCall().hangup();
        progressMoDisconnectCall(1);

        msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        assertNotNull("Message Time Out", msg);

        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Connection.DisconnectCause.LOCAL, cn.getDisconnectCause());

        // Test 3: local immediate hangup before GSM index is
        // assigned (CallTracker.hangupPendingMO case)

        mRadioControl.pauseResponses();

        cn = mGSMPhone.dial("+13125551212");
        progressMoConnectingCallState(1, "+13125551212", 0x81, false);

        cn.hangup();
        progressMoDisconnectCall(1);

        mRadioControl.resumeResponses();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.DISCONNECTED);
        // msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        // assertNotNull("Message Time Out", msg);
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());

        assertEquals(Connection.DisconnectCause.LOCAL,
                mGSMPhone.getForegroundCall().getEarliestConnection().getDisconnectCause());
    }

    public void testHangupOnChannelClose() throws Exception {
        mGSMPhone.dial("+13125551212");

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getConnections().isEmpty());

        mRadioControl.shutdown();

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
            mGSMPhone.clearDisconnected();
        } while (!mGSMPhone.getForegroundCall().getConnections().isEmpty());
    }

    public void testIncallMmiCallDeflection() throws Exception {
        Message msg;

        // establish an active call
        makeMOCall(1, true);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // establish a ringing (WAITING) call
        makeMTCall(2);

        //msg = mGSMTestHandler.waitForMessage(EVENT_RINGING);
        //assertNotNull("Message Time Out", msg);

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertTrue(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.WAITING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // Simulate entering 0 followed by SEND: release all held calls
        // or sets UDUB for a waiting call.
        mGSMPhone.handleInCallMmiCommands("0");
		progressMoDisconnectCall(2);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getState() == Call.State.WAITING);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertFalse(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // change the active call to holding call
        mGSMPhone.switchHoldingAndActive();
		progressHeldCallState(1, mDialString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() == Call.State.IDLE);


        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

        // Simulate entering 0 followed by SEND: release all held calls
        // or sets UDUB for a waiting call.
        mGSMPhone.handleInCallMmiCommands("0");
		progressMoDisconnectCall(1);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() == Call.State.HOLDING);

        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getBackgroundCall().getState());
    }

    public void testIncallMmiCallWaiting() throws Exception {
        Message msg;

        // establish an active call
        makeMOCall(1, true);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // establish a ringing (WAITING) call
        makeMTCall(2);

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertTrue(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.WAITING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // Simulate entering 1 followed by SEND: release all active calls
        // (if any exist) and accepts the other (held or waiting) call.

        mGSMPhone.handleInCallMmiCommands("1");
		progressMoDisconnectCall(1);
		progressMtAcceptCallState(2, mIncomingCallString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getState() == Call.State.WAITING);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertFalse(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(mIncomingCallString, mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());

        // change the active call to holding call
        mGSMPhone.switchHoldingAndActive();
		progressHeldCallState(2, mIncomingCallString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() == Call.State.IDLE);

        assertEquals(Call.State.IDLE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

        // Simulate entering 1 followed by SEND: release all active calls
        // (if any exist) and accepts the other (held or waiting) call.
        mGSMPhone.handleInCallMmiCommands("1");
		progressMtAcceptCallState(2, mIncomingCallString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() != Call.State.IDLE);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());
        assertEquals(mIncomingCallString, mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());

        // at this point, the active call with number==18005551212 should
        // have the gsm index of 2

        makeSecondMTCall(1);

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertTrue(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.WAITING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // Simulate entering "12" followed by SEND: release the call with
        // gsm index equals to 2.
        mGSMPhone.handleInCallMmiCommands("12");
		progressMoDisconnectCall(2);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() == Call.State.ACTIVE);

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertTrue(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.INCOMING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        mGSMPhone.acceptCall();
		progressMtAcceptCallState(1, mIncomingCallString2, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getState() != PhoneConstants.State.OFFHOOK);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertFalse(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // at this point, the call with number==16505550100 should
        // have the gsm index of 1

        mGSMPhone.switchHoldingAndActive();
		progressHeldCallState(1, mIncomingCallString2, 0x81);
		
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() == Call.State.IDLE);
        
        makeMOCall(2, true);

        do {
            mRadioControl.progressConnectingCallState();
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.ACTIVE ||
                mGSMPhone.getBackgroundCall().getState() != Call.State.HOLDING);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

        // at this point, the active call with number==13125551212 should
        // have the gsm index of 2

		//Smartphone can support releasing active or holding call, so ignore the following test case.
		/*
        // Simulate entering "11" followed by SEND: release the call with
        // gsm index equals to 1. This should not be allowed, and a
        // Supplementary Service notification must be received.
        mGSMPhone.handleInCallMmiCommands("11");
		progressMoDisconnectCall(1);

		//Smartphone can support releasing active or holding call.
        msg = mGSMTestHandler.waitForMessage(SUPP_SERVICE_FAILED);
        assertNotNull("Message Time Out", msg);
        assertFalse("IncallMmiCallWaiting: command should not work on holding call", msg == null);
		*/

        // Simulate entering "12" followed by SEND: release the call with
        // gsm index equals to 2.
        mGSMPhone.handleInCallMmiCommands("12");
		progressMoDisconnectCall(2);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() == Call.State.ACTIVE);

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

        // Simulate entering 1 followed by SEND: release all active calls
        // (if any exist) and accepts the other (held or waiting) call.
        mGSMPhone.handleInCallMmiCommands("1");
		progressMoActiveCallState(1, mIncomingCallString2, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() != Call.State.IDLE);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());
        assertEquals(mIncomingCallString2, mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());

        // Simulate entering "11" followed by SEND: release the call with
        // gsm index equals to 1.
        mGSMPhone.handleInCallMmiCommands("11");
		progressMoDisconnectCall(1);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() == Call.State.ACTIVE);

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());
    }

    public void testIncallMmiCallHold() throws Exception {
        Message msg;

        // establish an active call
		makeMOCall(1, true);

        assertEquals(Call.State.ACTIVE, ((Call)mGSMPhone.getForegroundCall()).getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // establish a ringing (WAITING) call
		makeMTCall(2);

        //msg = mGSMTestHandler.waitForMessage(EVENT_RINGING);
        //assertNotNull("Message Time Out", msg);
		
        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertTrue(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.WAITING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // simulate entering 2 followed by SEND: place all active calls
        // (if any exist) on hold and accepts the other (held or waiting)
        // call

        mGSMPhone.handleInCallMmiCommands("2");
		progressHeldCallState(1, mDialString, 0x81);
		progressMtAcceptCallState(2, mIncomingCallString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getState() == Call.State.WAITING);


        assertFalse(mGSMPhone.getRingingCall().isRinging());
        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(mIncomingCallString, mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());
        assertEquals(mDialString, mGSMPhone.getBackgroundCall().getConnections().get(0).getAddress());

        // swap the active and holding calls
        mGSMPhone.handleInCallMmiCommands("2");
		progressHeldCallState(2, mIncomingCallString, 0x81);
		progressMoActiveCallState(1, mDialString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(EVENT_PHONE_STATE_CHANGED));
        } while (!mDialString.equals(mGSMPhone.getForegroundCall().getConnections().get(0).getAddress()));

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(mDialString, mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());
        assertEquals(mIncomingCallString, mGSMPhone.getBackgroundCall().getConnections().get(0).getAddress());

        // merge the calls
        mGSMPhone.conference();
		progressMoActiveCallState(2, mIncomingCallString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() != Call.State.IDLE);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());
        assertEquals(2, mGSMPhone.getForegroundCall().getConnections().size());

        // at this point, we have an active conference call, with
        // call(1) = 13125551212 and call(2) = 18005551212

        // Simulate entering "23" followed by SEND: places all active call
        // on hold except call 3. This should fail and a supplementary service
        // failed notification should be received.

        mGSMPhone.handleInCallMmiCommands("23");

        msg = mGSMTestHandler.waitForMessage(SUPP_SERVICE_FAILED);
        assertNotNull("Message Time Out", msg);
        assertFalse("IncallMmiCallHold: separate should have failed!", msg == null);

        // Simulate entering "21" followed by SEND: places all active call
        // on hold except call 1.
        mGSMPhone.handleInCallMmiCommands("21");
        progressHeldCallState(2, mIncomingCallString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() == Call.State.IDLE);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(mDialString, mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());
        assertEquals(mIncomingCallString, mGSMPhone.getBackgroundCall().getConnections().get(0).getAddress());
    }

    public void testIncallMmiMultipartyServices() throws Exception {
        // establish an active call
        makeMOCall(1, true);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        // dial another call
        makeMTCall(2);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.WAITING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        mGSMPhone.handleInCallMmiCommands("2");
		progressHeldCallState(1, mDialString, 0x81);
		progressMtAcceptCallState(2, mIncomingCallString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getState() == Call.State.WAITING);

        mGSMPhone.handleInCallMmiCommands("3");
		progressMoActiveCallState(1, mDialString, 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() != Call.State.IDLE);

        assertEquals(PhoneConstants.State.OFFHOOK, mGSMPhone.getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(mIncomingCallString, mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());
        assertEquals(mDialString, mGSMPhone.getForegroundCall().getConnections().get(1).getAddress());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());
    }

    private ArrayList<DriverCall> getCurrentCalls() {
        Message msg;
        CommandsInterface ci = (CommandsInterface) mRadioControl;
        ci.getCurrentCalls(mGSMTestHandler.getHandler().obtainMessage(EVENT_GET_CURRENT_CALLS));
        msg = mGSMTestHandler.waitForMessage(EVENT_GET_CURRENT_CALLS);
        assertNotNull("Message Time Out", msg);
        AsyncResult ar = (AsyncResult) msg.obj;
        ArrayList<DriverCall> calls = (ArrayList<DriverCall>) ar.result;
        return calls;
    }

    
    private void progressMoConnectingCallState(int index, String number, int TOA,
            boolean remoteAutoPickup) {
        mRadioControl.progressConnectingCallState(index, CSMCC_MO_CALL_ID_ASSIGN_MSG, false,
                number, TOA);
        mRadioControl.progressConnectingCallState(index, CSMCC_CALL_PROCESS_MSG, false,
                number, TOA);
        mRadioControl.progressConnectingCallState(index, CSMCC_SYNC_MSG, false,
                number, TOA);
        mRadioControl.progressConnectingCallState(index, CSMCC_ALERT_MSG, false,
                number, TOA);

        if (remoteAutoPickup) {
            progressMoActiveCallState(index, number, TOA);
            progressMoConnectedCallState(index, number, TOA);
        }
    }

    private void progressMoActiveCallState(int index, String number, int TOA) {
        mRadioControl.progressConnectingCallState(index, CSMCC_STATE_CHANGE_ACTIVE, false,
                number, TOA);
    }

    private void progressMoConnectedCallState(int index, String number, int TOA) {
        mRadioControl.progressConnectingCallState(index, CSMCC_CALL_CONNECTED_MSG, false,
                number, TOA);
    }

    private void progressHeldCallState(int index, String number, int TOA) {
        mRadioControl.progressConnectingCallState(index, CSMCC_STATE_CHANGE_HELD, false,
                number, TOA);
    }

    private void progressMoDisconnectCall(int index) {
        mRadioControl.progressConnectingCallState(index, CSMCC_STATE_CHANGE_MO_DISCONNECTING,
                false,
                "", 0);
        mRadioControl.progressConnectingCallState(index, CSMCC_DISCONNECT_MSG, false,
                "", 0);
        mRadioControl.progressConnectingCallState(index, CSMCC_ALL_CALLS_DISC_MSG, false,
                "", 0);
        mRadioControl.progressConnectingCallState(index, CSMCC_STATE_CHANGE_DISCONNECTED, false,
                "", 0);
    }

    private void progressMtConnectingCallState(int index, String number, int TOA) {
        mRadioControl.progressConnectingCallState(index, CSMCC_SETUP_MSG, true,
                number, TOA);
        mRadioControl.progressConnectingCallState(index, CSMCC_SYNC_MSG, true,
                number, TOA);
    }

    private void progressMtAcceptCallState(int index, String number, int TOA) {
        mRadioControl.progressConnectingCallState(index, CSMCC_STATE_CHANGE_ACTIVE, true,
                number, TOA);
        mRadioControl.progressConnectingCallState(index, CSMCC_CALL_CONNECTED_MSG, true,
                number, TOA);
    }

	private void makeMOCall(int index, boolean autoAnswer) throws Exception {
		mGSMPhone.dial(mDialString);
		progressMoConnectingCallState(index, mDialString, 0x81, autoAnswer);

        if (autoAnswer) {
	       do {
		         mRadioControl.progressConnectingCallState();
		         assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
   	       } while (mGSMPhone.getForegroundCall().getState() != Call.State.ACTIVE);
        }
	}

	private void makeMTCall(int index) throws Exception {
		mRadioControl.triggerRing(mIncomingCallString);
		progressMtConnectingCallState(index, mIncomingCallString, 0x81);

	    do {
		      progressMtConnectingCallState(index, mIncomingCallString, 0x81);
		      assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
	    } while (PhoneConstants.State.RINGING != mGSMPhone.getState());
	}

	private void makeSecondMTCall(int index) throws Exception {
		mRadioControl.triggerRing(mIncomingCallString2);
		progressMtConnectingCallState(index, mIncomingCallString2, 0x81);

	    do {
		      progressMtConnectingCallState(index, mIncomingCallString2, 0x81);
		      assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
	    } while (PhoneConstants.State.RINGING != mGSMPhone.getState());
	}

    public void testCallIndex() throws Exception {
        Message msg;

        // establish the first call
        mGSMPhone.dial("16505550100");
        progressMoConnectingCallState(1, "16505550100", 0x81, true);
				
        mRadioControl.progressConnectingCallState();
        mRadioControl.progressConnectingCallState(1, CSMCC_CALL_CONNECTED_MSG, false,
                "16505550100", 0x81);
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.ACTIVE);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        progressHeldCallState(1, "16505550100", 0x81);

        String baseNumber = "1650555010";

        for (int i = 1; i < 6; i++) {
            String number = baseNumber + i;

            mGSMPhone.dial(number);
            progressMoConnectingCallState(i + 1, number, 0x81, true);
						
						mRadioControl.progressConnectingCallState();
            progressMoActiveCallState(i + 1, number, 0x81);
            // Hold last actived call
            progressHeldCallState(i, baseNumber + (i - 1), 0x81);
            progressMoConnectedCallState(i + 1, number, 0x81);
            do {
                assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
            } while (mGSMPhone.getForegroundCall().getState() != Call.State.ACTIVE);

            assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
            assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

            if (mGSMPhone.getBackgroundCall().getConnections().size() >= 5) {
                break;
            }

            mGSMPhone.conference();
						
            for (int j = 0; j <= i; j++) {
                progressMoActiveCallState(j + 1, baseNumber + j, 0x81);
            }
            do {
                assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
            } while (mGSMPhone.getBackgroundCall().getState() != Call.State.IDLE);

            assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
            assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());
        }

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals("16505550105",
                mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

        // create an incoming call, this call should have the call index
        // of 7
        mRadioControl.triggerRing("18005551212");

        // msg = mGSMTestHandler.waitForMessage(EVENT_RINGING);
        // assertNotNull("Message Time Out", msg);
        progressMtConnectingCallState(7, "18005551212", 0x81);
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (PhoneConstants.State.RINGING != mGSMPhone.getState());

        assertEquals(PhoneConstants.State.RINGING, mGSMPhone.getState());
        assertTrue(mGSMPhone.getRingingCall().isRinging());
        assertEquals(Call.State.WAITING, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());

        // hangup the background call and accept the ringing call
        mGSMPhone.getBackgroundCall().hangup();

        // Hangup 1-5
        for (int i = 1; i < 6; i++) {
            progressMoDisconnectCall(i);
        }

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getBackgroundCall().getState() != Call.State.DISCONNECTED);

        mGSMPhone.acceptCall();
        progressHeldCallState(6, "16505550105", 0x81);
        progressMtAcceptCallState(7, "18005551212", 0x81);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getRingingCall().getState() != Call.State.IDLE);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals("18005551212",
                mGSMPhone.getForegroundCall().getConnections().get(0).getAddress());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());
        assertEquals("16505550105",
                mGSMPhone.getBackgroundCall().getConnections().get(0).getAddress());

        mGSMPhone.handleInCallMmiCommands("17");

        ArrayList<DriverCall> calls = getCurrentCalls();
        if (calls != null) {
            // index 7 should not be existed.
            for (DriverCall call : calls) {
                assertFalse(7 == call.index);
            }
        }
        progressMoDisconnectCall(7);
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() == Call.State.ACTIVE);

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.HOLDING, mGSMPhone.getBackgroundCall().getState());
        assertEquals("16505550105",
                mGSMPhone.getBackgroundCall().getConnections().get(0).
                        getAddress());

        mGSMPhone.handleInCallMmiCommands("1");
        calls = getCurrentCalls();
        if (calls != null) {
            // index 6 should be forground call..
            for (DriverCall call : calls) {
                if (6 == call.index) {
                    assertEquals(call.state, DriverCall.State.ACTIVE);
                }
            }
        }
        progressMoActiveCallState(6, "16505550105", 0x81);
        progressMoConnectedCallState(6, "16505550105", 0x81);
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.ACTIVE);

        assertEquals(Call.State.ACTIVE, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        mGSMPhone.handleInCallMmiCommands("16");
        calls = getCurrentCalls();
        if (calls != null) {
            // index 6 should not be existed.
            for (DriverCall call : calls) {
                assertFalse(6 == call.index);
            }
        }
        progressMoDisconnectCall(6);
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() == Call.State.ACTIVE);

        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());
    }

    public void testPostDialSequences() throws Exception {
        Message msg;
        AsyncResult ar;
        Connection cn;

        mGSMPhone.dial("+13125551212,1234;5N8xx");

        progressMoConnectingCallState(1, "16505550100", 0x81, true);

        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        ar = (AsyncResult) (msg.obj);
        cn = (Connection) (ar.result);
        assertEquals(',', msg.arg1);
        assertEquals("1234;5N8", cn.getRemainingPostDialString());


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('1', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('2', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('3', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('4', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals(';', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        cn = (Connection) (ar.result);
        assertEquals(Connection.PostDialState.WAIT, cn.getPostDialState());
        assertEquals(Connection.PostDialState.WAIT, ar.userObj);
        cn.proceedAfterWaitChar();


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('5', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertEquals('N', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        cn = (Connection) (ar.result);
        assertEquals(Connection.PostDialState.WILD, cn.getPostDialState());
        assertEquals(Connection.PostDialState.WILD, ar.userObj);
        cn.proceedAfterWildChar(",6;7");


        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        ar = (AsyncResult) (msg.obj);
        cn = (Connection) (ar.result);
        assertEquals(',', msg.arg1);
        assertEquals("6;78", cn.getRemainingPostDialString());

        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('6', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);

        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals(';', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        cn = (Connection) (ar.result);
        assertEquals(Connection.PostDialState.WAIT, cn.getPostDialState());
        assertEquals(Connection.PostDialState.WAIT, ar.userObj);
        cn.proceedAfterWaitChar();

        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('7', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);

        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals('8', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        assertEquals(Connection.PostDialState.STARTED, ar.userObj);

        // Bogus chars at end should be ignored
        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals(0, msg.arg1);
        ar = (AsyncResult) (msg.obj);
        cn = (Connection) (ar.result);
        assertEquals(Connection.PostDialState.COMPLETE,
                cn.getPostDialState());
        assertEquals(Connection.PostDialState.COMPLETE, ar.userObj);
    }

    public void testPostDialCancel() throws Exception {
        Message msg;
        AsyncResult ar;
        Connection cn;

        mGSMPhone.dial("+13125551212,N");

        progressMoConnectingCallState(1, "+13125551212", 0x81, true);

        mRadioControl.progressConnectingToActive();

        mRadioControl.progressConnectingToActive();

        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertNotNull("Message Time Out", msg);
        assertEquals(',', msg.arg1);

        msg = mGSMTestHandler.waitForMessage(EVENT_POST_DIAL);
        assertEquals('N', msg.arg1);
        ar = (AsyncResult) (msg.obj);
        cn = (Connection) (ar.result);
        assertEquals(Connection.PostDialState.WILD, cn.getPostDialState());
        cn.cancelPostDial();

        assertEquals(Connection.PostDialState.CANCELLED, cn.getPostDialState());
    }

    public void testOutgoingCallFail() throws Exception {
        Message msg;
        /*
        * normal clearing
        */

        mRadioControl.setNextCallFailCause(CallFailCause.NORMAL_CLEARING);
        mRadioControl.setAutoProgressConnectingCall(false);

        Connection cn = mGSMPhone.dial("+13125551212");
        progressMoConnectingCallState(1, "+13125551212", 0x81, false);

        mRadioControl.progressConnectingCallState();

        // I'm just progressing the call state to
        // ensure getCurrentCalls() gets processed...
        // Normally these failure conditions would happen in DIALING
        // not ALERTING
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (cn.getState() == Call.State.DIALING);


        mRadioControl.triggerHangupAll();

        progressMoDisconnectCall(1);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.DISCONNECTED);

        // msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        // assertNotNull("Message Time Out", msg);
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        ///[JB Auto Test]Marked temporarily.
        //assertEquals(Connection.DisconnectCause.NORMAL, cn.getDisconnectCause());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        /*
        * busy
        */

        mRadioControl.setNextCallFailCause(CallFailCause.USER_BUSY);
        mRadioControl.setAutoProgressConnectingCall(false);

        cn = mGSMPhone.dial("+13125551212");

        mRadioControl.progressConnectingCallState();
        progressMoConnectingCallState(1, "+13125551212", 0x81, false);

        // I'm just progressing the call state to
        // ensure getCurrentCalls() gets processed...
        // Normally these failure conditions would happen in DIALING
        // not ALERTING
        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (cn.getState() == Call.State.DIALING);


        mRadioControl.triggerHangupAll();
        progressMoDisconnectCall(1);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.DISCONNECTED);

        // msg = mGSMTestHandler.waitForMessage(EVENT_DISCONNECT);
        // assertNotNull("Message Time Out", msg);
        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        assertEquals(Connection.DisconnectCause.BUSY, cn.getDisconnectCause());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DISCONNECTED,
                mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());

        /*
        * congestion
        */

        mRadioControl.setNextCallFailCause(CallFailCause.NO_CIRCUIT_AVAIL);
        mRadioControl.setAutoProgressConnectingCall(false);

        cn = mGSMPhone.dial("+13125551212");

        mRadioControl.progressConnectingCallState();
        progressMoConnectingCallState(1, "+13125551212", 0x81, false);

        // I'm just progressing the call state to
        // ensure getCurrentCalls() gets processed...
        // Normally these failure conditions would happen in DIALING
        // not ALERTING
        do {
            msg = mGSMTestHandler.waitForMessage(ANY_MESSAGE);
            assertNotNull("Message Time Out", msg);
        } while (cn.getState() == Call.State.DIALING);


        mRadioControl.triggerHangupAll();
        progressMoDisconnectCall(1);

        do {
            assertNotNull("Message Time Out", mGSMTestHandler.waitForMessage(ANY_MESSAGE));
        } while (mGSMPhone.getForegroundCall().getState() != Call.State.DISCONNECTED);

        // Unlike the while loops above, this one waits
        // for a "phone state changed" message back to "idle"
        do {
            msg = mGSMTestHandler.waitForMessage(ANY_MESSAGE);
            assertNotNull("Message Time Out", msg);
        } while (!(msg.what == EVENT_PHONE_STATE_CHANGED
                && mGSMPhone.getState() == PhoneConstants.State.IDLE));

        assertEquals(PhoneConstants.State.IDLE, mGSMPhone.getState());

        // assertEquals(Connection.DisconnectCause.CONGESTION,
        // cn.getDisconnectCause());

        assertEquals(0, mGSMPhone.getRingingCall().getConnections().size());
        assertEquals(1, mGSMPhone.getForegroundCall().getConnections().size());
        assertEquals(0, mGSMPhone.getBackgroundCall().getConnections().size());

        assertEquals(Call.State.IDLE, mGSMPhone.getRingingCall().getState());
        assertEquals(Call.State.DISCONNECTED, mGSMPhone.getForegroundCall().getState());
        assertEquals(Call.State.IDLE, mGSMPhone.getBackgroundCall().getState());

        assertTrue(mGSMPhone.getForegroundCall().getEarliestCreateTime() > 0);
        assertEquals(0, mGSMPhone.getForegroundCall().getEarliestConnectTime());
    }

    public void testSSNotification() throws Exception {
        // MO
        runTest(0, SuppServiceNotification.MO_CODE_UNCONDITIONAL_CF_ACTIVE);
        runTest(0, SuppServiceNotification.MO_CODE_CALL_IS_WAITING);
        runTest(0, SuppServiceNotification.MO_CODE_CALL_DEFLECTED);

        // MT
        runTest(1, SuppServiceNotification.MT_CODE_FORWARDED_CALL);
        runTest(1, SuppServiceNotification.MT_CODE_CALL_CONNECTED_ECT);
        runTest(1, SuppServiceNotification.MT_CODE_ADDITIONAL_CALL_FORWARDED);
    }

    private void runTest(int type, int code) {
        Message msg;

        mRadioControl.triggerSsn(type, code);

        msg = mGSMTestHandler.waitForMessage(EVENT_SSN);
        assertNotNull("Message Time Out", msg);
        AsyncResult ar = (AsyncResult) msg.obj;

        assertNull(ar.exception);

        SuppServiceNotification notification =
                (SuppServiceNotification) ar.result;

        assertEquals(type, notification.notificationType);
        assertEquals(code, notification.code);
    }

    public void testUssd() throws Exception {
        // Quick hack to work around a race condition in this test:
        // We may initiate a USSD MMI before GSMPhone receives its initial
        // GSMTestHandler.EVENT_RADIO_OFF_OR_NOT_AVAILABLE event.  When the phone sees this
        // event, it will cancel the just issued USSD MMI, which we don't
        // want.  So sleep a little first.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            // do nothing
        }

        verifyNormal();
        verifyCancel();
        varifyNetworkInitiated();
    }

    private void varifyNetworkInitiated() {
        Message msg;
        AsyncResult ar;
        MmiCode mmi;

        // Receive an incoming NOTIFY
        mRadioControl.triggerIncomingUssd("0", "NOTIFY message");
        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);
        ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;

        assertFalse(mmi.isUssdRequest());

        // Receive a REQUEST and send response
        mRadioControl.triggerIncomingUssd("1", "REQUEST Message");
        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);
        ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;

        assertTrue(mmi.isUssdRequest());

        mGSMPhone.sendUssdResponse("## TEST: TEST_GSMPhone responding...");
        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_INITIATE);
        assertNotNull("Message Time Out", msg);
        ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;

        GsmMmiCode gsmMmi = (GsmMmiCode) mmi;
        assertTrue(gsmMmi.isPendingUSSD());
        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);
        ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;

        assertNull(ar.exception);
        assertFalse(mmi.isUssdRequest());

        // Receive a REQUEST and cancel
        mRadioControl.triggerIncomingUssd("1", "REQUEST Message");
        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);
        ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;

        assertTrue(mmi.isUssdRequest());

        mmi.cancel();
        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);

        ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;

        assertNull(ar.exception);
        assertEquals(MmiCode.State.CANCELLED, mmi.getState());

        List mmiList = mGSMPhone.getPendingMmiCodes();
        assertEquals(0, mmiList.size());
    }

    private void verifyNormal() throws CallStateException {
        Message msg;
        AsyncResult ar;
        MmiCode mmi;

        mGSMPhone.dial("#646#");

        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_INITIATE);
        assertNotNull("Message Time Out", msg);

        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);

        ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;
        assertEquals(MmiCode.State.COMPLETE, mmi.getState());
    }


    private void verifyCancel() throws CallStateException {
        /**
         * This case makes an assumption that dial() will add the USSD
         * to the "pending MMI codes" list before it returns.  This seems
         * like reasonable semantics. It also assumes that the USSD
         * request in question won't complete until we get back to the
         * event loop, thus cancel() is safe.
         */
        Message msg;

        mGSMPhone.dial("#646#");

        List<? extends MmiCode> pendingMmis = mGSMPhone.getPendingMmiCodes();

        assertEquals(1, pendingMmis.size());

        MmiCode mmi = pendingMmis.get(0);
        assertTrue(mmi.isCancelable());
        mmi.cancel();

        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_INITIATE);
        assertNotNull("Message Time Out", msg);

        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);

        AsyncResult ar = (AsyncResult) msg.obj;
        mmi = (MmiCode) ar.result;

        assertEquals(MmiCode.State.CANCELLED, mmi.getState());
    }

    public void testRilHooks() throws Exception {
        //
        // These test cases all assume the RIL OEM hooks
        // just echo back their input
        //

        Message msg;
        AsyncResult ar;

        // null byte array

        mGSMPhone.invokeOemRilRequestRaw(null, mHandler.obtainMessage(EVENT_OEM_RIL_MESSAGE));

        msg = mGSMTestHandler.waitForMessage(EVENT_OEM_RIL_MESSAGE);
        assertNotNull("Message Time Out", msg);

        ar = ((AsyncResult) msg.obj);

        assertNull(ar.result);
        assertNull(ar.exception);

        // empty byte array

        mGSMPhone.invokeOemRilRequestRaw(new byte[0], mHandler.obtainMessage(EVENT_OEM_RIL_MESSAGE));

        msg = mGSMTestHandler.waitForMessage(EVENT_OEM_RIL_MESSAGE);
        assertNotNull("Message Time Out", msg);

        ar = ((AsyncResult) msg.obj);

        assertEquals(0, ((byte[]) (ar.result)).length);
        assertNull(ar.exception);

        // byte array with data

        mGSMPhone.invokeOemRilRequestRaw("Hello".getBytes("utf-8"),
                mHandler.obtainMessage(EVENT_OEM_RIL_MESSAGE));

        msg = mGSMTestHandler.waitForMessage(EVENT_OEM_RIL_MESSAGE);
        assertNotNull("Message Time Out", msg);

        ar = ((AsyncResult) msg.obj);

        assertEquals("Hello", new String(((byte[]) (ar.result)), "utf-8"));
        assertNull(ar.exception);

        // null strings

        mGSMPhone.invokeOemRilRequestStrings(null, mHandler.obtainMessage(EVENT_OEM_RIL_MESSAGE));

        msg = mGSMTestHandler.waitForMessage(EVENT_OEM_RIL_MESSAGE);
        assertNotNull("Message Time Out", msg);

        ar = ((AsyncResult) msg.obj);

        assertNull(ar.result);
        assertNull(ar.exception);

        // empty byte array

        mGSMPhone.invokeOemRilRequestStrings(new String[0],
                mHandler.obtainMessage(EVENT_OEM_RIL_MESSAGE));

        msg = mGSMTestHandler.waitForMessage(EVENT_OEM_RIL_MESSAGE);
        assertNotNull("Message Time Out", msg);

        ar = ((AsyncResult) msg.obj);

        assertEquals(0, ((String[]) (ar.result)).length);
        assertNull(ar.exception);

        // Strings with data

        String s[] = new String[1];

        s[0] = "Hello";

        mGSMPhone.invokeOemRilRequestStrings(s, mHandler.obtainMessage(EVENT_OEM_RIL_MESSAGE));

        msg = mGSMTestHandler.waitForMessage(EVENT_OEM_RIL_MESSAGE);
        assertNotNull("Message Time Out", msg);

        ar = ((AsyncResult) msg.obj);

        assertEquals("Hello", ((String[]) (ar.result))[0]);
        assertEquals(1, ((String[]) (ar.result)).length);
        assertNull(ar.exception);
    }

    public void testMmi() throws Exception {
        mRadioControl.setAutoProgressConnectingCall(false);

        // "valid" MMI sequences
        runValidMmi("*#67#", false);
        runValidMmi("##43*11#", false);
        runValidMmi("#33*1234*11#", false);
        runValidMmi("*21*6505551234**5#", false);
        runValidMmi("**03**1234*4321*4321#", false);

		// pound string
        runValidMmi("5308234092307540923#", true);

		// short code, the number will not be regarded as MMI string, so ignore this tese. 
        //runValidMmi("22", true);

		// as part of call setup
        runValidMmiWithConnect("*31#6505551234");

        // invalid MMI sequences
        runNotMmi("6505551234");
        runNotMmi("1234#*12#34566654");
        runNotMmi("*#*#12#*");
    }

    private void runValidMmi(String dialString, boolean cancelable) throws CallStateException {
        Connection c = mGSMPhone.dial(dialString);
        assertNull(c);
        Message msg = mGSMTestHandler.waitForMessage(EVENT_MMI_INITIATE);
        assertNotNull("Message Time Out", msg);
        // Should not be cancelable.
        AsyncResult ar = (AsyncResult) msg.obj;
        MmiCode mmi = (MmiCode) ar.result;
        assertEquals(cancelable, mmi.isCancelable());

        msg = mGSMTestHandler.waitForMessage(EVENT_MMI_COMPLETE);
        assertNotNull("Message Time Out", msg);
    }

    private void runValidMmiWithConnect(String dialString) throws CallStateException {
        mRadioControl.pauseResponses();

        Connection c = mGSMPhone.dial(dialString);
        assertNotNull(c);
        progressMoConnectingCallState(1, dialString, 0x81, false);

        hangup(c);
    }

    private void hangup(Connection cn) throws CallStateException {
        cn.hangup();
		progressMoDisconnectCall(1);

        mRadioControl.resumeResponses();
        assertNotNull(mGSMTestHandler.waitForMessage(EVENT_DISCONNECT));

    }

    private void runNotMmi(String dialString) throws CallStateException {
        mRadioControl.pauseResponses();

        Connection c = mGSMPhone.dial(dialString);
        assertNotNull(c);
		progressMoConnectingCallState(1, dialString, 0x81, false);

        hangup(c);
    }

    public class MockSharedPreferences implements SharedPreferences {
        public Map<String, ?> getAll() {
            return null;
        }

        public String getString(String key, String defValue) {
            return null;
        }

        public Set<String> getStringSet(String key, Set<String> defValues) {
            return null;
        }

        public int getInt(String key, int defValue) {
            return 0;
        }

        public long getLong(String key, long defValue) {
            return 0;
        }

        public float getFloat(String key, float defValue) {
            return 0;
        }

        public boolean getBoolean(String key, boolean defValue) {
            return false;
        }

        public boolean contains(String key) {
            return false;
        }

        public Editor edit() {
            return null;
        }

        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {

        }

    }

    // Partial implementation of MockResources.
    public class MockResources extends android.test.mock.MockResources
    {
        @Override
        public void getValue(int id, TypedValue outValue, boolean resolveRefs) {
            if (id == com.android.internal.R.bool.config_voice_capable) {
                outValue.type = TypedValue.TYPE_INT_BOOLEAN;
                outValue.data = 0;
            }
        }

        @Override
        public XmlResourceParser getXml(int id) throws NotFoundException {
            return null;
        }

        @Override
        public String[] getStringArray(int id) throws NotFoundException {
            return new String[0];
        }

        @Override
        public CharSequence getText(int id) throws NotFoundException {
            return "";
        }
    }

  /****** By Jingle: PowerManager is a final class
    // Partial implementation of PowerManager.
    public class MockPowerManager extends PowerManager {
        public MockPowerManager(IPowerManager service, Handler handler) {
            super(service, handler);
        }

        @Override
        public WakeLock newWakeLock(int flags, String tag)
        {
            Log.i(TAG, "MockPowerManager.newWakeLock tag=" + tag);
            return super.newWakeLock(flags, tag);
        }
    }
  *****/
  
    // Partial implementation of MockContentProvider.
    public class MockContentProvider extends android.test.mock.MockContentProvider {
        public MockContentProvider(Context context) {
            super(context);
        }

        @Override
        public Bundle call(String method, String request, Bundle args) {
            Bundle ret = new Bundle();

            if (request.equals(Settings.Global.SMS_OUTGOING_CHECK_MAX_COUNT)) {
                ret.putCharSequence(Settings.Global.SMS_OUTGOING_CHECK_MAX_COUNT, "100");
            }
            return ret;
        }

        @Override
        public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }
    }

    public class MockPackageManager extends android.test.mock.MockPackageManager {

        @Override
        public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
            List<ResolveInfo> list = new ArrayList<ResolveInfo>(0);
            return list;
        }
    }

    // Partial implementation of MockContext.
    public class MockContext extends android.test.mock.MockContext {
        private MockContentResolver mResolver;
        private Resources mResources;
        private SharedPreferences mSp;
        private ContentProvider mProvider;
        private MockPackageManager mPm;

        public MockContext() {
            mResolver = new MockContentResolver();
            mResources = new MockResources();
            mSp = new MockSharedPreferences();
            mProvider = new MockContentProvider(this);
            mPm = new MockPackageManager();
            mResolver.addProvider("settings", mProvider);
            mResolver.addProvider("telephony", new MockContentProvider(this));
        }

        @Override
        public ContentResolver getContentResolver() {
            return mResolver;
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        @Override
        public Object getSystemService(String name) {
            return mContext.getSystemService(name);
        }

        @Override
        public String getPackageName() {
            return "com.android.internal.telephony.gsm";
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return mSp;
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            return null;
        }

        @Override
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
                String broadcastPermission, Handler scheduler) {
            return null;
        }

        @Override
        public boolean bindService(Intent service, ServiceConnection conn, int flags) {
            return false;
        }

        @Override
        public void sendBroadcast(Intent intent) {
        }

        @Override
        public void sendBroadcast(Intent intent, String receiverPermission) {
        }

        @Override
        public void sendOrderedBroadcast(Intent intent,
                String receiverPermission) {
        }

        @Override
        public void sendOrderedBroadcast(Intent intent, String receiverPermission,
                BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
                String initialData,
                Bundle initialExtras) {
        }

        @Override
        public void sendStickyBroadcast(Intent intent) {
        }

        @Override
        public PackageManager getPackageManager() {
            return mPm;
        }
    }
}
