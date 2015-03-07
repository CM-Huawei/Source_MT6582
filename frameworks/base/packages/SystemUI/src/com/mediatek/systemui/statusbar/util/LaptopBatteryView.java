/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.mediatek.systemui.statusbar.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

import com.android.systemui.R;
import com.mediatek.systemui.statusbar.util.BatteryHelper;

public class LaptopBatteryView extends View {
    public static final String TAG = "LaptopBatteryView";
    public static final int FULL = 96;
    public static final int EMPTY = 4;
    public static final float SUBPIXEL = 0.4f;  // inset rects for softer edges
    public static final int UNKNOWN_LEVEL = -1;
    int[] mColors;
    Paint mFramePaint, mBatteryPaint, mBoltPaint;
    int mButtonHeight;
    private int mHeight;
    private int mWidth;
    private final int mChargeColor;
    private final float[] mBoltPoints;
    private final Path mBoltPath = new Path();
    private final RectF mFrame = new RectF();
    private final RectF mClipFrame = new RectF();
    private final RectF mBoltFrame = new RectF();
    private final RectF mSmbFrame = new RectF();
    ///
    private int level = UNKNOWN_LEVEL;
    private boolean plugged;

    public LaptopBatteryView(Context context) {
        this(context, null, 0);
    }

    public LaptopBatteryView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LaptopBatteryView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        mColors = new int[2*N];
        for (int i=0; i<N; i++) {
            mColors[2*i] = levels.getInt(i, 0);
            mColors[2*i+1] = colors.getColor(i, 0);
        }
        levels.recycle();
        colors.recycle();

        mFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFramePaint.setColor(res.getColor(R.color.batterymeter_frame_color));
        mFramePaint.setDither(true);
        mFramePaint.setStrokeWidth(0);
        mFramePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mFramePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));

        mBatteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBatteryPaint.setDither(true);
        mBatteryPaint.setStrokeWidth(0);
        mBatteryPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mChargeColor = getResources().getColor(R.color.batterymeter_charge_color);

        mBoltPaint = new Paint();
        mBoltPaint.setAntiAlias(true);
        mBoltPaint.setColor(res.getColor(R.color.batterymeter_bolt_color));
        mBoltPoints = loadBoltPoints(res);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    private static float[] loadBoltPoints(Resources res) {
        final int[] pts = res.getIntArray(R.array.batterymeter_bolt_points);
        int maxX = 0, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            maxX = Math.max(maxX, pts[i]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        final float[] ptsF = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            ptsF[i] = (float)pts[i] / maxX;
            ptsF[i + 1] = (float)pts[i + 1] / maxY;
        }
        return ptsF;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mHeight = h;
        mWidth = w;
    }

    private int getColorForLevel(int percent) {
        int thresh, color = 0;
        for (int i=0; i<mColors.length; i+=2) {
            thresh = mColors[i];
            color = mColors[i+1];
            if (percent <= thresh) return color;
        }
        return color;
    }

    @Override
    public void draw(Canvas c) {
        if (level == UNKNOWN_LEVEL) return;

        float drawFrac = (float) level / 100f;
        if (level >= FULL) {
            drawFrac = 1f;
        } else if (level <= EMPTY) {
            drawFrac = 0f;
        }

        // fill 'er up
        final int color = plugged ? mChargeColor : getColorForLevel(level);
        mBatteryPaint.setColor(color);

        final int pt = getPaddingTop();
        final int pl = getPaddingLeft();
        final int pr = getPaddingRight();
        final int pb = getPaddingBottom();
        
        if (true) {
	        int height = mHeight - pt - pb;
	        int width = (int)((mWidth - pl - pr) * 0.5);
	
	        mButtonHeight = (int) (height * 0.12f);
	
	        mFrame.set(0, 0, width, height);
	        mFrame.offset(pl, pt);
	      
	        mFrame.left += SUBPIXEL;
	        mFrame.top += SUBPIXEL;
	        mFrame.right -= SUBPIXEL;
	        mFrame.bottom -= SUBPIXEL;
	
	        /// Reset, draw button
	        mSmbFrame.set(
	                mFrame.left + width * 0.25f,
	                mFrame.top,
	                mFrame.right - width * 0.25f,
	                mFrame.top + mButtonHeight + 5);
	        mSmbFrame.top += SUBPIXEL;
	        mSmbFrame.left += SUBPIXEL;
	        mSmbFrame.right -= SUBPIXEL;
	        c.drawRect(mSmbFrame, drawFrac == 1f ? mBatteryPaint : mFramePaint);
	        
	        // first, draw the battery shape
	        ///
	        mSmbFrame.set(mFrame);
	        mSmbFrame.top += mButtonHeight;
	        ///mSmbFrame.left = mFrame.left + width * 0.75f;
	        mSmbFrame.bottom = mSmbFrame.top + mFrame.height() / 3;
	        c.drawRect(mSmbFrame, mFramePaint);
	        mClipFrame.set(mSmbFrame);
	        mClipFrame.top += (mFrame.height() * (1f - drawFrac));
	        if (mClipFrame.top <  mSmbFrame.bottom) {
		        c.save(Canvas.CLIP_SAVE_FLAG);
		        c.clipRect(mClipFrame);
		        c.drawRect(mFrame, mBatteryPaint);
		        c.restore();
	        }
	        ///
	        mSmbFrame.set(mFrame);
	        mSmbFrame.top += mButtonHeight;
	        //mSmbFrame.right = mFrame.left + width * 0.66f;
	        mSmbFrame.right = mFrame.right - width * 0.35f;
	        c.drawRect(mSmbFrame, mFramePaint);
	        mClipFrame.set(mSmbFrame);
	        mClipFrame.top += (mFrame.height() * (1f - drawFrac));
	        c.save(Canvas.CLIP_SAVE_FLAG);
	        c.clipRect(mClipFrame);
	        c.drawRect(mFrame, mBatteryPaint);
	        c.restore();
	        ///
	        mSmbFrame.set(mFrame);
	        mSmbFrame.top += mButtonHeight;
	        //mSmbFrame.left = mFrame.left + width * 0.65f;
	        //mSmbFrame.right = mFrame.left + width * 0.75f;
	        mSmbFrame.right = mFrame.right - width * 0.25f;
	        mSmbFrame.bottom -= mButtonHeight;
	        c.drawRect(mSmbFrame, mFramePaint);
	        mClipFrame.set(mSmbFrame);
	        mClipFrame.top += (mFrame.height() * (1f - drawFrac));
	        if (mClipFrame.top <  mSmbFrame.bottom) {
		        c.save(Canvas.CLIP_SAVE_FLAG);
		        c.clipRect(mClipFrame);
		        c.drawRect(mFrame, mBatteryPaint);
		        c.restore();
	        }

        }
        if (true) {
	        int blockSize = mButtonHeight / 2 ;//(int) (width * 0.15);
	        int height = (mHeight - pt - pb) / 2 - blockSize;//(int)((mHeight - pt - pb) * 0.4);
	        int width = height; //(int)((mWidth - pl - pr) * 0.4);

	        int offsetX = (int)(mFrame.right - width * 0.25f + blockSize);
	        int offsetY = (int)(mFrame.top + mFrame.height() / 3 + blockSize + mButtonHeight);

	        mFrame.set(0, 0, width, height);
	        mFrame.offset(offsetX, offsetY);
	      
        	/// Reset, draw laptop
	        mSmbFrame.set(mFrame);
	        mSmbFrame.bottom = mFrame.top + blockSize;
	        c.drawRect(mSmbFrame, mBatteryPaint);
	        
	        mSmbFrame.set(mFrame);
	        mSmbFrame.right = mFrame.left + blockSize;
	        c.drawRect(mSmbFrame, mBatteryPaint);
	        
	        mSmbFrame.set(mFrame);
	        mSmbFrame.left = mFrame.right - blockSize;
	        c.drawRect(mSmbFrame, mBatteryPaint);
	        
	        mSmbFrame.set(mFrame);
	        mSmbFrame.top = mFrame.bottom - blockSize;
	        c.drawRect(mSmbFrame, mBatteryPaint);
	        
	        mSmbFrame.set(mFrame);
	        mSmbFrame.left = mFrame.left - blockSize;
	        mSmbFrame.right = mFrame.left + blockSize + mFrame.width();
	        mSmbFrame.top = mFrame.top + height;
	        mSmbFrame.bottom = mSmbFrame.top + 2 * blockSize;
	        c.drawRect(mSmbFrame, mBatteryPaint);
        }
        

        if (plugged) {
            // draw the bolt
            final float bl = mFrame.left + mFrame.width() / 2.5f;
            final float bt = mFrame.top + mFrame.height() / 4f;
            final float br = mFrame.right - mFrame.width() / 3.5f;
            final float bb = mFrame.bottom - mFrame.height() / 8f;
            if (mBoltFrame.left != bl || mBoltFrame.top != bt
                    || mBoltFrame.right != br || mBoltFrame.bottom != bb) {
                mBoltFrame.set(bl, bt, br, bb);
                mBoltPath.reset();
                mBoltPath.moveTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
                for (int i = 2; i < mBoltPoints.length; i += 2) {
                    mBoltPath.lineTo(
                            mBoltFrame.left + mBoltPoints[i] * mBoltFrame.width(),
                            mBoltFrame.top + mBoltPoints[i + 1] * mBoltFrame.height());
                }
                mBoltPath.lineTo(
                        mBoltFrame.left + mBoltPoints[0] * mBoltFrame.width(),
                        mBoltFrame.top + mBoltPoints[1] * mBoltFrame.height());
            }
            c.drawPath(mBoltPath, mBatteryPaint);
        }
    }

    public void setBatteryLevel(int batteryLevel, boolean pluggedIn) {
        level = batteryLevel;
        plugged = pluggedIn;
        postInvalidate();
    }
}
