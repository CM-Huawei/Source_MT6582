package com.mediatek.nfc.gsmahandset;


import java.util.LinkedList;
import java.util.List;

import java.io.File;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;

import android.content.ComponentName;
import android.content.ServiceConnection;


//import android.media.MediaScannerConnection;
import android.net.Uri;


import android.nfc.NfcAdapter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import android.os.IBinder;

//import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.RemoteException;

import android.util.Log;

import java.io.UnsupportedEncodingException;




import android.provider.MediaStore;
import android.database.Cursor;




import org.simalliance.openmobileapi.service.CardException;
import org.simalliance.openmobileapi.service.ISmartcardService;
import org.simalliance.openmobileapi.service.ISmartcardServiceCallback;
import org.simalliance.openmobileapi.service.SmartcardError;



/**
 * EvtTransactionHandle of GSAM NFCHandset
 */
public class EvtTransactionHandle {


    static final String TAG = "EvtTransactionHandle";
    static final boolean DBG = true;

    final Context mContext;

    private static EvtTransactionHandle mStaticInstance = null;
    private boolean multiBroadcastFlag = false;

    /**
     * This implementation is used to receive callbacks from backend.
     */
    private final ISmartcardServiceCallback mCallback = new ISmartcardServiceCallback.Stub() {
    };

    public EvtTransactionHandle(Context context) {
        mContext = context;
        Log.i(TAG, " EvtTransactionHandle Construct ");

        AccessCheckImpl.createSingleton(context);
    }

    public static void createSingleton(Context context) {
        if (mStaticInstance == null) {
            mStaticInstance = new EvtTransactionHandle(context); 
        }
    }

    public static EvtTransactionHandle getInstance() {
        return mStaticInstance;
    }

    public boolean processEvt(byte[] aid){
        Log.i(TAG, " processEvt() ");

    	List<ResolveInfo> interestedInfos;
    	List<ResolveInfo> acPassedInfos;
    	Intent composeIntent;
        String activeSe;

    	String aidString = bytesToString(aid);
    	Log.i(TAG, "aidString: "+aidString);

        if(aidString.isEmpty()){
    		Log.i(TAG, " aidString.isEmpty ,return false");
    		return false;
        }            

        composeIntent = AccessCheckImpl.getInstance().composeNewIntent(aidString);

    	interestedInfos = AccessCheckImpl.getInstance().getInterestedPackage(composeIntent);
    	
    	if(interestedInfos.size()==0){
    		Log.i(TAG, " interestedInfos.size()==0 return false");
    		return false;
    	}

        activeSe = AccessCheckImpl.getInstance().getActiveSeName();
        if(activeSe.startsWith("SIM"))
        {
        	acPassedInfos = AccessCheckImpl.getInstance().accessControlCheck(aid,interestedInfos);
        	
        	if(acPassedInfos == null || acPassedInfos.size()==0){
        		Log.i(TAG, " acPassedInfos == null or size()==0 return false  ,acPassedInfos:"+acPassedInfos);
        		return false;
        	}
        }
        else{
            Log.i(TAG, " activeSe: "+activeSe+"  ByPass AC check");
            acPassedInfos = interestedInfos;
        }
    	
    	Log.i(TAG, " multiBroadcastFlag: "+multiBroadcastFlag);
    	
    	if(multiBroadcastFlag){
    		AccessCheckImpl.getInstance().multiBroadcastAction(composeIntent,acPassedInfos);
            return true;
    	}else{
    		//uni broadcast
    		
    		//1.Fg activity check
    		if(isFgActivityRegister()){
    			// TODO:: send intent to FgActivity 
    			return true;
    		}else{
    			
    			//2.backGround acivity check
    			return AccessCheckImpl.getInstance().priorityDispatch(composeIntent,acPassedInfos);
    		}
    	}
    }

/*
    String getUiccName(){
    	Log.i(TAG, "getUiccName TODO:: always return SIM1");
    	// TODO::
    	return "SIM1";
    }
*/
    
    void enableMultiBroadcast(){
        Log.i(TAG, "enableMultiBroadcast multiBroadcastFlag:"+multiBroadcastFlag);
        
        multiBroadcastFlag = true;

    }
    

    void disableMultiBroadcast(){
        Log.i(TAG, "disableMultiBroadcast multiBroadcastFlag:"+multiBroadcastFlag);
        
        multiBroadcastFlag = false;
    }


    

    void enableUiccForegroundDispatch(Activity fgActivity, PendingIntent pIntent, IntentFilter iFilter){
        Log.i(TAG, "enableUiccForegroundDispatch  //TODO:");

    }
    
    void disableUiccForegroundDispatch(Activity fgActivity){
        Log.i(TAG, "disableUiccForegroundDispatch  //TODO:");

    }

    

	boolean isFgActivityRegister(){
		Log.d(TAG, "//TODO :: isFgActivityRegister() always return false ");
		return false;
		
	}



    String getSelectedSE(){
            Log.i(TAG, "getSelectedSE  //TODO:return SIM1");
            return "SIM1";
    }

    void selectUicc(){
            Log.i(TAG, "selectUicc  //TODO");
    }

    String bytesToString(byte[] bytes) {
    	if(bytes == null)
    		return "";
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        String str = sb.toString();
        Log.d(TAG, "bytesToString str:(sb.toString())"+str);
        Log.d(TAG, "bytesToString  str.length():"+str.length());
        
        //if (str.length() > 0) {
        //    str = str.substring(0, str.length() - 1);
        //}
        return str;
    }




}

