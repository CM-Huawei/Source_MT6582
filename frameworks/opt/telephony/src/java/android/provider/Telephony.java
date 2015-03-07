/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.provider;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Parcelable;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.telephony.Rlog;
import android.util.Patterns;

import com.android.internal.telephony.SmsApplication;


import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//MTK-START [mtk04070][111121][ALPS00093395]MTK added
import android.content.ContentUris;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.GeminiSmsMessage;
import java.util.ArrayList;
import java.util.List;
import android.telephony.SmsCbMessage;
//MTK-END [mtk04070][111121][ALPS00093395]MTK added

import java.lang.reflect.Method;

/**
 * The Telephony provider contains data related to phone operation, specifically SMS and MMS
 * messages and access to the APN list, including the MMSC to use.
 *
 * <p class="note"><strong>Note:</strong> These APIs are not available on all Android-powered
 * devices. If your app depends on telephony features such as for managing SMS messages, include
 * a <a href="{@docRoot}guide/topics/manifest/uses-feature-element.html">{@code &lt;uses-feature>}
 * </a> element in your manifest that declares the {@code "android.hardware.telephony"} hardware
 * feature. Alternatively, you can check for telephony availability at runtime using either
 * {@link android.content.pm.PackageManager#hasSystemFeature
 * hasSystemFeature(PackageManager.FEATURE_TELEPHONY)} or {@link
 * android.telephony.TelephonyManager#getPhoneType}.</p>
 *
 * <h3>Creating an SMS app</h3>
 *
 * <p>Only the default SMS app (selected by the user in system settings) is able to write to the
 * SMS Provider (the tables defined within the {@code Telephony} class) and only the default SMS
 * app receives the {@link android.provider.Telephony.Sms.Intents#SMS_DELIVER_ACTION} broadcast
 * when the user receives an SMS or the {@link
 * android.provider.Telephony.Sms.Intents#WAP_PUSH_DELIVER_ACTION} broadcast when the user
 * receives an MMS.</p>
 *
 * <p>Any app that wants to behave as the user's default SMS app must handle the following intents:
 * <ul>
 * <li>In a broadcast receiver, include an intent filter for {@link Sms.Intents#SMS_DELIVER_ACTION}
 * (<code>"android.provider.Telephony.SMS_DELIVER"</code>). The broadcast receiver must also
 * require the {@link android.Manifest.permission#BROADCAST_SMS} permission.
 * <p>This allows your app to directly receive incoming SMS messages.</p></li>
 * <li>In a broadcast receiver, include an intent filter for {@link
 * Sms.Intents#WAP_PUSH_DELIVER_ACTION}} ({@code "android.provider.Telephony.WAP_PUSH_DELIVER"})
 * with the MIME type <code>"application/vnd.wap.mms-message"</code>.
 * The broadcast receiver must also require the {@link
 * android.Manifest.permission#BROADCAST_WAP_PUSH} permission.
 * <p>This allows your app to directly receive incoming MMS messages.</p></li>
 * <li>In your activity that delivers new messages, include an intent filter for
 * {@link android.content.Intent#ACTION_SENDTO} (<code>"android.intent.action.SENDTO"
 * </code>) with schemas, <code>sms:</code>, <code>smsto:</code>, <code>mms:</code>, and
 * <code>mmsto:</code>.
 * <p>This allows your app to receive intents from other apps that want to deliver a
 * message.</p></li>
 * <li>In a service, include an intent filter for {@link
 * android.telephony.TelephonyManager#ACTION_RESPOND_VIA_MESSAGE}
 * (<code>"android.intent.action.RESPOND_VIA_MESSAGE"</code>) with schemas,
 * <code>sms:</code>, <code>smsto:</code>, <code>mms:</code>, and <code>mmsto:</code>.
 * This service must also require the {@link
 * android.Manifest.permission#SEND_RESPOND_VIA_MESSAGE} permission.
 * <p>This allows users to respond to incoming phone calls with an immediate text message
 * using your app.</p></li>
 * </ul>
 *
 * <p>Other apps that are not selected as the default SMS app can only <em>read</em> the SMS
 * Provider, but may also be notified when a new SMS arrives by listening for the {@link
 * Sms.Intents#SMS_RECEIVED_ACTION}
 * broadcast, which is a non-abortable broadcast that may be delivered to multiple apps. This
 * broadcast is intended for apps that&mdash;while not selected as the default SMS app&mdash;need to
 * read special incoming messages such as to perform phone number verification.</p>
 *
 * <p>For more information about building SMS apps, read the blog post, <a
 * href="http://android-developers.blogspot.com/2013/10/getting-your-sms-apps-ready-for-kitkat.html"
 * >Getting Your SMS Apps Ready for KitKat</a>.</p>
 *
 */
public final class Telephony {
    private static final String TAG = "Telephony";

    /**
     * Not instantiable.
     * @hide
     */
    private Telephony() {
    }

    /**
     * Base columns for tables that contain text-based SMSs.
     */
    public interface TextBasedSmsColumns {

        /** Message type: all messages. */
        public static final int MESSAGE_TYPE_ALL    = 0;

        /** Message type: inbox. */
        public static final int MESSAGE_TYPE_INBOX  = 1;

        /** Message type: sent messages. */
        public static final int MESSAGE_TYPE_SENT   = 2;

        /** Message type: drafts. */
        public static final int MESSAGE_TYPE_DRAFT  = 3;

        /** Message type: outbox. */
        public static final int MESSAGE_TYPE_OUTBOX = 4;

        /** Message type: failed outgoing message. */
        public static final int MESSAGE_TYPE_FAILED = 5;

        /** Message type: queued to send later. */
        public static final int MESSAGE_TYPE_QUEUED = 6;

        /**
         * The type of message.
         * <P>Type: INTEGER</P>
         */
        public static final String TYPE = "type";

        /**
         * The thread ID of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String THREAD_ID = "thread_id";

        /**
         * The address of the other party.
         * <P>Type: TEXT</P>
         */
        public static final String ADDRESS = "address";

        /**
         * The date the message was received.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE = "date";

        /**
         * The date the message was sent.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_SENT = "date_sent";

        /**
         * Has the message been read?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String READ = "read";

        /**
         * Has the message been seen by the user? The "seen" flag determines
         * whether we need to show a notification.
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String SEEN = "seen";

        /**
         * {@code TP-Status} value for the message, or -1 if no status has been received.
         * <P>Type: INTEGER</P>
         */
        public static final String STATUS = "status";

        /** TP-Status: no status received. */
        public static final int STATUS_NONE = -1;
        /** TP-Status: complete. */
        public static final int STATUS_COMPLETE = 0;
        // MTK-START
        /** TP-Status: CDMA card request deliver report 
               * @hide
        */
        public static final int STATUS_REPLACED_BY_SC = 2;
        // MTK-END
        /** TP-Status: pending. */
        public static final int STATUS_PENDING = 32;
        /** TP-Status: failed. */
        public static final int STATUS_FAILED = 64;

        /**
         * The subject of the message, if present.
         * <P>Type: TEXT</P>
         */
        public static final String SUBJECT = "subject";

        /**
         * The body of the message.
         * <P>Type: TEXT</P>
         */
        public static final String BODY = "body";

        /**
         * The ID of the sender of the conversation, if present.
         * <P>Type: INTEGER (reference to item in {@code content://contacts/people})</P>
         */
        public static final String PERSON = "person";

        /**
         * The protocol identifier code.
         * <P>Type: INTEGER</P>
         */
        public static final String PROTOCOL = "protocol";

        /**
         * Is the {@code TP-Reply-Path} flag set?
         * <P>Type: BOOLEAN</P>
         */
        public static final String REPLY_PATH_PRESENT = "reply_path_present";

        /**
         * The service center (SC) through which to send the message, if present.
         * <P>Type: TEXT</P>
         */
        public static final String SERVICE_CENTER = "service_center";

        /**
         * Is the message locked?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String LOCKED = "locked";

        /**
         * Error code associated with sending or receiving this message.
         * <P>Type: INTEGER</P>
         */
        public static final String ERROR_CODE = "error_code";

        // MTK-START
        /**
         * Sepcifi SIM identity for message
         * <P>Type: INTEGER </P>
         *
         * @hide
         */
        public static final String SIM_ID = "sim_id";
        // MTK-END
    }

    /**
     * Contains all text-based SMS messages.
     */
    public static final class Sms implements BaseColumns, TextBasedSmsColumns {

        /**
         * Not instantiable.
         * @hide
         */
        private Sms() {
        }

        /**
         * Used to determine the currently configured default SMS package.
         * @param context context of the requesting application
         * @return package name for the default SMS package or null
         */
        public static String getDefaultSmsPackage(Context context) {
            ComponentName component = SmsApplication.getDefaultSmsApplication(context, false);
            if (component != null) {
                return component.getPackageName();
            }
            return null;
        }

        /**
         * Return cursor for table query.
         * @hide
         */
        public static Cursor query(ContentResolver cr, String[] projection) {
            return cr.query(CONTENT_URI, projection, null, null, DEFAULT_SORT_ORDER);
        }

        /**
         * Return cursor for table query.
         * @hide
         */
        public static Cursor query(ContentResolver cr, String[] projection,
                String where, String orderBy) {
            return cr.query(CONTENT_URI, projection, where,
                    null, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
        }

        /**
         * The {@code content://} style URL for this table.
         */
        public static final Uri CONTENT_URI = Uri.parse("content://sms");

        // MTK-START
        /**
         * IP message field.
         *
         * @hide
         */
        public static final String IPMSG_ID = "ipmsg_id";

        /**
         * CT feature for concatenated message.
         * Specify the reference id for part of SMS.
         *
         * @hide
         */
        public static final String REFERENCE_ID = "ref_id";

        /**
         * CT feature for concatenated message.
         * Specify the receive length for concatenated SMS.
         *
         * @hide
         */
        public static final String TOTAL_LENGTH = "total_len";

        /**
         * CT feature for concatenated message.
         * Specify the receive length for part of SMS.
         *
         * @hide
         */
        public static final String RECEIVED_LENGTH = "rec_len";

        /**
         * CT feature for concatenated message.
         * Specify the received time for part of SMS.
         *
         * @hide
         */
        public static final String RECEIVED_TIME = "recv_time";

        /**
         * CT feature for concatenated message.
         * Specify the upload flag for part of SMS.
         *
         * @hide
         */
        public static final String UPLOAD_FLAG = "upload_flag";
        // MTK-END

        /**
         * The default sort order for this table.
         */
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        /**
         * Add an SMS to the given URI.
         *
         * @param resolver the content resolver to use
         * @param uri the URI to add the message to
         * @param address the address of the sender
         * @param body the body of the message
         * @param subject the pseudo-subject of the message
         * @param date the timestamp for the message
         * @param read true if the message has been read, false if not
         * @param deliveryReport true if a delivery report was requested, false if not
         * @return the URI for the new message
         * @hide
         */
        public static Uri addMessageToUri(ContentResolver resolver,
                Uri uri, String address, String body, String subject,
                Long date, boolean read, boolean deliveryReport) {
            return addMessageToUri(resolver, uri, address, body, subject,
                    date, read, deliveryReport, -1L);
        }

        /**
         * Add an SMS to the given URI with the specified thread ID.
         *
         * @param resolver the content resolver to use
         * @param uri the URI to add the message to
         * @param address the address of the sender
         * @param body the body of the message
         * @param subject the pseudo-subject of the message
         * @param date the timestamp for the message
         * @param read true if the message has been read, false if not
         * @param deliveryReport true if a delivery report was requested, false if not
         * @param threadId the thread_id of the message
         * @return the URI for the new message
         * @hide
         */
        public static Uri addMessageToUri(ContentResolver resolver,
                Uri uri, String address, String body, String subject,
                Long date, boolean read, boolean deliveryReport, long threadId) {

            return addMessageToUri(resolver, uri, address, body, subject,
                    date, read, deliveryReport, threadId, -1);
        }

        // MTK-START
        /**
         * Add an SMS to the given URI with thread_id specified.
         *
         * @param resolver the content resolver to use
         * @param uri the URI to add the message to
         * @param address the address of the sender
         * @param body the body of the message
         * @param subject the psuedo-subject of the message
         * @param date the timestamp for the message
         * @param read true if the message has been read, false if not
         * @param deliveryReport true if a delivery report was requested, false if not
         * @param threadId the thread_id of the message
         * @param simId the sim_id of the message
         * @return the URI for the new message
         * @internal
         * @hide
         */
        public static Uri addMessageToUri(ContentResolver resolver,
                Uri uri, String address, String body, String subject,
                Long date, boolean read, boolean deliveryReport, long threadId, int simId) {
            
            return addMessageToUri(resolver, uri, address, body, subject, null,
                    date, read, deliveryReport, threadId, simId);
        }

        /**
         * Add an SMS to the given URI with thread_id specified.
         *
         * @param resolver the content resolver to use
         * @param uri the URI to add the message to
         * @param address the address of the sender
         * @param body the body of the message
         * @param subject the psuedo-subject of the message
         * @param sc the service center of the message
         * @param date the timestamp for the message
         * @param read true if the message has been read, false if not
         * @param deliveryReport true if a delivery report was requested, false if not
         * @param threadId the thread_id of the message
         * @param simId the sim_id of the message
         * @return the URI for the new message
         * @internal
         * @hide
         */
        public static Uri addMessageToUri(ContentResolver resolver,
                Uri uri, String address, String body, String subject, String sc,
                Long date, boolean read, boolean deliveryReport, long threadId, int simId) {
            ContentValues values = new ContentValues(8);

            values.put(ADDRESS, address);
            if (date != null) {
                values.put(DATE, date);
            }
            if (sc != null) {
                values.put(SERVICE_CENTER, sc);
            }
            values.put(READ, read ? Integer.valueOf(1) : Integer.valueOf(0));
            values.put(SUBJECT, subject);
            values.put(BODY, body);
            values.put(SEEN, read ? Integer.valueOf(1) : Integer.valueOf(0));
            if (deliveryReport) {
                values.put(STATUS, STATUS_PENDING);
            }
            if (threadId != -1L) {
                values.put(THREAD_ID, threadId);
            }

            if (simId != -1) {
                values.put(SIM_ID, simId);
            }
            
            return resolver.insert(uri, values);
        }
        // MTK-END

        /**
         * Move a message to the given folder.
         *
         * @param context the context to use
         * @param uri the message to move
         * @param folder the folder to move to
         * @return true if the operation succeeded
         * @hide
         */
        public static boolean moveMessageToFolder(Context context,
                Uri uri, int folder, int error) {
            if (uri == null) {
                return false;
            }

            boolean markAsUnread = false;
            boolean markAsRead = false;
            switch(folder) {
            case MESSAGE_TYPE_INBOX:
            case MESSAGE_TYPE_DRAFT:
                break;
            case MESSAGE_TYPE_OUTBOX:
            case MESSAGE_TYPE_SENT:
                markAsRead = true;
                break;
            case MESSAGE_TYPE_FAILED:
            case MESSAGE_TYPE_QUEUED:
                markAsUnread = true;
                break;
            default:
                return false;
            }

            ContentValues values = new ContentValues(3);

            values.put(TYPE, folder);
            if (markAsUnread) {
                values.put(READ, 0);
            } else if (markAsRead) {
                values.put(READ, 1);
            }
            values.put(ERROR_CODE, error);

            return 1 == SqliteWrapper.update(context, context.getContentResolver(),
                            uri, values, null, null);
        }

        /**
         * Returns true iff the folder (message type) identifies an
         * outgoing message.
         * @hide
         */
        public static boolean isOutgoingFolder(int messageType) {
            return  (messageType == MESSAGE_TYPE_FAILED)
                    || (messageType == MESSAGE_TYPE_OUTBOX)
                    || (messageType == MESSAGE_TYPE_SENT)
                    || (messageType == MESSAGE_TYPE_QUEUED);
        }

        /**
         * Contains all text-based SMS messages in the SMS app inbox.
         */
        public static final class Inbox implements BaseColumns, TextBasedSmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Inbox() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri CONTENT_URI = Uri.parse("content://sms/inbox");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            /**
             * Add an SMS to the Draft box.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param date the timestamp for the message
             * @param read true if the message has been read, false if not
             * @return the URI for the new message
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, Long date,
                    boolean read) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, date, read, false);
            }

            //MTK-START
            /**
             * Add an SMS to the Inbbox
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param sc the service center address of the message
             * @param date the timestamp for the message
             * @param read true if the message has been read, false if not
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, String sc, Long date,
                    boolean read) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, sc, date, read, false, -1L, -1);
            }

            /**
             * Add an SMS to the Inbox.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param date the timestamp for the message
             * @param read true if the message has been read, false if not
             * @param simId the sim_id of the message
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, Long date,
                    boolean read, int simId) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, date, read, false, -1L, simId);
            }

            /**
             * Add an SMS to the Inbox.
             * 
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param sc the service center address of the message
             * @param date the timestamp for the message
             * @param read true if the message has been read, false if not
             * @param simId the sim_id of the message
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, String sc, Long date,
                    boolean read, int simId) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, sc, date, read, false, -1L, simId);
            }
            //MTK-START
        }

        /**
         * Contains all sent text-based SMS messages in the SMS app.
         */
        public static final class Sent implements BaseColumns, TextBasedSmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Sent() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri CONTENT_URI = Uri.parse("content://sms/sent");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            /**
             * Add an SMS to the Draft box.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param date the timestamp for the message
             * @return the URI for the new message
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, Long date) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, date, true, false);
            }


            //MTK-START
            /**
             * Add an SMS to the Sent box.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param sc the service center address of the message
             * @param date the timestamp for the message
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, String sc, Long date) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, sc, date, true, false, -1L, -1);
            }

            /**
             * Add an SMS to the Sent box.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param date the timestamp for the message
             * @param simId the sim_id of the message             
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, Long date, int simId) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, date, true, false, -1L, simId);
            }

            /**
             * Add an SMS to the Sent box.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param date the timestamp for the message
             * @param simId the sim_id of the message             
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, String sc, Long date, int simId) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, sc, date, true, false, -1L, simId);
            }
            //MTK-END
        }

        /**
         * Contains all sent text-based SMS messages in the SMS app.
         */
        public static final class Draft implements BaseColumns, TextBasedSmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Draft() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri CONTENT_URI = Uri.parse("content://sms/draft");
            
            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            //MTK-START
            /**
             * Add an SMS to the Draft box.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param date the timestamp for the message
             * @param simId the sim_id of the message         
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, Long date, int simId) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, date, true, false, -1L, simId);
            }
            //MTK-END
        }

        /**
         * Contains all pending outgoing text-based SMS messages.
         */
        public static final class Outbox implements BaseColumns, TextBasedSmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Outbox() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri CONTENT_URI = Uri.parse("content://sms/outbox");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            /**
             * Add an SMS to the outbox.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the pseudo-subject of the message
             * @param date the timestamp for the message
             * @param deliveryReport whether a delivery report was requested for the message
             * @return the URI for the new message
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, Long date,
                    boolean deliveryReport, long threadId) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, date, true, deliveryReport, threadId);
            }

            // MTK-START
            /**
             * Add an SMS to the outbox.
             *
             * @param resolver the content resolver to use
             * @param address the address of the sender
             * @param body the body of the message
             * @param subject the psuedo-subject of the message
             * @param date the timestamp for the message
             * @param deliveryReport whether a delivery report was requested for the message
             * @param threadId the conversation thread identity
             * @param simId the sim_id of the message         
             * @return the URI for the new message
             * @internal
             * @hide
             */
            public static Uri addMessage(ContentResolver resolver,
                    String address, String body, String subject, Long date,
                    boolean deliveryReport, long threadId, int simId) {
                return addMessageToUri(resolver, CONTENT_URI, address, body,
                        subject, date, true, deliveryReport, threadId, simId);
            }
            // MTK-END
        }

        /**
         * Contains all sent text-based SMS messages in the SMS app.
         */
        public static final class Conversations
                implements BaseColumns, TextBasedSmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Conversations() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri CONTENT_URI = Uri.parse("content://sms/conversations");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            /**
             * The first 45 characters of the body of the message.
             * <P>Type: TEXT</P>
             */
            public static final String SNIPPET = "snippet";

            /**
             * The number of messages in the conversation.
             * <P>Type: INTEGER</P>
             */
            public static final String MESSAGE_COUNT = "msg_count";
        }

        /**
         * Contains constants for SMS related Intents that are broadcast.
         */
        public static final class Intents {

            /**
             * Not instantiable.
             * @hide
             */
            private Intents() {
            }

            /**
             * Set by BroadcastReceiver to indicate that the message was handled
             * successfully.
             */
            public static final int RESULT_SMS_HANDLED = 1;

            /**
             * Set by BroadcastReceiver to indicate a generic error while
             * processing the message.
             */
            public static final int RESULT_SMS_GENERIC_ERROR = 2;

            /**
             * Set by BroadcastReceiver to indicate insufficient memory to store
             * the message.
             */
            public static final int RESULT_SMS_OUT_OF_MEMORY = 3;

            /**
             * Set by BroadcastReceiver to indicate that the message, while
             * possibly valid, is of a format or encoding that is not
             * supported.
             */
            public static final int RESULT_SMS_UNSUPPORTED = 4;

            /**
             * Set by BroadcastReceiver to indicate a duplicate incoming message.
             */
            public static final int RESULT_SMS_DUPLICATED = 5;

            // MTK-START
            /**
             * Set by mobile manager service to indicate a permitted incoming message.
             *
             * @hide
             */
            public static final int RESULT_SMS_ACCEPT_BY_MOMS = 100;

            /**
             * Set by mobile manager service to indicate a reject incoming message.
             *
             * @hide
             */
            public static final int RESULT_SMS_REJECT_BY_MOMS = 101;
            // MTK-END

            /**
             * Activity action: Ask the user to change the default
             * SMS application. This will show a dialog that asks the
             * user whether they want to replace the current default
             * SMS application with the one specified in
             * {@link #EXTRA_PACKAGE_NAME}.
             */
            @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
            public static final String ACTION_CHANGE_DEFAULT =
                    "android.provider.Telephony.ACTION_CHANGE_DEFAULT";

            /**
             * The PackageName string passed in as an
             * extra for {@link #ACTION_CHANGE_DEFAULT}
             *
             * @see #ACTION_CHANGE_DEFAULT
             */
            public static final String EXTRA_PACKAGE_NAME = "package";

            /**
             * Broadcast Action: A new text-based SMS message has been received
             * by the device. This intent will only be delivered to the default
             * sms app. That app is responsible for writing the message and notifying
             * the user. The intent will have the following extra values:</p>
             *
             * <ul>
             *   <li><em>"pdus"</em> - An Object[] of byte[]s containing the PDUs
             *   that make up the message.</li>
             * </ul>
             *
             * <p>The extra values can be extracted using
             * {@link #getMessagesFromIntent(Intent)}.</p>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             *
             * <p class="note"><strong>Note:</strong>
             * The broadcast receiver that filters for this intent must declare
             * {@link android.Manifest.permission#BROADCAST_SMS} as a required permission in
             * the <a href="{@docRoot}guide/topics/manifest/receiver-element.html">{@code
             * &lt;receiver>}</a> tag.
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SMS_DELIVER_ACTION =
                    "android.provider.Telephony.SMS_DELIVER";

            /**
             * Broadcast Action: A new text-based SMS message has been received
             * by the device. This intent will be delivered to all registered
             * receivers as a notification. These apps are not expected to write the
             * message or notify the user. The intent will have the following extra
             * values:</p>
             *
             * <ul>
             *   <li><em>"pdus"</em> - An Object[] of byte[]s containing the PDUs
             *   that make up the message.</li>
             * </ul>
             *
             * <p>The extra values can be extracted using
             * {@link #getMessagesFromIntent(Intent)}.</p>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SMS_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_RECEIVED";

            /**
             * Broadcast Action: A new data based SMS message has been received
             * by the device. This intent will be delivered to all registered
             * receivers as a notification. The intent will have the following extra
             * values:</p>
             *
             * <ul>
             *   <li><em>"pdus"</em> - An Object[] of byte[]s containing the PDUs
             *   that make up the message.</li>
             * </ul>
             *
             * <p>The extra values can be extracted using
             * {@link #getMessagesFromIntent(Intent)}.</p>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String DATA_SMS_RECEIVED_ACTION =
                    "android.intent.action.DATA_SMS_RECEIVED";

            /**
             * Broadcast Action: A new WAP PUSH message has been received by the
             * device. This intent will only be delivered to the default
             * sms app. That app is responsible for writing the message and notifying
             * the user. The intent will have the following extra values:</p>
             *
             * <ul>
             *   <li><em>"transactionId"</em> - (Integer) The WAP transaction ID</li>
             *   <li><em>"pduType"</em> - (Integer) The WAP PDU type</li>
             *   <li><em>"header"</em> - (byte[]) The header of the message</li>
             *   <li><em>"data"</em> - (byte[]) The data payload of the message</li>
             *   <li><em>"contentTypeParameters" </em>
             *   -(HashMap&lt;String,String&gt;) Any parameters associated with the content type
             *   (decoded from the WSP Content-Type header)</li>
             * </ul>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             *
             * <p>The contentTypeParameters extra value is map of content parameters keyed by
             * their names.</p>
             *
             * <p>If any unassigned well-known parameters are encountered, the key of the map will
             * be 'unassigned/0x...', where '...' is the hex value of the unassigned parameter.  If
             * a parameter has No-Value the value in the map will be null.</p>
             *
             * <p class="note"><strong>Note:</strong>
             * The broadcast receiver that filters for this intent must declare
             * {@link android.Manifest.permission#BROADCAST_WAP_PUSH} as a required permission in
             * the <a href="{@docRoot}guide/topics/manifest/receiver-element.html">{@code
             * &lt;receiver>}</a> tag.
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String WAP_PUSH_DELIVER_ACTION =
                    "android.provider.Telephony.WAP_PUSH_DELIVER";

            /**
             * Broadcast Action: A new WAP PUSH message has been received by the
             * device. This intent will be delivered to all registered
             * receivers as a notification. These apps are not expected to write the
             * message or notify the user. The intent will have the following extra
             * values:</p>
             *
             * <ul>
             *   <li><em>"transactionId"</em> - (Integer) The WAP transaction ID</li>
             *   <li><em>"pduType"</em> - (Integer) The WAP PDU type</li>
             *   <li><em>"header"</em> - (byte[]) The header of the message</li>
             *   <li><em>"data"</em> - (byte[]) The data payload of the message</li>
             *   <li><em>"contentTypeParameters"</em>
             *   - (HashMap&lt;String,String&gt;) Any parameters associated with the content type
             *   (decoded from the WSP Content-Type header)</li>
             * </ul>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             *
             * <p>The contentTypeParameters extra value is map of content parameters keyed by
             * their names.</p>
             *
             * <p>If any unassigned well-known parameters are encountered, the key of the map will
             * be 'unassigned/0x...', where '...' is the hex value of the unassigned parameter.  If
             * a parameter has No-Value the value in the map will be null.</p>
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String WAP_PUSH_RECEIVED_ACTION =
                    "android.provider.Telephony.WAP_PUSH_RECEIVED";

            /**
             * Broadcast Action: A new Cell Broadcast message has been received
             * by the device. The intent will have the following extra
             * values:</p>
             *
             * <ul>
             *   <li><em>"message"</em> - An SmsCbMessage object containing the broadcast message
             *   data. This is not an emergency alert, so ETWS and CMAS data will be null.</li>
             * </ul>
             *
             * <p>The extra values can be extracted using
             * {@link #getMessagesFromIntent(Intent)}.</p>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SMS_CB_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_CB_RECEIVED";

            /**
             * Broadcast Action: A new Emergency Broadcast message has been received
             * by the device. The intent will have the following extra
             * values:</p>
             *
             * <ul>
             *   <li><em>"message"</em> - An SmsCbMessage object containing the broadcast message
             *   data, including ETWS or CMAS warning notification info if present.</li>
             * </ul>
             *
             * <p>The extra values can be extracted using
             * {@link #getMessagesFromIntent(Intent)}.</p>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SMS_EMERGENCY_CB_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_EMERGENCY_CB_RECEIVED";

            /**
             * Broadcast Action: A new CDMA SMS has been received containing Service Category
             * Program Data (updates the list of enabled broadcast channels). The intent will
             * have the following extra values:</p>
             *
             * <ul>
             *   <li><em>"operations"</em> - An array of CdmaSmsCbProgramData objects containing
             *   the service category operations (add/delete/clear) to perform.</li>
             * </ul>
             *
             * <p>The extra values can be extracted using
             * {@link #getMessagesFromIntent(Intent)}.</p>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED";

            /**
             * Broadcast Action: The SIM storage for SMS messages is full.  If
             * space is not freed, messages targeted for the SIM (class 2) may
             * not be saved.
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SIM_FULL_ACTION =
                    "android.provider.Telephony.SIM_FULL";

            /**
             * Broadcast Action: An incoming SMS has been rejected by the
             * telephony framework.  This intent is sent in lieu of any
             * of the RECEIVED_ACTION intents.  The intent will have the
             * following extra value:</p>
             *
             * <ul>
             *   <li><em>"result"</em> - An int result code, e.g. {@link #RESULT_SMS_OUT_OF_MEMORY}
             *   indicating the error returned to the network.</li>
             * </ul>
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SMS_REJECTED_ACTION =
                "android.provider.Telephony.SMS_REJECTED";

            // MTK-START
            /**
             * Broadcast Action: The SMS sub-system in the modem is ready.
             * The intent is sent to inform the APP if the SMS sub-system
             * is ready or not. The intent will have the following extra value:</p>
             *
             * <ul>
             *   <li><em>ready</em> - An boolean result code, true for ready</li>
             * </ul>
             * @hide
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String SMS_STATE_CHANGED_ACTION =
                "android.provider.Telephony.SMS_STATE_CHANGED";

            /**
             * Broadcast Action: This intent is similar to DATA_SMS_RECEIVED_ACTION 
             * except that we will use this intent only when the SMS comes from 
             * OA: 10654040 to port number: 16998 for CMCC DM
             *
             * The intent will have the following extra values:</p>
             *
             * <ul>
             *   <li><em>pdus</em> - An Object[] od byte[]s containing the PDUs
             *   that make up the message.</li>
             * </ul>
             *
             * <p>The extra values can be extracted using
             * {@link #getMessagesFromIntent(Intent)}.</p>
             *
             * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             * @hide
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String DM_REGISTER_SMS_RECEIVED_ACTION =
                    "android.intent.action.DM_REGISTER_SMS_RECEIVED";

            /**
             * Broadcast Action: This intent is used to send to check if mobile
             * manager service(Moms feature) permit to dispatch SMS
             *
             * The intent will have the following extra values:</p>
             *
             * <ul>
             *   <li><em>pdus</em> - An Object[] od byte[]s containing the PDUs
             *   that make up the message.</li>
             * </ul>
             *
             * * <p>If a BroadcastReceiver encounters an error while processing
             * this intent it should set the result code appropriately.</p>
             *
             * @hide
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String MOMS_SMS_RECEIVED_ACTION =
                    "android.intent.action.MOMS_SMS_RECEIVED";
            // MTK-END

            /**
             * VIA add
             * Broadcast Action: This intent is similar to DM_REGISTER_SMS_RECEIVED_ACTION
             * but this one is for CDMA SMS(Teleservice is 0xFDED)
             *
             * @hide
             */
			public static final String CDMA_REG_SMS_ACTION = 
    				"android.telephony.sms.CDMA_REG_SMS_ACTION";

            // MTK-START
            /**
             * Broadcast Action: A new SMS message has been received
             * by the device, which contains specail message indication,
             * defined in 23.040 9.2.3.24.2
             *
             * @hide
             */
            public static final String MWI_SMS_RECEIVED_ACTION =
                    "android.provider.Telephony.MWI_SMS_RECEIVED";
            // MTK-END

            /**
             * VIA add
             * Broadcast Action: China Telecom request the devices must do a
             * short message auto-register work after the modem fetched the network.
             * The intent will be broadcasted by framework when the app-layer can do
             * this work.
             *
             * @hide
             */
            public static final String CDMA_AUTO_SMS_REGISTER_FEASIBLE_ACTION =
                    "android.provider.Telephony.CDMA_AUTO_SMS_REGISTER_FEASIBLE";

            /**
             * Read the PDUs out of an {@link #SMS_RECEIVED_ACTION} or a
             * {@link #DATA_SMS_RECEIVED_ACTION} intent.
             *
             * @param intent the intent to read from
             * @return an array of SmsMessages for the PDUs
             */
            public static SmsMessage[] getMessagesFromIntent(Intent intent) {
                Object[] messages = (Object[]) intent.getSerializableExtra("pdus");
                String format = intent.getStringExtra("format");

                // MTK-START
                if (messages == null) {
                    return null;
                }

                int simId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);
                Rlog.d(TAG, "Get SmeMessage ID: " + simId);
                // MTK-END

                int pduCount = messages.length;
                SmsMessage[] msgs = new SmsMessage[pduCount];

                for (int i = 0; i < pduCount; i++) {
                    byte[] pdu = (byte[]) messages[i];
                    // MTK-START
                    //msgs[i] = SmsMessage.createFromPdu(pdu, format);
                    msgs[i] = GeminiSmsMessage.createFromPdu(pdu, format, simId);
                    // MTK-END
                }
                return msgs;
            }
        }
    }

    // MTK-START
    /**
     * Base columns for tables that contain text based SMSCbs.
     *
     * @internal
     * @hide
     */
    public interface TextBasedSmsCbColumns {

        /**
         * The SIM ID which indicated which SIM the SMSCb comes from
         * Reference to Telephony.SIMx
         * <P>Type: INTEGER</P>
         */
        public static final String SIM_ID = "sim_id";

        /**
         * The channel ID of the message
         * which is the message identifier defined in the Spec. 3GPP TS 23.041
         * <P>Type: INTEGER</P>
         */
        public static final String CHANNEL_ID = "channel_id";

        /**
         * The date the message was sent
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE = "date";

        /**
         * Has the message been read
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String READ = "read";

        /**
         * The body of the message
         * <P>Type: TEXT</P>
         */
        public static final String BODY = "body";

        /**
         * The thread id of the message
         * <P>Type: INTEGER</P>
         */
        public static final String THREAD_ID = "thread_id";

        /**
         * Indicates whether this message has been seen by the user. The "seen" flag will be
         * used to figure out whether we need to throw up a statusbar notification or not.
         */
        public static final String SEEN = "seen";

        /**
         * Has the message been locked?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String LOCKED = "locked";
    }

    /**
     * Contains all cell broadcast messages in the cell broadcast app.
     *
     * @internal
     * @hide
     */
    public static final class SmsCb implements BaseColumns, TextBasedSmsCbColumns {

        /**
         * @internal
         */
        public static final Cursor query(ContentResolver cr, String[] projection) {
            return cr.query(CONTENT_URI, projection, null, null, DEFAULT_SORT_ORDER);
        }

        /**
         * @internal
         */
        public static final Cursor query(ContentResolver cr, String[] projection,
                String where, String orderBy) {
            return cr.query(CONTENT_URI, projection, where,
                                         null, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
        }

        /**
         * The content:// style URL for this table
         * @internal
         */
        public static final Uri CONTENT_URI =
            Uri.parse("content://cb/messages");

        /**
         * The content:// style URL for "canonical_addresses" table
         * @internal
         */
        public static final Uri ADDRESS_URI = Uri.parse("content://cb/addresses");

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        /**
         * Add an SMS to the given URI with thread_id specified.
         *
         * @param resolver the content resolver to use
         * @param uri the URI to add the message to
         * @param sim_id the id of the SIM card
         * @param channel_id the message identifier of the CB message
         * @param date the timestamp for the message
         * @param read true if the message has been read, false if not
         * @param body the body of the message
         * @return the URI for the new message
         * @hide
         * @internal
         */
        public static Uri addMessageToUri(ContentResolver resolver,
                Uri uri, int sim_id, int channel_id, long date,
                boolean read, String body) {
            ContentValues values = new ContentValues(5);

            values.put(SIM_ID, Integer.valueOf(sim_id));
            values.put(DATE, Long.valueOf(date));
            values.put(READ, read ? Integer.valueOf(1) : Integer.valueOf(0));
            values.put(BODY, body);
            values.put(CHANNEL_ID, Integer.valueOf(channel_id));

            return resolver.insert(uri, values);
        }

        /**
         * Contains all received SMSCb messages in the SMS app's.
         *
         * @internal
         * @hide
         */
        public static final class Conversations
                implements BaseColumns, TextBasedSmsCbColumns {
            /**
             * The content:// style URL for this table
             *@internal
             */
            public static final Uri CONTENT_URI =
                Uri.parse("content://cb/threads");

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";

            /**
             * The first 45 characters of the body of the message
             * <P>Type: TEXT</P>
             */
            public static final String SNIPPET = "snippet";

            /**
             * The number of messages in the conversation
             * <P>Type: INTEGER</P>
             */
            public static final String MESSAGE_COUNT = "msg_count";

            /**
             * The _id of address table in the conversation
             * <P>Type: INTEGER</P>
             */
            public static final String ADDRESS_ID = "address_id";
        }

        /**
         * Columns for the "canonical_addresses" table used by CB-SMS
         *
         * @hide
         */
        public interface CanonicalAddressesColumns extends BaseColumns {
            /**
             * An address used in CB-SMS. Just a channel number
             * <P>Type: TEXT</P>
             */
            public static final String ADDRESS = "address";
        }

        /**
         * Columns for the "canonical_addresses" table used by CB-SMS
         *
         * @internal
         * @hide
         */
        public static final class CbChannel implements BaseColumns {
            /**
             * The content:// style URL for this table
             * @internal
             */
            public static final Uri CONTENT_URI =
                Uri.parse("content://cb/channel");

            public static final String NAME = "name";

            public static final String NUMBER = "number";

            public static final String ENABLE = "enable";

        }
        // TODO open when using CB Message
        /**
         * Read the PDUs out of an {@link #SMS_CB_RECEIVED_ACTION} intent.
         *
         * @internal
         * @hide
         */
        public static final class Intents {

            /**
             * Read the PDUs out of an {@link #SMS_CB_RECEIVED_ACTION}.
             *
             * @param intent the intent to read from
             * @return an array of SmsCbMessages for the PDUs
             * @internal
             */
            public static final SmsCbMessage[] getMessagesFromIntent(
                    Intent intent) {
                Parcelable[] messages = intent.getParcelableArrayExtra("message");
                if (messages == null) {
                    return null;
                }

                SmsCbMessage[] msgs = new SmsCbMessage[messages.length];
                int simId = intent.getIntExtra(PhoneConstants.GEMINI_SIM_ID_KEY, -1);

                Rlog.d(TAG, "Get SmsCbMessage ID: " + simId);

                for (int i = 0; i < messages.length; i++) {
                    msgs[i] = (SmsCbMessage)messages[i];
                }
                return msgs;
            }
        }
    }
    // MTK-END

    /**
     * Base columns for tables that contain MMSs.
     */
    public interface BaseMmsColumns extends BaseColumns {

        /** Message box: all messages. */
        public static final int MESSAGE_BOX_ALL    = 0;
        /** Message box: inbox. */
        public static final int MESSAGE_BOX_INBOX  = 1;
        /** Message box: sent messages. */
        public static final int MESSAGE_BOX_SENT   = 2;
        /** Message box: drafts. */
        public static final int MESSAGE_BOX_DRAFTS = 3;
        /** Message box: outbox. */
        public static final int MESSAGE_BOX_OUTBOX = 4;

        /**
         * The thread ID of the message.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String THREAD_ID = "thread_id";

        /**
         * The date the message was received.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE = "date";

        /**
         * The date the message was sent.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_SENT = "date_sent";

        /**
         * The box which the message belongs to, e.g. {@link #MESSAGE_BOX_INBOX}.
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_BOX = "msg_box";

        /**
         * Has the message been read?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String READ = "read";

        /**
         * Has the message been seen by the user? The "seen" flag determines
         * whether we need to show a new message notification.
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String SEEN = "seen";

        /**
         * Does the message have only a text part (can also have a subject) with
         * no picture, slideshow, sound, etc. parts?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String TEXT_ONLY = "text_only";

        /**
         * The {@code Message-ID} of the message.
         * <P>Type: TEXT</P>
         */
        public static final String MESSAGE_ID = "m_id";

        /**
         * The subject of the message, if present.
         * <P>Type: TEXT</P>
         */
        public static final String SUBJECT = "sub";

        /**
         * The character set of the subject, if present.
         * <P>Type: INTEGER</P>
         */
        public static final String SUBJECT_CHARSET = "sub_cs";

        /**
         * The {@code Content-Type} of the message.
         * <P>Type: TEXT</P>
         */
        public static final String CONTENT_TYPE = "ct_t";

        /**
         * The {@code Content-Location} of the message.
         * <P>Type: TEXT</P>
         */
        public static final String CONTENT_LOCATION = "ct_l";

        /**
         * The expiry time of the message.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String EXPIRY = "exp";

        /**
         * The class of the message.
         * <P>Type: TEXT</P>
         */
        public static final String MESSAGE_CLASS = "m_cls";

        /**
         * The type of the message defined by MMS spec.
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_TYPE = "m_type";

        /**
         * The version of the specification that this message conforms to.
         * <P>Type: INTEGER</P>
         */
        public static final String MMS_VERSION = "v";

        /**
         * The size of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_SIZE = "m_size";

        /**
         * The priority of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String PRIORITY = "pri";

        /**
         * The {@code read-report} of the message.
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String READ_REPORT = "rr";

        /**
         * Is read report allowed?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String REPORT_ALLOWED = "rpt_a";

        /**
         * The {@code response-status} of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String RESPONSE_STATUS = "resp_st";

        /**
         * The {@code status} of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String STATUS = "st";

        // MTK-START
        /**
         * The status of the message.
         * <P>Type: INTEGER</P>
         *
         * @hide
         */
        public static final String STATUS_EXT = "st_ext";
        // MTK-END

        /**
         * The {@code transaction-id} of the message.
         * <P>Type: TEXT</P>
         */
        public static final String TRANSACTION_ID = "tr_id";

        /**
         * The {@code retrieve-status} of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String RETRIEVE_STATUS = "retr_st";

        /**
         * The {@code retrieve-text} of the message.
         * <P>Type: TEXT</P>
         */
        public static final String RETRIEVE_TEXT = "retr_txt";

        /**
         * The character set of the retrieve-text.
         * <P>Type: INTEGER</P>
         */
        public static final String RETRIEVE_TEXT_CHARSET = "retr_txt_cs";

        /**
         * The {@code read-status} of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String READ_STATUS = "read_status";

        /**
         * The {@code content-class} of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String CONTENT_CLASS = "ct_cls";

        /**
         * The {@code delivery-report} of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String DELIVERY_REPORT = "d_rpt";

        /**
         * The {@code delivery-time-token} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String DELIVERY_TIME_TOKEN = "d_tm_tok";

        /**
         * The {@code delivery-time} of the message.
         * <P>Type: INTEGER</P>
         */
        public static final String DELIVERY_TIME = "d_tm";

        /**
         * The {@code response-text} of the message.
         * <P>Type: TEXT</P>
         */
        public static final String RESPONSE_TEXT = "resp_txt";

        /**
         * The {@code sender-visibility} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String SENDER_VISIBILITY = "s_vis";

        /**
         * The {@code reply-charging} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String REPLY_CHARGING = "r_chg";

        /**
         * The {@code reply-charging-deadline-token} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String REPLY_CHARGING_DEADLINE_TOKEN = "r_chg_dl_tok";

        /**
         * The {@code reply-charging-deadline} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String REPLY_CHARGING_DEADLINE = "r_chg_dl";

        /**
         * The {@code reply-charging-id} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String REPLY_CHARGING_ID = "r_chg_id";

        /**
         * The {@code reply-charging-size} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String REPLY_CHARGING_SIZE = "r_chg_sz";

        /**
         * The {@code previously-sent-by} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String PREVIOUSLY_SENT_BY = "p_s_by";

        /**
         * The {@code previously-sent-date} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String PREVIOUSLY_SENT_DATE = "p_s_d";

        /**
         * The {@code store} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String STORE = "store";

        /**
         * The {@code mm-state} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MM_STATE = "mm_st";

        /**
         * The {@code mm-flags-token} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MM_FLAGS_TOKEN = "mm_flg_tok";

        /**
         * The {@code mm-flags} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MM_FLAGS = "mm_flg";

        /**
         * The {@code store-status} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String STORE_STATUS = "store_st";

        /**
         * The {@code store-status-text} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String STORE_STATUS_TEXT = "store_st_txt";

        /**
         * The {@code stored} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String STORED = "stored";

        /**
         * The {@code totals} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String TOTALS = "totals";

        /**
         * The {@code mbox-totals} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MBOX_TOTALS = "mb_t";

        /**
         * The {@code mbox-totals-token} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MBOX_TOTALS_TOKEN = "mb_t_tok";

        /**
         * The {@code quotas} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String QUOTAS = "qt";

        /**
         * The {@code mbox-quotas} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MBOX_QUOTAS = "mb_qt";

        /**
         * The {@code mbox-quotas-token} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MBOX_QUOTAS_TOKEN = "mb_qt_tok";

        /**
         * The {@code message-count} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String MESSAGE_COUNT = "m_cnt";

        /**
         * The {@code start} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String START = "start";

        /**
         * The {@code distribution-indicator} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String DISTRIBUTION_INDICATOR = "d_ind";

        /**
         * The {@code element-descriptor} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String ELEMENT_DESCRIPTOR = "e_des";

        /**
         * The {@code limit} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String LIMIT = "limit";

        /**
         * The {@code recommended-retrieval-mode} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String RECOMMENDED_RETRIEVAL_MODE = "r_r_mod";

        /**
         * The {@code recommended-retrieval-mode-text} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String RECOMMENDED_RETRIEVAL_MODE_TEXT = "r_r_mod_txt";

        /**
         * The {@code status-text} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String STATUS_TEXT = "st_txt";

        /**
         * The {@code applic-id} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String APPLIC_ID = "apl_id";

        /**
         * The {@code reply-applic-id} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String REPLY_APPLIC_ID = "r_apl_id";

        /**
         * The {@code aux-applic-id} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String AUX_APPLIC_ID = "aux_apl_id";

        /**
         * The {@code drm-content} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String DRM_CONTENT = "drm_c";

        /**
         * The {@code adaptation-allowed} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String ADAPTATION_ALLOWED = "adp_a";

        /**
         * The {@code replace-id} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String REPLACE_ID = "repl_id";

        /**
         * The {@code cancel-id} of the message.
         * <P>Type: TEXT</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String CANCEL_ID = "cl_id";

        /**
         * The {@code cancel-status} of the message.
         * <P>Type: INTEGER</P>
         * @deprecated this column is no longer supported.
         * @hide
         */
        @Deprecated
        public static final String CANCEL_STATUS = "cl_st";

        /**
         * Is the message locked?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String LOCKED = "locked";

        // MTK-START
        /**
         * Sepcify the SIM id of multimedia message
         *
         * <P>Type: INTEGER</P>
         * @hide
         */
        public static final String SIM_ID = "sim_id";

        /**
         * The service center (SC) through which to send the message, if present
         *
         * <P>Type: TEXT</P>
         * @hide
         */
        public static final String SERVICE_CENTER = "service_center";
        // MTK-END
    }

    /**
     * Columns for the "canonical_addresses" table used by MMS and SMS.
     */
    public interface CanonicalAddressesColumns extends BaseColumns {
        /**
         * An address used in MMS or SMS.  Email addresses are
         * converted to lower case and are compared by string
         * equality.  Other addresses are compared using
         * PHONE_NUMBERS_EQUAL.
         * <P>Type: TEXT</P>
         */
        public static final String ADDRESS = "address";
    }

    /**
     * Columns for the "threads" table used by MMS and SMS.
     */
    public interface ThreadsColumns extends BaseColumns {

        /**
         * The date at which the thread was created.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE = "date";

        /**
         * A string encoding of the recipient IDs of the recipients of
         * the message, in numerical order and separated by spaces.
         * <P>Type: TEXT</P>
         */
        public static final String RECIPIENT_IDS = "recipient_ids";

        /**
         * The message count of the thread.
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_COUNT = "message_count";
        
        // MTK-START
         /**
         * The read message count of the thread.
         * <P>Type: INTEGER</P>
         *
         * @hide
         */
        public static final String READCOUNT = "readcount";
        // MTK-END
        
        /**
         * Indicates whether all messages of the thread have been read.
         * <P>Type: INTEGER</P>
         */
        public static final String READ = "read";

        /**
         * The snippet of the latest message in the thread.
         * <P>Type: TEXT</P>
         */
        public static final String SNIPPET = "snippet";

        /**
         * The charset of the snippet.
         * <P>Type: INTEGER</P>
         */
        public static final String SNIPPET_CHARSET = "snippet_cs";

        /**
         * Type of the thread, either {@link Threads#COMMON_THREAD} or
         * {@link Threads#BROADCAST_THREAD}.
         * <P>Type: INTEGER</P>
         */
        public static final String TYPE = "type";

        /**
         * Indicates whether there is a transmission error in the thread.
         * <P>Type: INTEGER</P>
         */
        public static final String ERROR = "error";

        /**
         * Indicates whether this thread contains any attachments.
         * <P>Type: INTEGER</P>
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        // MTK-START
        /**
         * The date of the latest important message in the thread.
         * <P>Type: TEXT</P>
         *
         * @hide
         */
        public static final String LATEST_IMPORTANT_DATE = "li_date";

        /**
         * The snippet of the latest important message in the thread.
         * <P>Type: TEXT</P>
         *
         * @hide
         */
        public static final String LATEST_IMPORTANT_SNIPPET = "li_snippet";

        /**
         * The charset of the latest important snippet.
         * <P>Type: INTEGER</P>
         *
         * @hide
         */
        public static final String LATEST_IMPORTANT_SNIPPET_CHARSET = "li_snippet_cs";
        // MTK-END
    }

    /**
     * Helper functions for the "threads" table used by MMS and SMS.
     */
    public static final class Threads implements ThreadsColumns {

        private static final String[] ID_PROJECTION = { BaseColumns._ID };

        /**
         * Private {@code content://} style URL for this table. Used by
         * {@link #getOrCreateThreadId(android.content.Context, java.util.Set)}.
         */
        private static final Uri THREAD_ID_CONTENT_URI = Uri.parse(
                "content://mms-sms/threadID");

        /**
         * The {@code content://} style URL for this table, by conversation.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                MmsSms.CONTENT_URI, "conversations");

        /**
         * The {@code content://} style URL for this table, for obsolete threads.
         */
        public static final Uri OBSOLETE_THREADS_URI = Uri.withAppendedPath(
                CONTENT_URI, "obsolete");

        /** Thread type: common thread. */
        public static final int COMMON_THREAD    = 0;

        /** Thread type: broadcast thread. */
        public static final int BROADCAST_THREAD = 1;

        // MTK-START
        /**
         * Wap push thread.
         *
         * @hide
         */
        public static final int WAPPUSH_THREAD = 2;

        /**
         * Cell broadcast thread.
         *
         * @hide
         */
        public static final int CELL_BROADCAST_THREAD = 3;

        /**
         * IP message thread.
         *
         * @hide
         */
        public static final int IP_MESSAGE_GUIDE_THREAD = 10;

        /**
         * Whether a thread is being writen or not
         * 0: normal 1: being writen
         * <P>Type: INTEGER (boolean)</P>
         *
         * @hide
         */
        public static final String STATUS = "status";

        /**
         * CT feature for date sent
         *
         * @hide
         */
        public static final String DATE_SENT = "date_sent";
        // MTK-END

        /**
         * Not instantiable.
         * @hide
         */
        private Threads() {
        }

        /**
         * This is a single-recipient version of {@code getOrCreateThreadId}.
         * It's convenient for use with SMS messages.
         * @param context the context object to use.
         * @param recipient the recipient to send to.
         * @hide
         */
        public static long getOrCreateThreadId(Context context, String recipient) {
            Set<String> recipients = new HashSet<String>();

            recipients.add(recipient);
            return getOrCreateThreadId(context, recipients);
        }

        // MTK-START
        /**
         * Only for BackupRestore
         * Given the recipients list and subject of an unsaved message,
         * return its thread ID.  If the message starts a new thread,
         * allocate a new thread ID.  Otherwise, use the appropriate
         * existing thread ID.
         *
         * Find the thread ID of the same set of recipients (in
         * any order, without any additions). If one
         * is found, return it.  Otherwise, return a unique thread ID.
         *
         * @hide
         */
        public static long getOrCreateThreadId(
                Context context, Set<String> recipients, String backupRestoreIndex) {
            Uri.Builder uriBuilder = THREAD_ID_CONTENT_URI.buildUpon();
            
            if (backupRestoreIndex != null && backupRestoreIndex.length() > 0) {
                uriBuilder.appendQueryParameter("backupRestoreIndex", backupRestoreIndex);
            }

            for (String recipient : recipients) {
                if (Mms.isEmailAddress(recipient)) {
                    recipient = Mms.extractAddrSpec(recipient);
                }
                uriBuilder.appendQueryParameter("recipient", recipient);
            }
            Uri uri = uriBuilder.build();
            Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                    uri, ID_PROJECTION, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        Rlog.d(TAG,"getOrCreateThreadId for BackupRestore threadId =  " + cursor.getLong(0));
                        return cursor.getLong(0);
                    } else {
                    	Rlog.e(TAG, "getOrCreateThreadId for BackupRestore returned no rows!");
                    }
                } finally {
                    cursor.close();
                }
            }
            Rlog.e(TAG, "getOrCreateThreadId for BackupRestore failed with uri " + uri.toString());
            throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
        }
        // MTK-END

        /**
         * Given the recipients list and subject of an unsaved message,
         * return its thread ID.  If the message starts a new thread,
         * allocate a new thread ID.  Otherwise, use the appropriate
         * existing thread ID.
         *
         * <p>Find the thread ID of the same set of recipients (in any order,
         * without any additions). If one is found, return it. Otherwise,
         * return a unique thread ID.</p>
         * @hide
         */
        public static long getOrCreateThreadId(
                Context context, Set<String> recipients) {
            Uri.Builder uriBuilder = THREAD_ID_CONTENT_URI.buildUpon();

            for (String recipient : recipients) {
                if (Mms.isEmailAddress(recipient)) {
                    recipient = Mms.extractAddrSpec(recipient);
                }

                uriBuilder.appendQueryParameter("recipient", recipient);
            }

            Uri uri = uriBuilder.build();
            //if (DEBUG) Rlog.v(TAG, "getOrCreateThreadId uri: " + uri);

            Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                    uri, ID_PROJECTION, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        // MTK-START
                        // update the status to 0
                        ContentValues values = new ContentValues(1);
                        values.put(STATUS, 0);
                        Uri statusUri = ContentUris.withAppendedId(Uri.parse("content://mms-sms/conversations/status"), cursor.getLong(0));
                        int row = SqliteWrapper.update(context, context.getContentResolver(), statusUri, values, null, null);
                        Rlog.d(TAG,"getOrCreateThreadId getOrCreateThreadId row " + row);
                        // MTK-END
                        return cursor.getLong(0);
                    } else {
                        Rlog.e(TAG, "getOrCreateThreadId returned no rows!");
                    }
                } finally {
                    cursor.close();
                }
            }

            Rlog.e(TAG, "getOrCreateThreadId failed with uri " + uri.toString());
            throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
        }
    }

    // MTK-START
    /**
     * IP message for thread setting
     *
     * @hide
     */
    public static final class ThreadSettings implements BaseColumns {

        /**
         * Whether a thread is set notification enabled
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String NOTIFICATION_ENABLE = "notification_enable";

        /**
         * Which thread does this settings belongs to
         * <P>Type: INTEGER </P>
         */
        public static final String THREAD_ID = "thread_id";

        /**
         * Whether a thread is set spam
         * 0: normal 1: spam
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String SPAM = "spam";

        /**
         * Whether a thread is set mute
         * 0: normal >1: mute duration
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String MUTE = "mute";

        /**
         * when does a thread be set mute
         * 0: normal >1: mute start time
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String MUTE_START = "mute_start";

        /**
         * Whether a thread is set vibrate
         * 0: normal 1: vibrate
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String VIBRATE = "vibrate";

        /**
         * Ringtone for a thread
         * <P>Type: STRING</P>
         */
        public static final String RINGTONE = "ringtone";

        /**
         * Wallpaper for a thread
         * <P>Type: STRING</P>
         */
        public static final String WALLPAPER = "_data";
    }
    // MTK-END

    /**
     * Contains all MMS messages.
     */
    public static final class Mms implements BaseMmsColumns {

        /**
         * Not instantiable.
         * @hide
         */
        private Mms() {
        }

        /**
         * The {@code content://} URI for this table.
         */
        public static final Uri CONTENT_URI = Uri.parse("content://mms");

        /**
         * Content URI for getting MMS report requests.
         */
        public static final Uri REPORT_REQUEST_URI = Uri.withAppendedPath(
                                            CONTENT_URI, "report-request");

        /**
         * Content URI for getting MMS report status.
         */
        public static final Uri REPORT_STATUS_URI = Uri.withAppendedPath(
                                            CONTENT_URI, "report-status");

        /**
         * The default sort order for this table.
         */
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        /**
         * Regex pattern for names and email addresses.
         * <ul>
         *     <li><em>mailbox</em> = {@code name-addr}</li>
         *     <li><em>name-addr</em> = {@code [display-name] angle-addr}</li>
         *     <li><em>angle-addr</em> = {@code [CFWS] "<" addr-spec ">" [CFWS]}</li>
         * </ul>
         * @hide
         */
        public static final Pattern NAME_ADDR_EMAIL_PATTERN =
                Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");

        /**
         * Helper method to query this table.
         * @hide
         */
        public static Cursor query(
                ContentResolver cr, String[] projection) {
            return cr.query(CONTENT_URI, projection, null, null, DEFAULT_SORT_ORDER);
        }

        /**
         * Helper method to query this table.
         * @hide
         */
        public static Cursor query(
                ContentResolver cr, String[] projection,
                String where, String orderBy) {
            return cr.query(CONTENT_URI, projection,
                    where, null, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
        }

        /**
         * Helper method to extract email address from address string.
         * @hide
         */
        public static String extractAddrSpec(String address) {
            Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);

            if (match.matches()) {
                return match.group(2);
            }
            return address;
        }

        /**
         * Is the specified address an email address?
         *
         * @param address the input address to test
         * @return true if address is an email address; false otherwise.
         * @hide
         */
        public static boolean isEmailAddress(String address) {
            if (TextUtils.isEmpty(address)) {
                return false;
            }

            String s = extractAddrSpec(address);
            Matcher match = Patterns.EMAIL_ADDRESS.matcher(s);
            return match.matches();
        }

        /**
         * Is the specified number a phone number?
         *
         * @param number the input number to test
         * @return true if number is a phone number; false otherwise.
         * @hide
         */
        public static boolean isPhoneNumber(String number) {
            if (TextUtils.isEmpty(number)) {
                return false;
            }

            Matcher match = Patterns.PHONE.matcher(number);
            return match.matches();
        }

        /**
         * Contains all MMS messages in the MMS app inbox.
         */
        public static final class Inbox implements BaseMmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Inbox() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri
                    CONTENT_URI = Uri.parse("content://mms/inbox");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        /**
         * Contains all MMS messages in the MMS app sent folder.
         */
        public static final class Sent implements BaseMmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Sent() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri
                    CONTENT_URI = Uri.parse("content://mms/sent");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        /**
         * Contains all MMS messages in the MMS app drafts folder.
         */
        public static final class Draft implements BaseMmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Draft() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri
                    CONTENT_URI = Uri.parse("content://mms/drafts");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        /**
         * Contains all MMS messages in the MMS app outbox.
         */
        public static final class Outbox implements BaseMmsColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Outbox() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri
                    CONTENT_URI = Uri.parse("content://mms/outbox");

            /**
             * The default sort order for this table.
             */
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        /**
         * Contains address information for an MMS message.
         */
        public static final class Addr implements BaseColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Addr() {
            }

            /**
             * The ID of MM which this address entry belongs to.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String MSG_ID = "msg_id";

            /**
             * The ID of contact entry in Phone Book.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String CONTACT_ID = "contact_id";

            /**
             * The address text.
             * <P>Type: TEXT</P>
             */
            public static final String ADDRESS = "address";

            /**
             * Type of address: must be one of {@code PduHeaders.BCC},
             * {@code PduHeaders.CC}, {@code PduHeaders.FROM}, {@code PduHeaders.TO}.
             * <P>Type: INTEGER</P>
             */
            public static final String TYPE = "type";

            /**
             * Character set of this entry (MMS charset value).
             * <P>Type: INTEGER</P>
             */
            public static final String CHARSET = "charset";
        }

        /**
         * Contains message parts.
         */
        public static final class Part implements BaseColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private Part() {
            }

            /**
             * The identifier of the message which this part belongs to.
             * <P>Type: INTEGER</P>
             */
            public static final String MSG_ID = "mid";

            /**
             * The order of the part.
             * <P>Type: INTEGER</P>
             */
            public static final String SEQ = "seq";

            /**
             * The content type of the part.
             * <P>Type: TEXT</P>
             */
            public static final String CONTENT_TYPE = "ct";

            /**
             * The name of the part.
             * <P>Type: TEXT</P>
             */
            public static final String NAME = "name";

            /**
             * The charset of the part.
             * <P>Type: TEXT</P>
             */
            public static final String CHARSET = "chset";

            /**
             * The file name of the part.
             * <P>Type: TEXT</P>
             */
            public static final String FILENAME = "fn";

            /**
             * The content disposition of the part.
             * <P>Type: TEXT</P>
             */
            public static final String CONTENT_DISPOSITION = "cd";

            /**
             * The content ID of the part.
             * <P>Type: INTEGER</P>
             */
            public static final String CONTENT_ID = "cid";

            /**
             * The content location of the part.
             * <P>Type: INTEGER</P>
             */
            public static final String CONTENT_LOCATION = "cl";

            /**
             * The start of content-type of the message.
             * <P>Type: INTEGER</P>
             */
            public static final String CT_START = "ctt_s";

            /**
             * The type of content-type of the message.
             * <P>Type: TEXT</P>
             */
            public static final String CT_TYPE = "ctt_t";

            /**
             * The location (on filesystem) of the binary data of the part.
             * <P>Type: INTEGER</P>
             */
            public static final String _DATA = "_data";

            /**
             * The message text.
             * <P>Type: TEXT</P>
             */
            public static final String TEXT = "text";
        }

        /**
         * Message send rate table.
         */
        public static final class Rate {

            /**
             * Not instantiable.
             * @hide
             */
            private Rate() {
            }

            /**
             * The {@code content://} style URL for this table.
             */
            public static final Uri CONTENT_URI = Uri.withAppendedPath(
                    Mms.CONTENT_URI, "rate");

            /**
             * When a message was successfully sent.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String SENT_TIME = "sent_time";
        }

        // MTK-START
        /**
         * Temporarily file to pass to other app to use. For example, MMS application 
         * capture the image to camera app
         *
         * @internal
         * @hide
         */
        public static final class ScrapSpace {
            /**
             * The content:// style URL for this table
             * @internal
             */
            public static final Uri CONTENT_URI = Uri.parse("content://mms/scrapSpace");

            /**
             * This is the scrap file we use to store the media attachment when the user
             * chooses to capture a photo to be attached . We pass {#link@Uri} to the Camera app,
             * which streams the captured image to the uri. Internally we write the media content
             * to this file. It's named '.temp.jpg' so Gallery won't pick it up.
             */
            public static final String SCRAP_FILE_PATH = "/sdcard/mms/scrapSpace/.temp.jpg";
        }
        // MTK-END

        /**
         * Intents class.
         */
        public static final class Intents {

            /**
             * Not instantiable.
             * @hide
             */
            private Intents() {
            }

            /**
             * Indicates that the contents of specified URIs were changed.
             * The application which is showing or caching these contents
             * should be updated.
             */
            @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
            public static final String CONTENT_CHANGED_ACTION
                    = "android.intent.action.CONTENT_CHANGED";

            /**
             * An extra field which stores the URI of deleted contents.
             */
            public static final String DELETED_CONTENTS = "deleted_contents";
        }
    }

    /**
     * Contains all MMS and SMS messages.
     */
    public static final class MmsSms implements BaseColumns {

        /**
         * Not instantiable.
         * @hide
         */
        private MmsSms() {
        }

        /**
         * The column to distinguish SMS and MMS messages in query results.
         */
        public static final String TYPE_DISCRIMINATOR_COLUMN =
                "transport_type";

        /**
         * The {@code content://} style URL for this table.
         */
        public static final Uri CONTENT_URI = Uri.parse("content://mms-sms/");

        /**
         * The {@code content://} style URL for this table, by conversation.
         */
        public static final Uri CONTENT_CONVERSATIONS_URI = Uri.parse(
                "content://mms-sms/conversations");

        /**
         * The {@code content://} style URL for this table, by phone number.
         */
        public static final Uri CONTENT_FILTER_BYPHONE_URI = Uri.parse(
                "content://mms-sms/messages/byphone");

        /**
         * The {@code content://} style URL for undelivered messages in this table.
         */
        public static final Uri CONTENT_UNDELIVERED_URI = Uri.parse(
                "content://mms-sms/undelivered");

        /**
         * The {@code content://} style URL for draft messages in this table.
         */
        public static final Uri CONTENT_DRAFT_URI = Uri.parse(
                "content://mms-sms/draft");

        /**
         * The {@code content://} style URL for locked messages in this table.
         */
        public static final Uri CONTENT_LOCKED_URI = Uri.parse(
                "content://mms-sms/locked");

       // MTK-START
       /**
        * For the usage of native AP
        *
        * @internal
        * @hide
        */
        public static final Uri CONTENT_URI_QUICKTEXT = Uri.parse(
                "content://mms-sms/quicktext");
        // MTK-END

        /**
         * Pass in a query parameter called "pattern" which is the text to search for.
         * The sort order is fixed to be: {@code thread_id ASC, date DESC}.
         */
        public static final Uri SEARCH_URI = Uri.parse(
                "content://mms-sms/search");

        // Constants for message protocol types.

        /** SMS protocol type. */
        public static final int SMS_PROTO = 0;

        /** MMS protocol type. */
        public static final int MMS_PROTO = 1;

        // Constants for error types of pending messages.

        /** Error type: no error. */
        public static final int NO_ERROR                      = 0;

        /** Error type: generic transient error. */
        public static final int ERR_TYPE_GENERIC              = 1;

        /** Error type: SMS protocol transient error. */
        public static final int ERR_TYPE_SMS_PROTO_TRANSIENT  = 2;

        /** Error type: MMS protocol transient error. */
        public static final int ERR_TYPE_MMS_PROTO_TRANSIENT  = 3;

        /** Error type: transport failure. */
        public static final int ERR_TYPE_TRANSPORT_FAILURE    = 4;

        /** Error type: permanent error (along with all higher error values). */
        public static final int ERR_TYPE_GENERIC_PERMANENT    = 10;

        /** Error type: SMS protocol permanent error. */
        public static final int ERR_TYPE_SMS_PROTO_PERMANENT  = 11;

        /** Error type: MMS protocol permanent error. */
        public static final int ERR_TYPE_MMS_PROTO_PERMANENT  = 12;

        /**
         * Contains pending messages info.
         */
        public static final class PendingMessages implements BaseColumns {

            /**
             * Not instantiable.
             * @hide
             */
            private PendingMessages() {
            }

            public static final Uri CONTENT_URI = Uri.withAppendedPath(
                    MmsSms.CONTENT_URI, "pending");

            /**
             * The type of transport protocol (MMS or SMS).
             * <P>Type: INTEGER</P>
             */
            public static final String PROTO_TYPE = "proto_type";

            /**
             * The ID of the message to be sent or downloaded.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String MSG_ID = "msg_id";

            /**
             * The type of the message to be sent or downloaded.
             * This field is only valid for MM. For SM, its value is always set to 0.
             * <P>Type: INTEGER</P>
             */
            public static final String MSG_TYPE = "msg_type";

            /**
             * The type of the error code.
             * <P>Type: INTEGER</P>
             */
            public static final String ERROR_TYPE = "err_type";

            /**
             * The error code of sending/retrieving process.
             * <P>Type: INTEGER</P>
             */
            public static final String ERROR_CODE = "err_code";

            /**
             * How many times we tried to send or download the message.
             * <P>Type: INTEGER</P>
             */
            public static final String RETRY_INDEX = "retry_index";

            /**
             * The time to do next retry.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DUE_TIME = "due_time";

            /**
             * The time we last tried to send or download the message.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String LAST_TRY = "last_try";

            // MTK-START
            /**
             * Specify SIM identity for pending message
             *
             * @hide
             */
            public static final String SIM_ID = "pending_sim_id";            
            // MTK-END
        }

        /**
         * Words table used by provider for full-text searches.
         * @hide
         */
        public static final class WordsTable {

            /**
             * Not instantiable.
             * @hide
             */
            private WordsTable() {}

            /**
             * Primary key.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String ID = "_id";

            /**
             * Source row ID.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String SOURCE_ROW_ID = "source_id";

            /**
             * Table ID (either 1 or 2).
             * <P>Type: INTEGER</P>
             */
            public static final String TABLE_ID = "table_to_use";

            /**
             * The words to index.
             * <P>Type: TEXT</P>
             */
            public static final String INDEXED_TEXT = "index_text";
        }
    }

    /**
     * Carriers class contains information about APNs, including MMSC information.
     */
    public static final class Carriers implements BaseColumns {

        /**
         * Not instantiable.
         * @hide
         */
        private Carriers() {}

        /**
         * The {@code content://} style URL for this table.
         */
        public static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers");

        //MTK-START [mtk04070][111121][ALPS00093395]MTK added
        /**
         * Databae column for carriers dm
         * @internal
         * @hide
         */
        public static final Uri CONTENT_URI_DM =
            Uri.parse("content://telephony/carriers_dm");

        /* Add by mtk01411 for test purpose */
        /**
         * Databae column for carriers2
         * @internal
         * @hide
         */
        public static final Uri CONTENT_URI_2 =
            Uri.parse("content://telephony/carriers2");
        //MTK-END [mtk04070][111121][ALPS00093395]MTK added

        /**
         * The default sort order for this table.
         */
        public static final String DEFAULT_SORT_ORDER = "name ASC";

        /**
         * Entry name.
         * <P>Type: TEXT</P>
         */
        public static final String NAME = "name";

        /**
         * APN name.
         * <P>Type: TEXT</P>
         */
        public static final String APN = "apn";

        /**
         * Proxy address.
         * <P>Type: TEXT</P>
         */
        public static final String PROXY = "proxy";

        /**
         * Proxy port.
         * <P>Type: TEXT</P>
         */
        public static final String PORT = "port";

        /**
         * MMS proxy address.
         * <P>Type: TEXT</P>
         */
        public static final String MMSPROXY = "mmsproxy";

        /**
         * MMS proxy port.
         * <P>Type: TEXT</P>
         */
        public static final String MMSPORT = "mmsport";

        /**
         * Server address.
         * <P>Type: TEXT</P>
         */
        public static final String SERVER = "server";

        /**
         * APN username.
         * <P>Type: TEXT</P>
         */
        public static final String USER = "user";

        /**
         * APN password.
         * <P>Type: TEXT</P>
         */
        public static final String PASSWORD = "password";

        /**
         * MMSC URL.
         * <P>Type: TEXT</P>
         */
        public static final String MMSC = "mmsc";

        /**
         * Mobile Country Code (MCC).
         * <P>Type: TEXT</P>
         */
        public static final String MCC = "mcc";

        /**
         * Mobile Network Code (MNC).
         * <P>Type: TEXT</P>
         */
        public static final String MNC = "mnc";

        /**
         * Numeric operator ID (as String). Usually {@code MCC + MNC}.
         * <P>Type: TEXT</P>
         */
        public static final String NUMERIC = "numeric";

        /**
         * Authentication type.
         * <P>Type:  INTEGER</P>
         */
        public static final String AUTH_TYPE = "authtype";

        /**
         * Comma-delimited list of APN types.
         * <P>Type: TEXT</P>
         */
        public static final String TYPE = "type";

        /**
         * @hide
         */      
        public static final String INACTIVE_TIMER = "inactivetimer";

        /**
        * Only if enabled try Data Connection.
        * @hide 
        */
        public static final String ENABLED = "enabled";

        /**
        * Rules apply based on class.
        * @hide
        */        
        public static final String CLASS = "class";

        //MTK-START [mtk04070][111121][ALPS00093395]MTK added
        /**
         * @hide
         */              
        public static final String OMACPID = "omacpid";
        /**
         * @hide
         */              
        public static final String NAPID = "napid";
        /**
         * @hide
         */              
        public static final String PROXYID = "proxyid";
        /**
         * @hide
         */              
        public static final String SOURCE_TYPE = "sourcetype";
          /**
         * @hide
         */      
        public static final String CSD_NUM = "csdnum";      
        //MTK-END [mtk04070][111121][ALPS00093395]MTK added

        /**
         * The protocol to use to connect to this APN.
         *
         * One of the {@code PDP_type} values in TS 27.007 section 10.1.1.
         * For example: {@code IP}, {@code IPV6}, {@code IPV4V6}, or {@code PPP}.
         * <P>Type: TEXT</P>
         */
        public static final String PROTOCOL = "protocol";

        /**
         * The protocol to use to connect to this APN when roaming.
         * The syntax is the same as protocol.
         * <P>Type: TEXT</P>
         */
        public static final String ROAMING_PROTOCOL = "roaming_protocol";

        /**
         * Is this the current APN?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String CURRENT = "current";

        /**
         * Is this APN enabled?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String CARRIER_ENABLED = "carrier_enabled";

        /**
         * Radio Access Technology info.
         * To check what values are allowed, refer to {@link android.telephony.ServiceState}.
         * This should be spread to other technologies,
         * but is currently only used for LTE (14) and eHRPD (13).
         * <P>Type: INTEGER</P>
         */
        public static final String BEARER = "bearer";

        /**
         * MVNO type:
         * {@code SPN (Service Provider Name), IMSI, GID (Group Identifier Level 1)}.
         * <P>Type: TEXT</P>
         */
        public static final String MVNO_TYPE = "mvno_type";

        /**
         * MVNO data.
         * Use the following examples.
         * <ul>
         *     <li>SPN: A MOBILE, BEN NL, ...</li>
         *     <li>IMSI: 302720x94, 2060188, ...</li>
         *     <li>GID: 4E, 33, ...</li>
         * </ul>
         * <P>Type: TEXT</P>
         */
        public static final String MVNO_MATCH_DATA = "mvno_match_data";

        //MTK-START [mtk04070][111121][ALPS00093395]MTK added
          /**
         * @hide
         */          
        public static final String SPN = "spn";
          /**
         * @hide
         */          
        public static final String IMSI = "imsi";
          /**
         * @hide
         */          
        public static final String PNN = "pnn";

        // M: CDMA ppp column support
          /**
         * @hide
         */          
        public static final String PPP = "ppp";
        
          /**
         * @hide
         */  
        public static final class GeminiCarriers {
            /**
             * Databae column for Gemini carriers 
             * no using "at internal" tag due to geminiCarriers class should be 
             * removed in the future while gemini+ support DM
             */
            public static final  Uri CONTENT_URI =
                Uri.parse("content://telephony/carriers_gemini");

            /**
             * Databae column for Gemini carriers dm
             * no using "at internal" tag due to geminiCarriers class should be 
             * removed in the future while gemini+ support DM
             * @hide
             */
            public static final  Uri CONTENT_URI_DM =
                Uri.parse("content://telephony/carriers_dm_gemini"); 
        }

          /**
         * @hide
         */          
        public static final class SIM1Carriers {
            /**
             * Databae column for SIM1 carriers (gemini+)
             * @internal
             */
            public static final  Uri CONTENT_URI =
                Uri.parse("content://telephony/carriers_sim1"); 
        }

          /**
         * @hide
         */            
        public static final class SIM2Carriers {
            /**
             * Databae column for SIM2 carriers (gemini+)
             * @internal
             */
            public static final  Uri CONTENT_URI =
                Uri.parse("content://telephony/carriers_sim2"); 
        }

          /**
         * @hide
         */            
        public static final class SIM3Carriers {
            /**
             * Databae column for SIM3 carriers (gemini+)
             * @internal
             */
            public static final  Uri CONTENT_URI =
                Uri.parse("content://telephony/carriers_sim3"); 
        }
          /**
         * @hide
         */            
        public static final class SIM4Carriers {
            /**
             * Databae column for SIM4 carriers (gemini+)
             * @internal
             */
            public static final  Uri CONTENT_URI =
                Uri.parse("content://telephony/carriers_sim4"); 
        }
        //MTK-END [mtk04070][111121][ALPS00093395]MTK added

    }

    //MTK-START [mtk04070][111121][ALPS00093395]MTK added
          /**
         * @hide
         */      
    public static final class SimInfo implements BaseColumns{
        /** @internal */
        public static final Uri CONTENT_URI = 
            Uri.parse("content://telephony/siminfo");

        /** @internal */
        public static final String DEFAULT_SORT_ORDER = "name ASC";
        
        /**
         * <P>Type: TEXT</P>
         * @internal
         */
        public static final String ICC_ID = "icc_id";
        /**
         * <P>Type: TEXT</P>
         * @internal
         */
        public static final String DISPLAY_NAME = "display_name";
        /** @internal */
        public static final int DEFAULT_NAME_MIN_INDEX = 01;
        /** @internal */
        public static final int DEFAULT_NAME_MAX_INDEX= 99;
        /** @internal */
        public static final int DEFAULT_NAME_RES = com.mediatek.internal.R.string.new_sim;

        /**
         * <P>Type: INT</P>
         * @internal
         */
        public static final String NAME_SOURCE = "name_source";
        /** @internal */
        public static final int DEFAULT_SOURCE = 0;
        /** @internal */
        public static final int SIM_SOURCE = 1;
        /** @internal */
        public static final int USER_INPUT = 2;

        /**
         * <P>Type: TEXT</P>
         * @internal
         */
        public static final String NUMBER = "number";
        
        /**
         * 0:none, 1:the first four digits, 2:the last four digits.
         * <P>Type: INTEGER</P>
         * @internal
         */
        public static final String DISPLAY_NUMBER_FORMAT = "display_number_format";
        /** @internal */
        public static final int DISPALY_NUMBER_NONE = 0;
        /** @internal */
        public static final int DISPLAY_NUMBER_FIRST = 1;
        /** @internal */
        public static final int DISPLAY_NUMBER_LAST = 2;
        /** @internal */
        public static final int DISLPAY_NUMBER_DEFAULT = DISPLAY_NUMBER_FIRST;
        
        /**
         * Eight kinds of colors. 0-3 will represent the eight colors.
         * Default value: any color that is not in-use.
         * <P>Type: INTEGER</P>
         * @internal
         */
        public static final String COLOR = "color";
        /** @internal */
        public static final int COLOR_1 = 0;
        /** @internal */
        public static final int COLOR_2 = 1;
        /** @internal */
        public static final int COLOR_3 = 2;
        /** @internal */
        public static final int COLOR_4 = 3;
        /** @internal */
        public static final int COLOR_DEFAULT = COLOR_1;
        
        /**
         * 0: Don't allow data when roaming, 1:Allow data when roaming
         * <P>Type: INTEGER</P>
         * @internal
         */
        public static final String DATA_ROAMING = "data_roaming";
        /** @internal */
        public static final int DATA_ROAMING_ENABLE = 1;
        /** @internal */
        public static final int DATA_ROAMING_DISABLE = 0;
        /** @internal */
        public static final int DATA_ROAMING_DEFAULT = DATA_ROAMING_DISABLE;
        
        /**
         * <P>Type: INTEGER</P>
         * @internal
         */
        public static final String SLOT = "slot";
        /** @internal */
        public static final int SLOT_NONE = -1;
        
        public static final int ERROR_GENERAL = -1;
        public static final int ERROR_NAME_EXIST = -2;

        /**
         * <P>Type: TEXT</P>
         * @internal
         */
        public static final String OPERATOR = "operator";
        /** @internal */
        public static final String OPERATOR_OP01 = "OP01";
        /** @internal */
        public static final String OPERATOR_OP02 = "OP02";
        /** @internal */
        public static final String OPERATOR_OP09 = "OP09";
        /** @internal */
        public static final String OPERATOR_OTHERS = "others";
        
    }

    /**
     * A SIMInfo instance represent one record in siminfo database
     * @param mSimId SIM index in database
     * @param mICCId SIM IccId string
     * @param mDisplayName SIM display name shown in SIM management, can be modified by user
     * @param mDefaultName SIM default name shown in SIM management, can not modified by user
     * @param mNameSource Source of mDisplayName, 0: default source, 1: SIM source, 2: user source
     * @param mNumber Phone number string
     * @param mDispalyNumberFormat Display format of mNumber, 0: display none, 1: display number first, 2: display number last
     * @param mColor SIM color, 0: blue, 1: oprange, 2: green, 3: purple
     * @param mDataRoaming Data Roaming enable/disable status, 0: Don't allow data when roaming, 1:Allow data when roaming
     * @param mSlot SIM in slot, 0: SIM1, 1: SIM2, 2: SIM3, 3: SIM4
     * @param mOperator SIM operator
     * @hide
     */
    public static class SIMInfo {
        private static final String LOG_TAG = "PHONE";
        /** @internal */
        public long mSimId;
        /** @internal */
        public String mICCId;
        /** @internal */
        public String mDisplayName = "";
        /** @internal */
        public String mDefaultName = "";
        /** @internal */
        public int mNameSource;
        /** @internal */
        public String mNumber = "";
        /** @internal */
        public int mDispalyNumberFormat = SimInfo.DISLPAY_NUMBER_DEFAULT;
        /** @internal */
        public int mColor;
        /** @internal */
        public int mDataRoaming = SimInfo.DATA_ROAMING_DEFAULT;
        /** @internal */
        public int mSlot = SimInfo.SLOT_NONE;
        /** @internal */
        public String mOperator = "";

        private SIMInfo() {
        }
          /**
         * @hide
         */         
        public static class ErrorCode {
            public static final int ERROR_GENERAL = -1;
            public static final int ERROR_NAME_EXIST = -2;
        }

        /**
         * Get the SIMInfo(s) of the currently inserted SIM(s)
         * @param ctx Context provided by caller
         * @return the array list of Current SIM Info
         * @internal
         */
        public static List<SIMInfo> getInsertedSIMList(Context ctx) {
            logd("[getInsertedSIMList]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getInsertedSimInfoListAdp", Context.class);
                if (method != null) {
                    List<SIMInfo> simList = (List<SIMInfo>)method.invoke(null, ctx);
                    return simList;
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getInsertedSimInfoListAdp");
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Should only be used by SimInfoManagerAdp
         * @return a SIMInfo instance
         * @internal
         */
        public static SIMInfo getSIMInfoInstance() {
            logd("[getSIMInfoInstance]");
            SIMInfo info = new SIMInfo();
            return info;
        }
 
        /**
         * Get all the SIMInfo(s) in siminfo database
         * @param ctx Context provided by caller
         * @return array list of all the SIM Info include what were used before
         * @internal
         */
        public static List<SIMInfo> getAllSIMList(Context ctx) {
            logd("[getAllSIMList]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getAllSimInfoListAdp", Context.class);
                if (method != null) {
                    List<SIMInfo> simList = (List<SIMInfo>)method.invoke(null, ctx);
                    return simList;
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getAllSimInfoListAdp");
                e.printStackTrace();
            }
            return null;
        }
        
        /**
         * Get the SimInfoRecord according to an index
         * @param ctx Context provided by caller
         * @param SIMId the unique SIM id
         * @return SIM-Info, maybe null
         * @internal
         */
        public static SIMInfo getSIMInfoById(Context ctx, long SIMId) {
            logd("[getSIMInfoById]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getSimInfoByIdAdp", Context.class, long.class);
                if (method != null) {
                    SIMInfo info = (SIMInfo)method.invoke(null, ctx, SIMId);
                    return info;
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getSimInfoByIdAdp");
                e.printStackTrace();
            }
            return null;
        }
        
        /**
         * Get the SimInfoRecord according to a SIM display name
         * @param ctx Context provided by caller
         * @param SIMName the Name of the SIM Card
         * @return SIM-Info, maybe null
         * @internal
         */
        public static SIMInfo getSIMInfoByName(Context ctx, String SIMName) {
            logd("[getSIMInfoByName]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getSimInfoByNameAdp", Context.class, String.class);
                if (method != null) {
                    SIMInfo info = (SIMInfo)method.invoke(null, ctx, SIMName);
                    return info;
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getSimInfoByNameAdp");
                e.printStackTrace();
            }
            return null;
        }
        
        /**
         * Get the SimInfoRecord according to slot
         * @param ctx Context provided by caller
         * @param cardSlot SIM in slot
         * @return The SIM-Info, maybe null
         * @internal
         */
        public static SIMInfo getSIMInfoBySlot(Context ctx, int cardSlot) {
            logd("[getSIMInfoBySlot]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getSimInfoBySlotAdp", Context.class, int.class);
                if (method != null) {
                    SIMInfo info = (SIMInfo)method.invoke(null, ctx, cardSlot);
                    return info;
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getSimInfoBySlotAdp");
                e.printStackTrace();
            }
            return null;
        }
        
        /**
         * Get the SimInfoRecord according to an IccId
         * @param ctx Context provided by caller
         * @param iccid SIM IccId
         * @return The SIM-Info, maybe null
         * @internal
         */
        public static SIMInfo getSIMInfoByICCId(Context ctx, String iccid) {
            logd("[getSIMInfoByICCId]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getSimInfoByIccIdAdp", Context.class, String.class);
                if (method != null) {
                    SIMInfo info = (SIMInfo)method.invoke(null, ctx, iccid);
                    return info;
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getSimInfoByIccIdAdp");
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Get the SIM count of currently inserted SIM(s)
         * @param ctx Context provided by caller
         * @return current SIM Count
         * @internal
         */
        public static int getInsertedSIMCount(Context ctx) {
            logd("[getInsertedSIMCount]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getInsertedSimCountAdp", Context.class);
                if (method != null) {
                    Integer count = (Integer)method.invoke(null, ctx);
                    return count.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getInsertedSimCountAdp");
                e.printStackTrace();
            }
            return -1;
        }
        
        /**
         * Get the SIM count of all SIM(s) in siminfo database
         * @param ctx
         * @return the count of all the SIM Card include what was used before
         * @internal
         */
        public static int getAllSIMCount(Context ctx) {
            logd("[getAllSIMCount]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("getAllSimCountAdp", Context.class);
                if (method != null) {
                    Integer count = (Integer)method.invoke(null, ctx);
                    return count.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for getAllSimCountAdp");
                e.printStackTrace();
            }
            return -1;
        }

        public static int setOperatorById(Context ctx, String operator, long simId) {
            logd("[setOperatorById]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setOperatorByIdAdp", Context.class, String.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, operator, simId);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setOperatorByIdAdp");
                e.printStackTrace();
            }
            return -1;
        }
        
        /**
         * Set display name by SIM ID
         * @param ctx Context provided by caller
         * @param displayName SIM dispaly name to set
         * @param SIMId SIM index in database
         * @return -1 means general error, -2 means the name is exist. >0 means success
         * @internal
         */
        public static int setDisplayName(Context ctx, String displayName, long SIMId) {
            logd("[setDisplayName]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setDisplayNameAdp", Context.class, String.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, displayName, SIMId);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setDisplayNameAdp");
                e.printStackTrace();
            }
            return -1;
        }

        /**
         * Set display name by SIM ID with name source
         * @param ctx Context provided by caller
         * @param displayName SIM dispaly name to set
         * @param SIMId SIM index in database
         * @param Source, ex, SYSTEM_INPUT, USER_INPUT
         * @return -1 means general error, -2 means the name is exist. >0 means success
         * @internal
         */
        public static int setDisplayNameEx(Context ctx, String displayName, long SIMId, long Source) {
            logd("[setDisplayNameEx]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setDisplayNameExAdp", Context.class, String.class, long.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, displayName, SIMId, Source);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setDisplayNameExAdp");
                e.printStackTrace();
            }
            return -1;
        }

        /**
         * Set phone number by SIMId
         * @param ctx Context provided by caller
         * @param number Number to set
         * @param SIMId SIM index in database
         * @return >0 means success
         * @internal
         */
        public static int setNumber(Context ctx, String number, long SIMId) {
            logd("[setNumber]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setNumberAdp", Context.class, String.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, number, SIMId);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setNumberAdp");
                e.printStackTrace();
            }
            return -1;
        }
        
        /**
         * Set SIM color by SIMId
         * @param ctx Context provided by caller
         * @param color SIM color
         * @param SIMId SIM index in database
         * @return >0 means success
         * @internal
         */
        public static int setColor(Context ctx, int color, long SIMId) {
            logd("[setColor]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setColorAdp", Context.class, int.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, color, SIMId);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setColorAdp");
                e.printStackTrace();
            }
            return -1;
        }
        
        /**
         * Set the format
         * @param ctx Context provided by caller
         * @param format 0: none, 1: the first four digits, 2: the last four digits
         * @param SIMId SIM index in database
         * @return >0 means success
         * @internal
         */
        public static int setDispalyNumberFormat(Context ctx, int format, long SIMId) {
            logd("[setDispalyNumberFormat]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setDispalyNumberFormatAdp", Context.class, int.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, format, SIMId);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setDispalyNumberFormatAdp");
                e.printStackTrace();
            }
            return -1;
        }
        
        /**
         * Set data roaming
         * @param ctx Context provided by caller
         * @param roaming 0:Don't allow data when roaming, 1:Allow data when roaming
         * @param SIMId SIM index in database
         * @return >0 means success
         * @internal
         */
        public static int setDataRoaming(Context ctx, int roaming, long SIMId) {
            logd("[setDataRoaming]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setDataRoamingAdp", Context.class, int.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, roaming, SIMId);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setDataRoamingAdp");
                e.printStackTrace();
            }
            return -1;
        }
        
        /**
         * Insert the ICC ID and slot if needed
         * @param ctx Context provided by caller
         * @param ICCId SIM IccId
         * @param slot SIM in slot
         * @return Uri if success
         * @internal
         */
        public static Uri insertICCId(Context ctx, String ICCId, int slot) {
            logd("[insertICCId]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("addSimInfoRecordAdp", Context.class, String.class, int.class);
                if (method != null) {
                    Uri result = (Uri)method.invoke(null, ctx, ICCId, slot);
                    return result;
                }
            } catch (Exception e) {
                logd("createInstance:got exception for insertIccIdAdp");
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Set SIM default name
         * @param ctx Context provided by caller
         * @param simId SIM index in database
         * @param name SIM name to set
         * @internal
         */
        public static int setDefaultName(Context ctx, long simId, String name) {
            logd("[setDefaultName]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setDefaultNameAdp", Context.class, long.class, String.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, simId, name);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setDefaultNameAdp");
                e.printStackTrace();
            }
            return -1;
        }

        /**
         * Set SIM default name with name source
         * @param ctx Context provided by caller
         * @param simId SIM index in database
         * @param name SIM name to set
         * @param nameSource SIM name source
         * @internal
         */
        public static int setDefaultNameEx(Context ctx, long simId, String name, long nameSource) {
            logd("[setDefaultNameEx]");
            try {
                Class<?> clz = Class.forName("com.mediatek.telephony.SimInfoManagerAdp");
                Method method = clz.getMethod("setDefaultNameExAdp", Context.class, long.class, String.class, long.class);
                if (method != null) {
                    Integer result = (Integer)method.invoke(null, ctx, simId, name, nameSource);
                    return result.intValue();
                }
            } catch (Exception e) {
                logd("createInstance:got exception for setDefaultNameExAdp");
                e.printStackTrace();
            }
            return -1;
        }

        private static void logd(String msg) {
            Rlog.d(LOG_TAG, "[SIMInfo]" + msg);
        }
    }

          /**
         * @hide
         */      
    public static final class GprsInfo implements BaseColumns{
        /**
         * Databae column for GprsInfo
         * @internal
         */
        public static final Uri CONTENT_URI = 
            Uri.parse("content://telephony/gprsinfo");
                
        /**
         * <P>Type: INTEGER</P>
         */
        public static final String SIM_ID = "sim_id";
        /**
         * <P>Type: INTEGER</P>
         */
        public static final String GPRS_IN = "gprs_in";
        /**
         * <P>Type: INTEGER</P>
         */
        public static final String GPRS_OUT = "gprs_out";
    }

          /**
         * @hide
         */      
    public static final class GPRSInfo {
        public long mSimId = 0;
        public long mGprsIn = 0;
        public long mGprsOut = 0;
        private GPRSInfo () {
            
        }
        private static GPRSInfo fromCursor (Cursor cursor) {
            GPRSInfo info = new GPRSInfo();
            info.mSimId = cursor.getLong(cursor.getColumnIndexOrThrow(GprsInfo.SIM_ID));
            info.mGprsIn = cursor.getLong(cursor.getColumnIndexOrThrow(GprsInfo.GPRS_IN));
            info.mGprsOut = cursor.getLong(cursor.getColumnIndexOrThrow(GprsInfo.GPRS_OUT));
            return info;
        }
        
        public static long getGprsInBySim(Context context, long simId) {
            if (simId <= 0) return 0;
            Cursor cursor = context.getContentResolver().query(GprsInfo.CONTENT_URI, 
                    new String[]{GprsInfo.GPRS_IN}, GprsInfo.SIM_ID + "=" + simId, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return 0;
        }
        
        public static long getGprsOutBySim(Context context, long simId) {
            if (simId <= 0) return 0;
            Cursor cursor = context.getContentResolver().query(GprsInfo.CONTENT_URI, 
                    new String[]{GprsInfo.GPRS_OUT}, GprsInfo.SIM_ID + "=" + simId, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return 0;
        }
        
        public static GPRSInfo getGprsInfoBySim(Context context, long simId) {
            GPRSInfo info = new GPRSInfo();
            if (simId <= 0) return info;
            Cursor cursor = context.getContentResolver().query(GprsInfo.CONTENT_URI, 
                    null, GprsInfo.SIM_ID + "=" + simId, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    return GPRSInfo.fromCursor(cursor);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return info;
        }
        
        public static int resetGprsBySim(Context context, long simId) {
            if (simId <= 0) return 0;
            ContentValues values = new ContentValues(2);
            values.put(GprsInfo.GPRS_IN, 0);
            values.put(GprsInfo.GPRS_OUT, 0);
            return context.getContentResolver().update(GprsInfo.CONTENT_URI, values, GprsInfo.SIM_ID + "=" + simId, null);
        }
    }
    //MTK-END [mtk04070][111121][ALPS00093395]MTK added

    /**
     * Contains received SMS cell broadcast messages.
     * @hide
     */
    public static final class CellBroadcasts implements BaseColumns {

        /**
         * Not instantiable.
         * @hide
         */
        private CellBroadcasts() {}

        /**
         * The {@code content://} URI for this table.
         */
        public static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts");

        /**
         * Message geographical scope.
         * <P>Type: INTEGER</P>
         */
        public static final String GEOGRAPHICAL_SCOPE = "geo_scope";

        /**
         * Message serial number.
         * <P>Type: INTEGER</P>
         */
        public static final String SERIAL_NUMBER = "serial_number";

        /**
         * PLMN of broadcast sender. {@code SERIAL_NUMBER + PLMN + LAC + CID} uniquely identifies
         * a broadcast for duplicate detection purposes.
         * <P>Type: TEXT</P>
         */
        public static final String PLMN = "plmn";

        /**
         * Location Area (GSM) or Service Area (UMTS) of broadcast sender. Unused for CDMA.
         * Only included if Geographical Scope of message is not PLMN wide (01).
         * <P>Type: INTEGER</P>
         */
        public static final String LAC = "lac";

        /**
         * Cell ID of message sender (GSM/UMTS). Unused for CDMA. Only included when the
         * Geographical Scope of message is cell wide (00 or 11).
         * <P>Type: INTEGER</P>
         */
        public static final String CID = "cid";

        /**
         * Message code. <em>OBSOLETE: merged into SERIAL_NUMBER.</em>
         * <P>Type: INTEGER</P>
         */
        public static final String V1_MESSAGE_CODE = "message_code";

        /**
         * Message identifier. <em>OBSOLETE: renamed to SERVICE_CATEGORY.</em>
         * <P>Type: INTEGER</P>
         */
        public static final String V1_MESSAGE_IDENTIFIER = "message_id";

        /**
         * Service category (GSM/UMTS: message identifier; CDMA: service category).
         * <P>Type: INTEGER</P>
         */
        public static final String SERVICE_CATEGORY = "service_category";

        /**
         * Message language code.
         * <P>Type: TEXT</P>
         */
        public static final String LANGUAGE_CODE = "language";

        /**
         * Message body.
         * <P>Type: TEXT</P>
         */
        public static final String MESSAGE_BODY = "body";

        /**
         * Message delivery time.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DELIVERY_TIME = "date";

        /**
         * Has the message been viewed?
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String MESSAGE_READ = "read";

        // MTK-START
        /**
         * Sepcify SIM identity for cell braodcast message
         * The sim card id the messge come from.
         * <p>Type: INTEGER</p>
         *
         * @hide
         */
        public static final String SIM_ID = "sim_id";
        // MTK-END

        /**
         * Message format (3GPP or 3GPP2).
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_FORMAT = "format";

        /**
         * Message priority (including emergency).
         * <P>Type: INTEGER</P>
         */
        public static final String MESSAGE_PRIORITY = "priority";

        /**
         * ETWS warning type (ETWS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String ETWS_WARNING_TYPE = "etws_warning_type";

        /**
         * CMAS message class (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_MESSAGE_CLASS = "cmas_message_class";

        /**
         * CMAS category (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_CATEGORY = "cmas_category";

        /**
         * CMAS response type (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_RESPONSE_TYPE = "cmas_response_type";

        /**
         * CMAS severity (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_SEVERITY = "cmas_severity";

        /**
         * CMAS urgency (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_URGENCY = "cmas_urgency";

        /**
         * CMAS certainty (CMAS alerts only).
         * <P>Type: INTEGER</P>
         */
        public static final String CMAS_CERTAINTY = "cmas_certainty";

        /** The default sort order for this table. */
        public static final String DEFAULT_SORT_ORDER = DELIVERY_TIME + " DESC";

        /**
         * Query columns for instantiating {@link android.telephony.CellBroadcastMessage} objects.
         */
        public static final String[] QUERY_COLUMNS = {
                _ID,
                GEOGRAPHICAL_SCOPE,
                PLMN,
                LAC,
                CID,
                SERIAL_NUMBER,
                SERVICE_CATEGORY,
                LANGUAGE_CODE,
                MESSAGE_BODY,
                DELIVERY_TIME,
                MESSAGE_READ,
                SIM_ID, // MTK-add
                MESSAGE_FORMAT,
                MESSAGE_PRIORITY,
                ETWS_WARNING_TYPE,
                CMAS_MESSAGE_CLASS,
                CMAS_CATEGORY,
                CMAS_RESPONSE_TYPE,
                CMAS_SEVERITY,
                CMAS_URGENCY,
                CMAS_CERTAINTY
        };
    }
    
          /**
         * @hide
         */  
    public static final class Intents {
        private Intents() {
            // Not instantiable
        }

        /**
         * Broadcast Action: A "secret code" has been entered in the dialer. Secret codes are
         * of the form *#*#<code>#*#*. The intent will have the data URI:</p>
         *
         * <p><code>android_secret_code://&lt;code&gt;</code></p>
         */
        public static final String SECRET_CODE_ACTION =
                "android.provider.Telephony.SECRET_CODE";

        /**
         * Broadcast Action: The Service Provider string(s) have been updated.  Activities or
         * services that use these strings should update their display.
         * The intent will have the following extra values:</p>
         * <ul>
         *   <li><em>showPlmn</em> - Boolean that indicates whether the PLMN should be shown.</li>
         *   <li><em>plmn</em> - The operator name of the registered network, as a string.</li>
         *   <li><em>showSpn</em> - Boolean that indicates whether the SPN should be shown.</li>
         *   <li><em>spn</em> - The service provider name, as a string.</li>
         * </ul>
         * Note that <em>showPlmn</em> may indicate that <em>plmn</em> should be displayed, even
         * though the value for <em>plmn</em> is null.  This can happen, for example, if the phone
         * has not registered to a network yet.  In this case the receiver may substitute an
         * appropriate placeholder string (eg, "No service").
         *
         * It is recommended to display <em>plmn</em> before / above <em>spn</em> if
         * both are displayed.
         *
         * <p>Note this is a protected intent that can only be sent
         * by the system.
         */
        public static final String SPN_STRINGS_UPDATED_ACTION =
                "android.provider.Telephony.SPN_STRINGS_UPDATED";

        public static final String EXTRA_SHOW_PLMN  = "showPlmn";
        public static final String EXTRA_PLMN       = "plmn";
        public static final String EXTRA_SHOW_SPN   = "showSpn";
        public static final String EXTRA_SPN        = "spn";
        // Femtocell (CSG) START
        public static final String EXTRA_HNB_NAME   = "hnbName";
        public static final String EXTRA_CSG_ID     = "csgId";
        public static final String EXTRA_DOMAIN     = "domain";	
        // Femtocell (CSG) END

        //MTK-START [mtk04070][111121][ALPS00093395]MTK added
        /**
         * Broadcast Action: Notify AP to pop up dual SIM mode selection menu for user
         *
         * <p>Note this is a protected intent that can only be sent
         * by the system.
         */
        public static final String ACTION_DUAL_SIM_MODE_SELECT =
                "android.provider.Telephony.DUAL_SIM_MODE_SELECT";

        /**
         * Broadcast Action: Notify AP to pop up GPRS selection menu for user
         *
         * <p>Note this is a protected intent that can only be sent
         * by the system.
         */
        public static final String ACTION_GPRS_CONNECTION_TYPE_SELECT =
                "android.provider.Telephony.GPRS_CONNECTION_TYPE_SELECT";
        /**
         * Broadcast Action: Unlock keyguard for user
         *
         * <p>Note this is a protected intent that can only be sent
         * by the system.
         */
        public static final String ACTION_UNLOCK_KEYGUARD =
                "android.provider.Telephony.UNLOCK_KEYGUARD";
        //MTK-END [mtk04070][111121][ALPS00093395]MTK added
        
        /**
        * @hide
        */
        public static final String ACTION_REMOVE_IDLE_TEXT = "android.intent.aciton.stk.REMOVE_IDLE_TEXT";
        
        /**
        * @hide
        */
        public static final String ACTION_REMOVE_IDLE_TEXT_2 = "android.intent.aciton.stk.REMOVE_IDLE_TEXT_2";
    }

    // MTK-START
    /**
     * WapPush table columns
     *
     * @internal
     * @hide
     */
    public static final class WapPush implements BaseColumns{
        
        //public static final Uri CONTENT_URI = 
        public static final String DEFAULT_SORT_ORDER = "date ASC";

        /**
        * @internal
        */
        public static final Uri CONTENT_URI = Uri.parse("content://wappush");

        /**
        * @internal
        */
        public static final Uri CONTENT_URI_SI = Uri.parse("content://wappush/si");

        /**
        * @internal
        */
        public static final Uri CONTENT_URI_SL = Uri.parse("content://wappush/sl");

        /**
        * @internal
        */
        public static final Uri CONTENT_URI_THREAD = Uri.parse("content://wappush/thread_id");
        
        //Database Columns
        public static final String THREAD_ID = "thread_id";
        public static final String ADDR = "address";
        public static final String SERVICE_ADDR = "service_center";
        public static final String READ = "read";
        public static final String SEEN = "seen";
        public static final String LOCKED = "locked";
        public static final String ERROR = "error";
        public static final String DATE = "date";
        public static final String TYPE = "type";
        public static final String SIID = "siid";
        public static final String URL = "url";
        public static final String CREATE = "created";
        public static final String EXPIRATION = "expiration";
        public static final String ACTION = "action";
        public static final String TEXT = "text";
        public static final String SIM_ID = "sim_id";
        
        //
        public static final int TYPE_SI = 0;
        public static final int TYPE_SL = 1;
        
        public static final int STATUS_SEEN = 1;
        public static final int STATUS_UNSEEN = 0;
        
        public static final int STATUS_READ = 1;
        public static final int STATUS_UNREAD = 0;
        
        public static final int STATUS_LOCKED = 1;
        public static final int STATUS_UNLOCKED = 0;
    }
    // MTK-END
}
