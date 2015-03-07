/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Trace;

import com.android.camera.manager.MMProfileManager;

import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.RawTexture;
import com.android.gallery3d.ui.SurfaceTextureScreenNail;

/*
 * This is a ScreenNail which can displays camera preview.
 */
public class CameraScreenNail extends SurfaceTextureScreenNail {
    private static final String TAG = "CameraScreenNail";
    private static final int ANIM_NONE = 0;
    // Capture animation is about to start.
    private static final int ANIM_CAPTURE_START = 1;
    // Capture animation is running.
    private static final int ANIM_CAPTURE_RUNNING = 2;
    // Switch camera animation needs to copy texture.
    private static final int ANIM_SWITCH_COPY_TEXTURE = 3;
    // Switch camera animation shows the initial feedback by darkening the
    // preview.
    private static final int ANIM_SWITCH_DARK_PREVIEW = 4;
    // Switch camera animation is waiting for the first frame.
    private static final int ANIM_SWITCH_WAITING_FIRST_FRAME = 5;
    // Switch camera animation is about to start.
    private static final int ANIM_SWITCH_START = 6;
    // Switch camera animation is running.
    private static final int ANIM_SWITCH_RUNNING = 7;
    // M:Add for launch camera trace profile
    private static final int END_TRACE = 2;

    private boolean mVisible;
    // True if first onFrameAvailable has been called. If screen nail is drawn
    // too early, it will be all white.
    private boolean mFirstFrameArrived;
    // M:Add for launch camera trace profile
    private int mLaunchCameraTrace = 0;
    private Listener mListener;
    private SwitchActorStateListener mStateListener;
    private final float[] mTextureTransformMatrix = new float[16];

    // Animation.
    private CaptureAnimManager mCaptureAnimManager;
    private SwitchAnimManager mSwitchAnimManager = new SwitchAnimManager();
    private int mAnimState = ANIM_NONE;
    private RawTexture mAnimTexture;
    // Some methods are called by GL thread and some are called by main thread.
    // This protects mAnimState, mVisible, and surface texture. This also makes
    // sure some code are atomic. For example, requestRender and setting
    // mAnimState.
    private Object mLock = new Object();
    private boolean mDrawable = true;
    private boolean mLayoutChanged = true;
    private int mX;
    private int mY;
    private int mWidth;
    private int mHeight;
    private int mScreenNailWidth;
    private int mScreenNailHeight;
    
    private RawTexture mOriginSizeTexture;
    private int mSwitchActorState = ANIM_SIZE_CHANGE_NONE;
    private static final int ANIM_SIZE_CHANGE_NONE = 0;
    private static final int ANIM_SIZE_CHANGE_START = 1;
    private static final int ANIM_SIZE_CHANGE_RUNNING = 2;
    private boolean mAcquireTexture = false;
    private final DrawClient mDefaultDraw = new DrawClient() {
        @Override
        public void onDraw(GLCanvas canvas, int x, int y, int width, int height) {
            CameraScreenNail.super.draw(canvas, x, y, width, height);
        }

        @Override
        public boolean requiresSurfaceTexture() {
            return true;
        }

        @Override
        public RawTexture copyToTexture(GLCanvas c, RawTexture texture, int w, int h) {
            // We shouldn't be here since requireSurfaceTexture() returns true.
            return null;
        }
    };
    private DrawClient mDraw = mDefaultDraw;
    
    

    public interface Listener {
        void requestRender();
        // Preview has been copied to a texture.
        void onPreviewTextureCopied();
        
        void restoreSwitchCameraState();
    }
    public interface SwitchActorStateListener {
    	void onStateChanged(int state);
    }
    
    public interface DrawClient {
        void onDraw(GLCanvas canvas, int x, int y, int width, int height);

        boolean requiresSurfaceTexture();
        // The client should implement this if requiresSurfaceTexture() is false;
        RawTexture copyToTexture(GLCanvas c, RawTexture texture, int width, int height);
    }

    public CameraScreenNail(Listener listener,Context ctx) {
        mListener = listener;
        mCaptureAnimManager = new CaptureAnimManager(ctx);
    }

    @Override
    public void acquireSurfaceTexture() {
        synchronized (mLock) {
            mFirstFrameArrived = false;
            super.acquireSurfaceTexture();
            mAnimTexture = new RawTexture(getWidth(), getHeight(), true);
            mOriginSizeTexture = new RawTexture(getWidth(), getHeight(), true);
        }
    }

    @Override
    public void releaseSurfaceTexture() {
        synchronized (mLock) {
            if (mAcquireTexture) {
                mAcquireTexture = false;
                mLock.notifyAll();
            } else {
                if (super.getSurfaceTexture() != null) {
                    super.releaseSurfaceTexture();
                }
                mAnimState = ANIM_NONE; // stop the animation
            }
        }
    }

    public void copyTexture() {
        synchronized (mLock) {
            mListener.requestRender();
            mAnimState = ANIM_SWITCH_COPY_TEXTURE;
        }
    }

    public void copyOriginSizeTexture() {
        synchronized (mLock) {
            //run copyOriginSizeTexture is a new start, 
            //mSwitchActorState whatever it is should be set ANIM_SIZE_CHANGE_START.
            if (mFirstFrameArrived) {
                mListener.requestRender();
                mSwitchActorState = ANIM_SIZE_CHANGE_START;
            }
        }
    }

    public void stopSwitchActorAnimation() {
        synchronized (mLock) {
            if (mSwitchActorState != ANIM_SIZE_CHANGE_NONE) {
                mSwitchActorState = ANIM_SIZE_CHANGE_NONE;
                mListener.requestRender();
            }
        }
    }

    public void animateSwitchCamera() {
        Log.d(TAG, "animateSwitchCamera");
        MMProfileManager.startProfileAnimateSwitchCamera();
        synchronized (mLock) {
            if (mAnimState == ANIM_SWITCH_DARK_PREVIEW) {
                // Do not request render here because camera has been just
                // started. We do not want to draw black frames.
                mAnimState = ANIM_SWITCH_WAITING_FIRST_FRAME;
            }
        }
    }

    public void animateCapture(int animOrientation) {
        MMProfileManager.startProfileAnimateCapture();
        synchronized (mLock) {
            mCaptureAnimManager.setOrientation(animOrientation);
            mCaptureAnimManager.animateFlashAndSlide();
            mListener.requestRender();
            mAnimState = ANIM_CAPTURE_START;
        }
    }
    
    public void animateFlash(int displayRotation) {
        synchronized (mLock) {
            mCaptureAnimManager.setOrientation(displayRotation);
            mCaptureAnimManager.animateFlash();
            mListener.requestRender();
            mAnimState = ANIM_CAPTURE_START;
        }
    }

    public void animateSlide() {
        synchronized (mLock) {
            mCaptureAnimManager.animateSlide();
            mListener.requestRender();
        }
    }
    
    public RawTexture getAnimationTexture() {
        return mAnimTexture;
    }

    public void directDraw(GLCanvas canvas, int x, int y, int width, int height) {
        if (mSwitchActorState == ANIM_SIZE_CHANGE_RUNNING) {
            mOriginSizeTexture.draw(canvas, x, y, width, height);
        } else {
            DrawClient draw;
            synchronized (mLock) {
                draw = mDraw;
            }
            draw.onDraw(canvas, x, y, width, height);
        }
    }
    
    public void setDraw(DrawClient draw) {
        synchronized (mLock) {
            if (draw == null) {
                mDraw = mDefaultDraw;
            } else {
                mDraw = draw;
            }
        }
        mListener.requestRender();
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        MMProfileManager.startProfileDrawScreenNail();
        
        synchronized (mLock) {
            if (!mVisible) {
                mVisible = true;
            }
            SurfaceTexture surfaceTexture = getSurfaceTexture();
            if (mDraw.requiresSurfaceTexture() && (surfaceTexture == null || !mFirstFrameArrived)) {
                MMProfileManager.stopProfileDrawScreenNail();
                return;
            }

            if (mAnimState == ANIM_NONE && mSwitchActorState == ANIM_SIZE_CHANGE_NONE) {
                MMProfileManager.triggerSuperDrawNoAnimate();
                directDraw(canvas, x, y, width, height);
                MMProfileManager.stopProfileDrawScreenNail();
                if (mStateListener != null) {
                    mStateListener.onStateChanged(ANIM_SIZE_CHANGE_NONE);
                }
                return;
            }

            switch (mAnimState) {
                case ANIM_SWITCH_COPY_TEXTURE:
                    copyPreviewTexture(canvas);
                    mSwitchAnimManager.setReviewDrawingSize(width, height);
                    mListener.onPreviewTextureCopied();
                    mAnimState = ANIM_SWITCH_DARK_PREVIEW;
                    // The texture is ready. Fall through to draw darkened
                    // preview.
                case ANIM_SWITCH_DARK_PREVIEW:
                case ANIM_SWITCH_WAITING_FIRST_FRAME:
                    // Consume the frame. If the buffers are full,
                    // onFrameAvailable will not be called. Animation state
                    // relies on onFrameAvailable.
                    surfaceTexture.updateTexImage();
                    mSwitchAnimManager.drawDarkPreview(canvas, x, y, width,
                            height, mAnimTexture);
                    return;
                case ANIM_SWITCH_START:
                    mSwitchAnimManager.startAnimation();
                    mAnimState = ANIM_SWITCH_RUNNING;
                    break;
                case ANIM_CAPTURE_START:
                    copyPreviewTexture(canvas);
                    mCaptureAnimManager.startAnimation();
                    mAnimState = ANIM_CAPTURE_RUNNING;
                    break;
            }

            if (mAnimState == ANIM_CAPTURE_RUNNING || mAnimState == ANIM_SWITCH_RUNNING) {
                boolean drawn;
                if (mAnimState == ANIM_CAPTURE_RUNNING) {
                    drawn = mCaptureAnimManager.drawAnimation(canvas, this, mAnimTexture,
                            x, y, width, height);
                } else {
                    drawn = mSwitchAnimManager.drawAnimation(canvas, x, y,
                            width, height, this, mAnimTexture);
                }
                if (drawn) {
                    mListener.requestRender();
                } else {
                    // Continue to the normal draw procedure if the animation is
                    // not drawn.
                    if(mAnimState == ANIM_CAPTURE_RUNNING) {
                      MMProfileManager.stopProfileAnimateCapture();  
                    } else if(mAnimState == ANIM_SWITCH_RUNNING) {
                      MMProfileManager.stopProfileAnimateSwitchCamera();
                    }
                    mAnimState = ANIM_NONE;
                    // draw origin frame when size changed
                    if (mSwitchActorState == ANIM_SIZE_CHANGE_NONE) {
                        MMProfileManager.triggerSuperDrawOriginFrame();
                        directDraw(canvas, x, y, width, height);
                    }
                }
            }

            switch (mSwitchActorState) {
                case ANIM_SIZE_CHANGE_START:
                    copyOriginSizePreviewTexture(canvas);
                    mX = x;
                    mY = y;
                    mWidth = width;
                    mHeight = height;
                    mSwitchActorState = ANIM_SIZE_CHANGE_RUNNING;
                    break;
                case ANIM_SIZE_CHANGE_RUNNING:
                    if (mDrawable && (mWidth != width || mHeight != height) && (mAnimState != ANIM_CAPTURE_RUNNING)) {
                        mSwitchActorState = ANIM_SIZE_CHANGE_NONE;
                        MMProfileManager.triggerSuperDrawSizeChange();
                        directDraw(canvas, x, y, width, height);
                    }
                    break;
            }
            if (mAnimState == ANIM_NONE && mSwitchActorState == ANIM_SIZE_CHANGE_RUNNING) {
                mOriginSizeTexture.draw(canvas, mX, mY, mWidth, mHeight);
            }
        } // mLock
        MMProfileManager.stopProfileDrawScreenNail();
    }

    public void setSwitchActorStateListener(SwitchActorStateListener listener) {
    	mStateListener = listener;
    }
    
    private void copyPreviewTexture(GLCanvas canvas) {
        // For Mock Camera, do not copy texture, as SW OpenGL solution
        // does not support some function.
        if (com.mediatek.camera.FrameworksClassFactory.isMockCamera()) {
            return;
        }
        if (!mDraw.requiresSurfaceTexture()) {
            mAnimTexture =  mDraw.copyToTexture(
                    canvas, mAnimTexture, getTextureWidth(), getTextureHeight());
        } else {
            int width = mAnimTexture.getWidth();
            int height = mAnimTexture.getHeight();
            canvas.beginRenderTarget(mAnimTexture);
            // Flip preview texture vertically. OpenGL uses bottom left point
            // as the origin (0, 0).
            canvas.translate(0, height);
            canvas.scale(1, -1, 1);
            getSurfaceTexture().getTransformMatrix(mTextureTransformMatrix);
            canvas.drawTexture(mExtTexture,
                mTextureTransformMatrix, 0, 0, width, height);
           canvas.endRenderTarget();
        }
    }

    private void copyOriginSizePreviewTexture(GLCanvas canvas) {
        int width = mOriginSizeTexture.getWidth();
        int height = mOriginSizeTexture.getHeight();
        canvas.beginRenderTarget(mOriginSizeTexture);
        // Flip preview texture vertically. OpenGL uses bottom left point
        // as the origin (0, 0).
        canvas.translate(0, height);
        canvas.scale(1, -1, 1);
        getSurfaceTexture().getTransformMatrix(mTextureTransformMatrix);
        canvas.drawTexture(mExtTexture,
                mTextureTransformMatrix, 0, 0, width, height);
        getSurfaceTexture().updateTexImage();
        canvas.endRenderTarget();
    }

    @Override
    public void noDraw(GLCanvas canvas) {
        synchronized (mLock) {
            mVisible = false;
            mListener.restoreSwitchCameraState();
            mAnimState = ANIM_NONE;
        }
    }

    @Override
    public void recycle() {
        synchronized (mLock) {
            mVisible = false;
        }
    }

    private long mLastFrameArriveTime;
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        MMProfileManager.triggerFrameAvailable();
        // M: add for launch camera trace profile
        if (mLaunchCameraTrace <= END_TRACE) {
            mLaunchCameraTrace ++;
            if (mLaunchCameraTrace == END_TRACE) {
                Trace.traceCounter(Trace.TRACE_TAG_CAMERA, "AppUpdate", 1);
            }
        }
        if (mDebugLevel2) {
            Log.d(TAG, "[Preview] onFrameAvailable");
        }
        setDrawable(true);
        if (!mFirstFrameArrived) {
            MMProfileManager.triggerFirstFrameAvailable();
            if (mFrameListener != null) {
                mFrameListener.onFirstFrameArrived();
            }
            if (!mVisible) {
                // We need to ask for re-render if the SurfaceTexture receives a new frame.
                MMProfileManager.triggerRequestRender();
                mListener.requestRender();
            }
            //M: Add for CMCC camera capture test case
            Log.i(TAG, "[CMCC Performance test][Launcher][Camera] Start Camera end ["
                        + System.currentTimeMillis() + "]");
            Log.i(TAG, "onFrameAvailable is called(first time) " + this);
        }
        synchronized (mLock) {
            if (mDebug && !mFirstFrameArrived) {
                mRequestStartTime = System.currentTimeMillis() - 1; // avoid divide by zero
                mLastFrameArriveTime = mRequestStartTime;
                mDrawStartTime = mRequestStartTime;
                mRequestCount = 0;
                mDrawFrameCount = 0;
            }
            mFirstFrameArrived = true;
            if (mVisible) {
                if (mAnimState == ANIM_SWITCH_WAITING_FIRST_FRAME) {
                    mAnimState = ANIM_SWITCH_START;
                }
                if (mDebug) {
                    long currentTime = 0;
                    if (mDebugLevel2) {
                        currentTime = System.currentTimeMillis();
                        int frameInterval = (int)(currentTime - mLastFrameArriveTime);
                        if (frameInterval > 50) {
                            Log.d(TAG, "[Preview] onFrameAvailable, request render interval too long = " + frameInterval);
                        }
                        mLastFrameArriveTime = currentTime;
                    }
                    mRequestCount++;
                    if (mRequestCount % INTERVALS == 0) {
                        if (!mDebugLevel2) {
                            currentTime = System.currentTimeMillis();
                        }
                        int intervals = (int) (currentTime - mRequestStartTime);
                        Log.d(TAG, "[Preview] Request render, fps = "
                                + (mRequestCount * 1000.0f) / intervals + " in last " + intervals + " millisecond.");
                        mRequestStartTime = currentTime;
                        mRequestCount = 0;
                    }
                }
                // We need to ask for re-render if the SurfaceTexture receives a new
                // frame.
                MMProfileManager.triggerRequestRender();
                mListener.requestRender();
            }

        }
     // M: add for launch camera trace profile
        if (mLaunchCameraTrace == END_TRACE) {
            Trace.traceCounter(Trace.TRACE_TAG_CAMERA, "AppUpdate", 0);
            mLaunchCameraTrace = mLaunchCameraTrace + 1;
        }

    }

    
    private int getTextureWidth() {
        return super.getWidth();
    }

    private int getTextureHeight() {
        return super.getHeight();
    }
    
    // We need to keep track of the size of preview frame on the screen because
    // it's needed when we do switch-camera animation. See comments in
    // SwitchAnimManager.java. This is based on the natural orientation, not the
    // view system orientation.
    public void setPreviewFrameLayoutSize(int width, int height) {
        synchronized (mLock) {
            mSwitchAnimManager.setPreviewFrameLayoutSize(width, height);
        }
    }

    public boolean enableDebug() {
        return mDebug;
    }
    
    /// M: for loading animation @{
    public interface FrameListener {
        void onFirstFrameArrived();
    }
    
    private FrameListener mFrameListener;
    public void setFrameListener(FrameListener listener) {
        mFrameListener = listener;
    }
    /// @}

    public void setDrawable(boolean drawable) {
        Log.d(TAG, "setDrawable drawable = " + drawable);
        mDrawable = drawable;
    }
    
    //only when relative frame ready, 
    //new size can set to SurfaceTextureScreenNail during swtich Actor
    public void setOnLayoutChanged(boolean changed) {
        Log.d(TAG, "setOnLayoutChanged changed = " + changed);
        mLayoutChanged = changed;
        if (mLayoutChanged) {
            setSize(mScreenNailWidth, mScreenNailHeight);
        }
    }
    
    public boolean setScreenNailSize(int width, int height) {
        mScreenNailWidth = width;
        mScreenNailHeight = height;
        //during switch actor, should hold new size
        //when layout changed, set the new size to SurfaceTextureScreenNail
        if (mDrawable || !mFirstFrameArrived) {
            setSize(mScreenNailWidth, mScreenNailHeight);
            return true;
        }
        return false;
    }
}
