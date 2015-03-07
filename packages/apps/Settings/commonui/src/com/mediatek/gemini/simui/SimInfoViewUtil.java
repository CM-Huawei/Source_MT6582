package com.mediatek.gemini.simui;

import android.R.style;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

public class SimInfoViewUtil {
    
    private static final String TAG = "SimInfoViewUtil";
    private static final int NUM_WIDTH = 4;
    
    TextView mSimName;
    TextView mSimNum;
    RelativeLayout mSimIconView;
    ImageView mSimIndicator;
    TextView mSimShortNum;
    LinearLayout mWidgetFrame;
    CompoundButton mSimCustView;
    
    
    /**
     * Find the view Id for view holder
     * 
     * @param convertView parent view
     */
    
    public void initViewId(View convertView) {
        mSimIconView = (RelativeLayout) convertView.findViewById(R.id.sim_color);
        mSimName = (TextView) convertView.findViewById(R.id.sim_name);
        mSimNum = (TextView) convertView.findViewById(R.id.sim_number);
        mSimIndicator = (ImageView) convertView.findViewById(R.id.sim_status);
        mSimShortNum = (TextView) convertView.findViewById(R.id.sim_short_number);
        mWidgetFrame = (LinearLayout)convertView.findViewById(R.id.widget_frame);
    }
    
    /**
     * Set differenct sim views based on the siminfo 
     * @param simInfo
     */
    public void setSimInfoView(SimInfoRecord simInfo) {
        setSimBackgroundColor(simInfo.mColor);
        bindSimInfoView(simInfo);
    }
    
    /**
     * Set differenct sim views based on the siminfo 
     * @param simInfo
     * @param themeId current theme id
     */
    public void setSimInfoView(SimInfoRecord simInfo,int themeId) {
        setSimBackgroundColorInTheme(simInfo.mColor,themeId);
        bindSimInfoView(simInfo);
    }
    
    private void bindSimInfoView(SimInfoRecord simInfo) {
        setSimShortNum(simInfo.mNumber, simInfo.mDispalyNumberFormat);
        setSimName(simInfo.mDisplayName);
        setSimNumber(simInfo.mNumber);
    }
    
    /**
     * Set the indicator icon for sim card
     * @param indicator the actual indicator state of sim card
     */
    public void setSimIndicatorIcon(int indicator) {
        boolean isVisible = true;
        if (indicator == PhoneConstants.SIM_INDICATOR_UNKNOWN ||
            indicator == PhoneConstants.SIM_INDICATOR_NORMAL) {
            isVisible = false;
            Xlog.e(TAG,"indicator = " + indicator + "unable to show indicator icon");
        } else {
            int res = CommonUtils.getStatusResource(indicator);
            mSimIndicator.setImageResource(res);
        }
        mSimIndicator.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Set the custom widget view such as Switch/Checkbox/RadioButton
     * @param type widget type
     * @param context context
     */
    public void setCustomWidget(Context context, WidgetType type) {
        int id = 0;
        switch(type) {
            case None:
            case Dialog:
                break;
            case Switch:
                id = R.id.widget_switch;
                break;
            case RadioButton:
                id = R.id.widget_radiobutton;
                break;
            case CheckBox:
                id = R.id.widget_checkbox;
                break;
            default:
                Xlog.d(TAG,"unknow type type = " + type);
                break;
        }
        if (id == 0) {
            mWidgetFrame.setVisibility(View.GONE);
        } else {
            mWidgetFrame.setVisibility(View.VISIBLE);
            mSimCustView = (CompoundButton) mWidgetFrame.findViewById(id);
            if (mSimCustView != null) {
                mSimCustView.setVisibility(View.VISIBLE);
                mSimCustView.setFocusable(false);
            }
        } 
    }
    
    /**
     * Set the color for sim card background
     * @param colorId color Id from SimInfoRecord
     */
    public void setSimBackgroundColor(int colorId) {
        boolean isVisible = true;
        if (colorId >= 0) {
            int resColor = CommonUtils.getSimColorResource(colorId);
            if (resColor >= 0) {
                mSimIconView.setBackgroundResource(resColor);
            } else {
                isVisible = false;
                Xlog.e(TAG,"wrong colorId unable to get color for sim colorId = " + colorId + " resColor = " + resColor);
            }
        } else {
            isVisible = false;
            Xlog.e(TAG,"colorId < 0 not correct");
        }
        mSimIconView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Set the color for sim card background for different theme
     * @param colorId color Id from SimInfoRecord
     * @param themeId theme Id of current activity
     */
    public void setSimBackgroundColorInTheme(int colorId, int themeId) {
        boolean isVisible = true;
        if (colorId >= 0) {
            int resColor = themeId == android.R.style.Theme_Holo_Light_DialogWhenLarge ? 
                    CommonUtils.getSimColorLightResource(colorId) :
                    CommonUtils.getSimColorResource(colorId);
            if (resColor >= 0) {
                mSimIconView.setBackgroundResource(resColor);
            } else {
                isVisible = false;
                Xlog.e(TAG,"wrong colorId unable to get color for sim colorId = " + colorId + " resColor = " + resColor);
            }
        } else {
            isVisible = false;
            Xlog.e(TAG,"colorId < 0 not correct");
        }
        mSimIconView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Set the short number for the sim card
     * @param number the number of sim card
     * @param numberFormat the format of sim card
     */
    private void setSimShortNum(String number, int numberFormat) {
        boolean isVisible = true;
        if (number != null && !number.equals("")) {
            Xlog.d(TAG,"numberFormat = " + numberFormat + " number = " + number);
            if (numberFormat != SimInfoManager.DISPALY_NUMBER_NONE) {
                if (number.length() <= NUM_WIDTH) {
                    ///M: support keep phone number style
                    mSimShortNum.setText(CommonUtils.phoneNumString(number));
                } else {
                    String shortNum = numberFormat == SimInfoManager.DISPLAY_NUMBER_FIRST ?
                                      number.substring(0,NUM_WIDTH) :
                                      number.substring(number.length() - NUM_WIDTH);
                    ///M: support keep phone number style                                   
                    mSimShortNum.setText(CommonUtils.phoneNumString(shortNum));               
                }
            } else {
                isVisible = false;
            }   
        } else {
            Xlog.d(TAG,"No sim item not support to call the function");
            isVisible = false;
        }
        mSimShortNum.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Set the sim name
     * @param name the name for sim from SimInfoRecord
     */
    public void setSimName(String name) {
        boolean isVisible = true;
        if (name != null && !name.isEmpty()) {
            mSimName.setText(name);
        } else {
            Xlog.e(TAG,"No sim item not support to call the function");
            isVisible = false;
        }
        mSimName.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    
    public void enableView(View view, boolean isEnabled) {
        view.setEnabled(isEnabled);
        if (view instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) view;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                enableView(vg.getChildAt(i), isEnabled);
            }
        }
    }
    /**
     * Set the sim number
     * @param number the number
     */
    private void setSimNumber(String number) {
        boolean isVisible = true;
        if (number != null && !number.isEmpty()) {
            ///M: support keep phone number style
            mSimNum.setText(CommonUtils.phoneNumString(number));
        } else {
            Xlog.e(TAG,"No sim item not support to call the function");
            isVisible = false;
        }
        mSimNum.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * An enum for widget type defaut should be none and can extend more
     *
     * None: nothing no widget 
     * RadionButton: radio button widget
     * Switch:       switch widget
     * CheckBox:     checkbox widget
     */
    public enum WidgetType {
        None,
        Dialog, 
        RadioButton, 
        Switch, 
        CheckBox;
    }
}
