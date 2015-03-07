package com.android.camera.manager;

import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.android.camera.AnimationController;
import com.android.camera.Camera;
import com.android.camera.FeatureSwitcher;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.ui.NaviLineImageView;
import com.android.camera.ui.ProgressIndicator;
import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateLayout.OnSizeChangedListener;

public class GestureShotViewManager extends ViewManager {
    private static final String TAG = "GestureShotViewManager";
    private static final boolean LOG = Log.LOGV;
    
    private Animation mCountDownAnim;
    private TextView mRemainingSecondsView;
    private String mCountSeconds;
    
    public GestureShotViewManager(Camera context) {
        super(context);
    }
    @Override
    public View getView() {
    	mCountDownAnim = AnimationUtils.loadAnimation(getContext(), R.anim.count_down_exit);
        View view = inflate(R.layout.count_down_to_capture);
        mRemainingSecondsView = (TextView) view.findViewById(R.id.remaining_seconds);
        return view;
    }
    
    public void setCountSeconds(String countSeconds) {
    	mCountSeconds = countSeconds;
    	mRemainingSecondsView.setText(mCountSeconds);
    }
    
    public void startAnimation() {
        // Fade-out animation
        mCountDownAnim.reset();
        mRemainingSecondsView.clearAnimation();
        mRemainingSecondsView.startAnimation(mCountDownAnim);
    }
    
    public void endAnimation() {
    	mRemainingSecondsView.setText(null);
    	mCountDownAnim.reset();
        mRemainingSecondsView.clearAnimation();
    }
}
