package com.mediatek.keyguard.ext;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

public interface ICardInfoExt {
    // Add for CT sim indicator feature 
    void addOptrNameByIdx(TextView v, long simIdx, Context context, String optrname);
    void addOptrNameBySlot(TextView v, int slot, Context context, String optrname);
    Drawable getOptrDrawableByIdx(long simIdx, Context context);
    Drawable getOptrDrawableBySlot(long slot, Context context);
}
