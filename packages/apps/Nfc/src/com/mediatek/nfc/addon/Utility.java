package com.mediatek.nfc.addon;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;

public class Utility {
	
	static public class SoundPoolListener implements SoundPool.OnLoadCompleteListener {
		static final String TAG = "SoundPoolListener";

		public class ResRecord {
			int id;
			boolean loaded;
		}
			
		private ResRecord[] mResRecord;
		private ArrayList<Integer> mReadyListBeforeSetting = new ArrayList<Integer>();
		private SoundPool mSoundPool;
		private Semaphore mSem;

		public SoundPoolListener(SoundPool soundPool) {
			mSem = new Semaphore(0);
			mSoundPool = soundPool;
    	    mSoundPool.setOnLoadCompleteListener(this);
		}
		
		/**
		 * Important!! This method can NOT be called in main thread!
		 * Since the callback will be executed in main thread,
		 * the sem.acquire() will cause main thread to block
		 */
		public void waitForSamplesReady(int[] ids) {
   		    Log.d(TAG, "waitForSamplesReady");
   		    Long time = SystemClock.elapsedRealtime();
			synchronized(this) {
			    mResRecord = new ResRecord[ids.length];
			    for (int i = 0; i < ids.length; i++) {
			    	mResRecord[i] = new ResRecord();
			    	mResRecord[i].id = ids[i];
			    	mResRecord[i].loaded = false;
			    }	
			    boolean allLoaded = true;
			    for (ResRecord r : mResRecord) {
			    	for (int id : mReadyListBeforeSetting) {
			    		if (r.id == id) {
			    			r.loaded = true;
			    		}
			    	}
			    	if (!r.loaded) {
			    		allLoaded = false;
			    	}
			    }
			    if (allLoaded) {
			    	/// OK, all samples loaded so quickly, simple return
			    	return;
			    }
			}

			/// now we're really waiting for all sound samples' status
		    try {
		    	boolean result = mSem.tryAcquire(5000, TimeUnit.MILLISECONDS);/// don't block forever
		    	Log.d(TAG, "all samples ready? " + result + ", time = " + (SystemClock.elapsedRealtime() - time));
		    } catch (Exception e) {
		    	e.printStackTrace();
		    }
		}

		@Override
		public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
			if (soundPool == mSoundPool) {
				synchronized(this) {
				    if (mResRecord == null) {
				    	if (soundPool == mSoundPool && status == 0) {
				    		mReadyListBeforeSetting.add(sampleId);
				    	}
				    	return;
				    }
				    boolean allLoaded = true;
		        	for (ResRecord r : mResRecord) {
		        		if (r.id == sampleId && status == 0) { /// according to SDK, status 0 refers to success
		        			Log.d(TAG, "loaded sampleId = " + sampleId);
		        			r.loaded = true;
		        		}
		        		if (!r.loaded) {
		        			allLoaded = false;
		        		}
		        	}
		        	if (allLoaded) {
		        		Log.d(TAG, "all samples are loaded");
		        		mSem.release();
		        	}
				}
			}
		}

	};

}
