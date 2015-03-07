
package com.mediatek.incallui.vt;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

import com.android.incallui.Log;

public class VTSurfaceView extends VideoView {
//    private final static int SCREENMODE_BIGSCREEN = 0;
//    private final static int SCREENMODE_FULLSCREEN = SCREENMODE_BIGSCREEN + 1;
//    private final static int SCREENMODE_CROPSCREEN = SCREENMODE_FULLSCREEN + 1;
//    private int mScreenMode = SCREENMODE_BIGSCREEN;

    private int mRequestWidth = 0;
    private int mRequestHeight = 0;

    public VTSurfaceView(final Context context) {
        super(context);
    }

    public VTSurfaceView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public VTSurfaceView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setVideoDisplaySize(int width, int height) {
        mRequestWidth = width;
        mRequestHeight = height;
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
//        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
//        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
//        int screenMode = ScreenModeManager.SCREENMODE_BIGSCREEN;
//
//        switch (mScreenMode) {
//        case SCREENMODE_BIGSCREEN:
//            if (mVideoWidth > 0 && mVideoHeight > 0) {
//                if (mVideoWidth * height > width * mVideoHeight) {
//                    // Log.i("@@@", "image too tall, correcting");
//                    height = width * mVideoHeight / mVideoWidth;
//                } else if (mVideoWidth * height < width * mVideoHeight) {
//                    // Log.i("@@@", "image too wide, correcting");
//                    width = height * mVideoWidth / mVideoHeight;
//                }
//            }
//            break;
//        case SCREENMODE_FULLSCREEN:
//            break;
//        case SCREENMODE_CROPSCREEN:
//            if (mVideoWidth > 0 && mVideoHeight > 0) {
//                if (mVideoWidth * height > width * mVideoHeight) {
//                    // extend width to be cropped
//                    width = height * mVideoWidth / mVideoHeight;
//                } else if (mVideoWidth * height < width * mVideoHeight) {
//                    // extend height to be cropped
//                    height = width * mVideoHeight / mVideoWidth;
//                }
//            }
//            break;
//        default:
//            width = 0;
//            height = 0;
//            break;
//        }
        if (mRequestHeight != 0 && mRequestHeight != 0) {
            Log.i(this, "setMeasuredDimension mRequestWidth=" + mRequestWidth + ", mRequestHeight=" + mRequestHeight);
            setMeasuredDimension(mRequestWidth, mRequestHeight);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

}
