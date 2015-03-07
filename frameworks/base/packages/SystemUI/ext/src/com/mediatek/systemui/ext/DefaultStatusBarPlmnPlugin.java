package com.mediatek.systemui.ext;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import com.mediatek.xlog.Xlog;
import android.view.View;
import android.widget.TextView;

/**
 * M: Default implementation of Plug-in definition of Status bar.
 */
  public class DefaultStatusBarPlmnPlugin extends ContextWrapper implements IStatusBarPlmnPlugin {
    static final String TAG = "DefaultStatusBarPlmnPlugin";
    public DefaultStatusBarPlmnPlugin(Context context) {
        super(context);
    }
    
   public TextView getPlmnTextView(Context cntx){
    Xlog.d(TAG, "into DefaultStatusBarPlmnPlugin getPlmnTextView");
         return null;
     }

  public void bindSettingService(Context cntx){
    Xlog.d(TAG, "into DefaultStatusBarPlmnPlugin bindSettingService");
    }
}
