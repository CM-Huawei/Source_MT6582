package com.mediatek.voiceunlock;

import java.util.ArrayList;

import android.content.Context;
import android.content.pm.PackageParser.NewPermissionInfo;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.TwoStatePreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import com.mediatek.voiceunlock.R;
import com.mediatek.voiceunlock.VoiceUnlock.VoiceUnlockFragment;
import com.mediatek.xlog.Xlog;

public class VoiceCommandPreference extends TwoStatePreference {

    private static final String TAG = "VoiceCommandPreference";

    private TextView mTitleView;
    private TextView mSummaryView;
    private RadioButton mRadioButton;
    
    private String mSummary;

    private Context mContext;
    private String mKey;
    private LayoutInflater mInflater;

    /**
     * VoiceUnlockPreference construct method
     * 
     * @param context
     *            the context which is associated with, through which it can
     *            access the theme and the resources
     * @param attrs
     *            the attributes of XML tag that is inflating the preference
     * @param defStyle
     *            the default style to apply to the preference
     */
    public VoiceCommandPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        mInflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mKey = getKey();
    }

    /**
     * VoiceUnlockPreference construct method
     * 
     * @param context
     *            which is associated with, through which it can access the
     *            theme and the resources
     */
    public VoiceCommandPreference(Context context) {
        this(context, null);
    }

    /**
     * bind the preference with the profile
     * 
     * @param key
     *            the profile key
     */
    public void setProfileKey(String key) {
        setKey(key);
        mKey = key;
    }

    /**
     * Gets the view that will be shown in the PreferenceActivity
     * 
     * @param parent
     *            The parent that this view will eventually be attached
     * @return the preference object
     */
    @Override
    public View onCreateView(ViewGroup parent) {
        log("onCreateView " + getKey());
        View view = mInflater.inflate(R.layout.voice_command_select_item , null);
        mRadioButton = (RadioButton) view.findViewById(R.id.radio);
        mTitleView = (TextView) view.findViewById(R.id.primary);
        mSummaryView = (TextView) view.findViewById(R.id.secondary);
        
        mTitleView.setText(getTitle());
        mSummaryView.setText(getSummary());
        mRadioButton.setChecked(isChecked());
        return view;
    }

    public void setSummaryAppName(String name) {
        mSummary = name;
    }

    private void log(String msg) {
        Xlog.d(VoiceUnlock.TAG, "VoiceCommandPreference: " + msg);
    }

}
