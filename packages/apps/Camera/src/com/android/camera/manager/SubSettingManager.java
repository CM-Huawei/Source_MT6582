package com.android.camera.manager;

import java.util.ArrayList;
import java.util.List;


import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import android.view.View;
import com.android.camera.Camera;
import com.android.camera.CameraPreference;
import com.android.camera.ListPreference;
import com.android.camera.Log;
import com.android.camera.PreferenceGroup;
import com.android.camera.SettingChecker;
import com.android.camera.Util;
import com.android.camera.manager.SettingManager;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.SettingListLayout;
import com.android.camera.ui.SubSettingLayout;
import com.mediatek.camera.ext.ExtensionHelper;

import com.android.camera.R;

public class SubSettingManager extends SettingManager{

	private static final String TAG = "SubSettingManager";
	private static final boolean LOG = Log.LOGV;
    
	public SubSettingManager(Camera context){
		super(context);
	}
	
	@Override
	protected View getView() {
		// TODO Auto-generated method stub
        View view = inflate(R.layout.sub_setting_indicator);
        mIndicator = (RotateImageView)view.findViewById(R.id.sub_setting_indicator);
        mIndicator.setOnClickListener(this);
        return view;
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if (v == mIndicator) {
			if (!mShowingContainer) {
				showSetting();
			}
		}
	}
	
    @Override
    public void onRefresh() {

        if (mShowingContainer) { //just apply checker when showing settings
            getContext().getSettingChecker().applyParametersToUI();
            notifyDataSetChanged();
        }
    }

	@Override
	public boolean collapse(boolean force) {
		boolean collapsechild = false;
		if (mShowingContainer && mPageView != null) {
			mPageView.collapseChild();
			hideSetting();
			collapsechild = true;
		}
		if (LOG) {
			Log.v(TAG, "collapse(" + force + ") mShowingContainer="
					+ mShowingContainer + ", return " + collapsechild);
		}
		return collapsechild;
	}
    
    @Override
    public void onPreferenceReady() {
        releaseSettingResource();
    }
    
    @Override
    public void onOrientationChanged(int orientation) {
        super.superOrientationChanged(orientation);
        Util.setOrientation(mPageView, getOrientation(), true);
        Util.setOrientation(mIndicator, getIndicatorOrientation(), true);
    }

    @Override
    public void show() {
        super.show();
        Util.setOrientation(mIndicator, getIndicatorOrientation(), false);
    }

    // Fix indicator direction
    private int getIndicatorOrientation() {
        int rotation = Util.getDisplayRotation(getContext());
        return (rotation == 0 || rotation == 180) ? 270 : 0;
    }
   
    
	public void showSetting() {
		// TODO Auto-generated method stub
		Log.d(TAG, "showSetting... start");

        if (getContext().isFullScreen()) {
            PreferenceGroup preferenceGroup = getContext().getPreferenceGroup();
            if (!mShowingContainer && preferenceGroup != null && getContext().isNormalViewState()) {
                mMainHandler.removeMessages(MSG_REMOVE_SETTING);
                mShowingContainer = true;
                initializeSettings();
                refresh();
                mSettingLayout.setVisibility(View.VISIBLE);
                if (mSettingLayout.getParent() == null) {
                	Log.i("LeiLei", "showSetting getContext() = " + getContext());
                    getContext().addView(mSettingLayout, SETTING_PAGE_LAYER);
                }
                getContext().setViewState(Camera.VIEW_STATE_SUB_SETTING);
                getContext().setSwipingEnabled(false);
                startFadeInAnimation(mSettingLayout);
                mIndicator.setVisibility(View.GONE);
            }
            setChildrenClickable(true);
            Log.d(TAG, "showSetting... end");
        }
	}
	
	private SubSettingLayout mPageView;
	private void initializeSettings() {
		if (mSettingLayout == null && getContext().getPreferenceGroup() != null) {
			mSettingLayout = (ViewGroup) getContext().inflate(
					R.layout.sub_setting_container, SETTING_PAGE_LAYER);
			// new page view
			mPageView = (SubSettingLayout) mSettingLayout
					.findViewById(R.id.sub_pager);
			mPageView.initialize(SettingChecker
					.getSettingKeys(SettingChecker.SETTING_GROUP_SUB_COMMON),
					true);
			
		}
		Util.setOrientation(mPageView, getOrientation(), false);
	}
    
    public void hideSetting() {
        if (LOG) {
            Log.v(TAG, "hideSetting() mShowingContainer=" + mShowingContainer + ", mSettingLayout=" + mSettingLayout);
        }
        if (mShowingContainer && mSettingLayout != null) {
            mMainHandler.removeMessages(MSG_REMOVE_SETTING);
            startFadeOutAnimation(mSettingLayout);
            mSettingLayout.setVisibility(View.GONE);
            mShowingContainer = false;
            if (getContext().getViewState() == Camera.VIEW_STATE_SUB_SETTING) {
                getContext().restoreViewState();
                getContext().setSwipingEnabled(true);
            }
            mMainHandler.sendEmptyMessageDelayed(MSG_REMOVE_SETTING, DELAY_MSG_REMOVE_SETTING_MS);
        }
        setChildrenClickable(false);
        mIndicator.setVisibility(View.VISIBLE);
    }       

    public void notifyDataSetChanged() {
    	mPageView.setSettingChangedListener(SubSettingManager.this);
    	mPageView.reloadPreference();
    }
}
