/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.incallui.vt;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.android.incallui.BaseFragment;
import com.android.incallui.CallCommandClient;
import com.android.incallui.CallList;
import com.android.incallui.InCallActivity;
import com.android.incallui.Log;
import com.android.incallui.R;
import com.android.services.telephony.common.Call;
import com.mediatek.incallui.InCallUtils;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.recorder.PhoneRecorderUtils;
import com.mediatek.incallui.recorder.PhoneRecorderUtils.RecorderState;
import com.mediatek.incallui.vt.Constants.VTScreenMode;

public class VTCallFragment extends BaseFragment<VTCallPresenter, VTCallPresenter.VTCallUi>
        implements VTCallPresenter.VTCallUi, SurfaceHolder.Callback, View.OnClickListener,
        PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener, View.OnTouchListener {

    private static final int WAITING_TIME_FOR_ASK_VT_SHOW_ME = 5;
    private static final int SECOND_TO_MILLISECOND = 1000;

    public VTCallPresenter createPresenter() {
        return new VTCallPresenter();
    }

    public VTCallPresenter.VTCallUi getUi() {
        return this;
    }

    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    private VTSurfaceView mVTHighVideo;
    private VTSurfaceView mVTLowVideo;
    private SurfaceHolder mLowVideoHolder;
    private SurfaceHolder mHighVideoHolder;

    public ViewGroup mVtCallStateAndSimIndicate;

    private AlertDialog mInCallVideoSettingDialog;
    private AlertDialog mInCallVideoSettingLocalEffectDialog;
    private AlertDialog mInCallVideoSettingLocalNightmodeDialog;
    private AlertDialog mInCallVideoSettingPeerQualityDialog;
    private AlertDialog mVTMTAskDialog;
    private AlertDialog mVTVoiceReCallDialog;
    private AlertDialog mVTRecorderSelector;
    
    private ImageButton mVTHighUp;
    private ImageButton mVTHighDown;
    private ImageButton mVTLowUp;
    private ImageButton mVTLowDown;

    ArrayList<String> mVTRecorderEntries;

    // "Audio mode" PopupMenu
    private PopupMenu mAudioModePopup;
    private boolean mAudioModePopupVisible;

    private VTBackgroundBitmapHandler mBkgBitmapHandler;

    private Context mContext;
    private CallList mCallList = CallList.getInstance();

    private boolean mLocaleChanged;

    private PowerManager.WakeLock mVTWakeLock;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity().getApplicationContext();
        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        PowerManager pw = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mVTWakeLock = pw.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                                  PowerManager.ON_AFTER_RELEASE,
                                                  "VTWakeLock");
        getPresenter().init(call);

        /// M: get the status bar and navigation bar height @{
        mStatusBarHeight = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        mNavigationBarHeight = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height);
        final Point point = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getRealSize(point);
        mScreenHeight = point.y;
        mScreenWidth = point.x;
        /// @}
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        return inflater.inflate(R.layout.mtk_vt_incall_screen, container, false);
    }


    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        log("onViewCreated()...");

        mVTHighVideo = (VTSurfaceView) view.findViewById(R.id.VTHighVideo);
        mVTHighVideo.setFocusable(false);
        mVTHighVideo.setFocusableInTouchMode(false);

        mVTLowVideo = (VTSurfaceView) view.findViewById(R.id.VTLowVideo);
        mVTLowVideo.setFocusable(false);
        mVTLowVideo.setFocusableInTouchMode(false);

        mVTHighVideo.setOnClickListener(this);
        mVTLowVideo.setOnClickListener(this);

        mHighVideoHolder = mVTHighVideo.getHolder();
        mLowVideoHolder = mVTLowVideo.getHolder();

        mHighVideoHolder.addCallback(this);
        mLowVideoHolder.addCallback(this);


        mVTLowVideo.setZOrderMediaOverlay(true);

        mVTHighUp = (ImageButton) view.findViewById(R.id.VTHighUp);
        mVTHighUp.setBackgroundColor(0);
        mVTHighUp.setOnClickListener(this);
        mVTHighUp.setVisibility(View.GONE);

        mVTHighDown = (ImageButton) view.findViewById(R.id.VTHighDown);
        mVTHighDown.setBackgroundColor(0);
        mVTHighDown.setOnClickListener(this);
        mVTHighDown.setVisibility(View.GONE);

        mVTLowUp = (ImageButton) view.findViewById(R.id.VTLowUp);
        mVTLowUp.setBackgroundColor(0);
        mVTLowUp.setOnClickListener(this);
        mVTLowUp.setVisibility(View.GONE);

        mVTLowDown = (ImageButton) view.findViewById(R.id.VTLowDown);
        mVTLowDown.setBackgroundColor(0);
        mVTLowDown.setOnClickListener(this);
        mVTLowDown.setVisibility(View.GONE);

        // mVTLowVideo.setZOrderOnTop(true);
//        initVTSurface();

        // For ALPS01298431 @{
        // For MO, dialVTCallSuccess() may be called before onViewCreate(),
        //         so update operation in dialVTCallSuccess() may be not executed. So we need do it here.
        // For MT, answerVTCallPre() is called actually after onViewCreate(),
        //         so we do it in answerVTCallPre() to make sure it will called after pushVTSettingParams.
        if (VTUtils.isVTOutgoing()) {
            updatePeerVideoBkgDrawable();
        }
        /// @}

        // If there has no VT call, make the VT UI as INVISIBLE as default.
        if (VTUtils.isVTIdle()) {
            getView().setVisibility(View.INVISIBLE);
        }

        ExtensionManager.getInstance().getVTCallExtension().onViewCreated(getActivity(), view,
                this, CallCommandClient.getInstance().getService());
    }

//    /**
//     * Currently, the method not for landscape.
//     * @param surfaceView
//     * @param curWidth
//     * @param curHieght
//     */
//    private void initVTSurface() {
//        if (mVTHighVideo != null) {
//            final int screenHeight = getActivity().getWindowManager().getDefaultDisplay()
//                    .getHeight();
//            final int height = screenHeight * 4 / 5;
//            final int width = height * 176 / 144;
//            mVTHighVideo.setVideoDisplaySize(width, height);
//
//            Log.i(this, "updateSurfaceSize mVTHighVideo: w=" + width + ", h=" + height);
//        }
//    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.mtk_vt_incall_menu, menu);
    }


    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (holder == mHighVideoHolder) {
            log("surfaceChanged : HighVideo");
            VTInCallScreenFlags.getInstance().mVTSurfaceChangedH = true;
        }

        if (holder == mLowVideoHolder) {
            log("surfaceChanged : LowVideo");
            VTInCallScreenFlags.getInstance().mVTSurfaceChangedL = true;
        }
        log("surfaceChanged : " + holder.toString() + ", w=" + width + ", height=" + height);

        if (VTInCallScreenFlags.getInstance().mVTSurfaceChangedH
                && VTInCallScreenFlags.getInstance().mVTSurfaceChangedL) {
            CallCommandClient.getInstance().setVTVisible(false);

            updateVTLocalPeerDisplay();

            log("surfaceChanged : CallCommandClient.getInstance().setVTVisible(true) start ...");
            CallCommandClient.getInstance().setVTVisible(true);
            log("surfaceChanged : CallCommandClient.getInstance().setVTVisible(true) end ...");
            log("- set CallCommandClient ready ! ");
            CallCommandClient.getInstance().setVTReady();
            log("- finish set CallCommandClient ready ! ");
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        if (DBG) {
            log("surfaceCreated : " + holder.toString());
        }
    }


    public void surfaceDestroyed(SurfaceHolder holder) {
        if (DBG) {
            log("surfaceDestroyed : " + holder.toString());
        }

        if (holder == mHighVideoHolder) {
            if (DBG) {
                log("surfaceDestroyed : HighVideo," +
                        " set mVTSurfaceChangedH = false");
            }
            VTInCallScreenFlags.getInstance().mVTSurfaceChangedH = false;
        }

        if (holder == mLowVideoHolder) {
            if (DBG) {
                log("surfaceDestroyed : LowVideo," +
                        " set mVTSurfaceChangedL = false");
            }
            VTInCallScreenFlags.getInstance().mVTSurfaceChangedL = false;
        }

        if ((!VTInCallScreenFlags.getInstance().mVTSurfaceChangedH)
                && (!VTInCallScreenFlags.getInstance().mVTSurfaceChangedL)) {
            if (DBG) {
                log("surfaceDestroyed :" +
                        " CallCommandClient.getInstance().setVTVisible(false) start ...");
            }
            CallCommandClient.getInstance().setVTVisible(false);
            if (DBG) {
                log("surfaceDestroyed :" +
                        "CallCommandClient.getInstance().setVTVisible(false) end ...");
            }
            CallCommandClient.getInstance().setDisplay(null, null);
        }
    }

    private void onVTInCallVideoSettingLocalEffect() {
        if (DBG) {
            log("onVTInCallVideoSettingLocalEffect() ! ");
        }
        AlertDialog.Builder myBuilder = new AlertDialog.Builder(getActivity());
        myBuilder.setNegativeButton(getResources().getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mInCallVideoSettingLocalEffectDialog != null) {
                    mInCallVideoSettingLocalEffectDialog.dismiss();
                    mInCallVideoSettingLocalEffectDialog = null;
                }
            }
        });

        List<String> supportEntryValues = VTManagerLocal.getInstance().getSupportedColorEffects();
        
        if (supportEntryValues == null || supportEntryValues.size() <= 0) {
            return;
        }

        CharSequence[] entryValues = getResources().getStringArray(
                R.array.vt_incall_setting_local_video_effect_values);
        CharSequence[] entries = getResources().getStringArray(
                R.array.vt_incall_setting_local_video_effect_entries);
        ArrayList<CharSequence> entryValues2 = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entries2 = new ArrayList<CharSequence>();

        for (int i = 0, len = entryValues.length; i < len; i++) {
            if (supportEntryValues.indexOf(entryValues[i].toString()) >= 0) {
                entryValues2.add(entryValues[i]);
                entries2.add(entries[i]);
            }
        }

        if (DBG) {
            log("onVTInCallVideoSettingLocalEffect() : entryValues2.size() - "
                    + entryValues2.size());
        }
        int currentValue = entryValues2.indexOf(VTManagerLocal.getInstance().getColorEffect());

        InCallVideoSettingLocalEffectListener myClickListener
                = new InCallVideoSettingLocalEffectListener();
        myClickListener.setValues(entryValues2);
        myBuilder.setSingleChoiceItems(entries2.toArray(
                            new CharSequence[entryValues2.size()]),
                            currentValue, myClickListener);
        myBuilder.setTitle(R.string.vt_local_video_effect);
        mInCallVideoSettingLocalEffectDialog = myBuilder.create();
        mInCallVideoSettingLocalEffectDialog.show();
    }

    class InCallVideoSettingLocalEffectListener implements DialogInterface.OnClickListener {
        private ArrayList<CharSequence> mValues = new ArrayList<CharSequence>();

        /**
         * set values
         * @param values    values
         */
        public void setValues(ArrayList<CharSequence> values) {
            for (int i = 0; i < values.size(); i++) {
                mValues.add(values.get(i));
            }
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {

            if (mInCallVideoSettingLocalEffectDialog != null) {
                mInCallVideoSettingLocalEffectDialog.dismiss();
                mInCallVideoSettingLocalEffectDialog = null;
            }
            CallCommandClient.getInstance().setColorEffect(mValues.get(which).toString());
            updateLocalZoomOrBrightness();
        }
    }

    private void onVTInCallVideoSettingLocalNightMode() {
        if (DBG) {
            log("onVTInCallVideoSettingLocalNightMode() ! ");
        }

        AlertDialog.Builder myBuilder = new AlertDialog.Builder(getActivity());
        myBuilder.setNegativeButton(getResources().getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mInCallVideoSettingLocalNightmodeDialog != null) {
                    mInCallVideoSettingLocalNightmodeDialog.dismiss();
                    mInCallVideoSettingLocalNightmodeDialog = null;
                }
            }
        });
        myBuilder.setTitle(R.string.vt_local_video_nightmode);

        DialogInterface.OnClickListener myClickListener
                = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mInCallVideoSettingLocalNightmodeDialog != null) {
                    mInCallVideoSettingLocalNightmodeDialog.dismiss();
                    mInCallVideoSettingLocalNightmodeDialog = null;
                }
                if (0 == which) {
                    if (DBG) {
                        log("onVTInCallVideoSettingLocalNightMode() :" +
                                " CallCommandClient.getInstance().setNightMode(true);");
                    }
                    CallCommandClient.getInstance().setNightMode(true);
//                    updateLocalZoomOrBrightness();
                } else if (1 == which) {
                    if (DBG) {
                        log("onVTInCallVideoSettingLocalNightMode() :"
                                + " CallCommandClient.getInstance().setNightMode(false);");
                    }
                    CallCommandClient.getInstance().setNightMode(false);
//                    updateLocalZoomOrBrightness();
                }
            }
        };

        if (VTManagerLocal.getInstance().isSupportNightMode()) {
            if (VTManagerLocal.getInstance().getNightMode()) {
                myBuilder.setSingleChoiceItems(
                                R.array.vt_incall_video_setting_local_nightmode_entries, 0,
                                myClickListener);
            } else {
                myBuilder.setSingleChoiceItems(
                                R.array.vt_incall_video_setting_local_nightmode_entries, 1,
                                myClickListener);
            }
        } else {
            myBuilder.setSingleChoiceItems(
                    R.array.vt_incall_video_setting_local_nightmode_entries2, 0,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (mInCallVideoSettingLocalNightmodeDialog != null) {
                                mInCallVideoSettingLocalNightmodeDialog.dismiss();
                                mInCallVideoSettingLocalNightmodeDialog = null;
                            }
                        }
                    });
        }

        mInCallVideoSettingLocalNightmodeDialog = myBuilder.create();
        mInCallVideoSettingLocalNightmodeDialog.show();
    }

    private void onVTInCallVideoSettingPeerQuality() {
        if (DBG) {
            log("onVTInCallVideoSettingPeerQuality() ! ");
        }
        AlertDialog.Builder myBuilder = new AlertDialog.Builder(getActivity());
        myBuilder.setNegativeButton(getResources().getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mInCallVideoSettingPeerQualityDialog != null) {
                    mInCallVideoSettingPeerQualityDialog.dismiss();
                    mInCallVideoSettingPeerQualityDialog = null;
                }
            }
        });
        myBuilder.setTitle(R.string.vt_peer_video_quality);

        DialogInterface.OnClickListener myClickListener
                = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mInCallVideoSettingPeerQualityDialog != null) {
                    mInCallVideoSettingPeerQualityDialog.dismiss();
                    mInCallVideoSettingPeerQualityDialog = null;
                }
                if (0 == which) {
                    if (DBG) {
                        log("onVTInCallVideoSettingPeerQuality() :" +
                                " CallCommandClient.getInstance()" +
                                ".setVideoQuality( CallCommandClient.VT_VQ_NORMAL );");
                    }
                    CallCommandClient.getInstance().setVideoQuality(VTManagerLocal.VT_VQ_NORMAL);
                } else if (1 == which) {
                    if (DBG) {
                        log("onVTInCallVideoSettingPeerQuality() :" +
                                " CallCommandClient.getInstance().setVideoQuality" +
                                "( CallCommandClient.VT_VQ_SHARP );");
                    }
                    CallCommandClient.getInstance().setVideoQuality(VTManagerLocal.VT_VQ_SHARP);
                }
            }
        };

        if (VTManagerLocal.VT_VQ_NORMAL == VTManagerLocal.getInstance().getVideoQuality()) {
            myBuilder.setSingleChoiceItems(
                    R.array.vt_incall_video_setting_peer_quality_entries, 0,
                    myClickListener);
        } else if ( VTManagerLocal.VT_VQ_SHARP == VTManagerLocal.getInstance().getVideoQuality()) {
            myBuilder.setSingleChoiceItems(
                    R.array.vt_incall_video_setting_peer_quality_entries, 1,
                    myClickListener);
        } else {
            if (DBG) {
                log("CallCommandClient.getInstance().getVideoQuality()" +
                        " is not VT_VQ_SHARP" +
                        " or VT_VQ_NORMAL , error ! ");
            }
        }

        mInCallVideoSettingPeerQualityDialog = myBuilder.create();
        mInCallVideoSettingPeerQualityDialog.show();
    }


    public void dismissVTDialogs() {
        if (DBG) {
            log("dismissVTDialogs() ! ");
        }
        if (mInCallVideoSettingDialog != null) {
            mInCallVideoSettingDialog.dismiss();
            mInCallVideoSettingDialog = null;
        }
        if (mInCallVideoSettingLocalEffectDialog != null) {
            mInCallVideoSettingLocalEffectDialog.dismiss();
            mInCallVideoSettingLocalEffectDialog = null;
        }
        if (mInCallVideoSettingLocalNightmodeDialog != null) {
            mInCallVideoSettingLocalNightmodeDialog.dismiss();
            mInCallVideoSettingLocalNightmodeDialog = null;
        }
        if (mInCallVideoSettingPeerQualityDialog != null) {
            mInCallVideoSettingPeerQualityDialog.dismiss();
            mInCallVideoSettingPeerQualityDialog = null;
        }
        if (mVTMTAskDialog != null) {
            mVTMTAskDialog.dismiss();
            mVTMTAskDialog = null;
        }
        if (mVTVoiceReCallDialog != null) {
            mVTVoiceReCallDialog.dismiss();
//            if (mCM.getActiveFgCall().isIdle() && mCM.getFirstActiveBgCall().isIdle()
//                    && mCM.getFirstActiveRingingCall().isIdle()) {
//                mInCallScreen.endInCallScreenSession();
//            }
            mVTVoiceReCallDialog = null;
        }
        if (mVTRecorderSelector != null) {
            mVTRecorderSelector.dismiss();
            mVTRecorderSelector = null;
        }
    }

    private boolean getVTInControlRes() {
        return VTInCallScreenFlags.getInstance().mVTInControlRes;
    }

    private void setVTInControlRes(boolean value) {
        VTInCallScreenFlags.getInstance().mVTInControlRes = value;
    }

    public void onVTReceiveFirstFrame() {
        if (DBG) {
            log("onVTReceiveFirstFrame()...  mVTPeerBigger: " + VTInCallScreenFlags.getInstance().mVTPeerBigger);
        }
        if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
            if (mVTHighVideo != null) {
                if (mVTHighVideo.getBackground() != null) {
                    mVTHighVideo.setBackgroundDrawable(null);
                }
            }
        } else {
            if (mVTLowVideo != null) {
                if (mVTLowVideo.getBackground() != null) {
                    mVTLowVideo.setBackgroundDrawable(null);
                }
            }
        }
    }

    public void onVTReady() {
        /// DM lock Feature @{
        if (InCallUtils.isDMLocked()) {
            if (DBG) {
                log("Now DM locked, just return");
            }
            return;
        }
        CallCommandClient.getInstance().unlockPeerVideo();
        if (DBG) {
            log("Now DM not locked," + " VTManager.getInstance().unlockPeerVideo();");
        }
        /// @}
        if (getPresenter() != null && getPresenter().isIncomingCall()
                && "1".equals(VTInCallScreenFlags.getInstance().mVTShowLocalMT)) {
            if (DBG) {
                log("- VTSettingUtils.getInstance().mShowLocalMT : 1 !");
            }
            mVTMTAskDialog = new AlertDialog.Builder(getActivity())
                    .setMessage(getResources().getString(R.string.vt_ask_show_local))
                    .setPositiveButton(getResources().getString(R.string.vt_ask_show_local_yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    if (DBG) {
                                        log(" user select yes !! ");
                                    }

                                    if (mVTMTAskDialog != null) {
                                        mVTMTAskDialog.dismiss();
                                        mVTMTAskDialog = null;
                                    }
//                                    VTInCallScreenFlags.getInstance().mVTShowLocalMT = "0";
                                    onVTHideMeClick();
                                    return;
                                }
                            })
                    .setNegativeButton(getResources().getString(R.string.vt_ask_show_local_no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    if (DBG) {
                                        log(" user select no !! ");
                                    }

                                    if (mVTMTAskDialog != null) {
                                        mVTMTAskDialog.dismiss();
                                        mVTMTAskDialog = null;
                                    }
//                                    VTInCallScreenFlags.getInstance().mVTShowLocalMT = "2";
//                                    VTCallUtils.updatePicToReplaceLocalVideo();
                                    CallCommandClient.getInstance().updatePicToReplaceLocalVideo();
                                    return;
                                }
                            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface arg0) {
                            if (DBG) {
                                log(" user no selection , default show !! ");
                            }

                            if (mVTMTAskDialog != null) {
                                mVTMTAskDialog.dismiss();
                                mVTMTAskDialog = null;
                            }
//                            VTInCallScreenFlags.getInstance().mVTShowLocalMT = "0";
                            onVTHideMeClick();
                            return;
                        }
                    }).create();
            mVTMTAskDialog.show();

            new DialogCancelTimer(WAITING_TIME_FOR_ASK_VT_SHOW_ME, mVTMTAskDialog).start();
        }
    }

    public class DialogCancelTimer {

        private final Timer mTimer = new Timer();
        private final int mSeconds;
        private AlertDialog mVTMTAskDialog;

        /**
         * Constructor function
         * @param seconds    time for cancel timer
         * @param dialog     the dialog to show
         */
        public DialogCancelTimer(int seconds, AlertDialog dialog) {
            this.mSeconds = seconds;
            this.mVTMTAskDialog = dialog;
        }

        /**
         * start count time
         */
        public void start() {
            mTimer.schedule(new TimerTask() {
                public void run() {
                    if (mVTMTAskDialog != null) {
                        if (mVTMTAskDialog.isShowing()) {
                            mVTMTAskDialog.cancel();
                        }
                    }
                    mTimer.cancel();
                }
            }, mSeconds * SECOND_TO_MILLISECOND);
        }
    }

    public void onClick(View view) {
        int id = view.getId();
        if (VDBG) {
            log("onClick(View " + view + ", id " + id + ")...");
        }
        switch (id) {
        case R.id.VTHighUp:
        case R.id.VTLowUp:
            adjustLocalVT(true);
            break;
        case R.id.VTHighDown:
        case R.id.VTLowDown:
            adjustLocalVT(false);
            break;
        default:
            break;
        }
        
    }

    private void showGenericErrorDialog(int resid, boolean isStartupError) {
        log("showGenericErrorDialog ");
//        mInCallScreen.showGenericErrorDialog(resid, isStartupError);
    }

    public void onStop() {
        Log.d(this, "onStop");
        super.onStop();
        dismissAudioModePopup();
        /// M: for ALPS01288272 @{
        // If VT screen is not visible, release the wake lock.
        releaseVtWakeLock();
        /// @}
    }

    @Override
    public void onStart() {
        Log.d(this, "onStart");
        super.onStart();
        /// M: for ALPS01288272 @{
        // If VT screen is visible, acquire the wake lock.
        acquireVtWakeLock();
        /// @}
    }

    private void makeVoiceReCall(final String number, final int slot) {
        if (DBG) { 
            log("makeVoiceReCall(), number is " + number + " slot is " + slot);
        }

        final Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
        intent.putExtra(Constants.EXTRA_SLOT_ID, slot);
        intent.putExtra(Constants.EXTRA_INTERNATIONAL_DIAL_OPTION, Constants.INTERNATIONAL_DIAL_OPTION_IGNORE);
        intent.putExtra(Constants.EXTRA_VT_MAKE_VOICE_RECALL, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        // only set mode to WAITING here, and close VT canvas until the next voice
        // call screen is updated, see ALPS01034170
        //        setVTScreenMode(VTScreenMode.VT_SCREEN_WAITING);
    }


    public void showReCallDialog(final int resid, final String number, final int slot) {

        if (DBG) {
            log("showReCallDialog... ");
        }

//        if (VTSettingUtils.getInstance().mAutoDropBack) {
//            showToast(getResources().getString(R.string.vt_voice_connecting));
////            PhoneUtils.turnOnSpeaker(mInCallScreen, false, true);
//            makeVoiceReCall(number, slot);
//        } else {
            showReCallDialogEx(resid, number, slot);
//        }
    }

    private void showReCallDialogEx(final int resid, final String number, final int slot) {
        if (DBG) {
            log("showReCallDialogEx... ");
        }

        if (null != mVTVoiceReCallDialog) {
            if (mVTVoiceReCallDialog.isShowing()) {
                return;
            }
        }
        CharSequence msg = getResources().getText(resid);

        // create the clicklistener and cancel listener as needed.

        DialogInterface.OnClickListener clickListener1
                = new DialogInterface.OnClickListener() {

        
            public void onClick(DialogInterface dialog, int which) {
                if (DBG) {
                    log("showReCallDialogEx... , on click, which=" + which);
                }
                if (null != mVTVoiceReCallDialog) {
                    mVTVoiceReCallDialog.dismiss();
                    mVTVoiceReCallDialog = null;
                }
//                PhoneUtils.turnOnSpeaker(mInCallScreen, false, true);
                makeVoiceReCall(number, slot);
            }
        };

        DialogInterface.OnClickListener clickListener2
                = new DialogInterface.OnClickListener() {

        
            public void onClick(DialogInterface dialog, int which) {
                if (DBG) {
                    log("showReCallDialogEx... , on click, which=" + which);
                }

                if (null != mVTVoiceReCallDialog) {
                    mVTVoiceReCallDialog.dismiss();
                    mVTVoiceReCallDialog = null;
                }

//                mInCallScreen.delayedCleanupAfterDisconnect();
            }
        };

        OnCancelListener cancelListener = new OnCancelListener() {

        
            public void onCancel(DialogInterface dialog) {
//                mInCallScreen.delayedCleanupAfterDisconnect();
            }
        };

        mVTVoiceReCallDialog = new AlertDialog.Builder(getActivity())
        .setMessage(msg)
        .setNegativeButton(getResources().getString(android.R.string.cancel), clickListener2)
                .setPositiveButton(getResources().getString(android.R.string.ok),
                clickListener1).setOnCancelListener(cancelListener).create();
        mVTVoiceReCallDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
//        mVTVoiceReCallDialog.setOnShowListener(mInCallScreen);

        mVTVoiceReCallDialog.show();
    }

    /**
     * Dismisses the "Audio mode" popup if it's visible.
     *
     * This is safe to call even if the popup is already dismissed, or even if
     * you never called showAudioModePopup() in the first place.
     */
    private void dismissAudioModePopup() {
        if (mAudioModePopup != null) {
            mAudioModePopup.dismiss();  // safe even if already dismissed
            mAudioModePopup = null;
            mAudioModePopupVisible = false;
        }
    }

    public void onDismiss(PopupMenu menu) {
        if (DBG) {
            log("- onDismiss: " + menu);
        }
        mAudioModePopupVisible = false;
    }


    private void updateVTLocalPeerDisplay() {
        log("updateVTLocalPeerDisplay()...mVTPeerBigger: " + VTInCallScreenFlags.getInstance().mVTPeerBigger);
        if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
            CallCommandClient.getInstance().setDisplay(mLowVideoHolder.getSurface(), mHighVideoHolder.getSurface());
        } else {
            CallCommandClient.getInstance().setDisplay(mHighVideoHolder.getSurface(), mLowVideoHolder.getSurface());
        }
    }

    private void dismissVideoSettingDialogs() {
        if (mInCallVideoSettingDialog != null) {
            mInCallVideoSettingDialog.dismiss();
            mInCallVideoSettingDialog = null;
        }
        if (mInCallVideoSettingLocalEffectDialog != null) {
            mInCallVideoSettingLocalEffectDialog.dismiss();
            mInCallVideoSettingLocalEffectDialog = null;
        }
        if (mInCallVideoSettingLocalNightmodeDialog != null) {
            mInCallVideoSettingLocalNightmodeDialog.dismiss();
            mInCallVideoSettingLocalNightmodeDialog = null;
        }
        if (mInCallVideoSettingPeerQualityDialog != null) {
            mInCallVideoSettingPeerQualityDialog.dismiss();
            mInCallVideoSettingPeerQualityDialog = null;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (ExtensionManager.getInstance().getVTCallExtension().onTouch(v, event,
                VTInCallScreenFlags.getInstance().mVTPeerBigger)) {
            return true;
        }

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            if (DBG) {
                log("MotionEvent.ACTION_DOWN");
            }
            hideLocalZoomOrBrightness(true);
            break;
        default:
            break;
        }
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }

    public void handleVTMenuClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.menu_switch_camera:
             onVTSwitchCameraClick();
             break;
        case R.id.menu_take_peer_photo:
             onVTTakePeerPhotoClick();
             break;
        case R.id.menu_hide_local_video:
             onVTHideMeClick();
             break;
        case R.id.menu_swap_videos:
             onVTSwapVideoClick();
             break;
        case R.id.menu_vt_record:
             onVoiceVideoRecordClick(menuItem);
             break;
        case R.id.menu_video_setting:
             onVTShowSettingClick();
             break;
        default:
            Log.d(this, "This is not VT menu item.");
        }
    }

    public void onVTSwitchCameraClick() {
        if (VTManagerLocal.getInstance().getState() != VTManagerLocal.State.READY
                && VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            Log.d(this, "onVTSwitchCameraClick: failed, state should be READY or CONNECTED.");
            return;
        }

        if (VTInCallScreenFlags.getInstance().mVTInSwitchCamera) {
            Log.i(this, "VTManager is handling switchcamera now, so returns this time.");
            return;
        }

        // switch camera, when telephony complete this action, will push a message to us, then we will set this flag to false.
        VTInCallScreenFlags.getInstance().mVTInSwitchCamera = true;
        CallCommandClient.getInstance().switchCamera();
        // below flag seems to be unuse. consider delete it.
        VTInCallScreenFlags.getInstance().mVTFrontCameraNow = !VTInCallScreenFlags.getInstance().mVTFrontCameraNow;
//        updateVTScreen(getVTScreenMode());

        hideLocalZoomOrBrightness(true);
    }

    public void onVTTakePeerPhotoClick() {
        Log.d(this, "onVTTakePeerPhotoClick()...");

        if (!VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame
                || VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            Log.d(this, "onVTTakePeerPhotoClick: failed, peer video is unvisiable now.");
            return;
        }

        if (VTInCallScreenFlags.getInstance().mVTInSnapshot) {
            Log.d(this, "onVTTakePeerPhotoClick: failed, VTManager is handling snapshot now.");
            return;
        }

        VTInCallScreenFlags.getInstance().mVTInSnapshot = true;
        CallCommandClient.getInstance().savePeerPhoto();
    }

    public void onVTHideMeClick() {
        Log.d(this, "onVTHideMeClick()...");

        if (VTManagerLocal.getInstance().getState() != VTManagerLocal.State.READY
                && VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            Log.d(this, "onVTHideMeClick: failed, state should be READY or CONNECTED.");
            return;
        }

        VTInCallScreenFlags.getInstance().mVTHideMeNow = !VTInCallScreenFlags.getInstance().mVTHideMeNow;
        CallCommandClient.getInstance().hideLocal(VTInCallScreenFlags.getInstance().mVTHideMeNow);
        hideLocalZoomOrBrightness(true);
    }

    public void onVTSwapVideoClick() {
        if (!VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame
                || VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            Log.d(this, "onVTSwapVideoClick: failed, peer video is unvisiable now.");
            return;
        }

        // this variable will only be set when the button is clicked and sync from VTSetting.
        if (VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting
                || VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting
                || VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
            hideLocalZoomOrBrightness(false);
        }

        VTInCallScreenFlags.getInstance().mVTPeerBigger = !VTInCallScreenFlags.getInstance().mVTPeerBigger;

        CallCommandClient.getInstance().setVTVisible(false);
        updateVTLocalPeerDisplay();
        CallCommandClient.getInstance().setVTVisible(true);

        if (VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting) {
            showVTLocalZoom();
        }
        if (VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting) {
            showVTLocalBrightness();
        }
        if (VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
            showVTLocalContrast();
        }
    }

    public void onVTShowSettingClick() {
        log("onVTInCallVideoSetting() ! ");

        if (VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            Log.d(this, "onVTShowSettingClick: failed, state should be CONNECTED.");
            return;
        }

        DialogInterface.OnClickListener myClickListener
                = new DialogInterface.OnClickListener() {

            private static final int DIALOG_ITEM_THREE = 3;
            private static final int DIALOG_ITEM_FOUR = 4;

            public void onClick(DialogInterface dialog, int which) {
                if (mInCallVideoSettingDialog != null) {
                    mInCallVideoSettingDialog.dismiss();
                    mInCallVideoSettingDialog = null;
                }
                if (0 == which) {
                    if (DBG) {
                        log("onVTInCallVideoSetting() : select - 0 ");
                    }
                    if (!VTManagerLocal.getInstance().canDecZoom()
                            && !VTManagerLocal.getInstance().canIncZoom()) {
                        showToast(getResources().getString(R.string.vt_cannot_support_setting));
                    } else {
                        showVTLocalZoom();
                    }
                } else if (1 == which) {
                    if (DBG) {
                        log("onVTInCallVideoSetting() : select - 1 ");
                    }
                    if (!VTManagerLocal.getInstance().canDecBrightness()
                            && !VTManagerLocal.getInstance().canIncBrightness()) {
                        showToast(getResources().getString(R.string.vt_cannot_support_setting));
                    } else {
                        showVTLocalBrightness();
                    }
                } else if (2 == which) {
                    if (DBG) {
                        log("onVTInCallVideoSetting() : select - 2 ");
                    }
                    if (!VTManagerLocal.getInstance().canDecContrast()
                            && !VTManagerLocal.getInstance().canIncContrast()) {
                        showToast(getResources().getString(R.string.vt_cannot_support_setting));
                    } else {
                        showVTLocalContrast();
                    }
                } else if (DIALOG_ITEM_THREE == which) {
                    if (DBG) {
                        log("onVTInCallVideoSetting() : select - 3 ");
                    }
                    onVTInCallVideoSettingLocalEffect();
                } else if (DIALOG_ITEM_FOUR == which) {
                    if (DBG) {
                        log("onVTInCallVideoSetting() : select - 4 ");
                    }
                    onVTInCallVideoSettingLocalNightMode();
                } else {
                    if (DBG) {
                        log("onVTInCallVideoSetting() : select - 5 ");
                    }
                    onVTInCallVideoSettingPeerQuality();
                }
            }
        };

        AlertDialog.Builder myBuilder = new AlertDialog.Builder(getActivity());
        myBuilder.setNegativeButton(R.string.custom_message_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mInCallVideoSettingDialog != null) {
                    mInCallVideoSettingDialog.dismiss();
                    mInCallVideoSettingDialog = null;
                }
            }
        });

        if (!VTInCallScreenFlags.getInstance().mVTHideMeNow) {
            if (VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting) {
                myBuilder.setSingleChoiceItems(R.array.vt_incall_video_setting_entries, 0,
                        myClickListener).setTitle(R.string.vt_settings);
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting) {
                myBuilder.setSingleChoiceItems(R.array.vt_incall_video_setting_entries, 1,
                        myClickListener).setTitle(R.string.vt_settings);
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
                myBuilder.setSingleChoiceItems(R.array.vt_incall_video_setting_entries, 2,
                        myClickListener).setTitle(R.string.vt_settings);
            } else {
                myBuilder.setSingleChoiceItems(R.array.vt_incall_video_setting_entries, -1,
                        myClickListener).setTitle(R.string.vt_settings);
            }
        } else {
            myBuilder.setSingleChoiceItems(R.array.vt_incall_video_setting_entries2, -1,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (mInCallVideoSettingDialog != null) {
                                mInCallVideoSettingDialog.dismiss();
                                mInCallVideoSettingDialog = null;
                            }
                            onVTInCallVideoSettingPeerQuality();
                        }
                    }).setTitle(R.string.vt_settings);
        }
        mInCallVideoSettingDialog = myBuilder.create();
        mInCallVideoSettingDialog.show();
    
    }

    private void adjustLocalVT(boolean inc) {
        if (VDBG) {
            log("onClick: adjustLocalVT()...");
        }
        if (inc) {
            if (VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting) {
                CallCommandClient.getInstance().incZoom();
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting) {
                CallCommandClient.getInstance().incBrightness();
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
                CallCommandClient.getInstance().incContrast();
            }
        } else {
            if (VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting) {
                CallCommandClient.getInstance().decZoom();
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting) {
                CallCommandClient.getInstance().decBrightness();
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
                CallCommandClient.getInstance().decContrast();
            }
        }
    }
    
    private void showVTLocalZoom() {
        if (DBG) {
            log("showVTLocalZoom()...");
        }

        // only when VTManager is under REDAY / CONNECT, can we do "Zoom" / "Brightness" / "Contrast" operations.
        if (VTManagerLocal.getInstance().getState() != VTManagerLocal.State.READY
                && VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            return;
        }

        if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
            mVTLowUp.setImageResource(R.drawable.mtk_vt_incall_button_zoomup);
            mVTLowDown.setImageResource(R.drawable.mtk_vt_incall_button_zoomdown);
            mVTLowUp.setVisibility(View.VISIBLE);
            mVTLowDown.setVisibility(View.VISIBLE);
            mVTLowUp.setEnabled(VTManagerLocal.getInstance().canIncZoom());
            mVTLowDown.setEnabled(VTManagerLocal.getInstance().canDecZoom());
        } else {
            mVTHighUp.setImageResource(R.drawable.mtk_vt_incall_button_zoomup);
            mVTHighDown.setImageResource(R.drawable.mtk_vt_incall_button_zoomdown);
            mVTHighUp.setVisibility(View.VISIBLE);
            mVTHighDown.setVisibility(View.VISIBLE);
            mVTHighUp.setEnabled(VTManagerLocal.getInstance().canIncZoom());
            mVTHighDown.setEnabled(VTManagerLocal.getInstance().canDecZoom());
        }

        VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting = true;
        VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting = false;
        VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting = false;

    }

    private void showVTLocalBrightness() {
        if (DBG) {
            log("showVTLocalBrightness()...");
        }

        // only when VTManager is under REDAY / CONNECT, can we do "Zoom" / "Brightness" / "Contrast" operations.
        if (VTManagerLocal.getInstance().getState() != VTManagerLocal.State.READY
                && VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            return;
        }

        if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
            mVTLowUp.setImageResource(R.drawable.mtk_vt_incall_button_brightnessup);
            mVTLowDown.setImageResource(R.drawable.mtk_vt_incall_button_brightnessdown);
            mVTLowUp.setVisibility(View.VISIBLE);
            mVTLowDown.setVisibility(View.VISIBLE);
            mVTLowUp.setEnabled(VTManagerLocal.getInstance().canIncBrightness());
            mVTLowDown.setEnabled(VTManagerLocal.getInstance().canDecBrightness());
        } else {
            mVTHighUp.setImageResource(R.drawable.mtk_vt_incall_button_brightnessup);
            mVTHighDown.setImageResource(R.drawable.mtk_vt_incall_button_brightnessdown);
            mVTHighUp.setVisibility(View.VISIBLE);
            mVTHighDown.setVisibility(View.VISIBLE);
            mVTHighUp.setEnabled(VTManagerLocal.getInstance().canIncBrightness());
            mVTHighDown.setEnabled(VTManagerLocal.getInstance().canDecBrightness());
        }

        VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting = false;
        VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting = true;
        VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting = false;

    }

    private void showVTLocalContrast() {
        if (DBG) {
            log("showVTLocalContrast()...");
        }

        // only when VTManager is under REDAY / CONNECT, can we do "Zoom" / "Brightness" / "Contrast" operations.
        if (VTManagerLocal.getInstance().getState() != VTManagerLocal.State.READY
                && VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            return;
        }

        if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
            mVTLowUp.setImageResource(R.drawable.mtk_vt_incall_button_contrastup);
            mVTLowDown.setImageResource(R.drawable.mtk_vt_incall_button_contrastdown);
            mVTLowUp.setVisibility(View.VISIBLE);
            mVTLowDown.setVisibility(View.VISIBLE);
            mVTLowUp.setEnabled(VTManagerLocal.getInstance().canIncContrast());
            mVTLowDown.setEnabled(VTManagerLocal.getInstance().canDecContrast());
        } else {
            mVTHighUp.setImageResource(R.drawable.mtk_vt_incall_button_contrastup);
            mVTHighDown.setImageResource(R.drawable.mtk_vt_incall_button_contrastdown);
            mVTHighUp.setVisibility(View.VISIBLE);
            mVTHighDown.setVisibility(View.VISIBLE);
            mVTHighUp.setEnabled(VTManagerLocal.getInstance().canIncContrast());
            mVTHighDown.setEnabled(VTManagerLocal.getInstance().canDecContrast());
        }

        VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting = false;
        VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting = false;
        VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting = true;

    }

    // when we call this method, we will hide local zoom,brightness and contrast
    private void hideLocalZoomOrBrightness(boolean resetSetting) {
        if (DBG) {
            log("hideLocalZoomOrBrightness()...");
        }

        mVTHighUp.setVisibility(View.GONE);
        mVTHighDown.setVisibility(View.GONE);
        mVTLowUp.setVisibility(View.GONE);
        mVTLowDown.setVisibility(View.GONE);
        
        if (resetSetting) {
            VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting = false;
            VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting = false;
            VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting = false;
        }
    }

    private void updateLocalZoomOrBrightness() {
        if (DBG) {
            log("updateLocalZoomOrBrightness()...");
        }

        if (!VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting
                && !VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting
                && !VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
            log("updateLocalZoomOrBrightness()... not in any operation, set related view gone.");
            mVTHighUp.setVisibility(View.GONE);
            mVTHighDown.setVisibility(View.GONE);
            mVTLowUp.setVisibility(View.GONE);
            mVTLowDown.setVisibility(View.GONE);
            return;
        }

        if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
            if (VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting) {
                mVTLowUp.setEnabled(VTManagerLocal.getInstance().canIncZoom());
                mVTLowDown.setEnabled(VTManagerLocal.getInstance().canDecZoom());
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting) {
                mVTLowUp.setEnabled(VTManagerLocal.getInstance().canIncBrightness());
                mVTLowDown.setEnabled(VTManagerLocal.getInstance().canDecBrightness());
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
                mVTLowUp.setEnabled(VTManagerLocal.getInstance().canIncContrast());
                mVTLowDown.setEnabled(VTManagerLocal.getInstance().canDecContrast());
            }
        } else {
            if (VTInCallScreenFlags.getInstance().mVTInLocalZoomSetting) {
                mVTHighUp.setEnabled(VTManagerLocal.getInstance().canIncZoom());
                mVTHighDown.setEnabled(VTManagerLocal.getInstance().canDecZoom());
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalBrightnessSetting) {
                mVTHighUp.setEnabled(VTManagerLocal.getInstance().canIncBrightness());
                mVTHighDown.setEnabled(VTManagerLocal.getInstance().canDecBrightness());
            } else if (VTInCallScreenFlags.getInstance().mVTInLocalContrastSetting) {
                mVTHighUp.setEnabled(VTManagerLocal.getInstance().canIncContrast());
                mVTHighDown.setEnabled(VTManagerLocal.getInstance().canDecContrast());
            }
        }
    }

    @Override
    public void updateVTScreen() {
        updateVTScreen(getVTScreenMode());
    }

    private void log(String msg) {
        Log.d(this, msg);
    }

    private void onVoiceVideoRecordClick(MenuItem menuItem) {
        Log.d(this, "onVoiceVideoRecordClick");

        final String title = menuItem.getTitle().toString();
        if (TextUtils.isEmpty(title)) {
            return;
        }

        if (!PhoneRecorderUtils.isExternalStorageMounted(mContext)) {
            Toast.makeText(mContext, getResources().getString(R.string.error_sdcard_access),
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!PhoneRecorderUtils.diskSpaceAvailable(PhoneRecorderUtils.PHONE_RECORD_LOW_STORAGE_THRESHOLD)) {
            handleStorageFull(true); // true for checking case
            return;
        }

        if (title.equals(mContext.getString(R.string.start_record_vt))) {
            if (RecorderState.IDLE_STATE == PhoneRecorderUtils.getRecorderState()) {
                Log.d(this, "startRecord");
                showStartVTRecorderDialog();
            }
        } else if (title.equals(mContext.getString(R.string.stop_record_vt))) {
            Log.d(this, "stopRecord");
            getPresenter().stopRecording();
        }
    }

    private void showStartVTRecorderDialog() {
        Log.d(this, "showStartVTRecorderDialog() ...");
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton(R.string.custom_message_cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (mVTRecorderSelector != null) {
                            mVTRecorderSelector.dismiss();
                            mVTRecorderSelector = null;
                        }
                    }
                });
        builder.setTitle(R.string.vt_recorder_start);

        if (mVTRecorderEntries == null) {
            mVTRecorderEntries = new ArrayList<String>();
        } else {
            mVTRecorderEntries.clear();
        }

        mVTRecorderEntries.add(getResources().getString(
                               R.string.vt_recorder_voice_and_peer_video));
        mVTRecorderEntries.add(getResources().getString(
                               R.string.vt_recorder_only_voice));
        mVTRecorderEntries.add(getResources().getString(
                               R.string.vt_recorder_only_peer_video));

        DialogInterface.OnClickListener myClickListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mVTRecorderSelector != null) {
                    mVTRecorderSelector.dismiss();
                    mVTRecorderSelector = null;
                }

                String currentString = mVTRecorderEntries.get(which);
                int type = 0;

                if (currentString.equals(getResources().getString(
                        R.string.vt_recorder_voice_and_peer_video))) {
                    if (DBG) {
                        log("The choice of start VT recording : voice and peer video");
                    }
                    type = PhoneRecorderUtils.PHONE_RECORDING_TYPE_VOICE_AND_PEER_VIDEO;
                } else if (currentString.equals(getResources().getString(
                        R.string.vt_recorder_only_voice))) {
                    if (DBG) {
                        log("The choice of start VT recording : only voice");
                    }
                    type = PhoneRecorderUtils.PHONE_RECORDING_TYPE_ONLY_VOICE;
                } else if (currentString.equals(getResources().getString(
                        R.string.vt_recorder_only_peer_video))) {
                    if (DBG) {
                        log("The choice of start VT recording : only peer video");
                    }
                    type = PhoneRecorderUtils.PHONE_RECORDING_TYPE_ONLY_PEER_VIDEO;
                } else {
                    if (DBG) {
                        log("The choice of start VT recording : wrong string");
                    }
                    return;
                }
                startRecord(type);
            }
        };

        builder.setSingleChoiceItems(mVTRecorderEntries
                .toArray(new CharSequence[mVTRecorderEntries.size()]), -1,
                myClickListener);

        mVTRecorderSelector = builder.create();
        mVTRecorderSelector.show();
    }

    private void startRecord(int type) {
        if (DBG) {
            log("startVTRecorder() ...");
        }

        if (VTManagerLocal.getInstance().getState() != VTManagerLocal.State.CONNECTED) {
            Log.d(this, "startRecord: failed, state should be CONNECTED.");
            return;
        }

        long sdMaxSize = PhoneRecorderUtils.getDiskAvailableSize() - PhoneRecorderUtils.PHONE_RECORD_LOW_STORAGE_THRESHOLD;
        if (sdMaxSize > 0) {
            if (PhoneRecorderUtils.PHONE_RECORDING_TYPE_ONLY_VOICE == type) {
                getPresenter().startVoiceRecording();
            } else if (type > 0) {
                getPresenter().startVtRecording(type, sdMaxSize);
            }
        } else if (-1 == sdMaxSize) {
            showToast(getResources().getString(R.string.vt_sd_null));
        } else {
            showToast(getResources().getString(R.string.vt_sd_not_enough));
        }
    }

    private void showToast(String string) {
        Toast.makeText(mContext, string, Toast.LENGTH_LONG).show();
    }

    private void handleStorageFull(final boolean isForCheckingOrRecording) {
        if (PhoneRecorderUtils.getMountedStorageCount(mContext) > 1) {
            // SD card case
            log("handleStorageFull(), mounted storage count > 1");
            if (PhoneRecorderUtils.STORAGE_TYPE_SD_CARD == PhoneRecorderUtils.getDefaultStorageType(
                    mContext)) {
                log("handleStorageFull(), SD card is using");
                showStorageFullDialog(com.mediatek.internal.R.string.storage_sd, true);
            } else if (PhoneRecorderUtils.STORAGE_TYPE_PHONE_STORAGE == PhoneRecorderUtils.getDefaultStorageType(
                    mContext)) {
                log("handleStorageFull(), phone storage is using");
                showStorageFullDialog(com.mediatek.internal.R.string.storage_withsd, true);
            } else {
                // never happen here
                log("handleStorageFull(), never happen here");
            }
        } else if (1 == PhoneRecorderUtils.getMountedStorageCount(mContext)) {
            log("handleStorageFull(), mounted storage count == 1");
            if (PhoneRecorderUtils.STORAGE_TYPE_SD_CARD == PhoneRecorderUtils.getDefaultStorageType(
                    mContext)) {
                log("handleStorageFull(), SD card is using, " + (isForCheckingOrRecording ? "checking case" : "recording case"));
                String toast = isForCheckingOrRecording ? getResources().getString(R.string.vt_sd_not_enough) :
                                                          getResources().getString(R.string.vt_recording_saved_sd_full);
                showToast(toast);
            } else if (PhoneRecorderUtils.STORAGE_TYPE_PHONE_STORAGE == PhoneRecorderUtils.getDefaultStorageType(
                    mContext)) {
                // only Phone storage case
                log("handleStorageFull(), phone storage is using");
                showStorageFullDialog(com.mediatek.internal.R.string.storage_withoutsd, false);
            } else {
                // never happen here
                log("handleStorageFull(), never happen here");
            }
        }
    }

    private AlertDialog mStorageSpaceDialog;

    public void showStorageFullDialog(final int resid, final boolean isSDCardExist) {
        if (DBG) {
            log("showStorageDialog... ");
        }

        if (null != mStorageSpaceDialog) {
            if (mStorageSpaceDialog.isShowing()) {
                return;
            }
        }
        CharSequence msg = getResources().getText(resid);

        // create the clickListener and cancel listener as needed.
        DialogInterface.OnClickListener oKClickListener = null;
        DialogInterface.OnClickListener cancelClickListener = null;
        OnCancelListener cancelListener = new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
            }
        };

        if (isSDCardExist) {
            oKClickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (DBG) {
                        log("showStorageDialog... , on click, which=" + which);
                    }
                    if (null != mStorageSpaceDialog) {
                        mStorageSpaceDialog.dismiss();
                    }
                    //To Setting Storage
                    Intent intent = new Intent(PhoneRecorderUtils.STORAGE_SETTING_INTENT_NAME);
                    startActivity(intent);
                }
            };
        }

        cancelClickListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (DBG) {
                    log("showStorageDialog... , on click, which=" + which);
                }
                if (null != mStorageSpaceDialog) {
                    mStorageSpaceDialog.dismiss();
                }
            }
        };

        CharSequence cancelButtonText = isSDCardExist ? getResources().getText(R.string.alert_dialog_dismiss) :
                                                        getResources().getText(R.string.ok);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext).setMessage(msg)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(getResources().getText(R.string.reminder))
            .setNegativeButton(cancelButtonText, cancelClickListener)
            .setOnCancelListener(cancelListener);
        if (isSDCardExist) {
            dialogBuilder.setPositiveButton(getResources().getText(R.string.vt_change_my_pic),
                                            oKClickListener);
        }

        mStorageSpaceDialog = dialogBuilder.create();
        mStorageSpaceDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mStorageSpaceDialog.show();
    }

    /**
     * If user choose picture to replace Peer Video in VTCallSetting, VTManager can't handle it before receive first frame,
     * So before receiving first frame, we do this function in app, and remove it when receiving first frame.
     * Note: We only do this just before VTCallFragment actually shown to make sure we have got latest values of VTSetting from TelService.
     * See ALPS01284999, VTCallSettingUtils.pushVTSettingParams() is called between
     * updatePeerVideoBkgDrawable() and onVTReceiveFirstFrame().
     */
    private void updatePeerVideoBkgDrawable() {
        log("updatePeerVideoBkgDrawable()... mVTToReplacePeer / mVTPeerBigger: "
                + VTInCallScreenFlags.getInstance().mVTToReplacePeer + " / "
                + VTInCallScreenFlags.getInstance().mVTPeerBigger);

        if (VTInCallScreenFlags.getInstance().mVTHasReceiveFirstFrame) {
            log("updatePeerVideoBkgDrawable()...Already receive first frame of VT Call, so do nothing here. ");
            return;
        }

        if (VTInCallScreenFlags.getInstance().mVTToReplacePeer) {
            if (null != VTInCallScreenFlags.getInstance().mVTReplacePeerBitmap) {
                log("updatePeerVideoBkgDrawable(): replace the peer video with drawable.");
                if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
                    mVTHighVideo.setBackgroundDrawable(new BitmapDrawable(VTInCallScreenFlags.getInstance().mVTReplacePeerBitmap));
                } else {
                    mVTLowVideo.setBackgroundDrawable(new BitmapDrawable(VTInCallScreenFlags.getInstance().mVTReplacePeerBitmap));
                }
            } else {
                if (DBG) {
                    log("VTInCallScreenFlags.getInstance().mVTReplacePeerBitmap is null");
                }
            }
        } else {
            log("updatePeerVideoBkgDrawable(): replace the peer video with BLACK color.");
            if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
                mVTHighVideo.setBackgroundColor(Color.BLACK);
            } else {
                mVTLowVideo.setBackgroundColor(Color.BLACK);
            }
        }
    }

    private void acquireVtWakeLock() {
        if (VTUtils.isVTActive() || VTUtils.isVTOutgoing()) {
            if (mVTWakeLock != null && !mVTWakeLock.isHeld()) {
                mVTWakeLock.acquire();
                Log.d(this, "acquire VT wake lock");
            }
        }
    }

    private void releaseVtWakeLock() {
        if (mVTWakeLock != null && mVTWakeLock.isHeld()) {
            mVTWakeLock.release();
            Log.d(this, "release VT wake lock");
        }
    }

    private int mScreenHeight;
    private int mScreenWidth;
    private int mStatusBarHeight;
    private int mNavigationBarHeight;

    /// M: Compute the vt dynamic layout.
    public void amendVtLayout(int callCardBottom) {
        // set vt call screen begain with call card bottom.
        final View view = getView();
        if (view != null) {
            ((MarginLayoutParams) view.getLayoutParams()).topMargin = callCardBottom;
        }

        // high video
        int highVideoWidth = mScreenWidth;
        int highVideoHeight = (highVideoWidth * 144) / 176;// W/H is 176/144
        mVTHighVideo.getLayoutParams().height = highVideoHeight;
        mVTHighVideo.getLayoutParams().width = highVideoWidth;

        // low video
        int lowVideoHeight = 0;
        int marginTop = 0;
        if (InCallUtils.isLandscape(mContext)) {
            marginTop = (int) getResources().getDimension(R.dimen.vt_call_low_video_margin_top);
            lowVideoHeight = mScreenHeight -mStatusBarHeight -callCardBottom -marginTop;
        } else {
            lowVideoHeight = mScreenHeight - mStatusBarHeight - callCardBottom - highVideoHeight;
        }

        if (InCallUtils.hasNavigationBar()) {
            lowVideoHeight -= mNavigationBarHeight;
        }

        int lowVideoWidth = (lowVideoHeight * 176) / 144;// W/H is 176/144;

        /// For ALPS01379560 @{
        // calculate low video's max width according to (low video width : button width) = 2:1
        int lowVideoMaxWidth = (mScreenWidth * 7) / 10;
        int lowVideoMarginBottom = 0;
        if (lowVideoWidth > lowVideoMaxWidth) {
            lowVideoWidth = lowVideoMaxWidth;
            lowVideoMarginBottom = lowVideoHeight;
            lowVideoHeight = (lowVideoWidth * 144) / 176;
            lowVideoMarginBottom = lowVideoMarginBottom - lowVideoHeight;
        }
        /// @}

        mVTLowVideo.getLayoutParams().height = lowVideoHeight;
        mVTLowVideo.getLayoutParams().width = lowVideoWidth;

        // notify for call button re-layout
        final int[] location = new int[2];
        mVTLowVideo.getLocationInWindow(location);

        int vtButtonInterval = (int) getResources().getDimension(R.dimen.vt_incall_screen_button_interval);
        int vtCallButtonMarginLeft = lowVideoWidth + vtButtonInterval;
        int vtCallButtonMarginBottom = lowVideoMarginBottom - vtButtonInterval;

        if (vtCallButtonMarginBottom <= 0) {
            vtCallButtonMarginBottom = 1;
        }
        if (InCallUtils.isLandscape(mContext)) {
            ((InCallActivity) getActivity()).onFinishVtVideoLayout(0, 0, callCardBottom);
        } else {
            ((InCallActivity) getActivity()).onFinishVtVideoLayout(vtCallButtonMarginLeft, vtCallButtonMarginBottom, lowVideoHeight);
        }
    }

    public void onFinishLayoutAmend() {
        mVTLowVideo.setVisibility(View.VISIBLE);
    }

    public void openVTCallFragment() {
        if (DBG) {
            log("openVTInCallCanvas!");
        }

        getView().setVisibility(View.VISIBLE);

        if (null != mVTHighVideo) {
            mVTHighVideo.setVisibility(View.VISIBLE);
        }
    }

    private Constants.VTScreenMode mVTScreenMode = Constants.VTScreenMode.VT_SCREEN_CLOSE;

    public Constants.VTScreenMode getVTScreenMode() {
        return mVTScreenMode;
    }

    public void setVTScreenMode(Constants.VTScreenMode mode) {
        Log.d(this, "setVTScreenMode()... mode: " + mode);

        if (Constants.VTScreenMode.VT_SCREEN_CLOSE == getVTScreenMode()
                && Constants.VTScreenMode.VT_SCREEN_CLOSE != mode) {
            openVTCallFragment();
            acquireVtWakeLock();
        }

        if (Constants.VTScreenMode.VT_SCREEN_CLOSE != getVTScreenMode()
                && Constants.VTScreenMode.VT_SCREEN_CLOSE == mode) {
            getView().setVisibility(View.INVISIBLE);
            releaseVtWakeLock();
        }
        mVTScreenMode = mode;
    }

    public void updateVTScreen(Constants.VTScreenMode mode) {
        Log.d(this, "updateVTScreen()... mode: " + mode);
        if (VTScreenMode.VT_SCREEN_CLOSE == mode) {
            return;
        }
        updateLocalZoomOrBrightness();
        /// DM lock @{
        if (InCallUtils.isDMLocked()) {
            hideLocalZoomOrBrightness();
        }
        /// @}
        // TODO: need update others ?
    }

    private void hideLocalZoomOrBrightness() {
        if (DBG) {
            log("hideLocalZoomOrBrightness()...");
        }

        mVTHighUp.setVisibility(View.GONE);
        mVTHighDown.setVisibility(View.GONE);
        mVTLowUp.setVisibility(View.GONE);
        mVTLowDown.setVisibility(View.GONE);
    }

    public void answerVTCallPre() {
        Log.d(this, "answerVTCallPre()...");
        updatePeerVideoBkgDrawable();
        // For MT, because surfaceChanged() is called when VTCallFragment is setup. mVTPeerBigger is not sync with VTSettingUtils in that point.
        // so we need call updateVTLocalPeerDisplay() here again to update the UI.
        // this may be called before surfaceChanged(), then we should skip setDisplay(), will be called in surfaceChanged() later. see ALPS01448353.
        if (VTInCallScreenFlags.getInstance().mVTSurfaceChangedH && VTInCallScreenFlags.getInstance().mVTSurfaceChangedL) {
            if (VTInCallScreenFlags.getInstance().mVTPeerBigger) {
                updateVTLocalPeerDisplay();
                VTUtils.setVTVisible(true);
            }
        }
        updateVTScreen();
    }

    public void dialVTCallSuccess() {
        Log.d(this, "dialVTCallSuccess()...");
//        updatePeerVideoBkgDrawable();
    }

}
