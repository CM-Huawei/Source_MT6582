package com.android.camera.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.camera.Camera;
import com.android.camera.CameraSettings;
import com.android.camera.FeatureSwitcher;
import com.android.camera.ListPreference;
import com.android.camera.Log;
import com.android.camera.ModeChecker;
import com.android.camera.R;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.ModePickerScrollView;

public class ModePicker extends ViewManager implements View.OnClickListener,
        View.OnLongClickListener, Camera.OnFullScreenChangedListener {
    private static final String TAG = "ModePicker";
    private ListPreference mModePreference;
    public interface OnModeChangedListener {
        void onModeChanged(int newMode);
    }
    
    //can not change this sequence
    //Before MODE_VIDEO is "capture mode" for UI,switch "capture mode" remaining view should not show
    public static final int MODE_PHOTO = 0;
    public static final int MODE_HDR = 1;
    public static final int MODE_FACE_BEAUTY = 2;
    public static final int MODE_PANORAMA = 3;
    public static final int MODE_MAV = 4;
    public static final int MODE_ASD = 5;
    public static final int MODE_SMILE_SHOT = 6;
    public static final int MODE_MOTION_TRACK = 7;
    public static final int MODE_GESTURE_SHOT = 8;
    public static final int MODE_LIVE_PHOTO = 9;
    
    public static final int MODE_VIDEO = 10;
    
    public static final int MODE_NUM_ALL = 11;
    public static final int OFFSET = 100;
    private static final int OFFSET_STEREO_PREVIEW = OFFSET;
    private static final int OFFSET_STEREO_SINGLE = OFFSET * 2;
    
    public static final int MODE_PHOTO_3D = OFFSET_STEREO_PREVIEW + MODE_PHOTO;
    public static final int MODE_VIDEO_3D = OFFSET_STEREO_PREVIEW + MODE_VIDEO;
    
    public static final int MODE_PHOTO_SGINLE_3D = OFFSET_STEREO_SINGLE + MODE_PHOTO;
    public static final int MODE_PANORAMA_SINGLE_3D = OFFSET_STEREO_SINGLE + MODE_PANORAMA;
    
    private static final int DELAY_MSG_HIDE_MS = 3000; //3s
    private static final int MODE_DEFAULT_MARGINBOTTOM = 100;
    private static final int MODE_DEFAULT_PADDING = 20;
    private static final int MODE_MIN_COUNTS = 4;
    private LinearLayout.LayoutParams mLayoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    
    private static final int[] MODE_ICONS_HIGHTLIGHT = new int[MODE_NUM_ALL];
    private static final int[] MODE_ICON_ORDER = {0, 7, 8, 2, 3, 4};
    static {
    	MODE_ICONS_HIGHTLIGHT[MODE_PHOTO] = R.drawable.ic_mode_photo_focus;
    	MODE_ICONS_HIGHTLIGHT[MODE_FACE_BEAUTY] = R.drawable.ic_mode_facebeauty_focus;
    	MODE_ICONS_HIGHTLIGHT[MODE_PANORAMA] = R.drawable.ic_mode_panorama_focus;
    	MODE_ICONS_HIGHTLIGHT[MODE_MAV] = R.drawable.ic_mode_mav_focus;
    	MODE_ICONS_HIGHTLIGHT[MODE_LIVE_PHOTO] = R.drawable.ic_mode_live_photo_focus;
    	MODE_ICONS_HIGHTLIGHT[MODE_MOTION_TRACK] = R.drawable.ic_mode_motion_track_focus;
    };
    private static final int[] MODE_ICONS_NORMAL = new int[MODE_NUM_ALL];
    static {
    	MODE_ICONS_NORMAL[MODE_PHOTO] = R.drawable.ic_mode_photo_normal;
    	MODE_ICONS_NORMAL[MODE_FACE_BEAUTY] = R.drawable.ic_mode_facebeauty_normal;
    	MODE_ICONS_NORMAL[MODE_PANORAMA] = R.drawable.ic_mode_panorama_normal;
    	MODE_ICONS_NORMAL[MODE_MAV] = R.drawable.ic_mode_mav_normal;
    	MODE_ICONS_NORMAL[MODE_LIVE_PHOTO] = R.drawable.ic_mode_live_photo_normal;
    	MODE_ICONS_NORMAL[MODE_MOTION_TRACK] = R.drawable.ic_mode_motion_track_normal;
    };
    
    private final RotateImageView[] mModeViews = new RotateImageView[MODE_NUM_ALL];
    private ModePickerScrollView mScrollView;
    private int mCurrentMode = -1;
    private OnModeChangedListener mModeChangeListener;
    private OnScreenToast mModeToast;
    private int mDisplayWidth;
    private int mModeWidth;
    private int mModeMarginBottom = MODE_DEFAULT_MARGINBOTTOM;
    
    public ModePicker(Camera context) {
        super(context);
        context.addOnFullScreenChangedListener(this);
    }
    
    public int getCurrentMode() {
        return mCurrentMode;
    }
    
    private void setRealMode(int mode) {
        Log.d(TAG, "setRealMode(" + mode + ") mCurrentMode=" + mCurrentMode);
        
        // in photo mode, if the hdr, asd, smile shot, gesture shot is on, we 
        // should set the current mode is hdr or asd or smile shot. in hdr, asd, 
        // smile shot, gesture shot mode if its values is off in sharepreference, 
        // we should set the current mode
        // as photo mode
        if (mode == MODE_PHOTO || mode == MODE_HDR || mode == MODE_SMILE_SHOT
                || mode == MODE_ASD || mode == MODE_GESTURE_SHOT) {
        	mode = getRealMode(mModePreference);
        } 
        
        if (mCurrentMode != mode) {
            mCurrentMode = mode;
            highlightCurrentMode();
            notifyModeChanged();
            if (mModeToast != null) {
                mModeToast.cancel();
            }
        } else {
        	// if mode do not change, we should reset ModePicker view enabled
        	setEnabled(true);
        }
    }
    
    public void setCurrentMode(int mode) {
        int realmode = getModeIndex(mode);
        if (getContext().isStereoMode()) {
            if (FeatureSwitcher.isStereoSingle3d()) {
                realmode += OFFSET_STEREO_SINGLE;
            } else {
                realmode += OFFSET_STEREO_PREVIEW;
            }
        }
        Log.i(TAG, "setCurrentMode(" + mode + ") realmode=" + realmode);
        setRealMode(realmode);
    }

    private void highlightCurrentMode() {
        int index = getModeIndex(mCurrentMode);
        for (int i = 0; i < MODE_NUM_ALL; i++) {
            if (mModeViews[i] != null) {
                if (i == index) {
                    mModeViews[i].setImageResource(MODE_ICONS_HIGHTLIGHT[i]);
                } else {
                    mModeViews[i].setImageResource(MODE_ICONS_NORMAL[i]);
                }
            }
            if (MODE_HDR == index || MODE_SMILE_SHOT == index 
            		|| MODE_ASD == index || MODE_GESTURE_SHOT == index) {
            	mModeViews[MODE_PHOTO].setImageResource(MODE_ICONS_HIGHTLIGHT[MODE_PHOTO]);
            }
        }
        
        
    }
    
    public int getModeIndex(int mode) {
        int index = mode % OFFSET;
        Log.d(TAG, "getModeIndex(" + mode + ") return " + index);
        return index;
    }
    
    public void setListener(OnModeChangedListener l) {
        mModeChangeListener = l;
    }

    @Override
    protected View getView() {
        clearListener();
        View view = inflate(R.layout.mode_picker);
        mScrollView = (ModePickerScrollView)view.findViewById(R.id.mode_picker_scroller);
        mModeViews[MODE_PHOTO] = (RotateImageView) view.findViewById(R.id.mode_photo);
        mModeViews[MODE_LIVE_PHOTO] = (RotateImageView) view.findViewById(R.id.mode_live_photo);
        mModeViews[MODE_MOTION_TRACK] = (RotateImageView) view.findViewById(R.id.mode_motion_track);
        mModeViews[MODE_FACE_BEAUTY] = (RotateImageView) view.findViewById(R.id.mode_face_beauty);
        mModeViews[MODE_PANORAMA] = (RotateImageView) view.findViewById(R.id.mode_panorama);
        mModeViews[MODE_MAV] = (RotateImageView) view.findViewById(R.id.mode_mav);
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        mDisplayWidth = Math.min(metrics.widthPixels, metrics.heightPixels) ;
        mModeWidth = getModeWidth();
        mModeMarginBottom = getDefaultMarginBottom();
        applyListener();
        highlightCurrentMode();
        return view;
    }

    private void applyListener() {
        for (int i = 0; i < MODE_NUM_ALL; i++) {
            if (mModeViews[i] != null) {
                mModeViews[i].setOnClickListener(this);
                mModeViews[i].setOnLongClickListener(this);
            }
        }
    }

    private void clearListener() {
        for (int i = 0; i < MODE_NUM_ALL; i++) {
            if (mModeViews[i] != null) {
                mModeViews[i].setOnClickListener(null);
                mModeViews[i].setOnLongClickListener(null);
                mModeViews[i] = null;
            }
        }
    }
    
    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick(" + view + ") isEnabled()=" + isEnabled()
                + ", view.isEnabled()=" + view.isEnabled()
                + ", getContext().isFullScreen()=" + getContext().isFullScreen());
        setEnabled(false);
        if (getContext().isFullScreen()) {
            for (int i = 0; i < MODE_NUM_ALL; i++) {
                if (mModeViews[i] == view ) {
                    setCurrentMode(i);
                    break;
                }
            }
        } else {
        	// if the is not full screen, we should reset PickMode view enable
        	setEnabled(true);
        }
        
        if (view.getContentDescription() != null) {
            if (mModeToast == null) {
                mModeToast = OnScreenToast.makeText(getContext(), view.getContentDescription());
            } else {
                mModeToast.setText(view.getContentDescription());
            }
            mModeToast.showToast();
        }
    }
    
    public void hideToast() {
    	Log.i(TAG, "hideToast(), mModeToast:" + mModeToast);
    	if (mModeToast != null) {
    		mModeToast.hideToast();
    	}
    }
    
    private void notifyModeChanged() {
        if (mModeChangeListener != null) {
            mModeChangeListener.onModeChanged(getCurrentMode());
        }
    }
    
    public void onRefresh() {
        Log.d(TAG, "onRefresh() mCurrentMode=" + mCurrentMode);
        //get counts of mode supported by back camera and compute the margin bottom 
        //between mode icon.
        int supportModes = ModeChecker.modesSupportedByCamera(getContext(), 0);
        if (supportModes < MODE_MIN_COUNTS && supportModes > 1) {
            mModeMarginBottom = (mDisplayWidth - supportModes * mModeWidth) / (supportModes - 1);
        }
        Log.d(TAG, "mModeMarginBottom:" + mModeMarginBottom);
        mLayoutParams.setMargins(0, 0, 0, mModeMarginBottom);
        
        int visibleCount = 0;
        for (int i = 0; i < MODE_NUM_ALL; i++) {
            if (mModeViews[i] != null) {
                boolean visible = ModeChecker.getModePickerVisible(getContext(), getContext().getCameraId(), i);
                mModeViews[i].setVisibility(visible ? View.VISIBLE : View.GONE);
                mModeViews[i].setLayoutParams(mLayoutParams);
                mModeViews[i].setPadding(MODE_DEFAULT_PADDING, MODE_DEFAULT_PADDING,
                        MODE_DEFAULT_PADDING, MODE_DEFAULT_PADDING);
                if (visible) {
                    visibleCount++; 
                }
            }
        }
        //set margin botton of the last mode icon as 0.
        for (int i = MODE_ICON_ORDER.length - 1; i >= 0; i-- ) {
        	int index = MODE_ICON_ORDER[i];
            if (mModeViews[index] != null && mModeViews[index].getVisibility() == View.VISIBLE) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 0);
                mModeViews[index].setLayoutParams(params);
                break;
            }
        }
        
        if (visibleCount <= 1) { //to enable/disable background
            mScrollView.setVisibility(View.GONE);
        } else {
            mScrollView.setVisibility(View.VISIBLE);
        }
        highlightCurrentMode();
    }
    
    @Override
    public boolean onLongClick(View view) {
        Log.d(TAG, "onLongClick(" + view + ")");
        if (view.getContentDescription() != null) {
            if (mModeToast == null) {
                mModeToast = OnScreenToast.makeText(getContext(), view.getContentDescription());
            } else {
                mModeToast.setText(view.getContentDescription());
            }
            mModeToast.showToast();
        }
        //don't consume long click event
        return false;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mScrollView != null) {
            mScrollView.setEnabled(enabled);
        }
        for (int i = 0; i < MODE_NUM_ALL; i++) {
            if (mModeViews[i] != null) {
                mModeViews[i].setEnabled(enabled);
                mModeViews[i].setClickable(enabled);
            }
        }
    }
    
    @Override
    protected void onRelease() {
        super.onRelease();
        mModeToast = null;
    }

    @Override
    public void onFullScreenChanged(boolean full) {
        Log.d(TAG, "onFullScreenChanged(" + full + ") mModeToast=" + mModeToast);
        if (mModeToast != null && !full) {
            mModeToast.cancel();
        }
    }

    private int getModeWidth() {
        Bitmap bitmap = BitmapFactory.decodeResource(getContext().getResources(), MODE_ICONS_NORMAL[MODE_PHOTO]);
        int bitmapWidth = bitmap.getWidth();
        return bitmapWidth + MODE_DEFAULT_PADDING * 2;
        
    }
    
    private int getDefaultMarginBottom() {
    	//default show three and half mode icons 
    	return (mDisplayWidth - MODE_MIN_COUNTS * mModeWidth) / (MODE_MIN_COUNTS - 1) 
    			+ (mModeWidth / (2 * (MODE_MIN_COUNTS - 1)));
    }
    
    public void setModePreference(ListPreference pref) {
    	mModePreference = pref;
    }
    
    /*
     * when change capture mode from other modes to MODE_PHOTO, if the SmileShot, HDR, 
     * ASD is on, the capture mode should be SmileShot, HDR, ASD.
     * 
     */
    public int getRealMode(ListPreference pref) {
    	mModePreference = pref;
    	int mode = ModePicker.MODE_PHOTO;
    	if (pref != null) {
    	    pref.reloadValue();
    	    if (pref.getValue().equals("on")) {
    	        String key = pref.getKey();
                if (key.equals(CameraSettings.KEY_SMILE_SHOT)) {
                    mode = ModePicker.MODE_SMILE_SHOT;
                } else if (key.equals(CameraSettings.KEY_HDR)) {
                    mode = ModePicker.MODE_HDR;
                } else if (key.equals(CameraSettings.KEY_ASD)) {
                    mode = ModePicker.MODE_ASD;
                } else if (key.equals(CameraSettings.KEY_GESTURE_SHOT)) {
                	mode = ModePicker.MODE_GESTURE_SHOT;
                }
                // if smile shot is on, set hdr, asd sharepreference value as off
                // so does hdr, asd.
                rewritePreference(getRewriteKeys(key), "off");
    	    }
    	}
    	Log.i(TAG, "getRealMode(), pref:" + pref + " ,mode:" + mode);
    	return mode;
    }
    
    private String[] getRewriteKeys(String key) {
    	String[] keys = {CameraSettings.KEY_SMILE_SHOT, 
    			CameraSettings.KEY_HDR, CameraSettings.KEY_ASD};
    	for (int i = 0; i < keys.length; i++) {
    		if (keys[i].equals(key)) {
    			keys[i] = null;
    		}
    	}
    	return keys;
    }
    
    private void rewritePreference(String[] keys, String value) {
    	for (String key : keys) {
    		if (key == null) {
    			continue;
    		}
    		ListPreference pref = getContext().getListPreference(key);
        	if (pref != null) {
        		pref.setValue(value);
        	}
    	}
    	
    }
}
