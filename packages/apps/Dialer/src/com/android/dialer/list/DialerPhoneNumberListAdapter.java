package com.android.dialer.list;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DialerSearch;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateFormat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.format.TextHighlighter;
import com.android.contacts.common.list.ContactListItemView;

import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.list.PhoneNumberListAdapter.PhoneQuery;
import com.android.dialer.R;
import com.android.internal.telephony.CallerInfo;

import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;
import com.mediatek.contacts.simcontact.SlotUtils;
import com.mediatek.dialer.calllog.CallLogSimInfoHelper;
import com.mediatek.dialer.calllogex.PhoneNumberHelperEx;
import com.mediatek.dialer.dialpad.DialerSearchItemView;
import com.mediatek.dialer.dialpad.DialerSearchUtils;
import com.mediatek.dialer.util.LogUtils;
import com.mediatek.dialer.widget.QuickContactBadgeWithPhoneNumber;
import com.mediatek.phone.HyphonManager;
import com.mediatek.phone.SIMInfoWrapper;

/**
 * {@link PhoneNumberListAdapter} with the following added shortcuts, that are displayed as list
 * items:
 * 1) Directly calling the phone number query
 * 2) Adding the phone number query to a contact
 *
 * These shortcuts can be enabled or disabled to toggle whether or not they show up in the
 * list.
 */
public class DialerPhoneNumberListAdapter extends PhoneNumberListAdapter {

    private String mFormattedQueryString;
    private String mCountryIso;

    public final static int SHORTCUT_INVALID = -1;
    public final static int SHORTCUT_DIRECT_CALL = 0;
    public final static int SHORTCUT_ADD_NUMBER_TO_CONTACTS = 1;

    public final static int SHORTCUT_COUNT = 2;

    private final boolean[] mShortcutEnabled = new boolean[SHORTCUT_COUNT];

    public DialerPhoneNumberListAdapter(Context context) {
        super(context);

        mCountryIso = GeoUtil.getCurrentCountryIso(context);

        // Enable all shortcuts by default
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            mShortcutEnabled[i] = true;
        }

        /// M: Use MTK Dialer UI @{
        initResources(context);
        /// M: @}
    }

    @Override
    public int getCount() {
        return super.getCount() + getShortcutCount();
    }

    /**
     * @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT
     */
    public int getShortcutCount() {
        int count = 0;
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            if (mShortcutEnabled[i]) count++;
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        final int shortcut = getShortcutTypeFromPosition(position);
        if (shortcut >= 0) {
            // shortcutPos should always range from 1 to SHORTCUT_COUNT
            return super.getViewTypeCount() + shortcut;
        } else {
            return super.getItemViewType(position);
        }
    }

    @Override
    public int getViewTypeCount() {
        // Number of item view types in the super implementation + 2 for the 2 new shortcuts
        return super.getViewTypeCount() + SHORTCUT_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            if (convertView != null) {
                assignShortcutToView((ContactListItemView) convertView, shortcutType);
                return convertView;
            } else {
                final ContactListItemView v = new ContactListItemView(getContext(), null);
                assignShortcutToView(v, shortcutType);
                return v;
            }
        } else {
            return super.getView(position, convertView, parent);
        }
    }

    /**
     * @param position The position of the item
     * @return The enabled shortcut type matching the given position if the item is a
     * shortcut, -1 otherwise
     */
    public int getShortcutTypeFromPosition(int position) {
        int shortcutCount = position - super.getCount();
        if (shortcutCount >= 0) {
            // Iterate through the array of shortcuts, looking only for shortcuts where
            // mShortcutEnabled[i] is true
            for (int i = 0; shortcutCount >= 0 && i < mShortcutEnabled.length; i++) {
                if (mShortcutEnabled[i]) {
                    shortcutCount--;
                    if (shortcutCount < 0) return i;
                }
            }
            throw new IllegalArgumentException("Invalid position - greater than cursor count "
                    + " but not a shortcut.");
        }
        return SHORTCUT_INVALID;
    }

    @Override
    public boolean isEmpty() {
        return getShortcutCount() == 0 && super.isEmpty();
    }

    @Override
    public boolean isEnabled(int position) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            return true;
        } else {
            return super.isEnabled(position);
        }
    }

    private void assignShortcutToView(ContactListItemView v, int shortcutType) {
        final CharSequence text;
        final int drawableId;
        final Resources resources = getContext().getResources();
        final String number = getFormattedQueryString();
        switch (shortcutType) {
            case SHORTCUT_DIRECT_CALL:
                text = resources.getString(R.string.search_shortcut_call_number, number);
                drawableId = R.drawable.ic_phone_dk;
                break;
            case SHORTCUT_ADD_NUMBER_TO_CONTACTS:
                text = resources.getString(R.string.search_shortcut_add_to_contacts);
                drawableId = R.drawable.ic_add_person_dk;
                break;
            default:
                throw new IllegalArgumentException("Invalid shortcut type");
        }
        v.setDrawableResource(R.drawable.list_item_avatar_bg, drawableId);
        v.setDisplayName(text);
        v.setPhotoPosition(super.getPhotoPosition());
    }

    public void setShortcutEnabled(int shortcutType, boolean visible) {
        mShortcutEnabled[shortcutType] = visible;
    }

    public String getFormattedQueryString() {
        return mFormattedQueryString;
    }

    @Override
    public void setQueryString(String queryString) {
        mFormattedQueryString = PhoneNumberUtils.formatNumber(
                PhoneNumberUtils.convertAndStrip(queryString), mCountryIso);
        super.setQueryString(queryString);
    }

//------------------------------------MTK--------------------------------
    private final String TAG = "DialerPhoneNumberListAdapter";

    private final int VIEW_TYPE_UNKNOWN = -1;
    private final int VIEW_TYPE_CONTACT = 0;
    private final int VIEW_TYPE_CALL_LOG = 1;

    private final int NUMBER_TYPE_NORMAL = 0;
    private final int NUMBER_TYPE_UNKNOWN = 1;
    private final int NUMBER_TYPE_VOICEMAIL = 2;
    private final int NUMBER_TYPE_PRIVATE = 3;
    private final int NUMBER_TYPE_PAYPHONE = 4;
    private final int NUMBER_TYPE_EMERGENCY = 5;

    private final int DS_MATCHED_DATA_INIT_POS    = 3;
    private final int DS_MATCHED_DATA_DIVIDER     = 3;

    private final int NAME_LOOKUP_ID_INDEX        = 0;
    private final int CONTACT_ID_INDEX            = 1;
    private final int DATA_ID_INDEX               = 2;
    private final int CALL_LOG_DATE_INDEX         = 3;
    private final int CALL_LOG_ID_INDEX           = 4;
    private final int CALL_TYPE_INDEX             = 5;
    private final int CALL_GEOCODED_LOCATION_INDEX = 6;
    private final int SIM_ID_INDEX                = 7;
    private final int VTCALL                      = 8;
    private final int INDICATE_PHONE_SIM_INDEX    = 9;
    private final int CONTACT_STARRED_INDEX       = 10;
    private final int PHOTO_ID_INDEX              = 11;
    private final int SEARCH_PHONE_TYPE_INDEX     = 12;
    private final int NAME_INDEX                  = 13;
    private final int SEARCH_PHONE_NUMBER_INDEX   = 14;
    private final int CONTACT_NAME_LOOKUP_INDEX   = 15;
    private final int IS_SDN_CONTACT              = 16;
    private final int DS_MATCHED_DATA_OFFSETS     = 17;
    private final int DS_MATCHED_NAME_OFFSETS     = 18;

    private HyphonManager mHyphonManager;
    private ContactPhotoManager mContactPhotoManager;
    private CallLogSimInfoHelper mCallLogSimInfoHelper;

    private String mVoiceMailNumber;
    private String mVoiceMailNumber2;

    private String mUnknownNumber;
    private String mPrivateNumber;
    private String mPayphoneNumber;

    private String mEmergency;
    private String mVoiceMail;

    private int mOperatorVerticalPadding;
    private int mOperatorHorizontalPaddingRight;
    private int mOperatorHorizontalPaddingLeft;

    private PhoneNumberHelperEx mPhoneNumberHelper;

    private Drawable[] mVideoCallTypeDrawables = new Drawable[6];
    private Drawable[] mCallTypeDrawables = new Drawable[6];

    private TextHighlighter mTextHighlighter;

    private void initResources (Context context) {
        mHyphonManager = HyphonManager.getInstance();
        mContactPhotoManager = ContactPhotoManager.getInstance(context);
        mCallLogSimInfoHelper = new CallLogSimInfoHelper(context.getResources());
        mEmergency = context.getResources().getString(R.string.emergencycall);
        mVoiceMail = context.getResources().getString(R.string.voicemail);
        mPrivateNumber = context.getResources().getString(R.string.private_num);
        mPayphoneNumber = context.getResources().getString(R.string.payphone);
        mUnknownNumber = context.getResources().getString(R.string.unknown);

        mPhoneNumberHelper = new PhoneNumberHelperEx(context.getResources());

        // 1. incoming 2. outgoing 3. missed 4.VVM 5. auto
        // rejected(Calls.AUTOREJECTED_TYPE)
        mCallTypeDrawables[1] = context.getResources().getDrawable(
                R.drawable.ic_call_incoming_holo_dark);
        mCallTypeDrawables[2] = context.getResources().getDrawable(
                R.drawable.ic_call_outgoing_holo_dark);
        mCallTypeDrawables[3] = context.getResources().getDrawable(
                R.drawable.ic_call_missed_holo_dark);
        /**M: [VVM] set voice mail icon color red.*/
        mCallTypeDrawables[4] = context.getResources().getDrawable(
                R.drawable.mtk_ic_call_voicemail_holo_dark_red);
        mCallTypeDrawables[5] = context.getResources().getDrawable(
                R.drawable.mtk_ic_call_autorejected_holo_dark);

        mVideoCallTypeDrawables[1] = context.getResources().getDrawable(
                R.drawable.mtk_ic_video_call_incoming_holo_dark);
        mVideoCallTypeDrawables[2] = context.getResources().getDrawable(
                R.drawable.mtk_ic_video_call_outgoing_holo_dark);
        mVideoCallTypeDrawables[3] = context.getResources().getDrawable(
                R.drawable.mtk_ic_video_call_missed_holo_dark);
        /**M: [VVM] set voice mail icon color red.*/
        mVideoCallTypeDrawables[4] = context.getResources().getDrawable(
                R.drawable.mtk_ic_call_voicemail_holo_dark_red);
        mVideoCallTypeDrawables[5] = context.getResources().getDrawable(
                R.drawable.mtk_ic_video_call_autorejected_holo_dark);

        final Resources r = context.getResources();
        mOperatorVerticalPadding = 0;
        mOperatorHorizontalPaddingRight = r
                .getDimensionPixelSize(R.dimen.dialpad_operator_horizontal_padding_right);
        mOperatorHorizontalPaddingLeft = r
                .getDimensionPixelSize(R.dimen.dialpad_operator_horizontal_padding_left);
    }


    private int getViewType(Cursor cursor) {
        int retval = VIEW_TYPE_UNKNOWN;
        final int contactId = cursor.getInt(CONTACT_ID_INDEX);
        final int callLogId = cursor.getInt(CALL_LOG_ID_INDEX);

        LogUtils.d(TAG, "getViewType: contactId: " + contactId + " ,callLogId: " + callLogId);

        if (contactId > 0) {
            retval = VIEW_TYPE_CONTACT;
        } else if (callLogId > 0) {
            retval = VIEW_TYPE_CALL_LOG;
        }

        return retval;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        LogUtils.d(TAG, "---bindView---begin");

        final int viewType = getViewType(cursor);

        switch (viewType) {
            case VIEW_TYPE_CONTACT:
                bindContactView(itemView, getContext(), cursor);
                break;
            case VIEW_TYPE_CALL_LOG:
                bindCallLogView(itemView, getContext(), cursor);
                break;
            default:
                break;
        }
        LogUtils.d(TAG, "---bindView---end");
    }


    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        LogUtils.d(TAG, "---newView---begin");

        View view = View.inflate(context, R.layout.mtk_dialer_search_item_view, null);

        ViewHolder viewHolder = createViewHolder();
        viewHolder.name = (TextView) view.findViewById(R.id.name);
        viewHolder.labelAndNumber = (TextView) view.findViewById(R.id.labelAndNumber);
        viewHolder.callType = (ImageView) view.findViewById(R.id.callType);
        viewHolder.operator = (TextView) view.findViewById(R.id.operator);
        viewHolder.date = (TextView) view.findViewById(R.id.date);
        viewHolder.quickContactBadge = (QuickContactBadgeWithPhoneNumber) view.findViewById(R.id.quick_contact_photo);
        view.setTag(viewHolder);

        LogUtils.d(TAG, "---newView---end");
        return view;
    }

    private void bindContactView(View view, Context context, Cursor cursor) {

        final ViewHolder viewHolder = (ViewHolder) view.getTag();
        viewHolder.callType.setVisibility(View.GONE);
        viewHolder.operator.setVisibility(View.GONE);
        viewHolder.date.setVisibility(View.GONE);
        viewHolder.labelAndNumber.setVisibility(View.VISIBLE);

        final String number = cursor.getString(SEARCH_PHONE_NUMBER_INDEX);
        final String formatNumber = numberLeftToRight(mHyphonManager.formatNumber(number));
        final int numberType = getNumberType(number);

        final int simId = cursor.getInt(SIM_ID_INDEX);
        final int slotID = SIMInfoWrapper.getDefault().getSimSlotById(simId);
        mVoiceMailNumber = SlotUtils.getVoiceMailNumberForSlot(slotID);

        final int labelType = cursor.getInt(SEARCH_PHONE_TYPE_INDEX);
        CharSequence label = CommonDataKinds.Phone.getTypeLabel(context.getResources(), labelType, null);

        final CharSequence name = cursor.getString(NAME_INDEX);

//        /** M:AAS @ } */
//        final int slotId = SIMInfoWrapper.getDefault().getSimSlotById(indicate);
//        label = ExtensionManager.getInstance().getContactAccountExtension().getTypeLabel(context.getResources(),
//                labelType, null, slotId, ExtensionManager.COMMD_FOR_AAS);
//        /** M: @ } */

        LogUtils.d(TAG, "bindContactView. name = " + name + " number = " + number + " label = " + label);

        Uri contactUri = getContactUri(cursor);
        LogUtils.d(TAG, "bindContactView, contactUri: " + contactUri);

        long photoId = cursor.getLong(PHOTO_ID_INDEX);
        int indicate = cursor.getInt(INDICATE_PHONE_SIM_INDEX);

        if (indicate > 0) {
            int isSdnContact = cursor.getInt(IS_SDN_CONTACT);
            photoId = DialerSearchUtils.getSimType(indicate, isSdnContact);
        }

        if (numberType == NUMBER_TYPE_VOICEMAIL || numberType == NUMBER_TYPE_EMERGENCY) {
            photoId = 0;
            viewHolder.quickContactBadge.assignPhoneNumber(null, false);
            viewHolder.quickContactBadge.assignContactUri(null);
        } else {
            viewHolder.quickContactBadge.assignPhoneNumber(null, false);
            viewHolder.quickContactBadge.assignContactUri(contactUri);
        }

        mContactPhotoManager.loadThumbnail(viewHolder.quickContactBadge, photoId, false);

        if (isSpecialNumber(numberType)) {
            if (numberType == NUMBER_TYPE_VOICEMAIL || numberType == NUMBER_TYPE_EMERGENCY) {
                if (numberType == NUMBER_TYPE_VOICEMAIL) {
                    viewHolder.name.setText(mVoiceMail);
                } else {
                    viewHolder.name.setText(mEmergency);
                }

                viewHolder.labelAndNumber.setVisibility(View.VISIBLE);
                String highlight = getNumberHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightHyphon(highlight, formatNumber, number);
                    viewHolder.labelAndNumber.setText(style);
               } else {
                   viewHolder.labelAndNumber.setText(formatNumber);
                }
            } else {
                final String convert = specialNumberToString(numberType);
                viewHolder.name.setText(convert);
            }
        } else {
            // empty name ?
            if (!TextUtils.isEmpty(name)) {
                // highlight name
                String highlight = getNameHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightString(highlight, name);
                    viewHolder.name.setText(style);

             //       if (DialerSearchUtils.isInValidDialpadString(highlight)) {
                    if (isRegularSearch(cursor)) {
                        viewHolder.name.setText(highlightName(highlight, name));
                    }
                } else {
                    viewHolder.name.setText(name);
                }
                // highlight number
                if (!TextUtils.isEmpty(formatNumber)) {
                    highlight = getNumberHighlight(cursor);
                    if (!TextUtils.isEmpty(highlight)) {
                        SpannableStringBuilder style = highlightHyphon(highlight, formatNumber, number);
                        setLabelAndNumber(viewHolder.labelAndNumber, label, style);
                    } else {
                        setLabelAndNumber(viewHolder.labelAndNumber, label, formatNumber);
                   }
                }
            } else {
                viewHolder.labelAndNumber.setVisibility(View.GONE);

                // highlight number and set number to name text view
                if (!TextUtils.isEmpty(formatNumber)) {
                    final String highlight = getNumberHighlight(cursor);
                    if (!TextUtils.isEmpty(highlight)) {
                        SpannableStringBuilder style = highlightHyphon(highlight, formatNumber, number);
                        viewHolder.name.setText(style);
                    } else {
                        viewHolder.name.setText(formatNumber);
                    }
                } else {
                    viewHolder.name.setVisibility(View.GONE);
                }
            }
        }
    }


    private void bindCallLogView(View view, Context context, Cursor cursor) {
        final ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.callType.setVisibility(View.VISIBLE);
        viewHolder.operator.setVisibility(View.VISIBLE);
        viewHolder.date.setVisibility(View.VISIBLE);
        viewHolder.labelAndNumber.setVisibility(View.VISIBLE);

        final String number = cursor.getString(SEARCH_PHONE_NUMBER_INDEX);
        final String formatNumber = numberLeftToRight(mHyphonManager.formatNumber(number));
        final int numberType = getNumberType(number);

        final int type = cursor.getInt(CALL_TYPE_INDEX);
        final int videoCall = cursor.getInt(VTCALL);
        final int simId = cursor.getInt(SIM_ID_INDEX);
        final long date = cursor.getLong(CALL_LOG_DATE_INDEX);
        final int indicate = cursor.getInt(INDICATE_PHONE_SIM_INDEX);
        String geocode = cursor.getString(CALL_GEOCODED_LOCATION_INDEX);

 //       viewHolder.callId = cursor.getInt(CALL_LOG_ID_INDEX);

        LogUtils.d(TAG, "bindCallLogView : type = " + type + " videoCall = " + videoCall + " simId = " + simId + " number = "
                + number + " geocode = " + geocode + "formatNumber = " + formatNumber);

        long photoId = cursor.getLong(PHOTO_ID_INDEX);
        if (indicate > 0) {
            int isSdnContact = cursor.getInt(IS_SDN_CONTACT);
            photoId = DialerSearchUtils.getSimType(indicate, isSdnContact);
        }

        viewHolder.quickContactBadge.assignContactUri(null);
        viewHolder.quickContactBadge.assignPhoneNumber(number, mPhoneNumberHelper.isSipNumber(number));

        mContactPhotoManager.loadThumbnail(viewHolder.quickContactBadge, photoId, false);

        if (TextUtils.isEmpty(geocode)) {
            geocode = context.getResources().getString(R.string.call_log_empty_gecode);
        }

        viewHolder.labelAndNumber.setText(geocode);

        if (isSpecialNumber(numberType)) {
            if (numberType == NUMBER_TYPE_VOICEMAIL || numberType == NUMBER_TYPE_EMERGENCY) {
                if (numberType == NUMBER_TYPE_VOICEMAIL) {
                    viewHolder.name.setText(mVoiceMail);
                } else {
                    viewHolder.name.setText(mEmergency);
                }

                viewHolder.labelAndNumber.setVisibility(View.VISIBLE);
                String highlight = getNumberHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightHyphon(highlight, formatNumber, number);
                    viewHolder.labelAndNumber.setText(style);
                } else {
                    viewHolder.labelAndNumber.setText(formatNumber);
                }
            } else {
                final String convert = specialNumberToString(numberType);
                viewHolder.name.setText(convert);
            }
        } else {
            if (!TextUtils.isEmpty(formatNumber)) {
                String highlight = getNumberHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightHyphon(highlight, formatNumber, number);
                    viewHolder.name.setText(style);
                } else {
                    viewHolder.name.setText(formatNumber);
                }
            }
        }

        java.text.DateFormat dateFormat = DateFormat.getTimeFormat(context);
        String dateString = dateFormat.format(date);
        final String display = mCallLogSimInfoHelper.getSimDisplayNameById(simId);
        viewHolder.date.setText(dateString);

        Drawable[] callTypeDrawables = videoCall == 1 ? mVideoCallTypeDrawables : mCallTypeDrawables;
        viewHolder.callType.setImageDrawable(callTypeDrawables[type]);

        if (!TextUtils.isEmpty(display)) {
            viewHolder.operator.setText(display);
            viewHolder.operator.setBackgroundDrawable(mCallLogSimInfoHelper.getSimColorDrawableById(simId));
            viewHolder.operator.setPadding(mOperatorHorizontalPaddingLeft, mOperatorVerticalPadding,
                    mOperatorHorizontalPaddingRight, mOperatorVerticalPadding);
            viewHolder.operator.setGravity(Gravity.CENTER_VERTICAL);
        } else {
            viewHolder.operator.setVisibility(View.GONE);
        }
    }


    private int getNumberType(String number) {
        int type = NUMBER_TYPE_NORMAL;
        if (TextUtils.isEmpty(number)) {
            return type;
        }
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            type = NUMBER_TYPE_UNKNOWN;
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            type = NUMBER_TYPE_PRIVATE;
        } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            type = NUMBER_TYPE_PAYPHONE;
        } else {
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                if ((mVoiceMailNumber != null && PhoneNumberUtils.compare(mVoiceMailNumber, number))
                        || (mVoiceMailNumber2 != null && PhoneNumberUtils.compare(mVoiceMailNumber2, number))) {
                    type = NUMBER_TYPE_VOICEMAIL;
                }
            } else {
                if (mVoiceMailNumber != null && PhoneNumberUtils.compare(mVoiceMailNumber, number)) {
                    type = NUMBER_TYPE_VOICEMAIL;
                }
            }
        }
        return type;
    }

    private Uri getContactUri(Cursor cursor) {
        final String lookup = cursor.getString(CONTACT_NAME_LOOKUP_INDEX);
        final int contactId = cursor.getInt(CONTACT_ID_INDEX);
        return Contacts.getLookupUri(contactId, lookup);
    }

    private boolean isSpecialNumber(int type) {
        return type != NUMBER_TYPE_NORMAL;
    }

    private SpannableStringBuilder highlightString(String highlight, CharSequence target) {
        SpannableStringBuilder style = new SpannableStringBuilder(target);
        int length = highlight.length();
        for (int i = DS_MATCHED_DATA_INIT_POS; i < length; i += DS_MATCHED_DATA_DIVIDER) {
            if (((int) highlight.charAt(i)) > style.length()
                    || ((int) highlight.charAt(i + 1) + 1) > style.length()) {
                break;
            }
            style.setSpan(new StyleSpan(Typeface.BOLD), (int) highlight.charAt(i),
                    (int) highlight.charAt(i + 1) + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return style;
    }

    private CharSequence highlightName(String highlight, CharSequence target) {
        String highlightedPrefix = getUpperCaseQueryString();
        if (highlightedPrefix != null) {
            mTextHighlighter = new TextHighlighter(Typeface.BOLD);
            target =  mTextHighlighter.applyPrefixHighlight(target, highlightedPrefix);
        }
        return target;
    }

    private SpannableStringBuilder highlightHyphon(String highlight, String target, String origin) {
        if (target == null) {
            Log.w(TAG, "[highlightHyphon] target is null");
            return null;
        }
        SpannableStringBuilder style = new SpannableStringBuilder(target);
        ArrayList<Integer> numberHighlightOffset = DialerSearchUtils
                .adjustHighlitePositionForHyphen(target, highlight.substring(DS_MATCHED_DATA_INIT_POS), origin);
        if (numberHighlightOffset != null && numberHighlightOffset.size() > 1) {
            style.setSpan(new StyleSpan(Typeface.BOLD),
                    numberHighlightOffset.get(0),
                    numberHighlightOffset.get(1) + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return style;
    }

    private String getNameHighlight(Cursor cursor) {
        final int index = cursor.getColumnIndex(DialerSearch.MATCHED_NAME_OFFSETS);
        return index != -1 ? cursor.getString(index) : null;
    }

    private boolean isRegularSearch (Cursor cursor) {
        final int index = cursor.getColumnIndex(DialerSearch.MATCHED_DATA_OFFSETS);
        String regularSearch = (index != -1 ? cursor.getString(index) : null);
        LogUtils.d(TAG, "" + regularSearch);

        return Boolean.valueOf(regularSearch);
    }

    private String getNumberHighlight(Cursor cursor) {
        final int index = cursor.getColumnIndex(DialerSearch.MATCHED_DATA_OFFSETS);
        return index != -1 ? cursor.getString(index) : null;
    }

    private void setLabelAndNumber(TextView view, CharSequence label, SpannableStringBuilder number) {
        if (PhoneNumberUtils.isUriNumber(number.toString())) {
            view.setText(number);
            return;
        }
        if (TextUtils.isEmpty(label)) {
            view.setText(number);
        } else if (TextUtils.isEmpty(number)) {
            view.setText(label);
        } else {
            number.insert(0, label + " ");
            view.setText(number);
        }
    }

    private void setLabelAndNumber(TextView view, CharSequence label, String number) {
        if (PhoneNumberUtils.isUriNumber(number)) {
            view.setText(number);
            return;
        }

        if (TextUtils.isEmpty(label)) {
            view.setText(number);
        } else if (TextUtils.isEmpty(number)) {
            view.setText(label);
        } else {
            view.setText(label + " " + number);
        }
    }

    private String specialNumberToString(int type) {
        switch (type) {
            case NUMBER_TYPE_UNKNOWN:
                return mUnknownNumber;
            case NUMBER_TYPE_PRIVATE:
                return mPrivateNumber;
            case NUMBER_TYPE_PAYPHONE:
                return mPayphoneNumber;
            default:
                break;
        }
        return null;
    }

    private class ViewHolder {
        public QuickContactBadgeWithPhoneNumber quickContactBadge;
        public TextView name;
        public TextView labelAndNumber;
        public ImageView callType;
        public TextView operator;
        public TextView date;
    }

    private ViewHolder createViewHolder() {
        final ViewHolder viewHolder = new ViewHolder();
        return viewHolder;
    }

    /// M: Fix CR: ALPS01398152, Support RTL display for Arabic/Hebrew/Urdu @{
    private String numberLeftToRight(String origin) {
        return TextUtils.isEmpty(origin) ? origin : '\u202D' + origin + '\u202C';
    }
    /// M: @}
}
