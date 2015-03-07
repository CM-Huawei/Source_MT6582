package com.android.camera;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;

public class FileSaverService extends Service {
	private static final String TAG = "FileSaverService";
	private static final int SAVE_TASK_LIMIT = 3;
	
	private List<SaveRequest> mQueue = new LinkedList<SaveRequest>();
	private final Binder mBinder = new LocalBinder();
	private int mTaskNumber;
	private ContinuousSaveTask mContinuousSaveTask;
	private boolean mStopped = false;
	private List<FileSaverListener> mListeners = new ArrayList<FileSaverService.FileSaverListener>();
	
	public interface FileSaverListener {
		public void onQueueStatus(boolean full);
		public void onFileSaved(SaveRequest r);
		public void onSaveDone();
	}
	
	public void registerFileSaverListener(FileSaverListener listener) {
        Log.i(TAG, "registerFileSaverListener");
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void unregisterFileSaverListener(FileSaverListener listener) {
        Log.i(TAG, "unregisterFileSaverListener");
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

	class LocalBinder extends Binder {
		public FileSaverService getService() {
			return FileSaverService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flag, int startId) {
		return START_STICKY;
	}
	
	@Override
	public void onCreate() {
		mTaskNumber = 0;
	}
	
	@Override
	public void onDestroy() {
		
	}
	
	public void onContinousShotDone() {
		mStopped = true;
	}
	
	public boolean isNoneSaveTask() {
		return mTaskNumber == 0;
	}
	
	public long getWaitingDataSize() {
		long totalToWrite = 0;
		// LinkedList is not saved list, so mQueue should be sync in multi thread
		synchronized (mQueue) {
			for (SaveRequest r: mQueue) {
				totalToWrite += r.getDataSize();
			}
		}
		
		return totalToWrite;
	}
	
	public int getWaitingCount() {
	    synchronized (mQueue) {
            return mQueue.size();
        }
	}
	
	public boolean isQueueFull() {
		return mTaskNumber >= SAVE_TASK_LIMIT;
	}
	
	private void onQueueFull() {
		if (mListeners != null) {
		    for (FileSaverListener l : mListeners) {
		         l.onQueueStatus(true);
		    }
		    
		}
	}
	
	private void onQueueAvailable() {
		if (mListeners != null) {
		    for (FileSaverListener l : mListeners) {
		        l.onQueueStatus(false);
		    }
		}
	}
	
	// run in main thread
	public void addSaveRequest(SaveRequest request) {
		boolean isContinuousRequest = request.isContinuousRequest();
		if (!isContinuousRequest) {
			NormalSaveTask t = new NormalSaveTask(request);
			mTaskNumber++;
			if (isQueueFull()) {
				// when numbers of saveTask over 3, disable photo shutter button.
				onQueueFull();
			}
			t.execute();
			Log.i(TAG, "execute normal AsyncTask");
		} else {
			// LinkedList is not saved list, so mQueue should be sync in multi thread
			synchronized (mQueue) {
				mQueue.add(request);
			}
			if (mContinuousSaveTask == null) {
				mContinuousSaveTask = new ContinuousSaveTask();
				mTaskNumber++;
				mContinuousSaveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				Log.i(TAG, "execute continuous AsyncTask = " + mContinuousSaveTask);
			}
		}

	}
	
	// this AsyncTask is used to save snapshot image and video.
	private class NormalSaveTask extends AsyncTask<Void, Void, Void> {
		SaveRequest r;
		public NormalSaveTask(SaveRequest request) {
			r = request;
		}
		@Override
		protected void onPreExecute() {
		    Log.i(TAG, "NormalSaveTask, onPreExecute");
		}
		
		@Override
		protected Void doInBackground(Void...v) {
			Log.i(TAG, "NormalSaveTask, doInBackground(), saveRequest:" + r);
			if (Storage.isStorageReady()) {
				r.saveRequest();
			}
			r.notifyListener();
			for (FileSaverListener l : mListeners) {
			     l.onFileSaved(r);
			}
			
			mTaskNumber--;
			if (isNoneSaveTask()) {
				for (FileSaverListener l : mListeners) {
				    l.onSaveDone();
				}
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Void v) {
			Log.i(TAG, "NormalSaveTask, onPostExecute()");
			if (mTaskNumber == SAVE_TASK_LIMIT -1) onQueueAvailable();
		}
	}
	
	// this AsyncTask is used to save continous shot images.
	private class ContinuousSaveTask extends AsyncTask<Void, Void, Void> {
		SaveRequest r;
		public ContinuousSaveTask() {
		}
		
		@Override
		protected void onPreExecute() {
		    Log.i(TAG, "ContinousSaveTask, onPreExcute");
		}
		
		@Override
		protected Void doInBackground(Void...v) {
			Log.i(TAG, "ContinousSaveTask, doInBackground()");
			while(true) {
				if (mQueue.isEmpty()) {
					// if mQueue is empty and continuous shot is stopped, means 
					// continuous save task is finished, so break;
					if (mStopped) break;
				} else {
					r = mQueue.get(0);
					
					if (Storage.isStorageReady()) {
						r.saveRequest();
					}
					// LinkedList is not saved list, so mQueue should be sync in multi thread
					synchronized (mQueue) {
						mQueue.remove(0);
					}
					if (mListeners != null) {
						for (FileSaverListener l : mListeners) {
						    l.onFileSaved(r);
						}
					}
				}
			}
			
			mTaskNumber--;
			if (mListeners != null) {
				for (FileSaverListener l : mListeners) {
				    l.onSaveDone();
				}
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Void v) {
			Log.i(TAG, "ContinousSaveTask, onPostExecute()");
			mStopped = false;
			mContinuousSaveTask = null;
		}
	}
}
