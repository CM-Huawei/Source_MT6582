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
import android.content.res.Resources;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.camera.R;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.NinePatchTexture;
import com.android.gallery3d.glrenderer.RawTexture;

/**
 * Class to handle the capture animation.
 */
public class CaptureAnimManager {
    private static final String TAG = "CaptureAnimManager";
    private final Interpolator mSlideInterpolator = new AccelerateDecelerateInterpolator();

    private int mAnimOrientation;  // Could be 0, 90, 180 or 270 degrees.
    private long mAnimStartTime;  // milliseconds.
    private float mCenterX;  // The center of the whole view including preview and review.
    private float mCenterY;
    private int mDrawWidth;
    private int mDrawHeight;
    
    private Animation mAnimation;
    public int getAnimationDuration() {
        return mAnimation.getAnimationDuration();
    }

    /* preview: camera preview view.
     * review: view of picture just taken.
     */
    public CaptureAnimManager(Context ctx) {
        if(FeatureSwitcher.isMtkCaptureAnimationEnable()) {
            mAnimation = new FadeInFadeOutAnimation(ctx);
        } else {
            mAnimation = new FlashHoldAnimation(ctx);
        }
    }
    
    public void setOrientation(int animOrientation) {
        mAnimation.setOrientation(animOrientation);
    }
    
    // x, y, w and h: the rectangle area where the animation takes place.
    // transformMatrix: used to show the texture.
    public void startAnimation() {
        mAnimation.startAnimation();
    }
    
    private void setAnimationGeometry(int x, int y, int w, int h) {
        mAnimation.setAnimationGeometry(x, y, w, h);
    }
    
 // Returns true if the animation has been drawn.
    public boolean drawAnimation(GLCanvas canvas, CameraScreenNail preview,
                RawTexture review, int lx, int ly, int lw, int lh) {
        return mAnimation.drawAnimation(canvas, preview, review, lx, ly, lw, lh);
    }

    public void animateSlide() {
        mAnimation.animateSlide();
    }

    public void animateFlash() {
        mAnimation.animateFlash();
    }

    public void animateFlashAndSlide() {
       mAnimation.animateFlashAndSlide();
    }
    
    public abstract class Animation {
        public Animation(Context ctx) {
        }
        void startAnimation() {
            mAnimStartTime = SystemClock.uptimeMillis();
        }
        boolean drawAnimation(GLCanvas canvas, CameraScreenNail preview,
                RawTexture review, int lx, int ly, int lw, int lh) {
            return false;
        }
        void setOrientation(int animOrientation) {
        }
        int getAnimationDuration() {
            return 0;
        }
        void setAnimationGeometry(int x, int y, int w, int h) {
        }
        void animateSlide() {
            
        }

        void animateFlash() {
        }

        void animateFlashAndSlide() {
        }
    }
    
    //add for MTK Camera Capture Animation
    private class  FadeInFadeOutAnimation extends Animation {
        private static final float ZOOM_DELTA = 0.2f;  // The amount of change for zooming out.
        private static final float ZOOM_IN_BEGIN = 1f - ZOOM_DELTA;  // Pre-calculated value for
                                                                     // convenience.
        private static final float CAPTURE_ANIM_DURATION = 700;  // milliseconds.
        private static final float GAP_RATIO = 0.1f;  // The gap between preview and review based
                                                      // on the view dimension.
        private static final float TOTAL_RATIO = 1f + GAP_RATIO;
        
        private final Interpolator mZoomOutInterpolator = new DecelerateInterpolator();
        private final Interpolator mZoomInInterpolator = new AccelerateInterpolator();
        
        private float mCenterDelta;  // The amount of the center will move after whole animation.
        private float mGap;  // mGap = (width or height) * GAP_RATIO. (depends on orientation)
        private float mHalfGap;  // mHalfGap = mGap / 2f.
        
        public FadeInFadeOutAnimation(Context ctx) {
            super(ctx);
        }
        
        @Override
        void setOrientation(int animOrientation) {
            super.setOrientation(animOrientation);
            mAnimOrientation = animOrientation;
        }
        @Override
        void setAnimationGeometry(int x, int y, int w, int h) {
            // Set the views to the initial positions.
            mDrawWidth = w;
            mDrawHeight = h;
            switch (mAnimOrientation) {
             case 0:  // Preview is on the left.
                 mGap = w * GAP_RATIO;
                 mHalfGap = mGap / 2f;
                 mCenterX = x - mHalfGap;
                 mCenterDelta = w * (TOTAL_RATIO);
                 mCenterY = y + h / 2f;
                 break;
             case 90:  // Preview is below.
                 mGap = h * GAP_RATIO;
                 mHalfGap = mGap / 2f;
                 mCenterY = y + h + mHalfGap;
                 mCenterDelta = -h * (TOTAL_RATIO);
                 mCenterX = x + w / 2f;
                 break;
             case 180:  // Preview on the right.
                 mGap = w * GAP_RATIO;
                 mHalfGap = mGap / 2f;
                 mCenterX = x + mHalfGap;
                 mCenterDelta = -w * (TOTAL_RATIO);
                 mCenterY = y + h / 2f;
                 break;
             case 270:  // Preview is above.
                 mGap = h * GAP_RATIO;
                 mHalfGap = mGap / 2f;
                 mCenterY = y - mHalfGap;
                 mCenterDelta = h * (TOTAL_RATIO);
                 mCenterX = x + w / 2f;
                 break;
             }
        }
        @Override
        boolean drawAnimation(GLCanvas canvas, CameraScreenNail preview,
                RawTexture review, int lx, int ly, int lw, int lh) {
            setAnimationGeometry(lx, ly, lw, lh);
            long timeDiff = SystemClock.uptimeMillis() - mAnimStartTime;
            if (timeDiff > CAPTURE_ANIM_DURATION) return false;
            float fraction = timeDiff / CAPTURE_ANIM_DURATION;
            float scale = calculateScale(fraction);
            float centerX = mCenterX;
            float centerY = mCenterY;
            if (mAnimOrientation == 0 || mAnimOrientation == 180) {
                centerX = mCenterX + mCenterDelta * mSlideInterpolator.getInterpolation(fraction);
            } else {
                centerY = mCenterY + mCenterDelta * mSlideInterpolator.getInterpolation(fraction);
            }
            float height = mDrawHeight * scale;
            float width = mDrawWidth * scale;
            int previewX = (int) centerX;
            int previewY = (int) centerY;
            int reviewX = (int) centerX;
            int reviewY = (int) centerY;
            switch (mAnimOrientation) {
                case 0:
                    previewX = Math.round(centerX - width - mHalfGap * scale);
                    previewY = Math.round(centerY - height / 2f);
                    reviewX = Math.round(centerX + mHalfGap * scale);
                    reviewY = previewY;
                    break;
                case 90:
                    previewY = Math.round(centerY + mHalfGap * scale);
                    previewX = Math.round(centerX - width / 2f);
                    reviewY = Math.round(centerY - height - mHalfGap * scale);
                    reviewX = previewX;
                    break;
                case 180:
                    previewX = Math.round(centerX + width + mHalfGap * scale);
                    previewY = Math.round(centerY - height / 2f);
                    reviewX = Math.round(centerX - mHalfGap * scale);
                    reviewY = previewY;
                    break;
                case 270:
                    previewY = Math.round(centerY - height - mHalfGap * scale);
                    previewX = Math.round(centerX - width / 2f);
                    reviewY = Math.round(centerY + mHalfGap * scale);
                    reviewX = previewX;
                    break;
            }
            float alpha = canvas.getAlpha();
            canvas.setAlpha(fraction);
            preview.directDraw(canvas, previewX, previewY, Math.round(width), Math.round(height));
            canvas.setAlpha(alpha);
            review.draw(canvas, reviewX, reviewY, (int) width, (int) height);
            return true;
        }
        // Calculate the zoom factor based on the given time fraction.
        private float calculateScale(float fraction) {
            float value = 1f;
            if (fraction <= 0.5f) {
                // Zoom in for the beginning.
                value = 1f - ZOOM_DELTA * mZoomOutInterpolator.getInterpolation(
                        fraction * 2);
            } else {
                // Zoom out for the last.
                value = ZOOM_IN_BEGIN + ZOOM_DELTA * mZoomInInterpolator.getInterpolation(
                        (fraction - 0.5f) * 2f);
            }
            return value;
        }
    };
    
    //add for MR2 Capture Animation
    private class FlashHoldAnimation extends Animation {
        private Resources mResources;
        private NinePatchTexture mBorder;
        // times mark endpoint of animation phase
        private static final int TIME_FLASH = 200;
        private static final int TIME_HOLD = 400;
        private static final int TIME_SLIDE = 800;
        private static final int TIME_HOLD2 = 800;
        private static final int TIME_SLIDE2 = 800;
        
        private static final int ANIM_BOTH = 0;
        private static final int ANIM_FLASH = 1;
        private static final int ANIM_SLIDE = 2;
        private static final int ANIM_HOLD2 = 3;
        private static final int ANIM_SLIDE2 = 4;
        
        private int mAnimType;
        private int mHoldX;
        private int mHoldY;
        private int mHoldW;
        private int mHoldH;
        private int mOffset;

        private int mMarginRight;
        private int mMarginTop;
        private int mSize;
        private int mShadowSize;
        
        public FlashHoldAnimation(Context ctx) {
            super(ctx);
            mBorder = new NinePatchTexture(ctx, R.drawable.capture_thumbnail_shadow);
            mResources = ctx.getResources();
        }
        @Override
        void animateSlide() {
            if (mAnimType != ANIM_FLASH) {
                return;
            }
            mAnimType = ANIM_SLIDE;
            mAnimStartTime = SystemClock.uptimeMillis();
        }
        @Override
        void animateFlash() {
            mAnimType = ANIM_FLASH;
        }
        @Override
        void animateFlashAndSlide() {
            mAnimType = ANIM_BOTH;
        }
        @Override
        void setOrientation(int animOrientation) {
            super.setOrientation(animOrientation);
            //MTK Camera's thumb nail always in the right bottom
            //mAnimOrientation = (360 - animOrientation) % 360;
            mAnimOrientation = 270;
            animateFlashAndSlide();
        }
        @Override
        void setAnimationGeometry(int x, int y, int w, int h) {
            mMarginRight = mResources.getDimensionPixelSize(R.dimen.capture_margin_right);
            mMarginTop = mResources.getDimensionPixelSize(R.dimen.capture_margin_top);
            mSize = mResources.getDimensionPixelSize(R.dimen.capture_size);
            mShadowSize = mResources.getDimensionPixelSize(R.dimen.capture_border);
            mOffset = mMarginRight + mSize;
            // Set the views to the initial positions.
            mDrawWidth = w;
            mDrawHeight = h;
            mCenterX = x;
            mCenterY = y;
            mHoldW = mSize;
            mHoldH = mSize;
            switch (mAnimOrientation) {
                case 0:  // Preview is on the left.
                    mHoldX = x + w - mMarginRight - mSize;
                    mHoldY = y + mMarginTop;
                    break;
                case 90:  // Preview is below.
                    mHoldX = x + mMarginTop;
                    mHoldY = y + mMarginRight;
                    break;
                case 180:  // Preview on the right.
                    mHoldX = x + mMarginRight;
                    mHoldY = y + h - mMarginTop - mSize;
                    break;
                case 270:  // Preview is above.
                    mHoldX = x + w - mMarginTop - mSize;
                    mHoldY = y + h - mMarginRight - mSize;
                    break;
            }
        }
        
        @Override
        boolean drawAnimation(GLCanvas canvas, CameraScreenNail preview,
                RawTexture review, int lx, int ly, int lw, int lh) {
            setAnimationGeometry(lx, ly, lw, lh);
            long timeDiff = SystemClock.uptimeMillis() - mAnimStartTime;
            // Check if the animation is over
            if (mAnimType == ANIM_SLIDE && timeDiff > TIME_SLIDE2 - TIME_HOLD) return false;
            if (mAnimType == ANIM_BOTH && timeDiff > TIME_SLIDE2) return false;
            // determine phase and time in phase
            int animStep = mAnimType;
            if (mAnimType == ANIM_SLIDE) {
                timeDiff += TIME_HOLD;
            }
            if (mAnimType == ANIM_SLIDE || mAnimType == ANIM_BOTH) {
                if (timeDiff < TIME_HOLD) {
                    animStep = ANIM_FLASH;
                } else if (timeDiff < TIME_SLIDE) {
                    animStep = ANIM_SLIDE;
                    timeDiff -= TIME_HOLD;
                } else if (timeDiff < TIME_HOLD2) {
                    animStep = ANIM_HOLD2;
                    timeDiff -= TIME_SLIDE;
                } else {
                    // SLIDE2
                    animStep = ANIM_SLIDE2;
                    timeDiff -= TIME_HOLD2;
                }
            }
            if (animStep == ANIM_FLASH) {
                review.draw(canvas, (int) mCenterX, (int) mCenterY, mDrawWidth, mDrawHeight);
                if (timeDiff < TIME_FLASH) {
                    float f = 0.3f - 0.3f * timeDiff / TIME_FLASH;
                    int color = Color.argb((int) (255 * f), 255, 255, 255);
                    canvas.fillRect(mCenterX, mCenterY, mDrawWidth, mDrawHeight, color);
                }
            } else if (animStep == ANIM_SLIDE) {
                float fraction = mSlideInterpolator.getInterpolation((float) (timeDiff) / (TIME_SLIDE - TIME_HOLD));
                float x = mCenterX;
                float y = mCenterY;
                float w = 0;
                float h = 0;
                x = interpolate(mCenterX, mHoldX, fraction);
                y = interpolate(mCenterY, mHoldY, fraction);
                w = interpolate(mDrawWidth, mHoldW, fraction);
                h = interpolate(mDrawHeight, mHoldH, fraction);
                preview.directDraw(canvas, (int) mCenterX, (int) mCenterY, mDrawWidth, mDrawHeight);
                review.draw(canvas, (int) x, (int) y, (int) w, (int) h);
            } else if (animStep == ANIM_HOLD2) {
                preview.directDraw(canvas, (int) mCenterX, (int) mCenterY, mDrawWidth, mDrawHeight);
                review.draw(canvas, mHoldX, mHoldY, mHoldW, mHoldH);
                mBorder.draw(canvas, (int) mHoldX - mShadowSize, (int) mHoldY - mShadowSize,
                        (int) mHoldW + 2 * mShadowSize, (int) mHoldH + 2 * mShadowSize);
            } else if (animStep == ANIM_SLIDE2) {
                float fraction = (float)(timeDiff) / (TIME_SLIDE2 - TIME_HOLD2);
                float x = mHoldX;
                float y = mHoldY;
                float d = mOffset * fraction;
                switch (mAnimOrientation) {
                case 0:
                    x = mHoldX + d;
                    break;
                case 180:
                    x = mHoldX - d;
                    break;
                case 90:
                    y = mHoldY - d;
                    break;
                case 270:
                    y = mHoldY + d;
                    break;
                }
                preview.directDraw(canvas, (int) mCenterX, (int) mCenterY, mDrawWidth, mDrawHeight);
                mBorder.draw(canvas, (int) x - mShadowSize, (int) y - mShadowSize,
                        (int) mHoldW + 2 * mShadowSize, (int) mHoldH + 2 * mShadowSize);
                review.draw(canvas, (int) x, (int) y, mHoldW, mHoldH);
            }
            return true;
        }
        
        @Override
        int getAnimationDuration() {
            return super.getAnimationDuration();
        }
        
        private  float interpolate(float start, float end, float fraction) {
            return start + (end - start) * fraction;
        }
    };
}
