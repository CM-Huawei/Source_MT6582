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
import com.google.android.collect.Lists;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.MediaStore.MediaColumns;
import android.content.ContentValues;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.util.Log;
import android.util.Patterns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

/**
 * This class has some utilities for Opp application;
 */
public class BluetoothOppUtility {
    private static final String TAG = "[Bluetooth.OPP]BluetoothOppUtility";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    public static final String CALENDAR_AUTHORITY = "com.mediatek.calendarimporter";

    private static final ConcurrentHashMap<Uri, ArrayList<BluetoothOppSendFileInfo>> sSendFileMap
            = new ConcurrentHashMap<Uri, ArrayList<BluetoothOppSendFileInfo>>();

    public static BluetoothOppTransferInfo queryRecord(Context context, Uri uri) {
        Log.i(TAG, "queryRecord++, uri = " + uri);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothOppTransferInfo info = new BluetoothOppTransferInfo();

        try {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                info.mID = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare._ID));
                info.mStatus = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
                info.mDirection = cursor.getInt(cursor
                        .getColumnIndexOrThrow(BluetoothShare.DIRECTION));
                info.mTotalBytes = cursor.getInt(cursor
                        .getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
                info.mCurrentBytes = cursor.getInt(cursor
                        .getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
                info.mTimeStamp = cursor.getLong(cursor
                        .getColumnIndexOrThrow(BluetoothShare.TIMESTAMP));
                info.mDestAddr = cursor.getString(cursor
                        .getColumnIndexOrThrow(BluetoothShare.DESTINATION));

                info.mFileName = cursor.getString(cursor
                        .getColumnIndexOrThrow(BluetoothShare._DATA));
                if (info.mFileName == null) {
                    info.mFileName = cursor.getString(cursor
                            .getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
                }
                if (info.mFileName == null) {
                    info.mFileName = context.getString(R.string.unknown_file);
                }

                info.mFileUri = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.URI));

                if (info.mFileUri != null) {
                    Uri u = Uri.parse(info.mFileUri);
                    info.mFileType = context.getContentResolver().getType(u);
                } else {
                    Uri u = Uri.parse(info.mFileName);
                    info.mFileType = context.getContentResolver().getType(u);
                }
                if (info.mFileType == null) {
                    info.mFileType = cursor.getString(cursor
                            .getColumnIndexOrThrow(BluetoothShare.MIMETYPE));
                }

                BluetoothDevice remoteDevice = adapter.getRemoteDevice(info.mDestAddr);
                info.mDeviceName =
                        BluetoothOppManager.getInstance(context).getDeviceName(remoteDevice);

                int confirmationType = cursor.getInt(
                        cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
                info.mHandoverInitiated =
                        confirmationType == BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED;

                if (V) Log.v(TAG, "Get data from db:" + info.mFileName + info.mFileType
                            + info.mDestAddr);
            }
            cursor.close();
        } else {
            info = null;
            if (V) Log.v(TAG, "BluetoothOppManager Error: not got data from db for uri:" + uri);
            }
        } catch (Exception e) {
            // TODO: handle exception
            Log.e(TAG, "query record error");
            e.printStackTrace();
        }
        return info;
    }

    /**
     * Organize Array list for transfers in one batch
     */
    // This function is used when UI show batch transfer. Currently only show single transfer.
    public static ArrayList<String> queryTransfersInBatch(Context context, Long timeStamp) {
        ArrayList<String> uris = Lists.newArrayList();
        final String WHERE = BluetoothShare.TIMESTAMP + " == " + timeStamp;

        Cursor metadataCursor = context.getContentResolver().query(BluetoothShare.CONTENT_URI,
                new String[] {
                    BluetoothShare._DATA
                }, WHERE, null, BluetoothShare._ID);

        if (metadataCursor == null) {
            return null;
        }

        for (metadataCursor.moveToFirst(); !metadataCursor.isAfterLast(); metadataCursor
                .moveToNext()) {
            String fileName = metadataCursor.getString(0);
            Uri path = Uri.parse(fileName);
            // If there is no scheme, then it must be a file
            if (path.getScheme() == null) {
                path = Uri.fromFile(new File(fileName));
            }
            uris.add(path.toString());
            if (V) Log.d(TAG, "Uri in this batch: " + path.toString());
        }
        metadataCursor.close();
        return uris;
    }

    /**
     * Open the received file with appropriate application, if can not find
     * application to handle, display error dialog.
     */
    public static void openReceivedFile(Context context, String fileName, String mimetype,
            Long timeStamp, Uri uri) {
        if(V)Log.v(TAG, "openReceivedFile::fileName = " + fileName + " mimetype = " + mimetype);

        if (fileName == null || mimetype == null) {
            Log.e(TAG, "ERROR: Para fileName ==null, or mimetype == null");
            return;
        }

        File f = new File(fileName);
        if (!f.exists()) {
            Intent in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("title", context.getString(R.string.not_exist_file));
            in.putExtra("content", context.getString(R.string.not_exist_file_desc));
            context.startActivity(in);

            // Due to the file is not existing, delete related info in btopp db
            // to prevent this file from appearing in live folder
            if (V) Log.d(TAG, "This uri will be deleted: " + uri);
            context.getContentResolver().delete(uri, null, null);
            return;
        }

        Uri path = Uri.parse(fileName);
        // If there is no scheme, then it must be a file
        if (path.getScheme() == null) {
            path = Uri.fromFile(new File(fileName));
        }

        if (isRecognizedFileType(context, path, mimetype)) {
            Intent activityIntent = new Intent(Intent.ACTION_VIEW);
            activityIntent.setDataAndTypeAndNormalize(path, mimetype);

            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (V) Log.d(TAG, "ACTION_VIEW intent sent out: " + path + " / " + mimetype);
                context.startActivity(activityIntent);
            } catch (ActivityNotFoundException ex) {
                if (V) Log.d(TAG, "no activity for handling ACTION_VIEW intent:  " + mimetype, ex);
            }
        } else {
            if(V)Log.e(TAG, "openReceivedFile:: not recognized file");

            Intent in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("title", context.getString(R.string.unknown_file));
            in.putExtra("content", context.getString(R.string.unknown_file_desc));
            context.startActivity(in);
        }
    }

    /**
     * To judge if the file type supported (can be handled by some app) by phone
     * system.
     */
    public static boolean isRecognizedFileType(Context context, Uri fileUri, String mimetype) {
        boolean ret = true;

        if (D) Log.d(TAG, "RecognizedFileType() fileUri: " + fileUri + " mimetype: " + mimetype);

        Intent mimetypeIntent = new Intent(Intent.ACTION_VIEW);
        mimetypeIntent.setDataAndTypeAndNormalize(fileUri, mimetype);
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(mimetypeIntent,
                PackageManager.MATCH_DEFAULT_ONLY);

        if (list.size() == 0) {
            if (D) Log.d(TAG, "NO application to handle MIME type " + mimetype);
            ret = false;
        }
        return ret;
    }

    /**
     * update visibility to Hidden
     */
    public static void updateVisibilityToHidden(Context context, Uri uri) {
        if(V)Log.v(TAG, "updateVisibilityToHidden ++");
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
        context.getContentResolver().update(uri, updateValues, null, null);
    }

    /**
     * Helper function to build the progress text.
     */
    public static String formatProgressText(long totalBytes, long currentBytes) {
        if (totalBytes <= 0) {
            return "0%";
        }
        long progress = currentBytes * 100 / totalBytes;
        StringBuilder sb = new StringBuilder();
        sb.append(progress);
        sb.append('%');
        return sb.toString();
    }

    /**
     * Get status description according to status code.
     */
    public static String getStatusDescription(Context context, int statusCode, String deviceName) {
        if(V)Log.v(TAG, "getStatusDescription::statusCode = " + statusCode + " deviceName = " + deviceName);
        String ret;
        if (statusCode == BluetoothShare.STATUS_PENDING) {
            ret = context.getString(R.string.status_pending);
        } else if (statusCode == BluetoothShare.STATUS_RUNNING) {
            ret = context.getString(R.string.status_running);
        } else if (statusCode == BluetoothShare.STATUS_SUCCESS) {
            ret = context.getString(R.string.status_success);
        } else if (statusCode == BluetoothShare.STATUS_NOT_ACCEPTABLE) {
            ret = context.getString(R.string.status_not_accept);
        } else if (statusCode == BluetoothShare.STATUS_FORBIDDEN) {
            ret = context.getString(R.string.status_forbidden);
        } else if (statusCode == BluetoothShare.STATUS_CANCELED) {
            ret = context.getString(R.string.status_canceled);
        } else if (statusCode == BluetoothShare.STATUS_FILE_ERROR) {
            ret = context.getString(R.string.status_file_error);
        } else if (statusCode == BluetoothShare.STATUS_ERROR_NO_SDCARD) {
            ret = context.getString(R.string.status_no_sd_card);
        } else if (statusCode == BluetoothShare.STATUS_CONNECTION_ERROR) {
            ret = context.getString(R.string.status_connection_error);
        } else if (statusCode == BluetoothShare.STATUS_ERROR_SDCARD_FULL) {
            ret = context.getString(R.string.bt_sm_2_1, deviceName);
        } else if ((statusCode == BluetoothShare.STATUS_BAD_REQUEST)
                || (statusCode == BluetoothShare.STATUS_LENGTH_REQUIRED)
                || (statusCode == BluetoothShare.STATUS_PRECONDITION_FAILED)
                || (statusCode == BluetoothShare.STATUS_UNHANDLED_OBEX_CODE)
                || (statusCode == BluetoothShare.STATUS_OBEX_DATA_ERROR)) {
            ret = context.getString(R.string.status_protocol_error);
        } else {
            ret = context.getString(R.string.status_unknown_error);
        }
        return ret;
    }

    /**
     * Retry the failed transfer: Will insert a new transfer session to db
     */
    public static void retryTransfer(Context context, BluetoothOppTransferInfo transInfo) {
        Log.d(TAG, "retryTransfer ++");
        putSendFileInfo(Uri.parse(transInfo.mFileUri), BluetoothOppSendFileInfo.generateFileInfo(
            context, Uri.parse(transInfo.mFileUri), transInfo.mFileType));

        ContentValues values = new ContentValues();
        values.put(BluetoothShare.URI, transInfo.mFileUri);
        values.put(BluetoothShare.MIMETYPE, transInfo.mFileType);
        values.put(BluetoothShare.DESTINATION, transInfo.mDestAddr);

        final Uri contentUri = context.getContentResolver().insert(BluetoothShare.CONTENT_URI,
                values);
        if (V) Log.v(TAG, "Insert contentUri: " + contentUri + "  to device: " +
                transInfo.mDeviceName);
    }

    static void putSendFileInfo(Uri uri, BluetoothOppSendFileInfo sendFileInfo) {
        if (D) Log.d(TAG, "putSendFileInfo: uri=" + uri + " sendFileInfo=" + sendFileInfo);
        ArrayList<BluetoothOppSendFileInfo> fileInfoList = sSendFileMap.get(uri);
        if (fileInfoList == null) {
            fileInfoList = new ArrayList<BluetoothOppSendFileInfo>();
            fileInfoList.add(sendFileInfo);
            sSendFileMap.put(uri, fileInfoList);
            if (D) Log.d(TAG, "putSendFileInfo: uri=" + uri + ", is a new uri, create ArrayList");
        } else {
            fileInfoList.add(sendFileInfo);
            if (D) Log.d(TAG, "putSendFileInfo: uri=" + uri + ", already have, final size is " 
                    + fileInfoList.size());
        }
    }

    static BluetoothOppSendFileInfo getSendFileInfo(Uri uri) {
        if (D) Log.d(TAG, "getSendFileInfo: uri=" + uri);
        BluetoothOppSendFileInfo info = null;
        ArrayList<BluetoothOppSendFileInfo> fileInfoList = sSendFileMap.get(uri);
        if (fileInfoList != null) {
            info = fileInfoList.get(0);
        }
        return (info != null) ? info : BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR;
    }

    static void closeSendFileInfo(Uri uri) {
        if (D) Log.d(TAG, "closeSendFileInfo: uri=" + uri);
        BluetoothOppSendFileInfo info = null;
        ArrayList<BluetoothOppSendFileInfo> fileInfoList = sSendFileMap.get(uri);
        if (fileInfoList != null) {
            info = fileInfoList.remove(0);
            int listSize = fileInfoList.size();
            if (D) Log.d(TAG, "closeSendFileInfo: uri=" + uri + ", ArrayList size = " + listSize);
            if (listSize == 0) {
                sSendFileMap.remove(uri);
            }
        }

        if (info != null && info.mInputStream != null) {
            try {
                info.mInputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    static String getFileName(Uri uri) {
        if(V)Log.d(TAG, "getFileName ++");
        ArrayList<BluetoothOppSendFileInfo> fileInfoList = sSendFileMap.get(uri);
        if (fileInfoList == null || fileInfoList.isEmpty()) {
            if(V)Log.d(TAG, "BluetoothOppUtility return null");
            return null;
        }

        String ret = fileInfoList.get(0).mFileName;
        if(V)Log.d(TAG, "BluetoothOppUtility return " + ret);
        return ret;
    }

    static Uri creatFileForSharedContent(Context context, CharSequence subject,
            CharSequence text) {
        Log.d(TAG, "creatFileForSharedContent ++");

        if (text == null) {
            return null;
        }
        Log.d(TAG, "[URL pattern match begin, text: " + text.toString());
        if (subject != null) {

            Log.d(TAG, "[URL pattern match begin, subject: " + subject.toString());
        }

        FileOutputStream out = null;
        try {
            // delete first
            String filename = context.getString(R.string.bluetooth_share_file_name) + ".html";
            context.deleteFile(filename);

            // replace subject if it's null
            // subject = (subject == null) ? text : subject;

            // new algorithm for APP link share
            // 1. if text contains content + link, then we need to parse link and set as href,
            // left content will be showed as html body. subject feild will not be showed.
            // 2. if text only contains link(Compatible MTK Browser share):
            // (a) if subject feild is not empty, then show subject as html body, link as href.
            // (b) if subject feild is empty, then show link as html body, also link as href.
            // 3. if text only contains plain-text, then only fill in text as html body, ignore subject.

            // retrieve URL bt web regular expression
            Matcher matcher = Patterns.WEB_URL.matcher(text);
            String urlLink = null;
            int currentMatchIdx = 0;
            int currentStartIdx = 0;

            // match url and compose html
            String content = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;"
                    + " charset=UTF-8\"/></head><body>";
            StringBuffer body = new StringBuffer();

            // fill body and href
            while (matcher.find()) {

                urlLink = matcher.group();
                Log.d(TAG, "URL pattern match result, link: " + urlLink);
                Log.d(TAG, "URL pattern match result, link.length: " + urlLink.length());

                currentMatchIdx = matcher.start();
                Log.d(TAG, "URL pattern  current start index - " + currentStartIdx);
                Log.d(TAG, "URL pattern  current match index - " + currentMatchIdx);

                if (text.length() > urlLink.length()) {

                    if (currentMatchIdx > currentStartIdx) {

                        // text not start with uri, add text body
                        body.append(text.toString().substring(currentStartIdx, currentMatchIdx));

                        body.append("<a href=\"" + urlLink + "\">");
                        body.append(urlLink);
                        body.append("</a></p>");
                    } else if (currentMatchIdx == currentStartIdx) {

                        // text start with uri
                        body.append("<a href=\"" + urlLink + "\">");
                        body.append(urlLink);
                        body.append("</a></p>");

                        // if there is left body(without uri), need to append left part to body
                    }

                    currentStartIdx = currentMatchIdx + urlLink.length();
                } else {

                    // uri length == text length, no other text as body
                    if (subject == null) {

                        body.append("<a href=\"" + urlLink + "\">");
                        body.append(urlLink);
                        body.append("</a></p>");
                    } else {

                        body.append("<a href=\"" + urlLink + "\">");
                        body.append(subject);
                        body.append("</a></p>");
                    }

                    currentStartIdx = text.length();
                    break;
                }
            }

            Log.d(TAG, "After match currentStartIdx - " + currentStartIdx);
            Log.d(TAG, "After match current body:" + body.toString());

            // append left part(can not handle by match loop) to body
            if ((body.length() != 0) && (currentStartIdx < text.length())) {

                body.append(text.toString().substring(currentStartIdx, text.length()));
            }

            // check if no url match in text, fill full text as body, ignore subject feild
            if (body.length() == 0) {

                body.append(text);
            }

            // fill end charaters
            content += body.toString();
            content += "</body></html>";

            Log.d(TAG, "URL final compose content: " + content);
            byte[] byteBuff = content.getBytes();

            // change the text as hyperlink
            /*
             * StringBuilder content = new StringBuilder( 125 + text.length()*2 ) .append(
             * "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/></head><body>" ) .append(
             * "<a href=\"" ).append( text ).append( "\">" ).append( subject ).append( "</a>" ) .append( "</body></html>" );
             */
            // open and write file
            out = context.openFileOutput(filename, Context.MODE_PRIVATE);
            // out.write( content.toString().getBytes() );
            out.write(byteBuff, 0, byteBuff.length);
            out.flush();

            // create Uri
            String filePath = context.getFilesDir().getAbsolutePath();
            Log.i(TAG, "filePath =" + filePath);
            Uri result = Uri.fromFile(new File(context.getFilesDir(), filename));

            if (result == null) {

                Log.w(TAG, "createContextFileForText() - can't get Uri for created file.");
                context.deleteFile(filename);
            }

            return result;
        } catch (IOException ex) {

            Log.e(TAG, "createContextFileForText() error:" + ex.toString());
            return null;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {

                Log.w(TAG, "createContextFileForText() closing file output stream fail: " + ex.toString());
            }
        }

    }
    static String getFilePath(Context context, Uri fileUri) {
        String ret = null;
        Log.i(TAG, "getFilePath::fileUri = " + fileUri.toString());
        String authority = fileUri.getAuthority();
        if("content".equals(fileUri.getScheme())
                && !ContactsContract.AUTHORITY.equals(authority)
                && !CALENDAR_AUTHORITY.equals(authority)) {
            Log.d(TAG, "getFilePath:: content, not contact or caledar");
            String[] projection = new String[] {MediaColumns.DATA};
            Cursor cursor = context.getContentResolver().query(fileUri, projection, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.e(TAG, "getFilePath get file path fail");
                return null;
            }

            try {
                ret = cursor.getString(cursor.getColumnIndex(MediaColumns.DATA));
                cursor.close();
                return ret;
            } catch (Exception e) {
                // TODO: handle exception
                Log.e(TAG, "getFilePath get file path exception");
                return null;
            }
        } else if ("file".equals(fileUri.getScheme())) {
            return fileUri.getPath();
        }

        return null;
    }

    static boolean contactValid(Context context, Uri contactUri) {
        Log.i(TAG, "contactValid++++, contactUri = " + contactUri);
        if (!("content".equals(contactUri.getScheme())) || !ContactsContract.AUTHORITY.equals(contactUri.getAuthority())) {
            Log.i(TAG, "contactValid++, not contact, return false");
            return false;
        }

        Cursor cursor = context.getContentResolver().query(contactUri, null, null, null, null);
        if (cursor == null || !cursor.moveToNext()) {
            Log.i(TAG, "contactValid, cursor null, return false");
            if(cursor != null) {
                cursor.close();
            }
            return false;
        } else {
            Log.i(TAG, "contactValid, return true");
            cursor.close();
            return true;
        }
    }

    static int getTotalTaskCount() {
        Log.i(TAG, "getTotalTaskCount ++");
        int ret = 0;
        Iterator<Uri> iterator = sSendFileMap.keySet().iterator();
        while (iterator.hasNext()) {
            Uri uri = (Uri) iterator.next();
            ArrayList<BluetoothOppSendFileInfo> arraylist = sSendFileMap.get(uri);
            ret += arraylist.size();
        }
        Log.d(TAG, "getTotalTaskCount return " + ret);
        return ret;
    }

    static boolean doesDatabaseFileExist(Context ctx, String databaseName) {
        Log.i(TAG, "doesDatabaseFileExist++, databaseName = " + databaseName);
        File dbFile = ctx.getDatabasePath(databaseName);
        if (dbFile.exists()) {
            Log.d(TAG, "doesDatabaseFileExist return true");
            return true;
        } else {
            Log.d(TAG, "doesDatabaseFileExist return false");
            return false;
        }
    }
}
