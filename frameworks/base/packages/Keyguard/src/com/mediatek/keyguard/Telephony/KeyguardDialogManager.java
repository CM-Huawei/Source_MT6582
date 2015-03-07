
package com.android.keyguard;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import android.util.Log;
import com.mediatek.common.telephony.ITelephonyEx;
import com.mediatek.keyguard.ext.ICardInfoExt;
import com.mediatek.keyguard.ext.IOperatorSIMString;
import com.mediatek.keyguard.ext.IOperatorSIMString.SIMChangedTag;
import com.mediatek.keyguard.ext.KeyguardPluginFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class KeyguardDialogManager {

    private static final String TAG = "KeyguardDialogManager";
    private static final boolean DEBUG = true;
    private static final boolean DEBUG_SIM_STATES = DEBUG || false;
    private static final int FAILED_BIOMETRIC_UNLOCK_ATTEMPTS_BEFORE_BACKUP = 3;
    /// M: Change the threshold to 16 for mediatek device
    private static final int LOW_BATTERY_THRESHOLD = 16;

    private static KeyguardDialogManager sInstance;

    private final Context mContext;


    private AlertDialog mSimCardChangedDialog = null;
    private View mPromptView = null;
    private SIMStatus mSimChangedStatus;
    private static String SIM_DETECT_NEW = "NEW";
    private static String SIM_DETECT_REMOVE = "REMOVE";
    private static String SIM_DETECT_SWAP = "SWAP";
    
    /// M: For Gemini enhancement feature to update sim card when new sim is detected
    private static final int MSG_SIM_DETECTED = 1002;
    
    /// M: For Gemini enhancement feature, when locale changed due to new inserted sim card, 
    /// update all related text in this message handler
    private static final int MSG_CONFIGURATION_CHANGED = 1005;
    
    /// update SimInfo names, so we need to handle it in this message hander 
    private static final int MSG_KEYGUARD_SIM_NAME_UPDATE = 1006;

    /// M: Manage the dialog sequence.
    private DialogSequenceManager mDialogSequenceManager;
    
    private KeyguardUpdateMonitor mUpdateMonitor;

    /// M: for get the proper SIM UIM string according to operator. 
    private IOperatorSIMString mIOperatorSIMString;

    /// M: for card info extend
    private ICardInfoExt mCardInfoExt;
    
    // M: Save current orientation, so that we will only recreate views when orientation changed
    private int mCreateOrientation;
    private int mCreateScreenWidthDp;
    private int mCreateScreenHeightDp;


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONFIGURATION_CHANGED:
                    updateResources();
                    break;
                case MSG_SIM_DETECTED:
                    handleSIMCardChanged();
                    break;
                case MSG_KEYGUARD_SIM_NAME_UPDATE:
                    handleSIMNameUpdate(msg.arg1);
                    break;
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "received broadcast " + action);

            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(
                        MSG_CONFIGURATION_CHANGED));
            } else if (TelephonyIntents.ACTION_SIM_DETECTED.equals(action)) {
                String simDetectStatus = intent.getStringExtra("simDetectStatus");
                int simCount = intent.getIntExtra("simCount", 0);
                int newSIMSlot = intent.getIntExtra("newSIMSlot", 0);
                KeyguardUtils.xlogD(TAG,"detectStatus=" + simDetectStatus + ", simCount=" + simCount
                        + ", newSimSlot=" + newSIMSlot);
                mSimChangedStatus = new SIMStatus(simDetectStatus, simCount, newSIMSlot);
                if (mSimCardChangedDialog != null) {
                       mSimCardChangedDialog.dismiss();
                }
                ///M: show dialog by sequence manager.
                KeyguardUtils.xlogD(TAG, this + "Receive ACTION_SIM_DETECTED--requestShowDialog(..)");
                requestShowDialog(new NewSimDialogCallback());
            } else if ("android.intent.action.normal.boot".equals(action)) {
                Log.i(TAG, "received normal boot");
                if (null != mSimChangedStatus) {
                    mDialogSequenceManager.handleShowDialog();
                }
            } else if (TelephonyIntents.ACTION_SIM_NAME_UPDATE.equals(action)) {
                int slotId = intent.getIntExtra("slotId", 0);
                KeyguardUtils.xlogD(TAG, "SIM_NAME_UPDATE, slotId="+slotId);
                mHandler.sendMessage(mHandler.obtainMessage(
                        MSG_KEYGUARD_SIM_NAME_UPDATE, slotId, 0));
            }
        }
    };

    private KeyguardDialogManager(Context context) {
        mContext = context;

        mDialogSequenceManager = new DialogSequenceManager();


        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();

        filter.addAction(TelephonyIntents.ACTION_SIM_DETECTED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction("android.intent.action.normal.boot");
        filter.addAction(TelephonyIntents.ACTION_SIM_NAME_UPDATE);

        context.registerReceiver(mBroadcastReceiver, filter);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);

        mUpdateMonitor.registerCallback(mUpdateCallback);

        /// M: Init the plugin for changing the String with SIM according to Operator.
        mIOperatorSIMString = KeyguardPluginFactory.getOperatorSIMString(context);

        /// M: For card info extend
        mCardInfoExt = KeyguardPluginFactory.getCardInfoExt(context);

        /// M: Save initial config when view created
        mCreateOrientation = mContext.getResources().getConfiguration().orientation;
        mCreateScreenWidthDp = mContext.getResources().getConfiguration().screenWidthDp;
        mCreateScreenHeightDp = mContext.getResources().getConfiguration().screenHeightDp;

    }

    public static KeyguardDialogManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardDialogManager(context);
        }
        return sInstance;
    }

    KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onPhoneStateChanged(int phoneState) {
            /// M: If phone state change, dismiss sim detecd dialog
            if (mSimCardChangedDialog != null) {
                mSimCardChangedDialog.dismiss();
            }
        }

        @Override
        public void onDeviceProvisioned() {
            mDialogSequenceManager.handleShowDialog();
            
        }
    };


    
    /**
     * M: Either a lock screen (an informational keyguard screen), or an unlock
     * screen (a means for unlocking the device) is shown at any given time.
     */
    private class SIMStatus {
        private int mSimCount = 0;
        private String mSimDetectStatus = SIM_DETECT_NEW;
        private int mNewSimSlot = 0;

        public SIMStatus(final String simDetectStatus, final int simCount, final int newSimSlot) {
            mSimDetectStatus = simDetectStatus;
            mSimCount = simCount;
            mNewSimSlot = newSimSlot;
        }

        public String getSimDetectStatus() {
            return mSimDetectStatus;
        }

        public int getSIMCount() {
            return mSimCount;
        }
        
        public int getNewSimSlot() {
            return mNewSimSlot;
        }
    }

    public boolean isPhoneAppReady() {
        final ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        
        boolean ready = false;
        List<RunningAppProcessInfo> runningAppInfo = am.getRunningAppProcesses();   
        if (runningAppInfo == null) {
            Log.i(TAG, "runningAppInfo == null");
            return ready;
        }        
        for (RunningAppProcessInfo app : runningAppInfo) {
            if (app.processName.equals("com.android.phone")) {
                ready = true;
                break;
            }
        }
        return ready;
    }

    private void initSimChangedPrompt() {
        int newSimSlot = mSimChangedStatus.getNewSimSlot();
        String simDetectStatus = mSimChangedStatus.getSimDetectStatus();
        
        String msg = null;
        int newSimNumber = getSimNumber(newSimSlot);

        final int mNumOfTextView = 4;
        TextView mTextViewName[] = new TextView[mNumOfTextView];
        mTextViewName[0] = (TextView)mPromptView.findViewById(R.id.first_sim_name);
        mTextViewName[1] = (TextView)mPromptView.findViewById(R.id.second_sim_name);
        mTextViewName[2] = (TextView)mPromptView.findViewById(R.id.third_sim_name);
        mTextViewName[3] = (TextView)mPromptView.findViewById(R.id.fourth_sim_name);
        for (int i = 0; i < mNumOfTextView; i++) {
            mTextViewName[i].setVisibility(View.GONE);
        }

        if (SIM_DETECT_NEW.equals(simDetectStatus)) {
          //get prompt message and hide sim name text if it is excess
            if (newSimNumber == 1) {
                msg = mContext.getResources().getString(R.string.change_setting_for_onenewsim);
            } else {
                msg = mContext.getResources().getString(R.string.change_setting_for_twonewsim);
            }
            /// M: Change the String with SIM according to Operator.
            msg = mIOperatorSIMString.getOperatorSIMStringForSIMDetection(msg, newSimSlot, newSimNumber, mContext);
            //get sim name
            int simId = PhoneConstants.GEMINI_SIM_1;
            int mIndexOfTextView = 0;
            while (newSimSlot != 0) {
                if ((newSimSlot & 0x01) != 0) {
                    mTextViewName[mIndexOfTextView].setVisibility(View.VISIBLE);
                    mCardInfoExt.addOptrNameBySlot(mTextViewName[mIndexOfTextView], simId, mContext, 
                                            KeyguardUtils.getOptrNameBySlot(mContext, simId));
                    mIndexOfTextView++;
                }
                simId++;
                newSimSlot = newSimSlot >>> 1;
            }
        } else if (SIM_DETECT_REMOVE.equals(simDetectStatus)) {
            msg = mContext.getResources().getString(R.string.sim_card_removed);
          /// M: Change the String with SIM according to Operator.
            msg = mIOperatorSIMString.getOperatorSIMString(msg, -1, SIMChangedTag.DELSIM, mContext);
        } else if (SIM_DETECT_SWAP.equals(simDetectStatus)) {
            msg = mContext.getResources().getString(R.string.sim_card_swapped);
        } else {
            throw new IllegalStateException("Unknown SIMCard Changed:" + simDetectStatus);
        }
        
        ((TextView)mPromptView.findViewById(R.id.prompt)).setText(msg);
    }
    private void initSimSettingsView() {
        long voiceCallSimIdx = Settings.System.getLong(mContext.getContentResolver(), 
                     Settings.System.VOICE_CALL_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
        long smsSimIdx = Settings.System.getLong(mContext.getContentResolver(), 
                     Settings.System.SMS_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
        long dataSimIdx = Settings.System.getLong(mContext.getContentResolver(), 
                     Settings.System.GPRS_CONNECTION_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
        long videoCallSimIdx = Settings.System.getLong(mContext.getContentResolver(), 
                     Settings.System.VIDEO_CALL_SIM_SETTING, Settings.System.DEFAULT_SIM_NOT_SET);
        TelephonyManager telephony = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        boolean voiceCapable = (telephony != null && telephony.isVoiceCapable());
        boolean smsCapable = (telephony != null && telephony.isSmsCapable());
        boolean multiSim = mSimChangedStatus.getSIMCount() >= 2;
        
        if (DEBUG) {
            Log.i(TAG, "initSimSettingsView, isVoiceCapable=" + voiceCapable
                    + ", isSmsCapabl=" + smsCapable
                    + ", voiceCallSimIdx=" + voiceCallSimIdx
                    + ", smsSimIdx=" + smsSimIdx
                    + ", dataSimIdx=" + dataSimIdx
                    + ", videoCallSimIdx=" + videoCallSimIdx
                    + ", multiSim=" + multiSim);
        }

        /// M: Change the String with SIM according to Operator. @{
        String simSettingPrompt = mContext.getResources().getString(R.string.default_sim_setting_prompt);
        simSettingPrompt = mIOperatorSIMString.getOperatorSIMString(simSettingPrompt, -1, SIMChangedTag.DELSIM, mContext);
        ((TextView) mPromptView.findViewById(R.id.sim_setting_prompt)).setText(simSettingPrompt);
        /// @}

        TextView voiceCall = (TextView) mPromptView.findViewById(R.id.voice_call);
        TextView voiceCallOptr = (TextView) mPromptView.findViewById(R.id.voice_call_opr);
        View voiceCallItem = mPromptView.findViewById(R.id.voice_call_item);
        if (shouldShowVoiceCall(voiceCapable, multiSim)) {
            voiceCall.setText(R.string.keyguard_voice_call);
            mCardInfoExt.addOptrNameByIdx(voiceCallOptr, voiceCallSimIdx, mContext, 
                                            KeyguardUtils.getOptrNameByIdx(mContext, voiceCallSimIdx));
        } else {
            voiceCallItem.setVisibility(View.GONE);
        }
        
        TextView videoCall = (TextView) mPromptView.findViewById(R.id.video_call);
        TextView videoCallOptr = (TextView) mPromptView.findViewById(R.id.video_call_opr);
        View videoCallItem = mPromptView.findViewById(R.id.video_call_item);        
        if (shouldShowVideoCall(voiceCapable, multiSim)) {
            videoCall.setText(R.string.keyguard_video_call);
            mCardInfoExt.addOptrNameByIdx(videoCallOptr, videoCallSimIdx, mContext, 
                                            KeyguardUtils.getOptrNameByIdx(mContext, videoCallSimIdx));
        } else {
            videoCallItem.setVisibility(View.GONE);
        }
        
        TextView sms = (TextView) mPromptView.findViewById(R.id.sms);
        TextView smsOptr = (TextView) mPromptView.findViewById(R.id.sms_opr);
        View smsItem = mPromptView.findViewById(R.id.sms_item);
        if (shouldShowSms(smsCapable, multiSim)) {
            sms.setText(R.string.keyguard_sms);
            mCardInfoExt.addOptrNameByIdx(smsOptr, smsSimIdx, mContext, 
                                            KeyguardUtils.getOptrNameByIdx(mContext, smsSimIdx));
        } else {
            smsItem.setVisibility(View.GONE);
        }
        
        TextView data = (TextView) mPromptView.findViewById(R.id.data);
        TextView dataOptr = (TextView) mPromptView.findViewById(R.id.data_opr);
        data.setText(R.string.keyguard_data);
        mCardInfoExt.addOptrNameByIdx(dataOptr, dataSimIdx, mContext, 
                                            KeyguardUtils.getOptrNameByIdx(mContext, dataSimIdx));
    }
    
    private boolean shouldShowVoiceCall(boolean voiceCallCapable, boolean multiSim) {
        if (DEBUG) {
            Log.i(TAG, "shouldShowVoiceCall, voiceCallCapable = " + voiceCallCapable + ", multiSim = " + multiSim );
        }
        if (voiceCallCapable && (multiSim || internetCallIsOn())) {
            return true;
        } else {
            return false;
        }
    }
    
    private boolean internetCallIsOn() {
        boolean isSupport = SipManager.isVoipSupported(mContext);
        boolean isOn = Settings.System.getInt(mContext.getContentResolver(), Settings.System.ENABLE_INTERNET_CALL, 0) == 1;
        if (DEBUG) {
            Log.i(TAG, "internetCallIsOn, isSupport = " + isSupport + ", isOn = " + isOn );
        }
        if (isSupport && isOn) {
            return true;
        } else {
            return false;
        }
    }

    private boolean shouldShowVideoCall(boolean voiceCallCapable, boolean multiSim) {
        boolean Is3GSwitchManualEnabled = false;
        int slot3G = -1;
///		int activeModemType = LteModemSwitchHandler.MD_TYPE_UNKNOWN;
		
        try {
            final ITelephonyEx telephony = ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"));
            if (telephony != null) {
                slot3G = telephony.get3GCapabilitySIM();
                boolean IsManuAllowed = telephony.is3GSwitchManualEnabled();
                boolean IsManuSelected = telephony.is3GSwitchManualChange3GAllowed();
                Is3GSwitchManualEnabled = IsManuAllowed && IsManuSelected;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "ITelephonyEx exception");
        }
		
       /* try {
            activeModemType = ITelephonyEx.Stub.asInterface(ServiceManager.checkService("phoneEx")).getActiveModemType();
        } catch (RemoteException e) {
            Log.e(TAG, "getActiveModemType exception");
        }*/
		
        if (DEBUG) {
            Log.i(TAG, "shouldShowVideoCall, video_SUPPORT = " + KeyguardUtils.isMediatekVT3G324MSupport() 
                    + ", 3G_SWITCH = " + KeyguardUtils.isMediatekGemini3GSwitchSupport()
                    + ", voiceCallCapable = " + voiceCallCapable
                    + ", multiSim = " + multiSim
                    + ", Is3GSwitchManualEnabled = " + Is3GSwitchManualEnabled
                    + ", slot3G = " + slot3G);
        }

        if (voiceCallCapable && multiSim
                && KeyguardUtils.isMediatekVT3G324MSupport()
                && KeyguardUtils.isMediatekGemini3GSwitchSupport()
                && Is3GSwitchManualEnabled
                && slot3G != -1
                ///&& (activeModemType != LteModemSwitchHandler.MD_TYPE_LTNG) // not support VT for LTE-DC mode
                ) {
            return true;
        } else {
            return false;
        }
    }
    private boolean shouldShowSms(boolean smsCapable, boolean multiSim) {
        if (DEBUG) {
            Log.i(TAG, "shouldShowSms, smsCapable = " + smsCapable 
                    + ", multiSim = " + multiSim );
        }
        if (smsCapable && multiSim) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Whether or not exist SIM card in device.
     * 
     * @return
     */
    public static boolean isSIMInserted(int slotId) {
        try {
            final ITelephonyEx phoneEx = 
                ITelephonyEx.Stub.asInterface(ServiceManager.checkService("phoneEx"));
            if (phoneEx != null && phoneEx.hasIccCard(slotId)) {
                return true;
            }
        } catch (RemoteException ex) {
            KeyguardUtils.xlogE(TAG, "Get sim insert status failure!");
        }
        return false;
    }

    void updateResources() {
        if (null != mSimCardChangedDialog && null != mPromptView && mSimCardChangedDialog.isShowing()) {
            ///M: dismiss the showing dialog then recreate it again. 
            Configuration newConfig = mContext.getResources().getConfiguration();
            if (mCreateOrientation != newConfig.orientation
                || mCreateScreenWidthDp != newConfig.screenWidthDp
                || mCreateScreenHeightDp != newConfig.screenHeightDp) {
                mSimCardChangedDialog.dismiss();
                KeyguardUtils.xlogD(TAG, this + "updateResource --requestShowDialog(..) again");
                requestShowDialog(new NewSimDialogCallback());
            } else {
                /// M: Change the String with SIM according to Operator. @{
                String simCardChangedTitle = mContext.getResources().getString(R.string.sim_card_changed_dialog_title);
                simCardChangedTitle = mIOperatorSIMString.getOperatorSIMString(simCardChangedTitle, -1, SIMChangedTag.UIMSIM, mContext);
                mSimCardChangedDialog.setTitle(simCardChangedTitle);
                /// @}
                
                Button nagbtn = mSimCardChangedDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                if (null != nagbtn) {
                    nagbtn.setText(R.string.keyguard_close);
                }
                Button posbtn = mSimCardChangedDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (null != posbtn) {
                    posbtn.setText(R.string.change_settings);
                }
                
                initSimChangedPrompt();
                initSimSettingsView();
            }
        }

        /// M: Save new orientation
        mCreateOrientation = mContext.getResources().getConfiguration().orientation;
        mCreateScreenWidthDp = mContext.getResources().getConfiguration().screenWidthDp;
        mCreateScreenHeightDp = mContext.getResources().getConfiguration().screenHeightDp;
    }

    private void handleSIMCardChanged() {
        LayoutInflater factory = LayoutInflater.from(mContext);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);

        /// M: specially handle last SIM card is removed case
        int simCount = mSimChangedStatus.getSIMCount();
        String simDetectStatus = mSimChangedStatus.getSimDetectStatus();
        if (SIM_DETECT_REMOVE.equals(simDetectStatus) && 0 == simCount) {
            dialogBuilder.setCancelable(false);
            dialogBuilder.setTitle(com.android.internal.R.string.dialog_alert_title);
            dialogBuilder.setIcon(com.android.internal.R.drawable.ic_dialog_alert);
            dialogBuilder.setMessage(R.string.lockscreen_missing_sim_dialog_message);
            /// M: Change the String with SIM according to Operator. @{
            String msg = mContext.getResources().getString(R.string.lockscreen_missing_sim_dialog_message);
            msg = mIOperatorSIMString.getOperatorSIMString(msg, -1, SIMChangedTag.UIMSIM, mContext);
            dialogBuilder.setMessage(msg);
            /// @}
            dialogBuilder.setPositiveButton(android.R.string.ok, null);
            mPromptView = null; // avoid to enter initSimChangedPrompt() and initSimSettingsView()
        }
        else {
            dialogBuilder.setCancelable(false);
            dialogBuilder.setPositiveButton(R.string.change_settings,
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface arg0, int arg1) {
                        //begin to call setting interface
                        Intent intent = new Intent("android.settings.GEMINI_MANAGEMENT");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    }
            });
            dialogBuilder.setNegativeButton(R.string.keyguard_close, null);
            /// M: Change the String with SIM according to Operator. @{
            String simCardChangedTitle = mContext.getResources().getString(R.string.sim_card_changed_dialog_title);
            simCardChangedTitle = mIOperatorSIMString.getOperatorSIMString(simCardChangedTitle, -1, SIMChangedTag.UIMSIM, mContext);
            dialogBuilder.setTitle(simCardChangedTitle);
            /// @}
            
            mPromptView = factory.inflate(R.layout.mtk_prompt, null);

            initSimChangedPrompt();
            initSimSettingsView();
            dialogBuilder.setView(mPromptView);
        }

        mSimCardChangedDialog = dialogBuilder.create();
        mSimCardChangedDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        mSimCardChangedDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface di) {
                // report close
                reportDialogClose();
            }
        });
        mSimCardChangedDialog.show();
    }

    private void handleSIMNameUpdate(int slotId) {
       if (KeyguardUtils.isGemini()) {
           updateResources();//update the new sim detected or default sim removed
       }
    }
    
    private int getSimNumber(int simSlot) {
        int n = 0;
        while (simSlot != 0) {
            if ((simSlot & 0x01) != 0) {
                n++;
            }
            simSlot = simSlot >>> 1;
        }
        return n;
    }


    /**
     * M: Return device is in encrypte mode or not.
     * If it is in encrypte mode, we will not show lockscreen.
     */
    public boolean isEncryptMode() {
        String state = SystemProperties.get("vold.decrypt");
        return !("".equals(state) || "trigger_restart_framework".equals(state));
    }


     /**
     * M: interface is a call back for the user who need to popup Dialog.
     */
    public static interface DialogShowCallBack {
        public void show();
    }

    /**
     * M: request show dialog
     * @param callback the user need to implement the callback.
     */
    public void requestShowDialog(DialogShowCallBack callback) {
        if (!KeyguardViewMediator.isKeyguardInActivity) {
            mDialogSequenceManager.requestShowDialog(callback);
        } else {
            KeyguardUtils.xlogD(TAG, "Ignore showing dialog in KeyguardMock");
        }
    }

    /**
     * M: when the user close dialog, should report the status. 
     */
    public void reportDialogClose() {
        mDialogSequenceManager.reportDialogClose();
    }

    /**
     * M: interface for showing dialog sequencely manager.
     * 
     */
    public static interface SequenceDialog {
        /**
         * the client  needed to show a dialog should call this
         * @param callback the client should implement the callback.
         */
        public void requestShowDialog(DialogShowCallBack callback);
        /**
         * If the client close the dialog, should call this to report.
         */
        public void reportDialogClose();
    }

    /**
     * M: Manage the dialog sequence.
     * It implment the main logical of the sequence process.
     */
    private class DialogSequenceManager implements SequenceDialog {
        /// M: log tag for this class
        private static final String CLASS_TAG = "DialogSequenceManager";
        /// M: debug switch for the log.
        private static final boolean CLASS_DEBUG = true;
        /// M: The queue to save the call backs.
        private Queue<DialogShowCallBack> mDialogShowCallbackQueue;
        /// M: Whether the inner dialog is showing
        private boolean mInnerDialogShowing = false;
        /// M: If keyguard set the dialog sequence value, and inner dialog is showing. 
        private boolean mLocked = false;

        public DialogSequenceManager() {
            if (CLASS_DEBUG) {
                KeyguardUtils.xlogD(TAG, CLASS_TAG + " DialogSequenceManager()");
            }
            mDialogShowCallbackQueue = new LinkedList<DialogShowCallBack>();

            mContext.getContentResolver().registerContentObserver(System.getUriFor(System.DIALOG_SEQUENCE_SETTINGS),
                    false, mDialogSequenceObserver);
            mContext.getContentResolver().registerContentObserver(System.getUriFor(System.OOBE_DISPLAY),
                    false, mOOBEObserver);
         }

        public void requestShowDialog(DialogShowCallBack callback) {
            if (CLASS_DEBUG) {
                KeyguardUtils.xlogD(TAG, CLASS_TAG + " --requestShowDialog()");
            }
            mDialogShowCallbackQueue.add(callback);
            handleShowDialog();
        }

        public void handleShowDialog() {
            if (CLASS_DEBUG) {
                KeyguardUtils.xlogD(TAG, CLASS_TAG + " --handleShowDialog()--enableShow() = " + enableShow());
            }
            if (enableShow()) {
                if (getLocked()) {
                    DialogShowCallBack dialogCallBack = mDialogShowCallbackQueue.poll();
                    if (CLASS_DEBUG) {
                        KeyguardUtils.xlogD(TAG, CLASS_TAG + " --handleShowDialog()--dialogCallBack = " + dialogCallBack);
                    }
                    if (dialogCallBack != null) {
                        dialogCallBack.show();
                        setInnerDialogShowing(true);
                    }
                } else {
                    if (CLASS_DEBUG) {
                        KeyguardUtils.xlogD(TAG, CLASS_TAG + " --handleShowDialog()--System.putInt( " 
                                + System.DIALOG_SEQUENCE_SETTINGS + " value = " + System.DIALOG_SEQUENCE_KEYGUARD);
                    }
                    System.putInt(mContext.getContentResolver(), System.DIALOG_SEQUENCE_SETTINGS,
                            System.DIALOG_SEQUENCE_KEYGUARD);
                }
            }
        }

        public void reportDialogClose() {
            if (CLASS_DEBUG) {
                KeyguardUtils.xlogD(TAG, CLASS_TAG + " --reportDialogClose()--mDialogShowCallbackQueue.isEmpty() = " 
                        + mDialogShowCallbackQueue.isEmpty());
            }
            setInnerDialogShowing(false);
            
            if (mDialogShowCallbackQueue.isEmpty()) {
                if (CLASS_DEBUG) {
                    KeyguardUtils.xlogD(TAG, CLASS_TAG + " --reportDialogClose()--System.putInt( " 
                            + System.DIALOG_SEQUENCE_SETTINGS + " value = " + System.DIALOG_SEQUENCE_DEFAULT
                            + " --setLocked(false)--");
                }
                System.putInt(mContext.getContentResolver(), System.DIALOG_SEQUENCE_SETTINGS,
                        System.DIALOG_SEQUENCE_DEFAULT);
                setLocked(false);
            } else {
                handleShowDialog();
            }
        }

        /**
         * M : Combine the conditions to deceide whether enable showing or not
         */
        private boolean enableShow() {
            if (CLASS_DEBUG) {
                KeyguardUtils.xlogD(TAG, CLASS_TAG + " --enableShow()-- !mDialogShowCallbackQueue.isEmpty() = " + !mDialogShowCallbackQueue.isEmpty()
                        + " !getInnerDialogShowing() = " + !getInnerDialogShowing()
                        + " !isOtherModuleShowing() = " + !isOtherModuleShowing()
                        + "!isAlarmBoot() = " + !PowerOffAlarmManager.isAlarmBoot()
                        + " isDeviceProvisioned() = " + mUpdateMonitor.isDeviceProvisioned()
                        + " !isOOBEShowing() = " + !isOOBEShowing());
            }

            return !mDialogShowCallbackQueue.isEmpty() && !getInnerDialogShowing() && !isOtherModuleShowing() 
                    && !PowerOffAlarmManager.isAlarmBoot() && mUpdateMonitor.isDeviceProvisioned() 
                    && !isOOBEShowing() && !isEncryptMode();
        }

        /**
         * M : Query the dialog sequence settings to decide whether other module's dialog is showing or not.
         */
        private boolean isOtherModuleShowing() {
            int value = queryDialogSequenceSeetings();
            if (CLASS_DEBUG) {
                KeyguardUtils.xlogD(TAG, CLASS_TAG + " --isOtherModuleShowing()--" + System.DIALOG_SEQUENCE_SETTINGS + " = " + value);
            }
            if (value == System.DIALOG_SEQUENCE_DEFAULT || value == System.DIALOG_SEQUENCE_KEYGUARD) {
                return false;
            }
            return true;
        }

        private void setInnerDialogShowing(boolean show) {
            mInnerDialogShowing = show;
        }

        private boolean getInnerDialogShowing() {
            return mInnerDialogShowing;
        }
        
        private void setLocked(boolean locked) {
            mLocked = locked;
        }

        private boolean getLocked() {
            return mLocked;
        }

        /**
         * M : Query dialog sequence settings value 
         */
        private int queryDialogSequenceSeetings() {
            int value = System.getInt(mContext.getContentResolver(), System.DIALOG_SEQUENCE_SETTINGS,
                    System.DIALOG_SEQUENCE_DEFAULT);
            return value;
        }

        /// M: dialog sequence observer for dialog sequence settings
        private ContentObserver mDialogSequenceObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                int value = queryDialogSequenceSeetings();
                if (CLASS_DEBUG) {
                    KeyguardUtils.xlogD(TAG, CLASS_TAG + " DialogSequenceObserver--onChange()--"
                            + System.DIALOG_SEQUENCE_SETTINGS + " = " + value);
                }
                if (value == System.DIALOG_SEQUENCE_DEFAULT) {
                    setLocked(false);
                    handleShowDialog();
                } else if (value == System.DIALOG_SEQUENCE_KEYGUARD) {
                    setLocked(true);
                    handleShowDialog();
                }
            }
        };

        /**
         * M :Query the OOBE display value
         */
        private int queryOOBEDisplay() {
            int value = System.getInt(mContext.getContentResolver(), System.OOBE_DISPLAY,
                    System.OOBE_DISPLAY_DEFAULT);
            return value;
        }

        /// M: OOBE observer for settings
        private ContentObserver mOOBEObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                int value = queryOOBEDisplay();
                if (CLASS_DEBUG) {
                    KeyguardUtils.xlogD(TAG, CLASS_TAG + " OOBEObserver--onChange()--" + System.OOBE_DISPLAY 
                            + " = " + value);
                }
                if (value != System.OOBE_DISPLAY_ON) {
                    handleShowDialog();
                }
            }
        };

        /**
         * M :return whether the OOBE is showing or not.
         */
        private boolean isOOBEShowing() {
            int value = queryOOBEDisplay();
            if (CLASS_DEBUG) {
                KeyguardUtils.xlogD(TAG, CLASS_TAG + " OOBEObserver--isOOBEShowing()--" + System.OOBE_DISPLAY + " = " + value);
            }
            return (value == System.OOBE_DISPLAY_ON);
        }
    }

    /**
     * M: implement new sim dialog callback
     */
    private class NewSimDialogCallback implements DialogShowCallBack {
        public void show() {
            KeyguardUtils.xlogD(TAG, "NewSimDialogCallback--show()--");
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_DETECTED));
        }
    }
    

    
}
