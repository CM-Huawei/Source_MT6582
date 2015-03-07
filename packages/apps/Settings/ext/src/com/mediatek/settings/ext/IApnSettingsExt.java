package com.mediatek.settings.ext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.view.Menu;

public interface IApnSettingsExt {
    
    /**
     * whether allowed to edit the present apn, called at fill apn list,
     * in ApnSettings
     * @param type the apn type
     * @param apn name to query
     * @param numeric the mcc + mnc, such as "46000"
     * @param sourcetype 
     * @return if the specified apn could be edited.
     */
     boolean isAllowEditPresetApn(String type, String apn, String numeric, int sourcetype);

    /**
     * cutomize tethering apn setting, called at init tether settings in TetherSettins
     * @param root through root PreferenceScreen, you can customize your preference.
     */
    void customizeTetherApnSettings(PreferenceScreen root);
    
    /**
     * judge the apn can be selected or not, called in ApnSettings.
     * @param type the apn type
     */
    boolean isSelectable(String type);

    /**
     * the IntentFilter customize by plugin.
     * @return IntentFilter.
     */
    IntentFilter getIntentFilter();

    /**
     * get BrocastReceiver customize by plugin.
     * @param receiver the receiver in default 
     * @return BrocastReceiver.
     */
    BroadcastReceiver getBroadcastReceiver(BroadcastReceiver receiver);


    /**
     * get query statement for db.
     * @param numeric mcc+mnc on sim card 
     * @param slotId slot id  
     * @return query statement.
     */
    String getFillListQuery(String numeric,int slotId);

    /**
     * add option menu item called at onCreateOptionsMenu() in ApnSettings.
     * @param menu
     * @param activity  
     * @param add  the add menu item resource id
     * @param restore the resore menu item resource id 
     * @param numeric  
     */
    void addMenu(Menu menu, Activity activity, int add, int restore, String numeric);

    /**
     * add intent extra for start ApnEditor.
     * @param it  the intent to start ApnEditor 
     */
    void addApnTypeExtra(Intent it);

    /**
     * update Tether apn state called at onResume at ApnSettings.
     * @param activity
     */
    void updateTetherState(Activity activity);

    /**
     * init some fields for tether apn called at onCreate at ApnSettings. 
     * @param activity
     */
    void initTetherField(Activity activity);

    /**
     * get restore uri customized by plugin.
     * @param slotId 
     */
    Uri getRestoreCarrierUri(int slotId);

    /**
     * judge the screen if enable or disable.
     * @param slotId 
     * @param activity 
     * @return true screen should enable.
     * @return false screen should disable.
     */
    boolean getScreenEnableState(int slotId, Activity activity);       

    /**
     * judge if we should hide the apn.
     * @param type 
     * @param rcseExt
     * @return true hide the apn.
     * @return false show the apn.
     */
    boolean isSkipApn(String type, IRcseOnlyApnExtension rcseExt);

    /**
     * set apn type preference state (enable or disable), called at 
     * update UI in ApnEditor
     * @param preference The preference to set state
     */
    void setApnTypePreferenceState(Preference preference);
    /**
     * get Uri from intent
     * @param context The parent context
     * @param intent The parent intent
     */
    Uri getUriFromIntent(Context context, Intent intent);

    /**
     * get array of the apn type called at constructor in ApnTypePreference
     * @param defResId The default resource Id
     * @param isTether The type is tether or not
     * @return The apn type array
     */
    String[] getApnTypeArray(Context context, int defResId, boolean isTether);

    /**
     * update the customized status(enable , disable), called at update screen status
     * @param slotId the current slotId.
     * @param root though the root PreferenceScreen , you can find any preference
     */
    void updateFieldsStatus(int slotId, PreferenceScreen root);

    /**
     * set the preference text and summary according to the slotId.
     * called at update UI, in ApnEditor.
     * @param slotId: the current slotId.
     * @param text the text for the preference 
     */
    void setPreferenceTextAndSummary(int slotId, String text);

    /**
     * add a preference in the prefernce screen according to the slotId, such as 
     * add ppp preference
     * @param slotId the current slotId
     * @param root though the root PreferenceScreen , you can customize any preference
     */
    void addPreference(int slotId, PreferenceScreen root);

    /**
     * customize apn titles arrording to the slotId. called at onCreate once.
     * in ApnEditor.
     * @param mSlotId the current slotId
     * @param root though the root PreferenceScreen , you can customize any preference
     */
     void customizeApnTitles(int slotId,PreferenceScreen root);

     /**
      * customize apn projection, such as add Telephony.Carriers.PPP
      * Called at onCreate in ApnEditor.
      * @param projection: the default source String[]
      * @return customized projection
      */
     String[] customizeApnProjection(String[] projection);

     /**
      * save the added apn values called when save the added apn vaule, 
      * in ApnEditor.
      * @param contentValues the default content vaules
      */
     void saveApnValues(ContentValues contentValues);
     
     /**
      * customize the cursor , check it need reset or not.
      * query from provider again to get MNO apn if MVNO is null on 
      * common load , but in some special case, such as orange tethering apn ,
      * must keep itself logic , should not query again
      * @param activity Activity
      * @param cursor Cursor
      * @param uri Uri
      * @param numeric String
      * @return the new cursor
      */
     Cursor customizeQueryResult(Activity activity,Cursor cursor,Uri uri,String numeric);
     
     /**
      * set MVNO preference state (enable or disable), called at 
      * update UI in ApnEditor
      * @param preference The preference to set state
      */
     void setMVNOPreferenceState(Preference preference);
}
