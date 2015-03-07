
package com.android.keyguard;

import android.app.Service;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException ;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.mediatek.common.featureoption.FeatureOption;

import com.mediatek.common.dm.DmAgent;
import com.mediatek.common.ppl.IPplAgent;
import com.mediatek.common.ppl.IPplManager;


public class AntiTheftManager {

    // The SMALLER value represents the HIGHER priority.
    // Ex: DmLock (value = 1) is more important than PplLock(value = 2), 
    //       so we will use DmLock even if both type of locks exist.
    public static class AntiTheftMode {
        public static final int None = 0 ; 
        public static final int DmLock = 1 << 0;
        public static final int PplLock = 1 << 1;
    }
	
    private final static String TAG = "AntiTheftManager";

    private static Context mContext;
    private KeyguardViewMediator mKeyguardViewMediator;
    private LockPatternUtils mLockPatternUtils;
    private static AntiTheftManager sInstance;

	public static final String RESET_FOR_ANTITHEFT_LOCK = "antitheftlock_reset";
	private static final int MSG_ANTITHEFT_KEYGUARD_UPDATE = 1001;    

    // Each bit represents the related AntiTheft lock is LOCKED or NOT.
    // Bit value -- 1 : locked. 0 : not locked
    private static int mAntiTheftLockEnabled = 0 ;
    // Each bit represents the related AntiTheft lock NEEDS KEYPAD or NOT.
    // Bit value -- 1 : needs. 0 : no need
    private static int mKeypadNeeded = 0 ;
    // Each bit represents the related AntiTheft lock can be DISMISSED or NOT.
    // Bit value -- 1 : can be dismissed. 0 : cannot be dismissed    
    private static int mDismissable = 0 ;

	/**
     * Construct a AntiTheftManager
     * @param context
     * @param lockPatternUtils optional mock interface for LockPatternUtils
     */
    public AntiTheftManager(Context context, KeyguardViewMediator keyguardViewMediator,
                                        LockPatternUtils lockPatternUtils) {
        Log.d(TAG, "AntiTheftManager() is called.") ;
    
        mContext = context;
        mKeyguardViewMediator = keyguardViewMediator;
        mLockPatternUtils = lockPatternUtils;

	    IntentFilter filter = new IntentFilter();

		// set up DM Lock properties
		setKeypadNeeded(AntiTheftMode.DmLock, false) ;
		setDismissable(AntiTheftMode.DmLock, false) ;
		filter.addAction(OMADM_LAWMO_LOCK);
        filter.addAction(OMADM_LAWMO_UNLOCK);

		// set up pPhonePrivacy Lock(PPL) properties
		if(FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {		
            Log.d(TAG, "MTK_PRIVACY_PROTECTION_LOCK is enabled.") ;
			setKeypadNeeded(AntiTheftMode.PplLock, true) ;
			setDismissable(AntiTheftMode.PplLock, true) ;			
			filter.addAction(PPL_LOCK) ;
			filter.addAction(PPL_UNLOCK) ;
		}
		
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    // Use singleton to make sure that only one AntiTheftManager obj existed in the system.
    public static AntiTheftManager getInstance(Context context, 
                       KeyguardViewMediator keyguardViewMediator, LockPatternUtils lockPatternUtils) {
        Log.d(TAG, "getInstance(...) is called.") ;                
        if (sInstance == null) {
            Log.d(TAG, "getInstance(...) create one.") ;
            sInstance = new AntiTheftManager(context, keyguardViewMediator, lockPatternUtils);
        }
        return sInstance;
    }

    public static String getAntiTheftModeName(final int mode) {
		switch(mode) {
			case AntiTheftMode.None:
				return "AntiTheftMode.None" ;
			case AntiTheftMode.DmLock:
				return "AntiTheftMode.DmLock" ;
			case AntiTheftMode.PplLock:
				return "AntiTheftMode.PplLock" ;	
		}

		return "AntiTheftMode.None" ;
	}

	// Search from HIGHEST priority lock to LOWEST priority lock.
	// Return the HIGHEST priority lock as the current mode.
	public static int getCurrentAntiTheftMode() {
		int shift = 0 ;

		Log.d(TAG, "getCurrentAntiTheftMode() is called.") ;

		if(!isAntiTheftLocked())
			return AntiTheftMode.None;
		
		while(shift < 32) {
			int mode = mAntiTheftLockEnabled & (1 << shift) ;
			if(mode != AntiTheftMode.None) {
				return mode ; 
			}
			shift++ ;
		}

		return AntiTheftMode.None ;
	}

	public static boolean isKeypadNeeded() {
		final int mode = getCurrentAntiTheftMode() ;
		Log.d(TAG, "getCurrentAntiTheftMode() = " + getAntiTheftModeName(mode)) ;
		
		final boolean needKeypad = (mKeypadNeeded & mode) != 0 ;
		Log.d(TAG, "isKeypadNeeded() = " + needKeypad) ;

		return needKeypad;		
	}

	public static void setKeypadNeeded(final int lockMode, boolean need) {
		if(need) {
        	mKeypadNeeded |= lockMode;
		}
		else {
			mKeypadNeeded &= ~lockMode;
		}
	}

	public static boolean isAntiTheftLocked() {
		return mAntiTheftLockEnabled != 0 ;
	}

    public static void setAntiTheftLocked(final int lockMode, boolean enable) {
		if(enable) {
        	mAntiTheftLockEnabled |= lockMode;
		}
		else {
			mAntiTheftLockEnabled &= ~lockMode;
		} 
    }

	public static boolean isDismissable() {
		final int mode = getCurrentAntiTheftMode() ;
		boolean dismissAble = false ;

		if(mode == AntiTheftMode.None) {
			dismissAble = true ;
		}
		else {
			if ((mode & mDismissable) != 0) {
				dismissAble = true ;
			}
		}

		Log.d(TAG, "mode = " + mode + ", dismiss = " + dismissAble) ;
		
		return dismissAble ;
	}

    public static void setDismissable(final int lockMode, boolean canBeDismissed) {
		Log.d(TAG,"mDismissable is " + mDismissable + " before") ;		
		if(canBeDismissed) {
        	mDismissable |= lockMode;
		}
		else {
			mDismissable &= ~lockMode;
		}

		Log.d(TAG,"mDismissable is " + mDismissable + " after") ;
    }

    // Some security views have to show at first even if AntiTheft Lock is activated.
    // We do some checks here -- is current highest priority anti-theft lock has higher priority than "KeyguardSecurityModel.SecurityMode mode"?
    // If yes, return ture and the Keyguard will show the AntiTheftLockView.
	public static boolean isCurrentAntiTheftShouldShowBefore(KeyguardSecurityModel.SecurityMode mode) {
		final int currentAntiTheftMode = getCurrentAntiTheftMode() ;
		boolean showBefore = false ;

		Log.d(TAG, "isCurrentAntiTheftShouldShowBefore(mode = " + mode + "), currentAntiTheft = " + currentAntiTheftMode) ;

		if (isAntiTheftLocked()) {
			if (mode == KeyguardSecurityModel.SecurityMode.None) {
				showBefore = true ;
			}
			else {
				switch(mode) {
					case SimPinPukMe1:
					case SimPinPukMe2:
					case SimPinPukMe3:						
					case SimPinPukMe4:						
					case AlarmBoot:
						if(currentAntiTheftMode == AntiTheftMode.DmLock) {
							//only higher priority AntiThefLock can show before.
							showBefore = true ;
						}
						break;
					default:
						showBefore = true ;
						break;
				}
			}
		}

		return showBefore ;
	}

    // Reource related APIs
	public static int getAntiTheftViewId() {
		return R.id.keyguard_antitheft_lock_view;
	}

	public static int getAntiTheftLayoutId() {
		return R.layout.mtk_keyguard_anti_theft_lock_view;
	}

	public static int getPrompt() {
		int mode = getCurrentAntiTheftMode() ;

		if(mode == AntiTheftMode.DmLock) {
				return R.string.dm_prompt;
		}
		else {
				return R.string.ppl_prompt;
		}			
	}   

	// If possible, refine this part to eliminate the coupling between Keyguard modules. -- start
	private static boolean mKeyguardIsAntiTheftModeNow = false ;
	public static void setKeyguardCurrentModeIsAntiTheftMode(boolean isAntiTheft) {
		Log.d(TAG, "setKeyguardCurrentModeIsAntiTheftMode is " + isAntiTheft) ;
		mKeyguardIsAntiTheftModeNow = isAntiTheft ;        
    }
    
	public static boolean isKeyguardCurrentModeAntiTheftMode() {
		Log.d(TAG, "mKeyguardIsAntiTheftModeNow is " + mKeyguardIsAntiTheftModeNow) ;
		return mKeyguardIsAntiTheftModeNow ;
	}
    // If possible, refine this part to eliminate the coupling between Keyguard modules. -- end
    
	public static boolean checkPassword(String pw) {		
		boolean unlockSuccess = false ;        
        final int mode = getCurrentAntiTheftMode() ;

        Log.d(TAG, "checkPassword, mode is " + getAntiTheftModeName(mode)+ "pw is " + pw) ;
        
        switch(mode) {
            case AntiTheftMode.PplLock:
                unlockSuccess = doPplCheckPassword(pw) ;
                break ;
            default:
                break ;
        }						

        Log.d(TAG, "checkPassword, unlockSuccess is " + unlockSuccess) ;

		return unlockSuccess ;
		
	}

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

			/// M: DM Begin @{
            if (OMADM_LAWMO_LOCK.equals(action)) {				
                Log.d(TAG, "receive OMADM_LAWMO_LOCK");
				setAntiTheftLocked(AntiTheftMode.DmLock, true);
				
                Message msg = mHandler.obtainMessage(MSG_ANTITHEFT_KEYGUARD_UPDATE);
                msg.arg1 = 1;
                msg.sendToTarget();
            } else if (OMADM_LAWMO_UNLOCK.equals(action)) {
                Log.d(TAG, "receive OMADM_LAWMO_UNLOCK");
				setAntiTheftLocked(AntiTheftMode.DmLock, false);
				
                Message msg = mHandler.obtainMessage(MSG_ANTITHEFT_KEYGUARD_UPDATE);
                msg.arg1 = 0;
                msg.sendToTarget();               
            }
			/// DM end @}

			/// M: PPL Begin @{
            else if (PPL_LOCK.equals(action)) {				
                Log.d(TAG, "receive PPL_LOCK");
				setAntiTheftLocked(AntiTheftMode.PplLock, true);                
				
                Message msg = mHandler.obtainMessage(MSG_ANTITHEFT_KEYGUARD_UPDATE);
                msg.arg1 = 1;
                msg.sendToTarget();
            } else if (PPL_UNLOCK.equals(action)) {
                Log.d(TAG, "receive PPL_UNLOCK");
				setAntiTheftLocked(AntiTheftMode.PplLock, false);
				
                Message msg = mHandler.obtainMessage(MSG_ANTITHEFT_KEYGUARD_UPDATE);
                msg.arg1 = 0;
                msg.sendToTarget();               
            }
			/// PPL end @}
        }
    };

    private Handler mHandler = new Handler(Looper.myLooper(), null, true) {
		private String getMessageString(Message message) {
            switch (message.what) {				
                case MSG_ANTITHEFT_KEYGUARD_UPDATE:
                    return "MSG_ANTITHEFT_KEYGUARD_UPDATE";     
            }
            return null;
        }
		
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage enter msg name=" + getMessageString(msg));
            switch (msg.what) {
                case MSG_ANTITHEFT_KEYGUARD_UPDATE:
                    handleAntiTheftViewUpdate(msg.arg1 == 1);
                    break;				
            }
            Log.d(TAG, "handleMessage exit msg name=" + getMessageString(msg));
        }
    };
    
    private void handleAntiTheftViewUpdate(boolean lock) {
        Log.d(TAG, "handlePrivacyProtectionKeyguardUpdate lock=" + lock);
        if(lock) {
            if (!mKeyguardViewMediator.isShowing()) {            
                mKeyguardViewMediator.showLocked(null);
            } else {
                /// M: If anti-theft lock cause reset, tell KeyguardViewManager to force reset
                Bundle option = new Bundle();
                option.putBoolean(RESET_FOR_ANTITHEFT_LOCK, true);
                mKeyguardViewMediator.resetStateLocked(option);                
            }
        }
        else {
            /// M: ALPS01370779 refinement -- use dismiss() instead of resetStateLocked().
            mKeyguardViewMediator.dismiss() ;
            mKeyguardViewMediator.adjustStatusBarLocked() ;
        }
    }

     public void doBindAntiThftLockServices() {
        Log.d(TAG, "doBindAntiThftLockServices() is called.") ;
        
        if(FeatureOption.MTK_PRIVACY_PROTECTION_LOCK) {
            bindPplService() ;
        }
    }

    public void doAntiTheftLockCheck() {
		doDmLockCheck() ;		
	}

    public static int getHideStatusBarIconFlags() {
        Log.d(TAG, "hideStatusBarIcons() is called.");
        
        int flags = StatusBarManager.DISABLE_NONE;
        if(isKeyguardCurrentModeAntiTheftMode() && isAntiTheftLocked()) {           
           flags = StatusBarManager.DISABLE_EXPAND | StatusBarManager.DISABLE_NOTIFICATION_ALERTS
                    | StatusBarManager.DISABLE_NOTIFICATION_ICONS
                    | StatusBarManager.DISABLE_NOTIFICATION_TICKER;
           Log.d(TAG, "Since there is at least one AntiTheftLock is activated, we hide the no-need icons on statusbar flag = " + Integer.toHexString(flags)) ;
        }

        return flags ;
    }

	/// M: DM begin @{
	public static final String OMADM_LAWMO_LOCK = "com.mediatek.dm.LAWMO_LOCK";
    public static final String OMADM_LAWMO_UNLOCK = "com.mediatek.dm.LAWMO_UNLOCK";

	private static void doDmLockCheck() {
        try {
            //for OMA DM
            IBinder binder = ServiceManager.getService("DmAgent");
            if (binder != null) {
                DmAgent agent = DmAgent.Stub.asInterface(binder);
                boolean flag = agent.isLockFlagSet();
                Log.i(TAG,"dmCheckLocked, the lock flag is:" + flag);
                setAntiTheftLocked(AntiTheftMode.DmLock, flag);
            } else {
                Log.i(TAG,"dmCheckLocked, DmAgent doesn't exit");
            }
        } catch (Exception e) {
            Log.e(TAG,"get DM status failed!");
        }   
    }	    
   
	/// M: PhonePrivacyLock(PPL) begin @{
	public static final String PPL_LOCK = "com.mediatek.ppl.NOTIFY_LOCK";
    public static final String PPL_UNLOCK = "com.mediatek.ppl.NOTIFY_UNLOCK";   
    private static IPplManager mIPplManager ;

    private ServiceConnection mPplServiceConnection = new ServiceConnection() {  		  
        @Override  
        public void onServiceConnected(ComponentName name, IBinder service) {  
            // TODO Auto-generated method stub	          
            Log.i(TAG, "onServiceConnected() -- PPL");	       
	        
            //iBinder = service ;
            mIPplManager = IPplManager.Stub.asInterface(service) ;	   
	    }  
	  
        @Override  
        public void onServiceDisconnected(ComponentName name) {  
	        // TODO Auto-generated method stub  
	        Log.i(TAG, "onServiceDisconnected()");  
            mIPplManager = null ;
	    }  
    } ;

    private void bindPplService() {
        Log.e(TAG,"binPplService() is called.");   

        if(mIPplManager == null) {
            try {
                Intent intent = new Intent("com.mediatek.ppl.service") ;
                mContext.bindService(intent, mPplServiceConnection,  Context.BIND_AUTO_CREATE) ;            
            } catch (Exception e) {
                Log.e(TAG,"doPplCheckPassword() get PPL status failed!");
            }
        }
        else {
            Log.d(TAG, "bindPplService() -- the ppl service is already bound.") ;
        }
    }   

    private static boolean doPplCheckPassword(String pw) {
        boolean unlockSuccess = false ;

        if(mIPplManager != null) {        
            try {               
                unlockSuccess = mIPplManager.unlock(pw);
                Log.i(TAG,"doPplCheckPassword, unlockSuccess is " + unlockSuccess);      

                if(unlockSuccess) {
                    //clear ppl lock
                    setAntiTheftLocked(AntiTheftMode.PplLock,false);
                }
            }
            catch(RemoteException e) {
                //just test, we don't handle it.
            }
        }
        else {
            Log.i(TAG, "doPplCheckPassword() mIPplManager == null !!??") ;
        }

        return unlockSuccess ;
    }

    public void adjustStatusBarLocked() {
        mKeyguardViewMediator.adjustStatusBarLocked() ;
    }
}
