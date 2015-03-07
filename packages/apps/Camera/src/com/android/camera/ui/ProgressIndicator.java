package com.android.camera.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.camera.Log;
import com.android.camera.R;

public class ProgressIndicator {

    private static final String TAG = "ProgressIndicator";
    public static final int TYPE_MAV = 0;
    public static final int TYPE_PANO = 1;
    public static final int TYPE_SINGLE3D = 2;
    public static final int TYPE_MOTION_TRACK = 3;

    public static final int BLOCK_NUMBERS = 9;
    public static final int BLOCK_NUMBERS_SINGLE3D = 2;

    // those sizes are designed for mdpi in pixels, you need to change progress_indicator.xml when change the values here.
    private int mPanoBlockSizes[] = { 17, 15, 13, 12, 11, 12, 13, 15, 17 };
    private int mMavBlockSizes[] = { 11, 12, 13, 15, 17, 15, 13, 12, 11 };
    private int mSingle3DBlockSizes[] = { 11, 11 };
    private int mMotionTrackBlockSizes []= {11,11,11,11,11,11,11,11,11};

    public static final int MAV_CAPTURE_NUM = 15;
    public static final int PANORAMA_CAPTURE_NUM = 9;

    private int mBlockPadding = 4;

    private View mProgressView;
    private ImageView mProgressBars;
    private static int sIndicatorMarginLong = 0;
    private static int sIndicatorMarginShort = 0;

    private int mBlockSize[][] ={mMavBlockSizes,mPanoBlockSizes,mSingle3DBlockSizes,mMotionTrackBlockSizes};
    private int mBlockNumber[] = {BLOCK_NUMBERS,BLOCK_NUMBERS,BLOCK_NUMBERS_SINGLE3D,BLOCK_NUMBERS};
    public ProgressIndicator(Activity activity, int indicatorType) {
        mProgressView = activity.findViewById(R.id.progress_indicator);
        mProgressView.setVisibility(View.VISIBLE);
        mProgressBars = (ImageView) activity.findViewById(R.id.progress_bars);

        Resources res = activity.getResources();
        final float scale = res.getDisplayMetrics().density;
        int blockNumber = mBlockNumber[indicatorType];
        int mDrawBlokSize[] = mBlockSize[indicatorType];
        if (scale != 1.0f) {
            mBlockPadding = (int) (mBlockPadding * scale + 0.5f);
            for (int i = 0; i < blockNumber; i++) {
                mDrawBlokSize[i] = (int) (mDrawBlokSize[i] * scale + 0.5f);
            }
        }
        mProgressBars.setImageDrawable(new ProgressBarDrawable(activity, mProgressBars, mDrawBlokSize, mBlockPadding));
        getIndicatorMargin();
//        setOrientation(0);
    }

    public void setVisibility(int visibility) {
        mProgressView.setVisibility(visibility);
    }

    public void setProgress(int progress) {
        Log.d(TAG, "setProgress: " + progress);
        mProgressBars.setImageLevel(progress);
    }

    private void getIndicatorMargin() {
        if (sIndicatorMarginLong == 0 && sIndicatorMarginShort == 0) {
            Resources res = mProgressView.getResources();
            sIndicatorMarginLong = res.getDimensionPixelSize(R.dimen.progress_indicator_bottom_long);
            sIndicatorMarginShort = res.getDimensionPixelSize(R.dimen.progress_indicator_bottom_short);
        }
        Log.d(TAG, "getIndicatorMargin: sIndicatorMarginLong = " + sIndicatorMarginLong
                + " sIndicatorMarginShort = " + sIndicatorMarginShort);
    }

    public void setOrientation(int orientation) {
        LinearLayout progressViewLayout = (LinearLayout) mProgressView;
        RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(progressViewLayout.getLayoutParams());
        int activityOrientation = mProgressView.getResources().getConfiguration().orientation;
        if ((Configuration.ORIENTATION_LANDSCAPE == activityOrientation && (orientation == 0 || orientation == 180))
                || (Configuration.ORIENTATION_PORTRAIT == activityOrientation
                        && (orientation == 90 || orientation == 270))) {
            rp.setMargins(rp.leftMargin, rp.topMargin, rp.rightMargin, sIndicatorMarginShort);
        } else {
            rp.setMargins(rp.leftMargin, rp.topMargin, rp.rightMargin, sIndicatorMarginLong);
        }

        rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        rp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        progressViewLayout.setLayoutParams(rp);
        progressViewLayout.requestLayout();
    }
}
