package com.mediatek.nfc.addon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.os.Handler;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants;
/// import com.android.internal.telephony.PhoneConstants;
import com.mediatek.nfc.porting.*;

public class NfcSimStateObserver implements Handler.Callback {
    private static final String TAG = "NfcSimStateObserver";
    private static final int MSG_TIMEOUT = 1;
    private Context mContext;
    private Handler mHandler;
    private IntentFilter mFilter;
    private Callback mCallback;
    private boolean mIsSim1Ready;
    private boolean mIsSim2Ready;

    public interface Callback {
        public void onSimReady(int simId);
        public void onSimReadyTimeOut();
    }

    public boolean isSimReady(int simId) {
		Log.d(TAG, "isSimReady simId = " + simId + ", mIsSim1Ready = " + mIsSim1Ready + " mIsSim2Ready = " + mIsSim2Ready);
		
        if (simId == 0) {
            return mIsSim1Ready;
        } else if (simId == 1) {
            return mIsSim2Ready;
        }
        return false;
    }
    
    public NfcSimStateObserver(Context context, Callback callback, int msToWait) {
        mContext = context;
        mCallback = callback;
        mHandler = new Handler(this);
		mContext.registerReceiver(mReceiver, new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        mFilter = new IntentFilter(); 
		mFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
		mFilter.addAction(Intent.ACTION_MEDIA_EJECT);
		mFilter.addDataScheme("file");
        mContext.registerReceiver(mSdCardBroadcastReceiver, mFilter);
        Log.d(TAG, "wait for sim ready, msToWait = " + msToWait);
        mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, msToWait);
		mInstance = this;
    }

    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_TIMEOUT:
                Log.d(TAG, "handleMessage: MSG_TIMEOUT");
                mCallback.onSimReadyTimeOut();
                return true;
        }
        return false;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String iccState;
                int simId;
                iccState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                simId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
                if (iccState == null) {
                    iccState = "NULL";
                }
                Log.d(TAG, "ACTION_SIM_STATE_CHANGED receiver with iccState = " + iccState + ", simId = " + simId);
                /// if (iccState.equals(IccCardConstants.INTENT_VALUE_ICC_READY)) {
				if (iccState.equals(IccCardConstants.INTENT_VALUE_ICC_LOADED)) {
					/// add for SecureElementSelector
					/// first update the status field
					/// then notify the client and let it query sim state via isSimReady()
					if (simId == 0) {
						mIsSim1Ready = true;
					} else if (simId == 1) {
						mIsSim2Ready = true;
					}					
					mCallback.onSimReady(simId);
					
					if (mSimListener != null) {
						mSimListener.onSimStateChanged(simId, SimEventListener.READY);
					}
                } else if (iccState.equals(IccCardConstants.INTENT_VALUE_ICC_NOT_READY) ||
                           iccState.equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT)) {
					if (simId == 0) {
						mIsSim1Ready = false;
					} else if (simId == 1) {
						mIsSim2Ready = false;
					}
					if (mSimListener != null) {
						mSimListener.onSimStateChanged(simId, SimEventListener.NOT_READY);
					}
				}
            }
        }
    };
	
	private BroadcastReceiver mSdCardBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
			if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                Log.d(TAG, "Intent.ACTION_MEDIA_EJECT");
				if (mSdCardListener != null) {
					mSdCardListener.onSdCardStateChanged(0); /// currently don't care, simply trigger update
				}
			} else if (action.equals(Intent.ACTION_MEDIA_CHECKING)) {
				Log.d(TAG, "Intent.ACTION_MEDIA_CHECKING");
				if (mSdCardListener != null) {
					mSdCardListener.onSdCardStateChanged(0); /// currently don't care, simply trigger update
				}
			}
        }
    };
	
	static NfcSimStateObserver mInstance;
	private SimEventListener mSimListener;
	private SdCardEventListener mSdCardListener;
	
	static public NfcSimStateObserver getInstance() {
		return mInstance;
	}
	
	public interface SimEventListener {		
		static public final int SIM1 = 0;
		static public final int SIM2 = 1;
		static public final int NOT_READY = 0;
		static public final int READY = 1;
		public void onSimStateChanged(int simId, int event);	
	}
	
	public interface SdCardEventListener {
		static public final int REMOVE = 0;
		static public final int INSERT = 1;
		public void onSdCardStateChanged(int event);
	}
	
	public void registerSimEventListener(SimEventListener simListener) {
		mSimListener = simListener;
	}
	
	public void registerSdCardEventListener(SdCardEventListener sdCardListener) {
		mSdCardListener = sdCardListener;
	}
	
	public void unregisterSimEventListener() {
		mSimListener = null;
	}
	
	public void unregisterSdCardEventListener() {
		mSdCardListener = null;
	}	
	
}

