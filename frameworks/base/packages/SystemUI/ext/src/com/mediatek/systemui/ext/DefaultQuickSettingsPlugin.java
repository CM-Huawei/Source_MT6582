package com.mediatek.systemui.ext;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

import com.mediatek.xlog.Xlog;
/**
 * M: Default implementation of Plug-in definition of Quick Settings.
 */
public class DefaultQuickSettingsPlugin extends ContextWrapper implements
        IQuickSettingsPlugin {
    private Context mContext;
    private static final String TAG = "DefaultQuickSettingsPlugin";

    public DefaultQuickSettingsPlugin(Context context) {
        super(context);
        mContext = context;
    }

    public void customizeTileViews(ViewGroup parent) {
        Xlog.d(TAG, "customizeTileViews parent= "+ parent);
    }

    public void updateResources() {
    }

    protected View removeTileById(ViewGroup parent, QuickSettingsTileViewId id) {
        View removeTileView = getTileViewById(parent, id);
        if (removeTileView != null) {
            parent.removeView(removeTileView);
        }
        return removeTileView;
    }        
    
    protected View getTileViewById(ViewGroup parent, QuickSettingsTileViewId id) {
        final int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            QuickSettingsTileView tileView = (QuickSettingsTileView) parent.getChildAt(i);
            if (tileView.getTileViewId() == id) {
                return tileView;
            }
        }
        // if not found, return null
        return null;
    }
    
    protected int getTileIndexById(ViewGroup parent, QuickSettingsTileViewId id) {
        final int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            QuickSettingsTileView tileView = (QuickSettingsTileView) parent.getChildAt(i);
            if (tileView.getTileViewId() == id) {
                return i;
            }
        }
        // if not found, return -1
        return -1;
    }
}

