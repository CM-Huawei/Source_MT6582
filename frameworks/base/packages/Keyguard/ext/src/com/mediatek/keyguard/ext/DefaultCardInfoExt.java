package com.mediatek.keyguard.ext;

import android.content.Context;
import android.graphics.drawable.Drawable;
//import android.provider.Telephony.SIMInfo;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.mediatek.keyguard.ext.ICardInfoExt;
import com.mediatek.telephony.SimInfoManager;

public class DefaultCardInfoExt implements ICardInfoExt {
    
    private static final String TAG = "DefaultKeyguardUtilExt";
    
    @Override
    public void addOptrNameByIdx(TextView v, long simIdx, Context context, String optrname) {
        v.setBackground(getOptrDrawableByIdx(simIdx, context));
        int simCardNamePadding = context.getResources().
                    getDimensionPixelSize(com.mediatek.internal.R.dimen.sim_card_name_padding);
        v.setPadding(simCardNamePadding, 0, simCardNamePadding, 0);
        if (null == optrname) {
            v.setText(com.mediatek.internal.R.string.searching_simcard);
        } else {
            v.setText(optrname);
        }
    }

    @Override
    public void addOptrNameBySlot(TextView v, int slot, Context context, String optrname) {
        v.setBackground(getOptrDrawableBySlot(slot, context));
        int simCardNamePadding = context.getResources().
                    getDimensionPixelSize(com.mediatek.internal.R.dimen.sim_card_name_padding);
        v.setPadding(simCardNamePadding, 0, simCardNamePadding, 0);
        if (null == optrname) {
            v.setText(com.mediatek.internal.R.string.searching_simcard);
        } else {
            v.setText(optrname);
        }
    }
    
    @Override
    public Drawable getOptrDrawableByIdx(long simIdx, Context context) {
        if (simIdx > 0) {
            Log.d(TAG, "getOptrDrawableById, xxsimId=" + simIdx);
            SimInfoManager.SimInfoRecord info = SimInfoManager.getSimInfoById(context, (int)simIdx); 
            if (null == info) {
                Log.d(TAG, "getOptrDrawableBySlotId, return null");
               return null;
            } else {
               return context.getResources().getDrawable(info.mSimBackgroundDarkRes);
            }
        } else {
            return null;
        }
    }

    @Override
    public Drawable getOptrDrawableBySlot(long slot, Context context) {
        if (slot >= 0) {
            Log.d(TAG, "getOptrDrawableBySlot, xxslot=" + slot);
            SimInfoManager.SimInfoRecord info = SimInfoManager.getSimInfoBySlot(context, (int)slot); 
            if (null == info) {
                Log.d(TAG, "getOptrDrawableBySlotId, return null");
                return null;
            } else {
                return context.getResources().getDrawable(info.mSimBackgroundDarkRes);
            }
        } else {
            throw new IndexOutOfBoundsException();
        }
    }
}
