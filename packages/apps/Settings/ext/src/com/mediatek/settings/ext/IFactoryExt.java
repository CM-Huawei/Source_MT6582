package com.mediatek.settings.ext;

import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import android.app.Activity;
public interface IFactoryExt {

	 /**
	  *
	  * @return two checkbox status
	  */
	 int getCheckBoxStatus();

     /**
	  * 
	  * @param lists ct need operator views
      */
     void setLayout(List<View> lists);

     /**
      * 
      * @param mEraseInternalData checkbox status
      */
     boolean onClick(int eraseInternalData);

     /**
      *  Update Container view, add encrypted container view, add internal erase container view
      *  @param container the main layout of MasterClear fragment
      *  @param siberViewId The id of siberView, we will add erase container view to top of siberView
      */
     void updateContentViewLayout(ViewGroup container, int siberViewId);

     /**
      * CT Factory reset feature refactory
      * @param activity which will startService or sendBroadcast
      * @param eraseInternalData: data | app | media
      * @param eraseSdCard: use in DefaultFactoryExt.java
      */
      void onResetPhone(Activity activity, int eraseInternalData, boolean eraseSdCard);
}
