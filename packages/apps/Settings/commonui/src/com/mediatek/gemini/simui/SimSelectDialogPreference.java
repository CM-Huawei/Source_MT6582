package com.mediatek.gemini.simui;


import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.android.internal.telephony.PhoneConstants;
import com.mediatek.gemini.simui.SimInfoViewUtil.WidgetType;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.List;

public class SimSelectDialogPreference extends DialogPreference implements OnItemClickListener {

    private static final String TAG = "SimSelectDialogPreference";
    
    private SimDialogAdapter mAdapter;
    
    // the entry values which are the sim id of item
    private List<Long> mEntryValues = new ArrayList<Long>();
    
    // sim item data
    private List<SimInfoRecord> mSimItem = new ArrayList<SimInfoRecord>();
    // sim item indicator
    private List<Integer> mSimIndicators = new ArrayList<Integer>();
    // sim item status(enable,disable)
    private List<Boolean> mItemStatus;
    // the item for normal type
    private List<String> mNormalItem = new ArrayList<String>();
    
    //default not selected any items
    private int mIndex = -1;
    private int mClickedDialogEntryIndex;
    private WidgetType mWidgetType = WidgetType.RadioButton;
    private boolean mEnableNormalItem = true;
    private Context mContext;
    private ListView mListView;
    private boolean mIsEnaled = true;
    /**
     * Perform inflation from XML and apply a class-specific base style.
     * @param context The Context this is associated with, through which it can
     *            access the current theme, resources
     * @param attrs The attributes of the XML tag that is inflating the preference.
     */
    public SimSelectDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray typeArray = context.obtainStyledAttributes(attrs,R.styleable.SimSelectDialogPreference);
        int paddingStart = (int) typeArray.getDimension(R.styleable.SimSelectDialogPreference_paddingStart, 
                (float) context.getResources().getDimension(R.dimen.sim_dialog_preference_padding_start));
        int paddingEnd = (int) typeArray.getDimension(R.styleable.SimSelectDialogPreference_paddingEnd,
                (float) context.getResources().getDimension(R.dimen.sim_dialog_preference_padding_end));
        
        setPersistent(false);
        mContext = context;
        mAdapter = new SimDialogAdapter(context);
        mAdapter.setPadding(paddingStart, paddingEnd);
    }
    
    /**
     * Constructor to create a SimSelectDialogPreference 
     * @param context The Context this is associated with, through which it can
     *            access the current theme, resources
     */
    public SimSelectDialogPreference(Context context) {
        this(context,null);
    }
    
    /**
     * Set the adapter widget type default is raidobutton
     * @param type the type of widget 
     */
    public void setWidgetType(WidgetType type) {
        mWidgetType = type;
    }
    /**
     * Init the data for preference
     * @param infoValues the siminforecord data
     * @param statusValues the indicator of sim card
     */
    public void setEntriesData(List<SimInfoRecord> simValues, List<Integer> indicator, List<String> lable, List<Boolean> itemStatus) {
        mSimItem = simValues;
        mNormalItem = lable;
        mSimIndicators = indicator;
        mItemStatus = itemStatus;
    }
    
    public void updateSimInfoList(List<SimInfoRecord> simValues) {
        mSimItem = simValues;
        mAdapter.notifyDataSetChanged();
    }
    
    public void updateSimIndicator(int slotId, int indicator) {
        int listIndex = 0; 
        for (SimInfoRecord item : mSimItem) {
            if (item.mSimSlotId == slotId) {
                if (mSimIndicators.size() != 0) {
                    mSimIndicators.set(listIndex, indicator);
                    mAdapter.notifyDataSetChanged();
                }
            }
            listIndex++;
        }
    }
    
    public int getTotalItemCount() {
        return mAdapter.getCount();
    }
    
    /**
     * Set values for each entry
     * @param values a list item includes the sim id for each item
     */
    public void setEntryValues(List<Long> values) {
        mEntryValues = values;
    }
    
    public int getItemCount() {
        return mAdapter.getCount();
    }
    
    /**
     * Sets the index of given value 
     * @param value one values of the entries
     */
    public void setValue(long value) {
        mIndex = findIndexOfValue(value);
        if (mIndex >= 0) {
            setSummary(getPrefSummary(mIndex));
            mAdapter.notifyDataSetChanged();
        }
    }
   
    public void setEnableNormalItem(boolean isEnabled) {
        mEnableNormalItem = isEnabled;
        mAdapter.notifyDataSetChanged();
    }
    private CharSequence getPrefSummary(int index) {
        String summary = null;
        Object item = mAdapter.getItem(index);
        if (item instanceof SimInfoRecord) {
            SimInfoRecord simItem = (SimInfoRecord) item;
            summary = simItem.mDisplayName;
        } else if (item instanceof String) {
            String nonSimItem = (String) item;
            summary = nonSimItem;
        }
        return summary;
    }

    /**
     * Returns the index of the given value (in the entry values array).
     * 
     * @param value The value whose index should be returned.
     * @return The index of the value, or -1 if not found.
     */
    private int findIndexOfValue(long value) {
        if (mEntryValues != null) {
            for (int i = 0 ; i < mEntryValues.size(); i++) {
                if (mEntryValues.get(i).equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        
        if (mAdapter != null) {
            mListView = new ListView(mContext);
            mListView.setAdapter(mAdapter);
            mListView.setOnItemClickListener(this);
            builder.setView(mListView);
        } else {
            Xlog.d(TAG,"Error with null adapter");
        }
        builder.setPositiveButton(null, null);
    } 
    
    
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        Xlog.d(TAG,"positiveResult = " + positiveResult + 
                   " mIndex = " + mIndex + 
                   " mClickedDialogEntryIndex = " + mClickedDialogEntryIndex);
        if (positiveResult && mIndex != mClickedDialogEntryIndex) {
            //get the selected values --- simId
            long simId = mEntryValues.get(mClickedDialogEntryIndex);
            // pass simId to onPreferenceChangeListener
            if (callChangeListener(simId)) {
                mIndex = mClickedDialogEntryIndex;
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        Drawable icon = getIcon();
        if (icon != null) {
            icon.setAlpha(enabled ? CommonUtils.ORIGINAL_IMAGE : 
                                    CommonUtils.IMAGE_GRAY);
        } else {
            Xlog.d(TAG,"fail to set icon alpha due to icon is null"); 
        }
        super.setEnabled(enabled);
        mIsEnaled = enabled;
    }
    
    // Ovrride this function for CR 1256060
    @Override
    public boolean isEnabled() {
        return mIsEnaled;
    }
    /**
     * An adapter for sim card with sim background and name and number also with non sim item
     *
     */
    class SimDialogAdapter extends BaseAdapter {
        
        private static final String TAG = "SimCardAdapter";
        private static final int NUM_WIDTH = 4;
        private static final int TYPE_SIM_ITEM = 0;
        private static final int TYPE_NORMAL_ITEM = 1;
        
        private Context mContext;
        private int mPaddingStart;
        private int mPaddingEnd;
        private LayoutInflater mInflater;
        
        public SimDialogAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        
        public void setPadding(int start, int end) {
            mPaddingStart = start;
            mPaddingEnd = end;
            Xlog.d(TAG,"setPadding mPaddingStart = " + mPaddingStart + " mPaddingEnd = " + mPaddingEnd);
        }
        
        @Override
        public int getCount() {
            int simCount = (mSimItem != null ? mSimItem.size() : 0);
            int normalCount = (mNormalItem != null ? mNormalItem.size() : 0);
            return simCount + normalCount;
        }

        @Override
        public Object getItem(int position) {
            Object object = null;
            int size = 0;
            if (mSimItem != null && position < (size = mSimItem.size())) {
                object = mSimItem.get(position);
            } else {
                if (mNormalItem != null && (position - size) < mNormalItem.size()) {
                    object = mNormalItem.get(position - size);
                }
            }
            return object;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SimInfoViewUtil simInfoView;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.sim_information_layout,null);
                
                //set padding for different cases
                LinearLayout layout = (LinearLayout) convertView.findViewById(R.id.sim_main_layout);
                layout.setPadding(mPaddingStart, 0, mPaddingEnd, 0);
                layout.setBackgroundDrawable(null);
                simInfoView = new SimInfoViewUtil();
                simInfoView.initViewId(convertView);
                simInfoView.setCustomWidget(mContext, mWidgetType);
                if (simInfoView.mSimCustView != null) {
                    simInfoView.mSimCustView.setClickable(false);
                }
                convertView.setTag(simInfoView);
            } else {
                simInfoView = (SimInfoViewUtil)convertView.getTag();
            }
            // All view fields must be updated every time, because the view may be recycled
            simInfoView.enableView(convertView, true);
            Object object = getItem(position);
            if (object instanceof SimInfoRecord) {
                SimInfoRecord simInfoItem = (SimInfoRecord) object;
                simInfoView.setSimInfoView(simInfoItem);
                simInfoView.setSimIndicatorIcon(mSimIndicators.get(position));
                if (mSimIndicators.get(position) == PhoneConstants.SIM_INDICATOR_RADIOOFF
                        || mItemStatus.get(position) == false) {
                    simInfoView.enableView(convertView, false);
                }
            } else if (object instanceof String) {
                String normalItem = (String) object;
                hideViewForNoSimItem(simInfoView);
                simInfoView.setSimName(normalItem);
                if (normalItem.equals(mContext.getString(R.string.simui_intenet_call))) {
                    //not a internet call item so only show the title string
                    simInfoView.setSimBackgroundColor(CommonUtils.INTERNET_COLOR_ID);
                }
                if (!mEnableNormalItem) {
                    simInfoView.enableView(convertView, false);
                }
            }
            //set highlight value
            if (simInfoView.mSimCustView != null) {
                simInfoView.mSimCustView.setChecked(position == mIndex ? true : false);
            }
            return convertView;
        }
        
        
        private void hideViewForNoSimItem(SimInfoViewUtil view) {
            view.mSimIconView.setVisibility(View.GONE);
            view.mSimIndicator.setVisibility(View.GONE);
            view.mSimShortNum.setVisibility(View.GONE);
            view.mSimNum.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
        if (view.isEnabled()) {
            mClickedDialogEntryIndex = position;
            Xlog.d(TAG,"onItemClick and click item = " + mClickedDialogEntryIndex);
            /*
             * Clicking on an item simulates the positive button
             * click, and dismisses the dialog.
             */
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            getDialog().dismiss();
        }
    }
}
