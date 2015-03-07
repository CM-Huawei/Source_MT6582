package com.mediatek.nfc.addon;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.HashMap;
import java.io.File;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.provider.Settings;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.ServiceManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.widget.Toast;

import com.android.nfc.dhimpl.NativeNfcManager;
import com.mediatek.nfc.addon.NfcSimStateObserver;
import com.mediatek.nfc.configutil.ConfigUtil;
/// import com.mediatek.telephony.SimInfoManager;
/// import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
/// import com.android.internal.telephony.PhoneConstants;
import com.mediatek.nfc.porting.*;
import com.mediatek.common.featureoption.FeatureOption;

import com.android.nfc.R;

interface ISeController {
	public boolean init();
	public void deinit(boolean deselect);
	public void applyIpoSequence();
    public int getActiveSeValue();
	interface Callback {
		public void onDeselectByUser(boolean enableDiscovery);
        public void onSelectByUser();
	}
}

public class SecureElementSelector 
	implements ISeController,
				NfcSimStateObserver.SimEventListener, 
				NfcSimStateObserver.SdCardEventListener,
				Handler.Callback {
	static private final String TAG = "SecureElementSelector";
	static private final String PREF_BACKUP_SE = "backupSeString";
	static private final String PREF_BACKUP_SE_NONE = "none";
	
	static private final String CFG_FILE_PATH = "system/etc/nfcse.cfg";
	static private final String CFG_FILE_RULES[] = {
		"SWP1=1:SIM1=1,SIM2=2",
		"SWP2=2:SIM1=1,SIM2=2",
		"SD=3:NO=0,YES=1",
		"ESE=4:NO=0,YES=1",
	};
	
	static public final int USER_OFF = 0;
	static public final int USER_SIM1 = 1;
	static public final int USER_SIM2 = 2;
	static public final int USER_SSD = 3;
	static public final int USER_ESE = 4;
	
	static public final int CHIP_OFF = 0;
	static public final int CHIP_SWP1 = 1;
	static public final int CHIP_SWP2 = 2;
	static public final int CHIP_SSD_ESE = 3;
	//static public final int CHIP_SSD = 3;
	//static public final int CHIP_ESE = 4;
	
	/// TODO: move to setting later
	public static final String NFC_MULTISE_ON = "nfc_multise_on"; //value type: int,0 for Off, 1 for on
	public static final String NFC_MULTISE_LIST = "nfc_multise_list";//SIM1,SIM2,Smart SD
	public static final String NFC_MULTISE_ACTIVE = "nfc_multise_active";//value type: String
	public static final String NFC_MULTISE_PREVIOUS = "nfc_multise_previous";
	public static final String NFC_MULTISE_IN_SWITCHING = "nfc_multise_in_switching";
    public static final String NFC_MULTISE_IN_TRANSACTION = "nfc_multise_in_transation";
	public static final String NFC_MULTISE_NFC_ENABLED = "nfc_on";
	public static final String NFC_USER_DESIRED_SE = "nfc_user_desired_se";
	public static final String NFC_SEAPI_SUPPORT_CMCC = "nfc_seapi_support_cmcc";
	public static final String NFC_SEAPI_CMCC_SIM = "nfc_seapi_cmcc_sim";
	
	public static final String SETTING_STR_SIM1 = "SIM1";
	public static final String SETTING_STR_SIM2 = "SIM2";
	public static final String SETTING_STR_SSD = "Smart SD card";
	public static final String SETTING_STR_ESE = "Embedded SE";
	public static final String SETTING_STR_OFF = "Off";
	
	static final private String DEFAULT_SE_NAME = SETTING_STR_SIM1;
	static final private String ACTION_FAIL_TO_SELECT_SE = "android.nfc.action.SWITCH_FAIL_DIALOG_REQUEST";
	static final private String ACTION_NOT_NFC_SIM = "android.nfc.action.NOT_NFC_SIM_DIALOG_REQUEST";
	static final private String ACTION_NOT_NFC_TWO_SIM = "android.nfc.action.NOT_NFC_TWO_SIM_DIALOG_REQUEST";
	static final private String EXTRA_WHAT_SIM = "android.nfc.extra.WHAT_SIM";
	static final private String EMBEDDED_SE_READY = "android.nfc.EMBEDDED_SE_READY";
	static final private int NOTIFY_SELECTION_FAIL = 0;
	static final private int NOTIFY_SIM1_NOT_NFC = 1;
	static final private int NOTIFY_SIM2_NOT_NFC = 2;
	static final private int NOTIFY_SIM1_SIM2_NOT_NFC = 3;

	private static final int MSG_TIMEOUT = 1;
	
	private Context mContext;
	private ISeController.Callback mCallback;
	private Object mSyncLock;
	private MySecureElement mActiveSe;
	private NativeNfcManager mNativeNfcManager;
	private MyContentProviderUtil mContentProviderUtil;
	private MyContentObserverUtil mContentObserverUtil;
	private ConfigUtil.IParser mConfigFileParser;
	private boolean mIsMultiSeSupported = false;
	static private boolean mShouldSimWarningDialogPopup[] = new boolean[2];
	private Handler mHandler;
	boolean mIsSwpSim1Alarm = false;
	boolean mIsSwpSim2Alarm = false;
	
	public void onSimStateChanged(int simId, int event) {
		onSeRefresh(simId == 0 ? USER_SIM1 : USER_SIM2, event);
	}
	
	public void onSdCardStateChanged(int event) {
		onSeRefresh(USER_SSD, event);
	}

	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MSG_TIMEOUT:
				Log.d(TAG, "handleMessage: MSG_TIMEOUT");
				checkAllSwpSimDIalog(true);
				return true;
		}
		return false;
	}

	private void checkCmccSeMapping() {

		Log.i(TAG, "checkCmccSeMapping");
		if (!NfcRuntimeOptions.isSeapiSupportCmcc()) {
			Log.i(TAG, "Not CMCC project");
			return;
		}
		
		mConfigFileParser = NfcRuntimeOptions.getParser();
		
		int userConfig[] = new int[1];
		int userSe = 0;
		int chipSe = 0;
		int cmccSim = -1;
		if (mConfigFileParser.get(1, userConfig)) {/// SWP1
			if (userConfig[0] == 1) {/// SIM1
				userSe = USER_SIM1;
			} else if (userConfig[0] == 2) {/// SIM2
				userSe = USER_SIM2;				
			} else {
				Log.d(TAG, "wrong value in nfcse.cfg");
				userSe = USER_OFF;
			}
			if (userSe != USER_OFF) {
				if (cmccSim == -1) {
					cmccSim = userSe-1;
					Log.i(TAG, "CMCC SIM in SWP1");
				}
			}	
		}
		
		if (mConfigFileParser.get(2, userConfig)) {/// SWP2
			if (userConfig[0] == 1) {/// SIM1
				userSe = USER_SIM1;				
			} else if (userConfig[0] == 2) {/// SIM2
				userSe = USER_SIM2;				
			} else if (userConfig[0] == 3) {/// ESE
				userSe = USER_ESE;
			} else {
				Log.d(TAG, "wrong value in nfcse.cfg");
				userSe = USER_OFF;
			}
			if (userSe != USER_OFF) {
				if ((cmccSim == -1) && NfcRuntimeOptions.isSeapiSupportCmcc()) {
					cmccSim = userSe-1;
					if (cmccSim >= 1) {
						cmccSim = 1;
					}
					Log.i(TAG, "CMCC SIM in SWP2");
				}
			}
		}		

		Log.i(TAG, "cmccSim number is " + cmccSim);
		mContentProviderUtil.setSeapiCmccSimNumber(cmccSim);
	}
	
	public SecureElementSelector(Context context, NativeNfcManager nativeNfcManager, Object syncLock, ISeController.Callback callback) {
		mContext = context;
		mCallback = callback;
		mNativeNfcManager = nativeNfcManager;
		mSyncLock = syncLock;
		mContentProviderUtil = new MyContentProviderUtil();
		mIsMultiSeSupported = true;
		mContentObserverUtil = new MyContentObserverUtil();
		mContentProviderUtil.setMultiSeSupport(mIsMultiSeSupported);
		mShouldSimWarningDialogPopup[0] = true;
		mShouldSimWarningDialogPopup[1] = true;
		mHandler = new Handler(this);

		mContentProviderUtil.setSeapiSupportCmcc(NfcRuntimeOptions.isSeapiSupportCmcc());
		checkCmccSeMapping();
	}
	
	public void applyIpoSequence() {
		mShouldSimWarningDialogPopup[0] = true;
		mShouldSimWarningDialogPopup[1] = true;
	}
	
	public boolean init() {
		mContentProviderUtil.setNfcEnabled(true);
		if (mIsMultiSeSupported) {
			Log.d(TAG, "init");
			boolean result = false;
			mContentObserverUtil.register();
			constructSeMapping();
			result = restorePreviousSe();
			NfcSimStateObserver.getInstance().registerSimEventListener(this);
			NfcSimStateObserver.getInstance().registerSdCardEventListener(this);
			mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, 30000);
			return result;
		} 
		return true;
	}

	private void notifySmartcardSerive() {

		if (mActiveSe.mSettingString.contains(SETTING_STR_ESE)) {
			Log.d(TAG, "notifySmartcardSerive");
			Intent intent = new Intent(EMBEDDED_SE_READY);
			mContext.sendBroadcast(intent);
		}
	}
    
	private boolean restorePreviousSe() {
		Log.d(TAG, "restorePreviousSe ENTRY");
	
		boolean isActiveSeChanged = false;
		String seStr = mContentProviderUtil.getUserDesiredSeString();
		
		if (seStr == null) {
			Log.d(TAG, "no previous record, default = " + DEFAULT_SE_NAME);
			seStr = DEFAULT_SE_NAME;
			mContentProviderUtil.setUserDesiredSeString(DEFAULT_SE_NAME);
			mContentProviderUtil.setPreviousActiveSeString(DEFAULT_SE_NAME);
		} 
        
        /// return the best SE as candidate, might be different from the original one
        /// if it's the case, the isActiveSeChanged must be set to update UI
        mActiveSe = MySecureElement.searchByName(seStr);
		if (!mActiveSe.mSettingString.equals(mContentProviderUtil.getActiveSeString())) {
			isActiveSeChanged = true;
		}
		
		boolean ret = false;
		
		try {		
			mContentProviderUtil.setInSwitchingFlag(true);		
			if (mActiveSe.mUserSe == USER_OFF) {
				mNativeNfcManager.deselectSecureElement();
			} else {
				try {
					if (mNativeNfcManager.selectSecureElementById(mActiveSe.mChipSe)) {
						Log.d(TAG, "selectSecureElementById success");
						ret = true;
						notifySmartcardSerive();
					} else {
						throw new Exception("selectSecureElementById fail");
					}
				} catch (Exception e) {
					e.printStackTrace();
					Log.d(TAG, "apply deselect sequence because of reselect fail");
					mNativeNfcManager.deselectSecureElement();
					mActiveSe = MySecureElement.getLastSe();/// the last one will always be "OFF"
					isActiveSeChanged = true;
					notifyApplication(NOTIFY_SELECTION_FAIL, null);
				}
			}
			
		} catch (Exception e) {
		
		} finally {
			if (isActiveSeChanged) {
				mContentProviderUtil.setActiveSeString(mActiveSe.mSettingString);
			}			
			
			mContentProviderUtil.setInSwitchingFlag(false);
			Log.d(TAG, "restorePreviousSe EXIT");
		}

		return ret;
	}
	
	public void deinit(boolean deselect) {
		if (mIsMultiSeSupported) {
			Log.d(TAG, "deinit");
			NfcSimStateObserver.getInstance().unregisterSimEventListener();
			NfcSimStateObserver.getInstance().unregisterSdCardEventListener();
			mContentObserverUtil.unregister();
			if (deselect) {
				mNativeNfcManager.deselectSecureElement();
			}
			mContentProviderUtil.setActiveSeString(MySecureElement.getLastSe().mSettingString);
		}
		mContentProviderUtil.setNfcEnabled(false);
	}
	
	private void onActiveSeChangedByUser(String seStr) {
		Log.d(TAG, "onActiveSeChangedByUser, seStr = " + seStr);
		synchronized (mSyncLock) {
			if (seStr.equals(mActiveSe.mSettingString)) {
				Log.d(TAG, "the same se, bypass");
			} else {
				Log.d(TAG, "start SelectTask");
				new Thread(new SelectTask(seStr)).start();
			}
		}
	}	

    public int getActiveSeValue() {

        Log.d(TAG, "getActiveSeValue");
        if (mActiveSe == null)
        {
            Log.d(TAG, "mActiveSe is null");
            return USER_OFF;
        }

        Log.d(TAG, "mActiveSe.mUserSe = " + mActiveSe.mUserSe);
        return mActiveSe.mUserSe;
    }
		
	private class MyContentObserverUtil {
		public MyContentObserverUtil() {
		}
		
		public void register() {
			mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(NFC_MULTISE_ACTIVE), false, mObserver);		
		}
		
		public void unregister() {
			mContext.getContentResolver().unregisterContentObserver(mObserver);
		}
		
		private final ContentObserver mObserver = new ContentObserver(null) {
			@Override
			public void onChange(boolean selfChange, Uri uri) {
				String activeSe = Settings.Global.getString(mContext.getContentResolver(), NFC_MULTISE_ACTIVE);
				Log.d(TAG, "MyContentObserverUtil, onChanged() --> activeSe = " + activeSe + ", selfChange = " + selfChange);
				if (!mActiveSe.isTheSame(activeSe)) {
					/**
					 * 	Here we use the mActiveSe.isTheSame() to distinguish whether this change comes from user.
					 * 	The logic is:
					 * 		If the change doesn't come from user (for example, we cannot find the SE in list during select sequence),
					 *		under this condition, we will sync mActiveSe in the end of select sequence.
					 */
					mContentProviderUtil.setUserDesiredSeString(activeSe);
				}
				onActiveSeChangedByUser(activeSe);
			}
		};

	}
	
	private class MyContentProviderUtil {
		public MyContentProviderUtil() {
			Settings.Global.putInt(mContext.getContentResolver(), NFC_MULTISE_IN_SWITCHING, 0);
			Settings.Global.putInt(mContext.getContentResolver(), NFC_MULTISE_IN_TRANSACTION, 0);
		}
		
		public String getActiveSeString() {
			String se = Settings.Global.getString(mContext.getContentResolver(), NFC_MULTISE_ACTIVE);
			Log.d(TAG, "getActiveSeString(), return = " + se);
			return se;
		}
		
		public void setActiveSeString(String seStr) {
			Log.d(TAG, "setActiveSeString(), seStr = " + seStr);
			Settings.Global.putString(mContext.getContentResolver(), NFC_MULTISE_ACTIVE, seStr);
		}
		
		public String getPreviousActiveSeString() {
			String se = Settings.Global.getString(mContext.getContentResolver(), NFC_MULTISE_PREVIOUS);
			Log.d(TAG, "getPreviousActiveSeString(), return = " + se);
			return se;
		}
		
		public void setPreviousActiveSeString(String prevSeStr) {
			Log.d(TAG, "setPreviousActiveSeString(), prevSeStr = " + prevSeStr);
			Settings.Global.putString(mContext.getContentResolver(), NFC_MULTISE_PREVIOUS, prevSeStr);
		}
				
		public void setAvailableSeList(ArrayList<MySecureElement> seList) {
			String outStr = "";
			boolean isFirst = true;
			for (MySecureElement se : seList) {
				if (!isFirst) {
					outStr += ",";
				}
				outStr += se.mSettingString;
				isFirst = false;
			}

			Log.d(TAG, "setAvailableSeList, outStr = " + outStr);
			Settings.Global.putString(mContext.getContentResolver(), NFC_MULTISE_LIST, outStr);
		}
		
		public void setMultiSeSupport(boolean isMultiSeSupport) {
			Log.d(TAG, "setMultiSeSupport, isMultiSeSupport = " + isMultiSeSupport);			
			Settings.Global.putInt(mContext.getContentResolver(), NFC_MULTISE_ON, isMultiSeSupport ? 1 : 0);
		}
		
		public void setInSwitchingFlag(boolean isInSwitching) {
			Log.d(TAG, "setInSwitchingFlag, isInSwitching = " + isInSwitching);			
			Settings.Global.putInt(mContext.getContentResolver(), NFC_MULTISE_IN_SWITCHING, isInSwitching ? 1 : 0);
		}
		
		public void setNfcEnabled(boolean isNfcEnabled) {
			Log.d(TAG, "setNfcEnabled, isNfcEnabled = " + isNfcEnabled);
			Settings.Global.putInt(mContext.getContentResolver(), NFC_MULTISE_NFC_ENABLED, isNfcEnabled ? 1 : 0);
		}
		
		public void setUserDesiredSeString(String userDesiredSe) {
			Log.d(TAG, "setUserDesiredSeString, userDesiredSe = " + userDesiredSe);
			Settings.Global.putString(mContext.getContentResolver(), NFC_USER_DESIRED_SE, userDesiredSe);
		}
		
		public String getUserDesiredSeString() {
			String se = Settings.Global.getString(mContext.getContentResolver(), NFC_USER_DESIRED_SE);
			Log.d(TAG, "getUserDesiredSeString(), return = " + se);
			return se;
		}

		public void setSeapiSupportCmcc(boolean isSupportCmcc) {
			Log.d(TAG, "setSeapiSupportCmcc, isSupportCmcc = " + isSupportCmcc);			
			Settings.Global.putInt(mContext.getContentResolver(), NFC_SEAPI_SUPPORT_CMCC, isSupportCmcc ? 1 : 0);
		}

		public void setSeapiCmccSimNumber(int cmccSimNumber) {
			Log.d(TAG, "setSeapiCmccSimNumber, cmccSimNumber = " + cmccSimNumber);			
			Settings.Global.putInt(mContext.getContentResolver(), NFC_SEAPI_CMCC_SIM, cmccSimNumber);
		}
	}
	
	private class SelectTask implements Runnable, Handler.Callback {
		static private final int MIN_NOTIFY_APP_TIME = 500;
		static private final int MSG_NOTIFY_APP = 0;
		static private final int MSG_SHOW_DIALOG = 1;
		private String mSe;
		private Handler mHandler;
		private boolean mIsUpdateSeList;
		private int mUpdatedSe;
		
		public SelectTask(String se) {
			mSe = se;
			mHandler = new Handler(mContext.getMainLooper(), this);
		}
		
		public SelectTask(boolean isUpdateSeList, int updatedSe) {
			mIsUpdateSeList = isUpdateSeList;
			mUpdatedSe = updatedSe;
			mHandler = new Handler(mContext.getMainLooper(), this);
		}
		
		public boolean handleMessage(Message msg) {
			if (msg.what == MSG_NOTIFY_APP) {
				mContentProviderUtil.setInSwitchingFlag(false);
				return true;
			} else if (msg.what == MSG_SHOW_DIALOG) {
				showAlternativeSeDialog((String)msg.obj);
				return true;
			}
			return false;
		}
		
		public void run() {
			synchronized (mSyncLock) {
				Log.d(TAG, "SelectTask ENTRY, mIsUpdateSeList = " + mIsUpdateSeList);
				if (mIsUpdateSeList) {
					mNativeNfcManager.disableDiscovery();
					updateSeMapping();
					mNativeNfcManager.enableDiscovery();		
					MySecureElement userDesiredSe = MySecureElement.searchByName(mContentProviderUtil.getUserDesiredSeString());
					if (userDesiredSe.isTheSame(mUpdatedSe) && !mActiveSe.isTheSame(mUpdatedSe)) {
						Log.d(TAG, "SelectTask, desired SE is ready");
						mSe = userDesiredSe.mSettingString;
					} else if (!MySecureElement.isSePresent(mActiveSe.mSettingString)) {
						Log.d(TAG, "SelectTask, active SE is gone");
						mSe = mActiveSe.mSettingString;//it will eventually deselect SE
					} else {
						Log.d(TAG, "SelectTask EXIT");
						return;
					}
				}
				Log.d(TAG, "SelectTask, mSe = " + mSe);
				boolean needToUpdateDb = false;
				MySecureElement candidateSe = null;
				Long startTime = SystemClock.elapsedRealtime();
				try {
					mContentProviderUtil.setInSwitchingFlag(true);

					candidateSe = MySecureElement.searchByName(mSe);
					
					Log.d(TAG, "SelectTask, candidateSe = " + candidateSe.mSettingString);
					
                    if (!candidateSe.mSettingString.equals(mContentProviderUtil.getActiveSeString())) {
                        needToUpdateDb = true;
                    }
					
					if (candidateSe.mUserSe == USER_OFF) {
						mNativeNfcManager.disableDiscovery();
						mNativeNfcManager.deselectSecureElement();
						mCallback.onDeselectByUser(true);
					} else {
						try {
							mNativeNfcManager.disableDiscovery();
							mNativeNfcManager.deselectSecureElement();
							if (mNativeNfcManager.selectSecureElementById(candidateSe.mChipSe)) {
								Log.d(TAG, "SelectTask, success");
                                mCallback.onSelectByUser();
							} else {
								throw new Exception("selectSecureElement fail");
							}
						} catch (Exception e) {
							Log.d(TAG, "SelectTask, failure");
							needToUpdateDb = true;
							candidateSe = MySecureElement.getLastSe();/// last se should be "OFF"
							mNativeNfcManager.deselectSecureElement();
							mCallback.onDeselectByUser(false);
							notifyApplication(NOTIFY_SELECTION_FAIL, null);
						} finally {
							mNativeNfcManager.enableDiscovery();
						}
					}
					
				} catch (Exception e) {
				
				} finally {					
					/**
					 *	Since the "previous active se" is used by Setting when it turns card emulation on, the value should never be OFF. 
					 * 	If user sets a non-Off SE, it should also be the previous active SE.
					 *	If user sets SE to OFF, we use the current active SE to be the previous active SE.
					 *	Otherwise, we set to default.
					 */
					if (!MySecureElement.getLastSe().isTheSame(mSe)) {
						mContentProviderUtil.setPreviousActiveSeString(mSe);
					} else if (!MySecureElement.getLastSe().isTheSame(mActiveSe)) {
						mContentProviderUtil.setPreviousActiveSeString(mActiveSe.mSettingString);
					} else {
						mContentProviderUtil.setPreviousActiveSeString(DEFAULT_SE_NAME);
					}
				
					mActiveSe = candidateSe;

					if (needToUpdateDb) {
						mContentProviderUtil.setActiveSeString(candidateSe.mSettingString);
					}
					
					Long duration = SystemClock.elapsedRealtime() - startTime;
					if (duration > MIN_NOTIFY_APP_TIME) {
						mContentProviderUtil.setInSwitchingFlag(false);
					} else {
						mHandler.sendEmptyMessageDelayed(MSG_NOTIFY_APP, MIN_NOTIFY_APP_TIME - duration);
					}
					
					/**
					 *	if user want to activate some SE, but finally we set active SE as OFF.
					 *	This means that "the SE wanted by user is currently not available".
					 */
					if (!MySecureElement.getLastSe().isTheSame(mSe) && 
						MySecureElement.getLastSe().isTheSame(mActiveSe)) {
						mHandler.sendMessage(Message.obtain(mHandler, MSG_SHOW_DIALOG, mSe));
					}
					Log.d(TAG, "SelectTask EXIT");
					notifySmartcardSerive();
				}
			}
		}
		
	}
		
	private void constructSeMapping() {
		Log.d(TAG, "constructSeMapping");

        mConfigFileParser = NfcRuntimeOptions.getParser();
		
		updateSeMapping();
	}
	
	public void checkSettingStrLanguage() {
		Log.d(TAG, "checkSettingStrLanguage");
		String str;
		
		str = mContentProviderUtil.getActiveSeString();
		if (str != null) {
			mContentProviderUtil.setActiveSeString(MySecureElement.switchStrLanguage(mContext, str));
		}

		str = mContentProviderUtil.getPreviousActiveSeString();
		if (str != null) {
			mContentProviderUtil.setPreviousActiveSeString(MySecureElement.switchStrLanguage(mContext, str));
		}

		str = mContentProviderUtil.getUserDesiredSeString();
		if (str != null) {
			mContentProviderUtil.setUserDesiredSeString(MySecureElement.switchStrLanguage(mContext, str));
		}
		
	}
		
	private void updateSeMapping() {
		Log.d(TAG, "updateSeMapping");
		checkSettingStrLanguage();
		MySecureElement.resetString(mContext);
		MySecureElement.clearSeList();
		
		/// Step1. retrieve se mapping
		int chipSeList[] = mNativeNfcManager.getSecureElementList();
		HashMap<Integer, Integer> chipSeTypeIdMap = new HashMap<Integer, Integer>();
		if (chipSeList != null) {
			for (int i = 0;i < chipSeList.length; i+=2) {
				chipSeTypeIdMap.put(chipSeList[i], chipSeList[i+1]);
			}
		}
		
		/// Step2. link the user/chip define
		int userConfig[] = new int[1];
		int userSe = 0;
		int chipSe = 0;
		/// check CFG_FILE_RULES for the defined value:
		/// 	"SWP1=1:SIM1=1,SIM2=2",
		/// 	"SWP2=2:SIM1=1,SIM2=2",
		/// 	"SD=3:NO=0,YES=1",
		/// 	"ESE=4:NO=0,YES=1",

		boolean isSwpSim1Alarm = false, isSwpSim2Alarm = false;
		boolean isBundleSimState = NfcRuntimeOptions.isBundleSimState();
		if (mConfigFileParser.get(1, userConfig)) {/// SWP1
			try {
				if (userConfig[0] == 1) {/// SIM1
					userSe = USER_SIM1;
				} else if (userConfig[0] == 2) {/// SIM2
					userSe = USER_SIM2;				
				} else {
					Log.d(TAG, "wrong value in nfcse.cfg");
					userSe = USER_OFF;
				}
				if (userSe != USER_OFF) {
					chipSe = chipSeTypeIdMap.get(CHIP_SWP1);				
					MySecureElement.addSe(userSe, chipSe, mContext, isBundleSimState);
				}
			} catch (Exception e) {
				Log.d(TAG, "CHIP_SWP1 not hit in current SE list");
				/// Not found in NFC controller's list, but found in host's list.
				/// This means that the UICC is not a NFC SIM.
				if (userSe == USER_SIM1) {
					isSwpSim1Alarm = true;
				} else if (userSe == USER_SIM2) {
					isSwpSim2Alarm = true;
				}
			}			
		}
		
		if (mConfigFileParser.get(2, userConfig)) {/// SWP2
			try {
				if (userConfig[0] == 1) {/// SIM1
					userSe = USER_SIM1;				
				} else if (userConfig[0] == 2) {/// SIM2
					userSe = USER_SIM2;				
				} else if (userConfig[0] == 3) {/// ESE
					userSe = USER_ESE;
				} else {
					Log.d(TAG, "wrong value in nfcse.cfg");
					userSe = USER_OFF;
				}
				if (userSe != USER_OFF) {
					chipSe = chipSeTypeIdMap.get(CHIP_SWP2);
					MySecureElement.addSe(userSe, chipSe, mContext, isBundleSimState);
				}
			} catch (Exception e) {
				Log.d(TAG, "CHIP_SWP2 not hit in current SE list");
				if (userSe == USER_SIM1) {
					isSwpSim1Alarm = true;
				} else if (userSe == USER_SIM2) {
					isSwpSim2Alarm = true;
				}
			}
		}

		int userConfigSsd[] = new int[1];
		int userConfigEse[] = new int[1];
		if (mConfigFileParser.get(3, userConfigSsd) && mConfigFileParser.get(4, userConfigEse)){
			if (userConfigSsd[0] == 1 && userConfigEse[0] == 1){
				Log.e(TAG, "The config is wrong");
			}else{ 
				Log.i(TAG, "userConfigSsd[0] = " + userConfigSsd[0] + ", userConfigEse[0] = " + userConfigEse[0]);
				if (userConfigSsd[0] == 1) {/// YES
					try {
						chipSe = chipSeTypeIdMap.get(CHIP_SSD_ESE);
						MySecureElement.addSe(USER_SSD, chipSe, mContext, isBundleSimState);
						Log.i(TAG, "SSD is Found");
					} catch (Exception e) {
						Log.d(TAG, "CHIP_SSD not hit in current SE list");
					}
				}

				if (userConfigEse[0] == 1) {/// YES
					try {
						chipSe = chipSeTypeIdMap.get(CHIP_SSD_ESE);
						MySecureElement.addSe(USER_ESE, chipSe, mContext, isBundleSimState);
						Log.i(TAG, "eSE is Found");
					} catch (Exception e) {
						Log.d(TAG, "CHIP_ESE not hit in current SE list");
					}
				}
			}
		}		
		Log.d(TAG, "isSwpSim1Alarm = " + isSwpSim1Alarm + ", isSwpSim2Alarm = " + isSwpSim2Alarm);
		mIsSwpSim1Alarm = isSwpSim1Alarm;
		mIsSwpSim2Alarm = isSwpSim2Alarm;
		checkAllSwpSimDIalog(false);
		
		/// Always add a OFF in the end of list		
		MySecureElement.addSe(USER_OFF, CHIP_OFF, mContext, isBundleSimState);
        
        /// force to make new cache
        MySecureElement.refresh();
		
		/// Notify application the SE list has been updated
		mContentProviderUtil.setAvailableSeList(MySecureElement.getAvailableSeList());

	}
	
	private void checkAllSwpSimDIalog(boolean isTimeout){

		if (NfcRuntimeOptions.isSupportNonNfcSimPopup() == false){
			return;
		}

		if (mShouldSimWarningDialogPopup[0] == false || mShouldSimWarningDialogPopup[1] == false) {
			Log.i(TAG, "The dialog has been displayed");
			return;
		}
		
		Log.i(TAG, "checkSwpSimDIalog isTimeout = " + isTimeout);

		if (FeatureOption.MTK_GEMINI_SUPPORT) {
			if (mIsSwpSim1Alarm == true && mIsSwpSim2Alarm == true) {
				if (isTimeout == false) {
					if (NfcSimStateObserver.getInstance().isSimReady(PhoneConstants.GEMINI_SIM_1) == false ||
						NfcSimStateObserver.getInstance().isSimReady(PhoneConstants.GEMINI_SIM_2) == false) { 
						Log.d(TAG, "SIM1 or SIM2 is not ready");
						return;						
					}else {
						notifyApplication(NOTIFY_SIM1_SIM2_NOT_NFC, null);
						return;
					}
				}else {
					Log.d(TAG, "Timeout");
					checkSingleSimDialog();
				}
			}else{
				Log.d(TAG, "one is SWPSIM, another is not");
				checkSingleSimDialog();
			}
		}else {
			Log.d(TAG, "single SIM");
			checkSingleSimDialog();
		}

	}

	private void checkSingleSimDialog() {
		Log.i(TAG, "checkSingleSimDialog mIsSwpSim1Alarm = " + mIsSwpSim1Alarm + " mIsSwpSim2Alarm = " + mIsSwpSim2Alarm);
		
		if(mIsSwpSim1Alarm == true) {
			if (NfcSimStateObserver.getInstance().isSimReady(PhoneConstants.GEMINI_SIM_1) == false) { 
				Log.d(TAG, "SIM1 are not ready");					
			}else{
				notifyApplication(NOTIFY_SIM1_NOT_NFC, MySecureElement.SETTING_STRs[USER_SIM1]);
				return;	
			}		
		}

		if(mIsSwpSim2Alarm == true) {
			if (NfcSimStateObserver.getInstance().isSimReady(PhoneConstants.GEMINI_SIM_2) == false) {
				Log.d(TAG, "SIM2 are not ready");				
			}else {
				notifyApplication(NOTIFY_SIM2_NOT_NFC, MySecureElement.SETTING_STRs[USER_SIM2]);
			}
		}
	}
		
	private void onSeRefresh(int updatedSe, int event) {
		Log.d(TAG, "onSeRefresh, updatedSe = " + updatedSe + ", event = " + event);
		new Thread(new SelectTask(true, updatedSe)).start();
	}
	
	private void notifyApplication(int type, Object obj) {
		if (type == NOTIFY_SELECTION_FAIL) {
			Intent intent = new Intent(ACTION_FAIL_TO_SELECT_SE);
			intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(intent);
		} else if (type == NOTIFY_SIM1_NOT_NFC || type == NOTIFY_SIM2_NOT_NFC) {
			Log.d(TAG, "NOTIFY_SIM1_NOT_NFC or NOTIFY_SIM2_NOT_NFC");
			if (NfcRuntimeOptions.isSupportNonNfcSimPopup() && mShouldSimWarningDialogPopup[(type == NOTIFY_SIM1_NOT_NFC ? 0 : 1)]) {
				Intent intent = new Intent(ACTION_NOT_NFC_SIM);
				intent.putExtra(EXTRA_WHAT_SIM, (String) obj);
				intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
				mContext.startActivity(intent);
				mShouldSimWarningDialogPopup[(type == NOTIFY_SIM1_NOT_NFC ? 0 : 1)] = false;
			}
		} else if (type == NOTIFY_SIM1_SIM2_NOT_NFC){
		    Log.d(TAG, "NOTIFY_SIM1_NOT_NFC and NOTIFY_SIM2_NOT_NFC");
			if (NfcRuntimeOptions.isSupportNonNfcSimPopup() && 
				mShouldSimWarningDialogPopup[0] && 
				mShouldSimWarningDialogPopup[1]) {

				Intent intent = new Intent(ACTION_NOT_NFC_TWO_SIM);
				intent.putExtra(EXTRA_WHAT_SIM, (String) obj);
				intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
				mContext.startActivity(intent);
				mShouldSimWarningDialogPopup[0] = false;
				mShouldSimWarningDialogPopup[1] = false;
			}		
		}
	}
	
    private void showAlternativeSeDialog(String originalSe) {
		String message;
		String firstSe;
		if (MySecureElement.getFirstSe().isTheSame(MySecureElement.getLastSe())) {
			message = mContext.getString(R.string.mtk_se_na);
			firstSe = "";
		} else {			
			message = originalSe + mContext.getString(R.string.mtk_se_alt) + MySecureElement.getFirstSe().mSettingString + "?";
			firstSe = MySecureElement.getFirstSe().mSettingString;
		}
			
		Intent intent = new Intent(mContext, SecureElementConfirmActivity.class);
		intent.putExtra(SecureElementConfirmActivity.EXTRA_TITLE, mContext.getString(R.string.mtk_nfc_card_emulation));
		intent.putExtra(SecureElementConfirmActivity.EXTRA_MESSAGE, message);
		intent.putExtra(SecureElementConfirmActivity.EXTRA_FIRSTSE, firstSe);
		intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
		mContext.startActivity(intent);		
    }
	
	static class MySecureElement {
		static private ArrayList<MySecureElement> sSeList = new ArrayList<MySecureElement>();
		static private String SETTING_STRs[] = {SETTING_STR_OFF, SETTING_STR_SIM1, SETTING_STR_SIM2, SETTING_STR_SSD, SETTING_STR_ESE};

		static public String switchStrLanguage(Context context, String str) {
			
			if (str.contains(SETTING_STRs[USER_SSD])) {				
				Log.d(TAG, "MySecureElement.switchSettingStrLanguage(), " + str + " switch to " + context.getString(R.string.mtk_se_ssd));				
				str = context.getString(R.string.mtk_se_ssd);
			} else if (str.contains(SETTING_STRs[USER_ESE])) {			
				Log.d(TAG, "MySecureElement.switchSettingStrLanguage(), " + str + " switch to " + context.getString(R.string.mtk_se_ese));
				str = context.getString(R.string.mtk_se_ese);
			}

			return str;
		}
		
		static public void resetString(Context context) {
			SETTING_STRs[USER_SSD] = context.getString(R.string.mtk_se_ssd);
			SETTING_STRs[USER_ESE] = context.getString(R.string.mtk_se_ese);
		}

		static public void refresh() {
			Log.d(TAG, "MySecureElement.refresh()");
			ArrayList<MySecureElement> newList = new ArrayList<MySecureElement>();
			for (MySecureElement se : sSeList) {
				if (se.mIsReady) {
					newList.add(se);
				}                
			}
			sSeList = newList;
		}
		
		static public ArrayList<MySecureElement> getAvailableSeList() {
			return sSeList;
		}
		
		static public boolean isSePresent(String seStr) {
			for (MySecureElement se: sSeList) {
				if (se.mSettingString.equals(seStr)) {
					return true;
				}
			}
			return false;
		}
		
		static public MySecureElement searchByName(String seStr) {
			return searchByName(seStr, false, true);
		}
		
		static public MySecureElement searchByName(String seStr, boolean exactlyMatch, boolean selectOffWhenMiss) {
			Log.d(TAG, "MySecureElement.searchByName(), arg = " + seStr);
			MySecureElement ret = null;
			for (MySecureElement se: sSeList) {
				if (exactlyMatch) {
					if (se.mSettingString.equals(seStr)) {
						ret = se;
						break;
					}
				} else {
					if (se.mSettingString.contains(seStr) || seStr.contains(se.mSettingString)) {
						ret = se;
						break;
					}
				}
			}
			if (ret == null) {
				ret = selectOffWhenMiss ? getLastSe() : getFirstSe();
			}
			Log.d(TAG, "MySecureElement.searchByName(), ret = " + ret.mSettingString);
			return ret;
		}
		
		static public MySecureElement getFirstSe() {
			return sSeList.get(0);
		}
		
		static public MySecureElement getLastSe() {
			return sSeList.get(sSeList.size() - 1);/// the last one will always be "OFF"
		}
		
		static public void clearSeList() {
			sSeList.clear();
		}
		
		static public void addSe(int userSe, int chipSe, Context context, boolean isBundleSimState) {
			sSeList.add(new MySecureElement(userSe, chipSe, context, isBundleSimState));
		}        

		public int mUserSe;
		public int mChipSe;
		public String mSettingString;
		public boolean mIsReady;
		
		private MySecureElement(int userSe, int chipSe, Context context, boolean isBundleSimState) {
			mUserSe = userSe;
			mChipSe = chipSe;
			mSettingString = getSettingString(mUserSe, context);
			if (mSettingString.equals("")) {
				if (!isBundleSimState && (userSe == USER_SIM1 || userSe == USER_SIM2)) {
					mSettingString = SETTING_STRs[userSe];
					mIsReady = true;
					Log.d(TAG, "allow card mode in airplane, mSettingString = " + mSettingString);
				} else {
					mIsReady = false;
				}
			} else {
				mIsReady = true;
			}
			Log.d(TAG, "new MySecureElement: userSe = " + mUserSe + ", chipSe = " + mChipSe + ", string = " + mSettingString);
		}

		static public String getSettingString(int userSe, Context context) {
			if (userSe == USER_OFF || userSe == USER_SSD || userSe == USER_ESE) {
				return SETTING_STRs[userSe];
			} else {				
				String settingString = "";
				try {
					// ITelephony manager = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
					int simId = -1;
					if (userSe == USER_SIM1) {
						simId = PhoneConstants.GEMINI_SIM_1;
					} else if (userSe == USER_SIM2) {
						simId = PhoneConstants.GEMINI_SIM_2;
					}

					if ((!NfcSimStateObserver.getInstance().isSimReady(simId))) {
						throw new Exception("SIM state isn't ready");
					}

					/// SimInfoRecord info = SimInfoManager.getSimInfoBySlot(context, simId);
					/// settingString = info.mDisplayName;
					settingString = null;
					
					if (settingString == null || settingString.length() == 0) {
						settingString = SETTING_STRs[userSe];
					} else {
						settingString = SETTING_STRs[userSe] + ": " + settingString;
					}
				} catch (Exception e) {
					Log.d(TAG, "exception: " + e.getMessage());
				} finally {
					return settingString;
				}
			}			
		}
		
		public boolean isTheSame(int userSe) {
			return mUserSe == userSe;
		}
		
		public boolean isTheSame(String seString) {
			return mSettingString.equals(seString);
		}

		public boolean isTheSame(MySecureElement se) {
			return mSettingString.equals(se.mSettingString);
		}
	}
	
}
