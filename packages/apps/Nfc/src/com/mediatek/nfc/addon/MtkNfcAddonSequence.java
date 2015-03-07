package com.mediatek.nfc.addon;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.util.Log;
import com.android.nfc.NfcService;
import com.android.nfc.dhimpl.NativeNfcManager;

public class MtkNfcAddonSequence 
	implements ISeController.Callback {
    private static final String TAG = "MtkNfcAddonSequence";
    
    public static final int MODE_READER = 1;
    public static final int MODE_P2P = 2;
    public static final int MODE_CARD = 4;
    public static final int FLAG_OFF = 0;
    public static final int FLAG_ON = 1;
    public static final String PREF_MODE_READER = "nfc.pref.mode.reader";
    public static final String PREF_MODE_P2P = "nfc.pref.mode.p2p";
    public static final String PREF_MODE_CARD = "nfc.pref.mode.card";

    private NativeNfcManager mNativeNfcManager;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEditor;
	private Context mContext;
	private ISeController mSecureElementSelector;

    private static MtkNfcAddonSequence mSingleton;

    private MtkNfcAddonSequence(Object nativeNfcManager, SharedPreferences pref, SharedPreferences.Editor prefEditor, Context context) {
        mNativeNfcManager = (NativeNfcManager)nativeNfcManager;
        mPrefs = pref;
        mPrefsEditor = prefEditor;
		mContext = context;
		mSecureElementSelector = new SecureElementSelector(mContext, mNativeNfcManager, NfcService.getInstance(), this);
    }

    public static void createSingleton(Object nativeNfcManager, SharedPreferences pref, SharedPreferences.Editor prefEditor, Context context) {
        mSingleton = new MtkNfcAddonSequence(nativeNfcManager, pref, prefEditor, context);
    }

    public static MtkNfcAddonSequence getInstance() {
        return mSingleton;
    }

    public int getActiveSeValue(){
        return mSecureElementSelector.getActiveSeValue();
    }

    public int getModeFlag(int mode, Object syncObj) {
		Log.d(TAG, "getModeFlag, mode = " + mode);
        int flag = -1;
        synchronized (syncObj) {
            if (MODE_READER == mode) {
                flag = mPrefs.getInt(PREF_MODE_READER, 1);
            } else if (MODE_P2P == mode) {
                flag = mPrefs.getInt(PREF_MODE_P2P, 1);
            } else if (MODE_CARD == mode) {
                flag = mPrefs.getInt(PREF_MODE_CARD, 1);
            } 
        }
		Log.d(TAG, "return = " + flag);
        return flag;
    }

    public void setModeFlag(boolean isNfcEnabled, int mode, int flag, Object syncObj) {
		Log.d(TAG, "setModeFlag, isNfcEnabled = " + isNfcEnabled + ", mode = " + mode + ", flag = " + flag + ", syncObj = " + syncObj);
		if (mode == MODE_CARD) {
			Log.d(TAG, "bypass card mode control from Setting");
			return;
		}
        synchronized (syncObj) {
			if ((mode > (MODE_READER | MODE_P2P | MODE_CARD) || mode < 0) ||
				(flag != FLAG_ON && flag != FLAG_OFF)) {
				Log.d(TAG, "incorrect mode or flag, return");
				return;
			}
			if ((mode & MODE_READER) != 0) {
				mPrefsEditor.putInt(PREF_MODE_READER, flag);
				mPrefsEditor.apply();
			} 
			if ((mode & MODE_P2P) != 0) {
				mPrefsEditor.putInt(PREF_MODE_P2P, flag);
				mPrefsEditor.apply();
			} 
			if ((mode & MODE_CARD) != 0) {
				mPrefsEditor.putInt(PREF_MODE_CARD, flag);
				mPrefsEditor.apply();
			} 
			
			if (isNfcEnabled) {
                Log.d(TAG, "Ready for ApplyPollingLoopThread");
				new ApplyPollingLoopThread(mode, flag, new WatchDogThread(), syncObj).start();
			}
        }
    }
	
	public void applyIpoSequence() {
		Log.d(TAG, "applyIpoSequence");
		mSecureElementSelector.applyIpoSequence();
	}

    public void applyEnableSequence() {
        Log.d(TAG, "applyEnableSequence ENTRY");
		int curMode = 0;			

        if (mPrefs.getInt(PREF_MODE_READER, 1) == 1) {
            curMode |=  MODE_READER;
		}
        if (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) {
            curMode |=  MODE_P2P;
		}
		
		/**
		 *	The card mode control should be bypass to SecureElementSelector.
		 * 	If it finally selects some SE, than we should turn on card mode,
		 *	otherwise we should turn off card mode.
		 */
		if (mSecureElementSelector.init()) {
			curMode |= MODE_CARD;
			mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_ON).apply();
		} else {
			mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_OFF).apply();
		}
		
        Log.d(TAG, "applyEnableSequence curMode= " + curMode);
	    mNativeNfcManager.setNfcMode(curMode);
        Log.d(TAG, "applyEnableSequence EXIT");
    }

    public void applyDisableSequence(boolean disableCardMode) {
        Log.d(TAG, "applyDisableSequence ENTRY, disableCardMode = " + disableCardMode);
		int curMode = 0;
		if (!disableCardMode && (mPrefs.getInt(PREF_MODE_CARD, 1) == 1)) {
			curMode |=  MODE_CARD;   
		}
				
		mNativeNfcManager.disableDiscovery();
		mSecureElementSelector.deinit(curMode == 0);
		mNativeNfcManager.setNfcMode(curMode);		
		if (curMode != 0) {
			mNativeNfcManager.enableDiscovery();
		}
		 
        Log.d(TAG, "applyDisableSequence EXIT");
    }
	
	public void applyPreEnableDiscoverySequence() {
		/// precondition: discovery is disabled
		Log.d(TAG, "applyPreEnableDiscoverySequence ENTRY");
		mNativeNfcManager.disableDiscovery();
		int curMode = 0;
		curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
		curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;
		curMode |= (mPrefs.getInt(PREF_MODE_CARD, 1) == 1) ? MODE_CARD : 0;
		mNativeNfcManager.setNfcMode(curMode);
		Log.d(TAG, "applyPreEnableDiscoverySequence EXIT");
	}
	
	public void applyPostDisableDiscoverySequence() {
		/// precondition: discovery is disabled
		Log.d(TAG, "applyPostDisableDiscoverySequence ENTRY");
		int curMode = 0;
		if (mPrefs.getInt(PREF_MODE_CARD, 1) == 1) {
			curMode |=  MODE_CARD;            
		}
		mNativeNfcManager.setNfcMode(curMode);
		mNativeNfcManager.enableDiscovery();
		Log.d(TAG, "applyPostDisableDiscoverySequence EXIT");
	}

    class ApplyPollingLoopThread extends Thread {
        int mMode;
        int mFlag;
        WatchDogThread mWatchDog;
        Object mSync;

        ApplyPollingLoopThread(int mode, int flag, WatchDogThread watchDog, Object syncObj) {
            mMode = mode;
            mFlag = flag;
            mWatchDog = watchDog;
            mSync = syncObj;
        }

        @Override
        public void run() {
            int ret = -1;
			int curMode = 0;
            mWatchDog.start();
            synchronized (mSync) {
                try {
                    mNativeNfcManager.disableDiscovery();
					curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
					curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;
					curMode |= (mPrefs.getInt(PREF_MODE_CARD, 1) == 1) ? MODE_CARD : 0;
					Log.d(TAG, "ApplyPollingLoopThread curMode= " + curMode);
					mNativeNfcManager.setNfcMode(curMode);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mNativeNfcManager.enableDiscovery();
                }
            }
            mWatchDog.cancel();
        }
    }
    
    class WatchDogThread extends Thread {
        boolean mWatchDogCanceled = false;

        @Override
        public void run() {
            boolean slept = false;
            while (!slept) {
                try {
                    Thread.sleep(10000);
                    slept = true;
                } catch (InterruptedException e) { }
            }
            synchronized (this) {
                if (!mWatchDogCanceled) {
                    // Trigger watch-dog
                    Log.e(TAG, "Watch dog triggered");
                    mNativeNfcManager.doAbort();
                }
            }
        }

        public synchronized void cancel() {
            mWatchDogCanceled = true;
        }
    }
	
	/// ISeController.Callback
	public void onDeselectByUser(boolean enableDiscovery) {
		int curMode = 0;		
		curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
		curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;
        Log.d(TAG, "onDeselectByUser curMode = " + curMode);
		mNativeNfcManager.setNfcMode(curMode);		
		if (enableDiscovery) {
			mNativeNfcManager.enableDiscovery();
		}
		mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_OFF).apply();
	}
    
    public void onSelectByUser() {
		int curMode = MODE_CARD;		
		curMode |= (mPrefs.getInt(PREF_MODE_READER, 1) == 1) ? MODE_READER : 0;
		curMode |= (mPrefs.getInt(PREF_MODE_P2P, 1) == 1) ? MODE_P2P : 0;
        mPrefsEditor.putInt(PREF_MODE_CARD, FLAG_ON).apply();
        Log.d(TAG, "onSelectByUser curMode = " + curMode);
		mNativeNfcManager.setNfcMode(curMode);				
    }
		
}
