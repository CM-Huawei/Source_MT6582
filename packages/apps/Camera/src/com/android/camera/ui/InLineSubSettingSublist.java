/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.camera.Camera;
import com.android.camera.ListPreference;
import com.android.camera.Log;
import com.android.camera.SettingUtils;
import com.android.camera.Util;
import com.android.camera.manager.ViewManager;

import com.android.camera.R;

/* Turns on/off sub setting. */
public class InLineSubSettingSublist extends InLineSettingSublist {
    private static final String TAG = "InLineSubSublist";
    private static final boolean LOG = Log.LOGV;
    
    private RotateImageView mImage;
    private TextView mTitleListname;
    private String Titlename;

    public InLineSubSettingSublist(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = (Camera)context;
    }

    @Override
    protected void onFinishInflate() {
    	callSuperOnFinishInflate();
        mImage = (RotateImageView)findViewById(R.id.image);
        setOnClickListener(mOnClickListener);
    }

   
    protected void setTextOrImage(int index, String text) {
        int iconId = mPreference.getIconId(index);
        if (iconId != ListPreference.UNKNOWN) {
            mImage.setVisibility(View.VISIBLE);
            mImage.setImageResource(iconId);
        } else {
            mImage.setVisibility(View.GONE);
        }
        

    }
    
    public void initialize(ListPreference preference) {
        if (LOG) {
            Log.v(TAG, "initialize(" + preference + ")");
        }
        if (preference == null || mPreference == preference) { return; }
        mPreference = preference;
        reloadPreference();
    }

    
    public boolean expendChild() {
        boolean expend = false;
        if (!mShowingChildList) {
            mShowingChildList = true;
            if (mListener != null) {
                mListener.onShow(this);
            }
            int resId = 0;
            resId = R.layout.sub_setting_sublist_layout;
            
            mSettingLayout = (SettingSublistLayout)mContext.inflate(resId,
                    ViewManager.VIEW_LAYER_SETTING);
            mSettingContainer = mSettingLayout.findViewById(R.id.container);
            mTitleListname = (TextView)mSettingLayout.findViewById(R.id.ItemListTitle);
            mTitleListname.setText(mPreference.getTitle());
            mSettingLayout.initialize(mPreference);
            mContext.addView(mSettingLayout, ViewManager.VIEW_LAYER_SETTING);
            mContext.addOnOrientationListener(this);
            mSettingLayout.setSettingChangedListener(this);
            setOrientation(mContext.getOrientationCompensation(), false);
            fadeIn(mSettingLayout);
            highlight();
            expend = true;
        }
        if (LOG) {
            Log.v(TAG, "expendChild() return " + expend);
        }
        return expend;
    }
    
    public boolean collapseChild() {
        return super.collapseChild();
    }
    
    protected void highlight() {
    	if (mImage != null){
    		mImage.setBackgroundResource(R.drawable.ic_color_effects_focus);
    	}
    }
    
    protected void normalText() {
    	if (mImage != null){
    		mImage.setBackground(null);

    	}
    }
}
