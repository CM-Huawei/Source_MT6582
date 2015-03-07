package com.mtk.vtcall;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;


import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;

public class VTCallTest extends InstrumentationTestCase {
    private static final String TAG = "VTCallTest";
    private Instrumentation mInst = null;
	private static final String SHREDPRE_NAME = "vtcalltest_config";
	private static final String PHONE_NUMBER_FLAG = "phone_number";
	private static final String DURATION_FLAG = "duration";
	private static final String ITERATION_FLAG = "iteration";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInst = getInstrumentation();
    }

    public void testVTCall() {
		final SharedPreferences spVtCall = mInst.getContext().getSharedPreferences(SHREDPRE_NAME, 0);
        final String phone_number = spVtCall.getString(PHONE_NUMBER_FLAG, null);
        final int duration_time = spVtCall.getInt(DURATION_FLAG, 0);
        final int nNumTests = spVtCall.getInt(ITERATION_FLAG, 0);
             
        Log.i(TAG, "testVTCall start!");	
        
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.fromParts("tel", phone_number, null));
        intent.putExtra("com.android.phone.extra.video", true);
        intent.putExtra("com.android.phone.extra.slot", 0);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		for (int i = 0; i < nNumTests; i++) {
	        mInst.getContext().startActivity(intent);
	        SystemClock.sleep(duration_time);
	        
	        endCall(i);
	        if (i < (nNumTests-1))
	        	SystemClock.sleep(5000);
		}
 
        Log.i(TAG, "testVTCall end!");
    }
    
    private void endCall(int i){
    	try {
    		// Get the corresponding objects by java reflection
    		// ex. ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE))
            String serviceManagerName = "android.os.ServiceManager";
            String serviceManagerNativeName = "android.os.ServiceManagerNative";
            String telephonyName = "com.android.internal.telephony.ITelephony";

            Class<?> telephonyClass;
            Class<?> telephonyStubClass;
            Class<?> serviceManagerClass;
            Class<?> serviceManagerNativeClass;

            Method telephonyEndCall;
            Method telephonyGetCallState;

            Object telephonyObject;
            Object serviceManagerObject;

            telephonyClass = Class.forName(telephonyName);
            telephonyStubClass = telephonyClass.getClasses()[0];
            serviceManagerClass = Class.forName(serviceManagerName);
            serviceManagerNativeClass = Class.forName(serviceManagerNativeName);

            Method getService = serviceManagerClass.getMethod("getService", String.class);

            Method tempInterfaceMethod = serviceManagerNativeClass.getMethod("asInterface", IBinder.class);

            Binder tmpBinder = new Binder();
            tmpBinder.attachInterface(null, "fake");

            serviceManagerObject = tempInterfaceMethod.invoke(null, tmpBinder);
            IBinder retbinder = (IBinder) getService.invoke(serviceManagerObject, Context.TELEPHONY_SERVICE);
            Method serviceMethod = telephonyStubClass.getMethod("asInterface", IBinder.class);

            // Get 'phone' service instance
            telephonyObject = serviceMethod.invoke(null, retbinder);
                        
            // Check if the call is 'off hook'
            telephonyGetCallState = telephonyClass.getMethod("getCallState");
            int nCallState = (Integer)telephonyGetCallState.invoke(telephonyObject);
            Log.i(TAG, "call state before hang up = " + nCallState);
            assertEquals(2, nCallState);
            
            // Snapshot the screen for further judgment
            assertTrue(takeScreenshot(i, 1.0f, 100));
            
            // Hang up the call
            telephonyEndCall = telephonyClass.getMethod("endCall");
            telephonyEndCall.invoke(telephonyObject);
            SystemClock.sleep(2000);
            
            // Check if the call is hangup
            nCallState = (Integer)telephonyGetCallState.invoke(telephonyObject);
            Log.i(TAG, "call state after hang up = " + nCallState);
            assertEquals(0, nCallState);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "FATAL ERROR: could not connect to telephony subsystem");
            Log.e(TAG, "Exception object: " + e);
        }
    	
      	
    	/*ActivityManager mActivityManager = (ActivityManager)mInst.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> RunningTask = mActivityManager.getRunningTasks(1);
        ActivityManager.RunningTaskInfo ar = RunningTask.get(0);
        Log.i(TAG, ar.topActivity.getClassName().toString());
        
        //View viewEndBtn = null;
        
        try {            
            File jarFile = new File("/system/app/Phone.apk"); 
            if ( jarFile.exists() ) {  
                //File dirOptimized = new File("/sdcard/vttest");
                //dirOptimized.mkdirs();
                
                File dexOutputDir = mInst.getContext().getDir("dex", 0);
                
            	DexClassLoader cl = new DexClassLoader(jarFile.toString(), dexOutputDir.getAbsolutePath(), null, this.getClass().getClassLoader());   
            	Class<?> c = cl.loadClass(ar.topActivity.getClassName().toString());  
            
            	//for (Method m : c.getMethods()) {
                //    Log.i(TAG, m.getName()+ " " + Arrays.toString(m.getParameterTypes()));
                //}
 
            	//Object obj = (Object)c.cast(ar.topActivity);
            	//Activity activityInCallScreen = (Activity)c.newInstance();
	            Method methodGetResources = c.getMethod("getResources", new Class[0]);
	            Resources rsc = (Resources)methodGetResources.invoke(ar.topActivity, new Object[0]);
	            
            	//Activity aty = mActivityMonitor.waitForActivity();
            	int id = rsc.getIdentifier("endButton", "id", ar.topActivity.getPackageName());      	
            	Log.i(TAG, new Integer(id).toString());
            	//mActivityMonitor.getLastActivity().finish();
            	
            	//Class[] param2 = {Integer.TYPE};
	            //Method methodFindViewById = c.getMethod("findViewById", param2);
	            //Object[] paramObjs2 = {new Integer(id)};
	            //View viewEndBtn = (View)methodFindViewById.invoke(ar.topActivity, paramObjs2);
	            //TouchUtils.clickView(this, viewEndBtn);
	            
	            SystemClock.sleep(2000);
            }
        } catch (Exception e) {
	    	    e.printStackTrace();
	    	    Log.e(TAG, "FATAL ERROR: could not connect to telephony subsystem");
	    	    Log.e(TAG, "Exception object: " + e);
	    }*/
    }
    
    private boolean takeScreenshot(int i, float scale, int quality) {
        // This is from com.android.systemui.screenshot.GlobalScreenshot#takeScreenshot
        // We need to orient the screenshot correctly (and the Surface api seems to take screenshots
        // only in the natural orientation of the device :!)
        DisplayMetrics displayMetrics = new DisplayMetrics();
        DisplayManager mActivityManager = (DisplayManager)mInst.getContext().getSystemService(Context.DISPLAY_SERVICE);
        Display display = mActivityManager.getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealMetrics(displayMetrics);
        float[] dims = {displayMetrics.widthPixels, displayMetrics.heightPixels};
        float degrees = getDegreesForRotation(display.getRotation());
        boolean requiresRotation = (degrees > 0);
        Matrix matrix = new Matrix();
        matrix.reset();
        if (scale != 1.0f) {
            matrix.setScale(scale, scale);
        }
        if (requiresRotation) {
            // Get the dimensions of the device in its native orientation
            matrix.preRotate(-degrees);
        }
        matrix.mapPoints(dims);
        dims[0] = Math.abs(dims[0]);
        dims[1] = Math.abs(dims[1]);

        // Take the screenshot
        Bitmap screenShot = SurfaceControl.screenshot((int) dims[0], (int) dims[1]);
        if (screenShot == null) {
            return false;
        }

        if (requiresRotation) {
            // Rotate the screenshot to the current orientation
            int width = displayMetrics.widthPixels;
            int height = displayMetrics.heightPixels;
            if (scale != 1.0f) {
                width = Math.round(scale * width);
                height = Math.round(scale * height);
            }
            Bitmap ss = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(ss);
            c.translate(ss.getWidth() / 2, ss.getHeight() / 2);
            c.rotate(degrees);
            c.translate(-dims[0] / 2, -dims[1] / 2);
            c.drawBitmap(screenShot, 0, 0, null);
            c.setBitmap(null);
            screenShot = ss;
        }

        // Optimizations
        screenShot.setHasAlpha(false);

        try {
        	//String imageDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date(System.currentTimeMillis())); 
            FileOutputStream fos = mInst.getContext().openFileOutput(++i+"-VTCallScreen.png", 0);
            screenShot.compress(Bitmap.CompressFormat.PNG, quality, fos);
            fos.flush();
            fos.close();
        } catch (IOException ioe) {
            Log.e(TAG, "failed to save screen shot to file", ioe);
            return false;
        } finally {
            screenShot.recycle();
        }
        return true;
    }
    
    private static float getDegreesForRotation(int value) {
        switch (value) {
        case Surface.ROTATION_90:
            return 360f - 90f;
        case Surface.ROTATION_180:
            return 360f - 180f;
        case Surface.ROTATION_270:
            return 360f - 270f;
        }
        return 0f;
    }
}
