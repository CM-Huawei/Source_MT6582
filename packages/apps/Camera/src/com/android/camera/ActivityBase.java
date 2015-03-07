package com.android.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import com.android.camera.manager.MMProfileManager;

import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AppBridge;
import com.android.gallery3d.app.FilmstripPage;
import com.android.gallery3d.app.GalleryActionBar;
import com.android.gallery3d.ui.GestureRecognizer.Listener;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.ScreenNail;
import com.mediatek.gallery3d.drm.DrmHelper;
import com.mediatek.gallery3d.stereo.StereoHelper;
import com.mediatek.gallery3d.util.MediatekFeature;
import com.mediatek.common.featureoption.FeatureOption;

import com.android.camera.R;

/**
 * Superclass of Camera and VideoCamera activities.
 */
public abstract class ActivityBase extends AbstractGalleryActivity
        implements View.OnLayoutChangeListener {

    private static final String TAG = "ActivityBase";
    private static final int CAMERA_APP_VIEW_TOGGLE_TIME = 100;  // milliseconds
    private static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
        "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
        "android.media.action.IMAGE_CAPTURE_SECURE";
    // The intent extra for camera from secure lock screen. True if the gallery
    // should only show newly captured pictures. sSecureAlbumId does not
    // increment. This is used when switching between camera, camcorder, and
    // panorama. If the extra is not set, it is in the normal camera mode.
    public static final String SECURE_CAMERA_EXTRA = "secure_camera";
    private static final int LONG_PRESS = 1;
    private static final int SINGLE_TAPUP = 0;

    // The activity is paused. The classes that extend this class should set
    // mPaused the first thing in onResume/onPause.
    protected boolean mPaused;
    private HideCameraAppView mHideCameraAppView;
    private View mSingleTapArea;
    private View mLongPressArea;
    protected GalleryActionBar mActionBar;
    protected MyAppBridge mAppBridge;
    protected CameraScreenNail mCameraScreenNail; // This shows camera preview.
    // The view containing only camera related widgets like control panel,
    // indicator bar, focus indicator and etc.
    protected View mCameraAppView;
    protected boolean mShowCameraAppView = true;
    // mFullScreen used as a flag to indicate the real state of this view
    protected boolean mFullScreen = true;
    // Secure album id. This should be incremented every time the camera is
    // launched from the secure lock screen. The id should be the same when
    // switching between camera, camcorder, and panorama.
    protected static int sSecureAlbumId;
    // True if the camera is started from secure lock screen.
    protected boolean mSecureCamera;
    private static boolean sFirstStartAfterScreenOn = true;
    
    //just for test
    private int mResultCodeForTesting;
    private Intent mResultDataForTesting;
    //3D
    protected boolean mStereoMode;
    
    // close activity when screen turns off
    private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
            Log.d(TAG, "mScreenOffReceiver receive");
        }
    };

    private static BroadcastReceiver sScreenOffReceiver;
    private static class ScreenOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            sFirstStartAfterScreenOn = true;
        }
    }

    public static boolean isFirstStartAfterScreenOn() {
        return sFirstStartAfterScreenOn;
    }

    public static void resetFirstStartAfterScreenOn() {
        sFirstStartAfterScreenOn = false;
    }
    
    
    @Override
    public void onCreate(Bundle icicle) {
        // M: enable screen shot by suggestion of planner
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.disableToggleStatusBar();
        // Set a theme with action bar. It is not specified in manifest because
        // we want to hide it by default. setTheme must happen before
        // setContentView.
        //
        // This must be set before we call super.onCreate(), where the window's
        // background is removed.
        setTheme(R.style.Theme_Gallery);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        //stop gallery from checking external storage
        mShouldCheckStorageState = false;

        // Check if this is in the secure camera mode.
        Intent intent = getIntent();
        String action = intent.getAction();
        if (INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)) {
            mSecureCamera = true;
            // Use a new album when this is started from the lock screen.
            sSecureAlbumId++;
        } else if (ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mSecureCamera = true;
        } else {
            mSecureCamera = intent.getBooleanExtra(SECURE_CAMERA_EXTRA, false);
        }
        if (mSecureCamera) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mScreenOffReceiver, filter);
            if (sScreenOffReceiver == null) {
                sScreenOffReceiver = new ScreenOffReceiver();
                getApplicationContext().registerReceiver(sScreenOffReceiver, filter);
            }
        }
        
        super.onCreate(icicle);
    }
    
    public boolean isPanoramaActivity() {
        return false;
    }

    @Override
    protected void onResume() {
        mPaused = false;
        super.onResume();
    }

    @Override
    protected void onPause() {
        mPaused = true;
        super.onPause();
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        // getActionBar() should be after setContentView
        mActionBar = new GalleryActionBar(this);
        mActionBar.hide();
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Prevent software keyboard or voice search from showing up.
        if (keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.isLongPress()) { return true; }
        }

        return super.onKeyDown(keyCode, event);
    }

    protected void setResultEx(int resultCode) {
        mResultCodeForTesting = resultCode;
        setResult(resultCode);
    }

    public void setResultEx(int resultCode, Intent data) {
        mResultCodeForTesting = resultCode;
        mResultDataForTesting = data;
        setResult(resultCode, data);
    }

    public int getResultCode() {
        return mResultCodeForTesting;
    }

    public Intent getResultData() {
        return mResultDataForTesting;
    }

    @Override
    protected void onDestroy() {
        if (mSecureCamera) {
            unregisterReceiver(mScreenOffReceiver);
        }
        super.onDestroy();
    }

    public void gotoGallery() {
        // Move the next picture with capture animation. "1" means next.
        mAppBridge.switchWithCaptureAnimation(1);
    }

    // Call this after setContentView.
    protected void createCameraScreenNail(boolean getPictures) {
        mCameraAppView = findViewById(R.id.camera_app_root);
        Bundle data = new Bundle();
        //String path = "/local/all/";
        // Intent mode does not show camera roll. Use 0 as a work around for
        // invalid bucket id.
        // TODO: add support of empty media set in gallery.
        //path += (getPictures ? MediaSetUtils.CAMERA_BUCKET_ID : "0");
        String path = getPictures ? Storage.getCameraScreenNailPath() : "/local/all/0";
        if (mSecureCamera) {
            path = "/secure/all/" + sSecureAlbumId;
        }
        data.putString(FilmstripPage.KEY_MEDIA_SET_PATH, path);
        data.putString(FilmstripPage.KEY_MEDIA_ITEM_PATH, path);
        data.putBoolean(FilmstripPage.KEY_SHOW_WHEN_LOCKED, mSecureCamera);
        // Send an AppBridge to gallery to enable the camera preview.
        mAppBridge = new MyAppBridge();
        data.putParcelable(FilmstripPage.KEY_APP_BRIDGE, mAppBridge);
        if (FeatureSwitcher.isStereo3dEnable()) {
            data.putInt(DrmHelper.DRM_INCLUSION, MediatekFeature.INCLUDE_ALL_STEREO_MEDIA);
        }
        getStateManager().startState(FilmstripPage.class, data);
        mCameraScreenNail = mAppBridge.getCameraScreenNail();
        mAppBridge.setSwipingEnabled(FeatureSwitcher.isSlideEnabled());
    }
     public void setPath(String setPath){
         mAppBridge.setCameraPath(setPath);
     }
    
    public int getSecureAlbumCount() {
        int num = 0;
        num = mAppBridge.getSecureAlbumCount();
        Log.i(TAG, "getSecureAlbumCount num = " + num);
        return num;
    }
     
    private class HideCameraAppView implements Runnable {
        @Override
        public void run() {
            // We cannot set this as GONE because we want to receive the
            // onLayoutChange() callback even when we are invisible.
            mCameraAppView.setVisibility(View.INVISIBLE);
        }
    }

    protected void updateCameraAppView() {
        if (mShowCameraAppView) {
            mCameraAppView.setVisibility(View.VISIBLE);
            // The "transparent region" is not recomputed when a sibling of
            // SurfaceView changes visibility (unless it involves GONE). It's
            // been broken since 1.0. Call requestLayout to work around it.
            mCameraAppView.requestLayout();
            // withEndAction(null) prevents the pending end action
            // mHideCameraAppView from being executed.
            mCameraAppView.animate()
                    .setDuration(CAMERA_APP_VIEW_TOGGLE_TIME)
                    .withLayer().alpha(1).withEndAction(null);
        } else {
            mCameraAppView.animate()
                    .setDuration(CAMERA_APP_VIEW_TOGGLE_TIME)
                    .withLayer().alpha(0).withEndAction(mHideCameraAppView);
        }
    }

    protected void updateCameraAppViewIfNeed() {
        int visibility = (mCameraAppView == null ? View.VISIBLE : mCameraAppView.getVisibility());
        Log.d(TAG, "updateCameraAppViewIfNeed() mShowCameraAppView=" + mShowCameraAppView
                + ", visibility=" + visibility);
        if ((mShowCameraAppView && View.VISIBLE != visibility)
                || (!mShowCameraAppView && View.VISIBLE == visibility)) {
            updateCameraAppView();
            onAfterFullScreeChanged(mShowCameraAppView);
        }
    }

    private void onFullScreenChanged(boolean full) {
        Log.i(TAG, "onFullScreenChanged(" + full + ") mShowCameraAppView=" + mShowCameraAppView);
        onFullScreenChanged(full, 0xFFFF);
    }
    
    private void onFullScreenChanged(boolean full, int type) {
        mFullScreen = full;
        //don't change camera app visibility for scale animation
        boolean scaleAnimation = (type & PhotoView.Listener.FULLSCREEN_TYPE_MINIMAL_SCALE) == 0;
        Log.i(TAG, "onFullScreenChanged(" + full + ", " + type + ") mShowCameraAppView=" + mShowCameraAppView
                + ", mPaused=" + mPaused + ", isFinishing()=" + isFinishing()
                + ", scaleAnimation=" + scaleAnimation);
        if (mShowCameraAppView == full || scaleAnimation) {
            return;
        }
        mShowCameraAppView = full;
        if (mPaused || isFinishing()) {
            return;
        }
        // Initialize the animation.
        if (mHideCameraAppView == null) {
            mHideCameraAppView = new HideCameraAppView();
            mCameraAppView.animate()
                .setInterpolator(new DecelerateInterpolator());
        }
        updateCameraAppView();
        onAfterFullScreeChanged(full);
    }
    
    protected abstract void onAfterFullScreeChanged(boolean full);
    public boolean isFullScreen() {
//        return mShowCameraAppView;
        return mFullScreen;
    }

    @Override
    public GalleryActionBar getGalleryActionBar() {
        return mActionBar;
    }

    // Preview frame layout has changed.
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (mAppBridge == null) { return; }

        if (left == oldLeft && top == oldTop && right == oldRight
                && bottom == oldBottom) {
            return;
        }

        int width = right - left;
        int height = bottom - top;
        MMProfileManager.triggerProfileLayoutChange("onLayoutChange(left=" + left
                + ", top=" + top + ", right=" + right + ", bottom=" + bottom + ")");
        if (Util.getDisplayRotation(this) % 180 == 0) {
            mCameraScreenNail.setPreviewFrameLayoutSize(width, height);
        } else {
            // Swap the width and height. Camera screen nail draw() is based on
            // natural orientation, not the view system orientation.
            mCameraScreenNail.setPreviewFrameLayoutSize(height, width);
        }

        // Find out the coordinates of the preview frame relative to GL
        // root view.
        View root = (View) getGLRoot();
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        v.getLocationInWindow(viewLocation);

        int l = viewLocation[0] - rootLocation[0];
        int t = viewLocation[1] - rootLocation[1];
        int r = l + width;
        int b = t + height;
//        // M: make sure the width is even, because the odd will make 
//        // Camera non-center in Gallery2.
//        if ((l + r) % 2 == 1) {
//            r--;
//        }
        Rect frame = new Rect(l, t, r, b);
        MMProfileManager.triggerProfileLayoutChange(
                "call mAppBridge.setCameraRelativeFrame(" + frame.toString() + ")");
        mAppBridge.setCameraRelativeFrame(frame);
        if (!frame.equals(mOldCameraRelativeFrame)) {
            mCameraScreenNail.setOnLayoutChanged(true);
            //when RelativeFrame changed , tell CameraScreenNail set New size to SurfaceTextureScreenNail 
            // make sure notifyScreenNailChanged is called
            notifyScreenNailChanged();
            Log.e(TAG, "onLayoutChange: notifyScreenNailChanged called");
        }
        Log.d(TAG, "onLayoutChange() frame=" + frame + ", mOldCameraRelativeFrame=" + mOldCameraRelativeFrame);
        mOldCameraRelativeFrame = frame;
    }
    private Rect mOldCameraRelativeFrame; // avoid changed

    protected void setSingleTapUpListener(View singleTapArea) {
        mSingleTapArea = singleTapArea;
    }

    protected void setLongPressListener(View singleTapArea) {
        mLongPressArea = singleTapArea;
    }
    private boolean onSingleTapUp(int x, int y) {
        // Ignore if listener is null or the camera control is invisible.
        return onTouchScreen(x, y, mSingleTapArea, SINGLE_TAPUP);
    }

    private boolean onLongPress(int x, int y) {
        // Ignore if listener is null or the camera control is invisible.
        return onTouchScreen(x, y, mLongPressArea, LONG_PRESS);
    }

    private boolean onTouchScreen(int x, int y, View area, int index) {
        // Ignore if listener is null or the camera control is invisible.
        if (area == null || !mShowCameraAppView) {
            return false;
        }

        int[] relativeLocation = Util.getRelativeLocation((View) getGLRoot(),
                area);
        x -= relativeLocation[0];
        y -= relativeLocation[1];
        if (x >= 0 && x < area.getWidth() && y >= 0 && y < area.getHeight()) {
            if (index == LONG_PRESS) {
                onLongPress(area, x, y);
            } else {
                onSingleTapUp(area, x, y);
            }
            return true;
        }
        onSingleTapUpBorder(mCameraAppView, x, y);
        return true;
    }

    protected void onSingleTapUp(View view, int x, int y) {
    }

    protected void onLongPress(View view, int x, int y) {
    }
    protected void onSingleTapUpBorder(View view, int x, int y) {
    }

    protected void setSwipingEnabled(boolean enabled) {
        mAppBridge.setSwipingEnabled(enabled);
    }

    protected void notifyScreenNailChanged() {
        mAppBridge.notifyScreenNailChanged();
    }
    
    public Listener setGestureListener(Listener listener) {
        Listener old = mAppBridge.setGestureListener(listener);
        Log.d(TAG, "setGestureListener(" + listener + ") return " + old);
        return old;
    }

    protected void onPreviewTextureCopied() {
    }
    
    protected  void  restoreSwitchCameraState() {
        
    }
    protected void setOritationTag (boolean lock, int orientationNum) {
        mAppBridge.setOritationTag(lock, orientationNum);
    }
    
    protected void addSecureAlbumItemIfNeeded(boolean isVideo, Uri uri) {
        if (mSecureCamera) {
            int id = Integer.parseInt(uri.getLastPathSegment());
            mAppBridge.addSecureAlbumItem(isVideo, id);
        }
    }

    public String getStereo3DType() {
        return null;
    }
    public boolean isSecureCamera() {
        return mSecureCamera;
    }

    //////////////////////////////////////////////////////////////////////////
    //  The is the communication interface between the Camera Application and
    //  the Gallery FilmstripPage.
    //////////////////////////////////////////////////////////////////////////

    class MyAppBridge extends AppBridge implements CameraScreenNail.Listener {
        private CameraScreenNail mCameraScreenNail;
        private Server mServer;

        @Override
        public ScreenNail attachScreenNail() {
            if (mCameraScreenNail == null) {
                mCameraScreenNail = new CameraScreenNail(this, ActivityBase.this);
            }
            return mCameraScreenNail;
        }

        @Override
        public void detachScreenNail() {
            mCameraScreenNail = null;
        }

        public CameraScreenNail getCameraScreenNail() {
            return mCameraScreenNail;
        }

        // Return true if the tap is consumed.
        @Override
        public boolean onSingleTapUp(int x, int y) {
            return ActivityBase.this.onSingleTapUp(x, y);
        }

        @Override
        public boolean onLongPress(int x, int y) {
            return ActivityBase.this.onLongPress(x, y);
        }
        // This is used to notify that the screen nail will be drawn in full screen
        // or not in next draw() call.
        @Override
        public void onFullScreenChanged(boolean full) {
            ActivityBase.this.onFullScreenChanged(full);
        }

        @Override
        public void requestRender() {
            getGLRoot().requestRenderForced();
        }

        @Override
        public void onPreviewTextureCopied() {
            ActivityBase.this.onPreviewTextureCopied();
        }
        
        @Override
        public void restoreSwitchCameraState() {
            ActivityBase.this.restoreSwitchCameraState();
        }

        @Override
        public void setServer(Server s) {
            mServer = s;
        }

        @Override
        public boolean isPanorama() {
            return ActivityBase.this.isPanoramaActivity();
        }
        
        public boolean isStaticCamera() {
            return false;
        }
        
        public void addSecureAlbumItem(boolean isVideo, int id) {
            if (mServer != null) mServer.addSecureAlbumItem(isVideo, id);
        }

        private void setCameraRelativeFrame(Rect frame) {
            if (mServer != null) { mServer.setCameraRelativeFrame(frame); }
        }

        private void switchWithCaptureAnimation(int offset) {
            if (mServer != null) {
                if (mServer.switchWithCaptureAnimation(offset)) {
                    Log.d(TAG, "switchWithCaptureAnimation mFullScreen=" + mFullScreen);
                    mFullScreen = false; 
                }
            }
        }

        private void setSwipingEnabled(boolean enabled) {
            if (mServer != null) { mServer.setSwipingEnabled(enabled); }
        }

        private void notifyScreenNailChanged() {
            if (mServer != null) {
                MMProfileManager.triggerNotifyServerSelfChange();
                mServer.notifyScreenNailChanged();
            }
        }
        
        private Listener setGestureListener(Listener listener) {
            if (mServer != null) {
                return mServer.setGestureListener(listener);
            }
            return null;
        }
        
        @Override
        public void onFullScreenChanged(boolean full, int type) {
            ActivityBase.this.onFullScreenChanged(full, type);
        }
        public void setCameraPath(String setPath) {
            if (mServer != null) {
                mServer.setCameraPath(setPath);
            }
        }

        public void setOritationTag(boolean lock, int oritationNum) {
            mServer.setOritationTag(lock, oritationNum);
        }
        
        public int getSecureAlbumCount() {
            if (mServer != null) {
                return mServer.getSecureAlbumCount();
            }
            return 0;
        }
    }
}
