package com.android.phone;

import android.os.Handler;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.SurfaceHolder.Callback;
import android.widget.Button;
import android.os.SystemClock;

import com.android.internal.telephony.gemini.*;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.vt.VTManager;
import com.mediatek.phone.wrapper.CallManagerWrapper;
import com.mediatek.settings.VTSettingUtils;

public class VTLoopbackActivity extends Activity implements View.OnClickListener, SurfaceHolder.Callback {
	private SurfaceView mVTHighVideo;
    private SurfaceView mVTLowVideo;
    private Button mVTDial;
    private Button mVTEnd;
    private boolean mHighVideoChanged = false;
    private boolean mLowVideoChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if(FeatureOption.MTK_VT3G324M_SUPPORT == false){
            Log.e("VTLoopbackActivity","VTLoopback suspend, since VT is not support in this project");
            return;
        }
    
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.vt_loopback);

        VTManager.getInstance().registerVTListener(new Handler());
        
        mVTHighVideo = (SurfaceView) findViewById(R.id.VTLBHighVideo);
        mVTLowVideo = (SurfaceView) findViewById(R.id.VTLBLowVideo);
        
        Log.d("VTLoopBack", "mVTHighVideo's holder is " + mVTHighVideo.getHolder().toString());
        Log.d("VTLoopBack", "mVTLowVideo's holder is " + mVTLowVideo.getHolder().toString());
        
        mVTHighVideo.getHolder().addCallback(this);
        mVTLowVideo.getHolder().addCallback(this);
        
        Log.d("VTLoopBack", "set type - SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS ! ");
        mVTHighVideo.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mVTLowVideo.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        mVTDial = (Button)findViewById(R.id.VTLBStart);
        mVTDial.setText("Start");
        mVTDial.setOnClickListener(this);
        mVTDial.setEnabled(false);
        
        mVTEnd = (Button)findViewById(R.id.VTLBEnd);
        mVTEnd.setText("Stop");
        mVTEnd.setOnClickListener(this);
        mVTEnd.setEnabled(false);
    }
    
    protected void onPause(){
    	Log.d("VTLoopBack", "onPause! -> finish itself !" );
    	super.onPause();
    	this.finish();
		Log.d("VTLoopBack", "onPause->VTManager.getInstance().onDisconnected() start ! ");
		VTManager.getInstance().onDisconnected();
		Log.d("VTLoopBack", "onPause->VTManager.getInstance().onDisconnected() end ! ");
		
		Log.d("VTLoopBack", "onPause->VTManager.getInstance().setVTClose() start ! ");
		VTManager.getInstance().setVTClose();
		Log.d("VTLoopBack", "onPause->VTManager.getInstance().setVTClose() end ! ");        
    }
    
    protected void onResume(){
    	super.onResume();
    	Log.d("VTLoopBack", "onResume!" );
    }
    
    protected void onStop(){
    	super.onStop();
    	Log.d("VTLoopBack", "onStop!" );
    }
    
    protected void onStart(){
    	super.onStart();
    	Log.d("VTLoopBack", "onStart!" );
    }

	public void surfaceChanged(SurfaceHolder holder, int format, int width,int height){
		Log.d("VTLoopBack", "surfaceChanged : " + holder.toString());
		
		if(holder == mVTHighVideo.getHolder() && mHighVideoChanged == false)
			mHighVideoChanged = true;
		
		if(holder == mVTLowVideo.getHolder() && mLowVideoChanged == false)
			mLowVideoChanged = true;
		
		if(mHighVideoChanged && mLowVideoChanged){
			Log.d("VTLoopBack", "surfaceChanged : VTManager.getInstance().setDisplay(mVTLowVideo.getHolder(), mVTHighVideo.getHolder())");
			VTManager.getInstance().setDisplay(mVTLowVideo.getHolder().getSurface(),
                                               mVTHighVideo.getHolder().getSurface());
			mVTDial.setEnabled(true);
			mVTEnd.setEnabled(true);
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.d("VTLoopBack", "surfaceCreated : " + holder.toString());
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d("VTLoopBack", "surfaceDestroyed : " + holder.toString());
	}

	public void onClick(View v) {
		if(R.id.VTLBStart == v.getId()){
    		Log.d("VTLoopBack", "VT loopback : dial ! ");

    		Log.d("VTLoopBack", "VTSettingUtils.getInstance().updateVTEngineerModeValues() start ! ");
    		VTSettingUtils.getInstance().updateVTEngineerModeValues();
    		Log.d("VTLoopBack", "VTSettingUtils.getInstance().updateVTEngineerModeValues() end ! ");
    		
    		Log.d("VTLoopBack", "VTManager.getInstance().setVTOpen() start ! ");
			VTManager.getInstance().setVTOpen(PhoneGlobals.getInstance().getBaseContext(),0);
			Log.d("VTLoopBack", "VTManager.getInstance().setVTOpen() end ! ");
			
			Log.d("VTLoopBack", "VTManager.getInstance().setVTReady() start ! ");
			SystemClock.sleep(50);
			VTManager.getInstance().setVTReady();
			Log.d("VTLoopBack", "VTManager.getInstance().setVTReady() end ! ");
			
			Log.d("VTLoopBack", "VTManager.getInstance().onConnected() start ! ");
			SystemClock.sleep(50);
			VTManager.getInstance().setVTConnected();
			Log.d("VTLoopBack", "VTManager.getInstance().onConnected() end ! ");
			
		}else if(R.id.VTLBEnd == v.getId()){
			Log.d("VTLoopBack", "VT loopback : hangup ! ");
			
			Log.d("VTLoopBack", "VTManager.getInstance().onDisconnected() start ! ");
			VTManager.getInstance().onDisconnected();
			Log.d("VTLoopBack", "VTManager.getInstance().onDisconnected() end ! ");
			
			Log.d("VTLoopBack", "VTManager.getInstance().setVTClose() start ! ");
			VTManager.getInstance().setVTClose();
			Log.d("VTLoopBack", "VTManager.getInstance().setVTClose() end ! ");
		}
	}
}
