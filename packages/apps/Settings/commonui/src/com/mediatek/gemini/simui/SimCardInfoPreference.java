package com.mediatek.gemini.simui;

import android.app.Dialog;
import android.content.Context;
import android.preference.Preference;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.gemini.simui.SimInfoViewUtil.WidgetType;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

public class SimCardInfoPreference extends ListPreference {

    private static final String TAG = "SimCardInfoPreference";
    private SimInfoRecord mSimInfo;
    private int mIndicator = PhoneConstants.SIM_INDICATOR_UNKNOWN;
    private Context mContext;
    protected SimInfoViewUtil mSimInfoUtil = new SimInfoViewUtil();
    private WidgetType mWidgetType = WidgetType.None;
    private boolean mIsChecked = false;
    private boolean mIsClickable = false;
    private OnCheckedChangeListener mOnCheckedChangeListener;
    private boolean mEnableWidget = true;

    /**
     * Construct SimCardInfoPreference with specific layout
     * 
     * @param context
     *            Context
     * @param attrs
     *            attributes
     */
    public SimCardInfoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setLayoutResource(R.layout.sim_information_layout);
        setPersistent(false);
    }

    /**
     * Constructor with declare widget type default is none
     * 
     * @param type
     *            The widget Type for custom.
     * @param context
     *            Context
     */
    public SimCardInfoPreference(WidgetType type, Context context) {
        this(context, null);
        mWidgetType = type;
    }

    /**
     * @param type
     *            The widget Type for custom.
     * @param context
     * @param isAirPlaneOn
     *            The status of air plane mode.
     */
    public SimCardInfoPreference(WidgetType type, Context context,
            boolean isAirPlaneOn) {
        this(type, context);
        mEnableWidget = !isAirPlaneOn;
    }

    /**
     * Set OnCheckedChangeListener for custom widget.
     * @param listerner
     *            The custom widget checked status change listener.
     */
    public void setCheckedChangeListener(OnCheckedChangeListener listerner) {
        mOnCheckedChangeListener = listerner;

    }

    /**
     * Set the sim property for preference.
     * @param simInfo
     *            The sim information for the preference.
     * @param indicator
     *            The indicator status for the sim.
     */
    public void setSimInfoProperty(SimInfoRecord simInfo, int indicator) {
        mSimInfo = simInfo;
        mIndicator = indicator;
        Xlog.d(TAG, "setSimIndicator -- mIndicator = " + mIndicator);
        notifyChanged();
    }

    /**
     * Set the list data for preference
     * 
     * @param simInfo
     *            list data of SimInfoRecord
     */
    public void setSimInfoRecord(SimInfoRecord simInfo) {
        mSimInfo = simInfo;
        notifyChanged();
    }

    /**
     * Set the indicator state for sim preference
     */
    public void setSimIndicator(int indicator) {
        mIndicator = indicator;
        Xlog.d(TAG, "setSimIndicator -- mIndicator = " + mIndicator);
        notifyChanged();
    }

    /**
     * Return the preference related slot id
     * 
     * @return sim slot Id
     */
    public int getSimSlotId() {
        return mSimInfo.mSimSlotId;
    }

    /**
     * Return the preference related sim id
     * 
     * @return sim id
     */
    public long getSimInfoId() {
        return mSimInfo.mSimInfoId;
    }

    @Override
    protected void onBindView(View view) {
        Xlog.d(TAG, "onBindView");
        if (mSimInfo != null) {
            mSimInfoUtil.initViewId(view);
            mSimInfoUtil.setSimInfoView(mSimInfo,mContext.getThemeResId());
            mSimInfoUtil.setSimIndicatorIcon(mIndicator);
            mSimInfoUtil.setCustomWidget(mContext, mWidgetType);
            if (mSimInfoUtil.mSimCustView != null) {
                mSimInfoUtil.mSimCustView.setChecked(mIsChecked);
                mSimInfoUtil.mSimCustView.setClickable(mIsClickable);
                if (mOnCheckedChangeListener != null) {
                    mSimInfoUtil.mSimCustView.setEnabled(mEnableWidget);
                    mSimInfoUtil.mSimCustView.setOnCheckedChangeListener(mOnCheckedChangeListener);
                }
            }
        }
        super.onBindView(view);
    }

    /**
     * Check/Uncheck preference widget view
     * 
     * @param isCheck
     *            boolean value
     */
    public void setChecked(boolean isCheck) {
        mIsChecked = isCheck;
        persistBoolean(isCheck);
        notifyChanged();
    }

    /**
     * Set widget view is able clickable or not
     * 
     * @param isClickable
     *            boolean value
     */
    public void setWidgetClickable(boolean isClickable) {
        mIsClickable = isClickable;
        notifyChanged();
    }
    
    public void enableWidget(boolean isEnable) {
        mEnableWidget = isEnable;
        notifyChanged();
    }

    @Override
    protected void onClick() {
        if (mWidgetType != WidgetType.Dialog) {
            return;
        }
        Dialog dialog = getDialog();
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        showDialog(null);
    }
}
