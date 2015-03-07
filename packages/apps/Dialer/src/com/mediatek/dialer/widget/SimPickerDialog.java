package com.mediatek.dialer.widget;

import android.accounts.Account;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.sip.SipManager;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.dialer.R;
import com.mediatek.dialer.util.ContactsSettingsUtils;
import com.mediatek.dialer.widget.SimPickerAdapter.ItemHolder;
import com.mediatek.telephony.SimInfoManager;
import com.mediatek.telephony.SimInfoManager.SimInfoRecord;

import java.util.ArrayList;
import java.util.List;

public class SimPickerDialog {

    public static AlertDialog createSingleChoice(Context context, String title, int choiceItem,
            DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        List<ItemHolder> items = createItemHolder(context, context
                .getString(R.string.call_log_filter_all_resources), true, true, null);
        SimPickerAdapter simAdapter = new SimPickerAdapter(context, items, Settings.System.DEFAULT_SIM_NOT_SET);
        simAdapter.setSingleChoice(true);
        simAdapter.setSingleChoiceIndex(choiceItem);
        builder.setSingleChoiceItems(simAdapter, -1, listener).setTitle(title);
        return builder.create();
    }

    public static AlertDialog create(Context context, String title, DialogInterface.OnClickListener listener) {
        return create(context, title, ContactsSettingsUtils.DEFAULT_SIM_NOT_SET, true, listener);
    }

    public static AlertDialog create(Context context, String title, boolean internet,
            DialogInterface.OnClickListener listener) {
        return create(context, title, ContactsSettingsUtils.DEFAULT_SIM_NOT_SET, internet, listener);
    }

    public static AlertDialog create(Context context, String title, long suggestedSimId,
            DialogInterface.OnClickListener listener) {
        return create(context, title, suggestedSimId, true, listener);
    }

    public static AlertDialog create(Context context, String title, long suggestedSimId,
            boolean internet, DialogInterface.OnClickListener listener) {
        return create(context, title, suggestedSimId, createItemHolder(context, internet), listener);
    }

    protected static AlertDialog create(Context context, String title, long suggestedSimId,
            List<ItemHolder> items, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        SimPickerAdapter simAdapter = new SimPickerAdapter(context, items, suggestedSimId);
        builder.setSingleChoiceItems(simAdapter, -1, listener)
               .setTitle(title);
        return builder.create();
    }

    protected static List<ItemHolder> createItemHolder(Context context, boolean internet) {
        return createItemHolder(context, null, internet, null);
    }

    protected static List<ItemHolder> createItemHolder(Context context, String phone,
            boolean internet, ArrayList<Account> accounts) {
        return createItemHolder(context, phone, internet, false, accounts);
    }

    protected static List<ItemHolder> createItemHolder(Context context, String phone,
            boolean internet, boolean forceInternet, ArrayList<Account> accounts) {
        List<SimInfoRecord> simInfos = SimInfoManager.getInsertedSimInfoList(context);
        ArrayList<ItemHolder> itemHolders = new ArrayList<ItemHolder>();
        ItemHolder temp = null;

        if (!TextUtils.isEmpty(phone)) {
            temp = new ItemHolder(phone, SimPickerAdapter.ITEM_TYPE_TEXT);
            itemHolders.add(temp);
        }
        
       int index = 0;
        for (SimInfoRecord simInfo : simInfos) {
            temp = new ItemHolder(simInfo, SimPickerAdapter.ITEM_TYPE_SIM);
            if (index == 0) {
                itemHolders.add(temp);
            } else {
                int lastPos = itemHolders.size() - 1;
                SimInfoRecord temInfo = (SimInfoRecord)itemHolders.get(lastPos).data;
                if (simInfo.mSimSlotId < temInfo.mSimSlotId) {
                    itemHolders.add(lastPos, temp);
                } else {
                    itemHolders.add(temp);
                }
            }
            index ++;
        }

        int enabled = Settings.System.getInt(context.getContentResolver(), Settings.System.ENABLE_INTERNET_CALL, 0);
        if (SipManager.isVoipSupported(context)) {
            if (forceInternet || (internet && enabled == 1)) {
                temp = new ItemHolder(context.getResources().getText(R.string.label_sip_address),
                        SimPickerAdapter.ITEM_TYPE_INTERNET);
                itemHolders.add(temp);
            }
        }

        if (accounts != null) {
            for (Account account : accounts) {
                temp = new ItemHolder(account, SimPickerAdapter.ITEM_TYPE_ACCOUNT);
                itemHolders.add(temp);
            }
        }

        return itemHolders;
    }

}
