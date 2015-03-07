/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import com.android.bluetooth.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * View showing the user's finished bluetooth opp transfers that the user does
 * not confirm. Including outbound and inbound transfers, both successful and
 * failed. *
 */
public class BluetoothOppTransferHistory extends Activity implements
        View.OnCreateContextMenuListener, OnItemClickListener {
    private static final String TAG = "[Bluetooth.OPP]BluetoothOppTransferHistory";

    private static final boolean V = Constants.VERBOSE;

    private ListView mListView;

    private Cursor mTransferCursor;

    private BluetoothOppTransferAdapter mTransferAdapter;

    private int mIdColumnId;

    private int mContextMenuPosition;

    private boolean mShowAllIncoming;

    private int mDir;

    /** Class to handle Notification Manager updates */
    private BluetoothOppNotification mNotifier;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if(V)Log.v(TAG, "onCreate ++");

        setContentView(R.layout.bluetooth_transfers_page);
        mListView = (ListView)findViewById(R.id.list);
        mListView.setEmptyView(findViewById(R.id.empty));

        mShowAllIncoming = getIntent().getBooleanExtra(
                Constants.EXTRA_SHOW_ALL_FILES, false);

        String direction;
        int dir = getIntent().getIntExtra("direction", 0);
        mDir = dir;
        if (dir == BluetoothShare.DIRECTION_OUTBOUND) {
            setTitle(getText(R.string.outbound_history_title));
            direction = "(" + BluetoothShare.DIRECTION + " == " + BluetoothShare.DIRECTION_OUTBOUND
                    + ")";
        } else {
            if (mShowAllIncoming) {
                setTitle(getText(R.string.btopp_live_folder));
            } else {
                setTitle(getText(R.string.inbound_history_title));
            }
            direction = "(" + BluetoothShare.DIRECTION + " == " + BluetoothShare.DIRECTION_INBOUND
                    + ")";
        }

        String selection = BluetoothShare.STATUS + " >= '200' AND " + direction;

        if (!mShowAllIncoming) {
            selection = selection + " AND ("
                    + BluetoothShare.VISIBILITY + " IS NULL OR "
                    + BluetoothShare.VISIBILITY + " == '"
                    + BluetoothShare.VISIBILITY_VISIBLE + "')";
        }

        final String sortOrder = BluetoothShare.TIMESTAMP + " DESC";

        mTransferCursor = managedQuery(BluetoothShare.CONTENT_URI, new String[] {
                "_id", BluetoothShare.FILENAME_HINT, BluetoothShare.STATUS,
                BluetoothShare.TOTAL_BYTES, BluetoothShare._DATA, BluetoothShare.TIMESTAMP,
                BluetoothShare.VISIBILITY, BluetoothShare.DESTINATION, BluetoothShare.DIRECTION
        }, selection, sortOrder);

        // only attach everything to the listbox if we can access
        // the transfer database. Otherwise, just show it empty
        if (mTransferCursor != null) {
            mIdColumnId = mTransferCursor.getColumnIndexOrThrow(BluetoothShare._ID);
            // Create a list "controller" for the data
            mTransferAdapter = new BluetoothOppTransferAdapter(this,
                    R.layout.bluetooth_transfer_item, mTransferCursor);
            mListView.setAdapter(mTransferAdapter);
            mListView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            mListView.setOnCreateContextMenuListener(this);
            mListView.setOnItemClickListener(this);
        }

        mNotifier = new BluetoothOppNotification(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mTransferCursor != null && !mShowAllIncoming) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.transferhistory, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!mShowAllIncoming) {
            boolean showClear = getClearableCount() > 0;
            menu.findItem(R.id.transfer_menu_clear_all).setEnabled(showClear);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.transfer_menu_clear_all:
                promptClearList();
                return true;
        }
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        mTransferCursor.moveToPosition(mContextMenuPosition);
        switch (item.getItemId()) {
            case R.id.transfer_menu_open:
                openCompleteTransfer();
                updateNotificationWhenBtDisabled();
                return true;

            case R.id.transfer_menu_clear:
                int sessionId = mTransferCursor.getInt(mIdColumnId);
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + sessionId);
                BluetoothOppUtility.updateVisibilityToHidden(this, contentUri);
                updateNotificationWhenBtDisabled();
                return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (mTransferCursor != null) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
            mTransferCursor.moveToPosition(info.position);
            mContextMenuPosition = info.position;

            String fileName = mTransferCursor.getString(mTransferCursor
                    .getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
            if (fileName == null) {
                fileName = this.getString(R.string.unknown_file);
            }
            menu.setHeaderTitle(fileName);

            MenuInflater inflater = getMenuInflater();
            if (mShowAllIncoming) {
                inflater.inflate(R.menu.receivedfilescontextfinished, menu);
            } else {
                inflater.inflate(R.menu.transferhistorycontextfinished, menu);
            }
        }
    }

    /**
     * Prompt the user if they would like to clear the transfer history
     */
    private void promptClearList() {
        new AlertDialog.Builder(this).setTitle(R.string.transfer_clear_dlg_title).setMessage(
                R.string.transfer_clear_dlg_msg).setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        ClearHistory clearHistory = new ClearHistory();
                        clearHistory.execute(0);
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    /**
     * Get the number of finished transfers, including error and success.
     */
    private int getClearableCount() {
        Log.i(TAG, "getClearableCount ++");
        int count = 0;
        try {
            if (mTransferCursor.moveToFirst()) {
                while (!mTransferCursor.isAfterLast()) {
                    int statusColumnId = mTransferCursor.getColumnIndexOrThrow(BluetoothShare.STATUS);
                    int status = mTransferCursor.getInt(statusColumnId);
                    if (BluetoothShare.isStatusCompleted(status)) {
                        count++;
                    }
                    mTransferCursor.moveToNext();
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }

        Log.i(TAG, "getClearableCount return " + count);
        return count;
    }

    /**
     * Clear all finished transfers, error and success transfer items.
     */
    private void clearAllDownloads() {
        String direction;
        if (mDir == BluetoothShare.DIRECTION_OUTBOUND) {
            direction = "(" + BluetoothShare.DIRECTION + " == " + BluetoothShare.DIRECTION_OUTBOUND
                    + ")";
        } else {
            direction = "(" + BluetoothShare.DIRECTION + " == " + BluetoothShare.DIRECTION_INBOUND
                    + ")";
        }

        String selection = BluetoothShare.STATUS + " >= '200' AND " + direction;

        if (!mShowAllIncoming) {
            selection = selection + " AND ("
                    + BluetoothShare.VISIBILITY + " IS NULL OR "
                    + BluetoothShare.VISIBILITY + " == '"
                    + BluetoothShare.VISIBILITY_VISIBLE + "')";
        }
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(BluetoothShare.CONTENT_URI,
                new String[] {"_id"},
                selection, null, "_id");
        if (cursor == null) {
            Log.d(TAG, "clearAllDownloads::cursor == null");
            return;
        }
        if(cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                int sessionId = cursor.getInt(mIdColumnId);
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + sessionId);
                Log.i(TAG, "cout = " + cursor.getCount() + " uri = " + contentUri);
                BluetoothOppUtility.updateVisibilityToHidden(this, contentUri);

                try {
                    cursor.moveToNext();
                } catch (Exception e) {
                    Log.e(TAG, "clearAllDownloads::exception hanppened");
                }

            }
            updateNotificationWhenBtDisabled();
        }
        cursor.close();
    }

    /*
     * (non-Javadoc)
     * @see
     * android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget
     * .AdapterView, android.view.View, int, long)
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Open the selected item
        try {
            mTransferCursor.moveToPosition(position);
        } catch (Exception e) {
            // TODO: handle exception
        }

        openCompleteTransfer();
        updateNotificationWhenBtDisabled();
    }

    /**
     * Open the selected finished transfer. mDownloadCursor must be moved to
     * appropriate position before calling this function
     */
    private void openCompleteTransfer() {
        int sessionId = mTransferCursor.getInt(mIdColumnId);
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + sessionId);
        BluetoothOppTransferInfo transInfo = BluetoothOppUtility.queryRecord(this, contentUri);
        if (transInfo == null) {
            Log.e(TAG, "Error: Can not get data from db");
            return;
        }
        if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND
                && BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
            // if received file successfully, open this file
            BluetoothOppUtility.updateVisibilityToHidden(this, contentUri);
            BluetoothOppUtility.openReceivedFile(this, transInfo.mFileName, transInfo.mFileType,
                    transInfo.mTimeStamp, contentUri);
        } else {
            Intent in = new Intent(this, BluetoothOppTransferActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.setDataAndNormalize(contentUri);
            this.startActivity(in);
        }
    }

    /**
     * When Bluetooth is disabled, notification can not be updated by
     * ContentObserver in OppService, so need update manually.
     */
    private void updateNotificationWhenBtDisabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
            if (V) Log.v(TAG, "Bluetooth is not enabled, update notification manually.");
            mNotifier.updateNotification();
        }
    }

    private final static int DIALOG_KEY_CLEARING = 100;
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_KEY_CLEARING:
                ProgressDialog dialog = new ProgressDialog(this);
                dialog.setMessage(getString(R.string.transfer_menu_clear_all) + "...");
                dialog.setIndeterminate(true);
                dialog.setCancelable(false);
                return dialog;

            default:
                return null;
        }
    }

    class ClearHistory extends AsyncTask<Integer, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(DIALOG_KEY_CLEARING);
        }

        @Override
        protected Boolean doInBackground(Integer... arg0) {
            // TODO Auto-generated method stub
            clearAllDownloads();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            dismissDialog(DIALOG_KEY_CLEARING);
            super.onPostExecute(result);
        }
    }
}
