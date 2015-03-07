package com.mediatek.phone;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import android.widget.EditText;
import android.widget.TextView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.internal.telephony.Phone;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;
import com.mediatek.phone.ext.ExtensionManager;
import com.mediatek.phone.gemini.GeminiUtils;
import com.mediatek.phone.wrapper.PhoneWrapper;

import java.io.IOException;

public class UssdAlertActivity extends AlertActivity implements DialogInterface.OnClickListener {

    private static final String TAG = "UssdAlertActivity";
    public static final int USSD_DIALOG_REQUEST = 1;
    public static final int USSD_DIALOG_NOTIFICATION = 2;
    public static final String USSD_MESSAGE_EXTRA = "ussd_message";
    public static final String USSD_TYPE_EXTRA = "ussd_type";
    public static final String USSD_SLOT_ID = "slot_id";

    private TextView mMsg;
    private EditText mInputText;
    private String mText;
    private int mType = USSD_DIALOG_REQUEST;
    private int mSlotId;
    private Phone mPhone;
    private MediaPlayer mMediaPlayer;

    private boolean mIsClick;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the "dialog"
        final AlertController.AlertParams p = mAlertParams;

        mPhone = PhoneGlobals.getInstance().phone;
        Intent intent = getIntent();
        mText = intent.getStringExtra(USSD_MESSAGE_EXTRA);
        mType = intent.getIntExtra(USSD_TYPE_EXTRA, USSD_DIALOG_REQUEST);
        mSlotId = intent.getIntExtra(USSD_SLOT_ID, 0);
        // p.mIconId = android.R.drawable.ic_dialog_alert;
        // p.mTitle = getString(R.string.bt_enable_title);
        // p.mTitle = "USSD";
        p.mView = createView();
        if (mType == USSD_DIALOG_REQUEST) {
            p.mPositiveButtonText = getString(R.string.send_button);
            p.mNegativeButtonText = getString(R.string.cancel);
        } else {
            p.mPositiveButtonText = getString(R.string.ok);
        }

        p.mPositiveButtonListener = this;
        p.mNegativeButtonListener = this;

        playUSSDTone(PhoneGlobals.getInstance().getApplicationContext());
        PhoneUtils.sUssdActivity = this;
        setupAlert();
    }

    protected void onResume() {
        super.onResume();
        mIsClick = false;
        if (ExtensionManager.getInstance().getMmiCodeExtension().onUssdAlertActivityResume(this, mInputText, mType, mAlert)) {
            return;
        }
        if (mType == USSD_DIALOG_REQUEST) {
            String text = mInputText.getText().toString();
            if (text != null && text.length() > 0) {
                mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
            } else {
                mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            }

            mInputText.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                public void afterTextChanged(Editable s) {
                    int count = s == null ? 0 : s.length();
                    if (count > 0) {
                        mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    } else {
                        mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                    }
                }
            });
        }
    }

    private View createView() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ussd_response, null);
        mMsg = (TextView) dialogView.findViewById(R.id.msg);
        mInputText = (EditText) dialogView.findViewById(R.id.input_field);

        if (mMsg != null) {
            mMsg.setText(mText);
        }

        if (mType == USSD_DIALOG_NOTIFICATION) {
            mInputText.setVisibility(View.GONE);
        }

        return dialogView;
    }

    public void onClick(DialogInterface dialog, int which) {
        mIsClick = true;
        switch (which) {
        case DialogInterface.BUTTON_POSITIVE:
            if (mType == USSD_DIALOG_REQUEST) {
                PhoneWrapper.sendUssdResponse(mPhone, mInputText.getText().toString(), mSlotId);
            }
            finish();
            break;

        case DialogInterface.BUTTON_NEGATIVE:
            PhoneUtils.cancelUssdDialog();
            finish();
            break;
        default:
            break;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        PhoneLog.d(TAG, "onNewIntent");
        /// For ALPS01374729. @{
        // When this flag is true, we finish this activitiy but not cancel
        // current ussd code.
        mIsNewIntent = true;
        /// @}
        // force to finish ourself and then start new one
        finish();
        playUSSDTone(PhoneGlobals.getInstance().getApplicationContext());
        startActivity(intent);
    }

    public void playUSSDTone(final Context context) {
        /// M: if in silent mode, do not play USSD tone, see ALPS00424814 @{
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
                || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            PhoneLog.d(TAG, "ringerMode = " + ringerMode + ", do not play USSD tone...");
            return;
        }
        ///@}

        new Thread(new Runnable() {
            public void run() {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.reset();
                try {
                    mMediaPlayer.setDataSource(context, RingtoneManager
                            .getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                    mMediaPlayer.prepare();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (SecurityException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mMediaPlayer.start();
                setMediaListener(mMediaPlayer);
            }
        }).start();
    }

    public void setMediaListener(MediaPlayer mediaPlayer) {
        mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        });
    }

    /**
     * @return the mInputText widget
     */
    public EditText getmInputTextOnlyForTest() {
        return mInputText;
    }

    /**
     * @return the mAlert widget
     */
    public AlertController getmAlertOnlyForTest() {
        return super.mAlert;
    }

    /**
     * @return the mType widget
     */
    public int getmTypeOnlyForTest() {
        return mType;
    }

    protected void onDestroy() {
        PhoneLog.d(TAG, "onDestroy.");
        super.onDestroy();
    }

    /// For ALPS01374729. @{
    // When user want to leave this activity by press home key or else, we
    // should cancel the current ussd code.
    private boolean mIsNewIntent = false;
    @Override
    protected void onPause() {
        super.onPause();
        if (!mIsClick && mType == USSD_DIALOG_REQUEST && !mIsNewIntent) {
            PhoneUtils.cancelUssdDialog();
            PhoneLog.d(TAG, "onPause: cancel the request dialog.");
            finish();
        }
    }
    /// @}
}
