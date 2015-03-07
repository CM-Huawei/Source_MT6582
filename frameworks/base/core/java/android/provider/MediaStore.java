/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MiniThumbFile;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.mediatek.xlog.Xlog;

/**
 * The Media provider contains meta data for all available media on both internal
 * and external storage devices.
 */
public final class MediaStore {
    private final static String TAG = "MediaStore";

    public static final String AUTHORITY = "media";

    private static final String CONTENT_AUTHORITY_SLASH = "content://" + AUTHORITY + "/";

   /**
     * Broadcast Action:  A broadcast to indicate the end of an MTP session with the host.
     * This broadcast is only sent if MTP activity has modified the media database during the
     * most recent MTP session.
     *
     * @hide
     */
    public static final String ACTION_MTP_SESSION_END = "android.provider.action.MTP_SESSION_END";

    /**
     * The method name used by the media scanner and mtp to tell the media provider to
     * rescan and reclassify that have become unhidden because of renaming folders or
     * removing nomedia files
     * @hide
     */
    public static final String UNHIDE_CALL = "unhide";

    /**
     * This is for internal use by the media scanner only.
     * Name of the (optional) Uri parameter that determines whether to skip deleting
     * the file pointed to by the _data column, when deleting the database entry.
     * The only appropriate value for this parameter is "false", in which case the
     * delete will be skipped. Note especially that setting this to true, or omitting
     * the parameter altogether, will perform the default action, which is different
     * for different types of media.
     * @hide
     */
    public static final String PARAM_DELETE_DATA = "deletedata";

    /**
     * Activity Action: Launch a music player.
     * The activity should be able to play, browse, or manipulate music files stored on the device.
     *
     * @deprecated Use {@link android.content.Intent#CATEGORY_APP_MUSIC} instead.
     */
    @Deprecated
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_MUSIC_PLAYER = "android.intent.action.MUSIC_PLAYER";

    /**
     * Activity Action: Perform a search for media.
     * Contains at least the {@link android.app.SearchManager#QUERY} extra.
     * May also contain any combination of the following extras:
     * EXTRA_MEDIA_ARTIST, EXTRA_MEDIA_ALBUM, EXTRA_MEDIA_TITLE, EXTRA_MEDIA_FOCUS
     *
     * @see android.provider.MediaStore#EXTRA_MEDIA_ARTIST
     * @see android.provider.MediaStore#EXTRA_MEDIA_ALBUM
     * @see android.provider.MediaStore#EXTRA_MEDIA_TITLE
     * @see android.provider.MediaStore#EXTRA_MEDIA_FOCUS
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_MEDIA_SEARCH = "android.intent.action.MEDIA_SEARCH";

    /**
     * An intent to perform a search for music media and automatically play content from the
     * result when possible. This can be fired, for example, by the result of a voice recognition
     * command to listen to music.
     * <p>
     * Contains the {@link android.app.SearchManager#QUERY} extra, which is a string
     * that can contain any type of unstructured music search, like the name of an artist,
     * an album, a song, a genre, or any combination of these.
     * <p>
     * Because this intent includes an open-ended unstructured search string, it makes the most
     * sense for apps that can support large-scale search of music, such as services connected
     * to an online database of music which can be streamed and played on the device.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    /**
     * An intent to perform a search for readable media and automatically play content from the
     * result when possible. This can be fired, for example, by the result of a voice recognition
     * command to read a book or magazine.
     * <p>
     * Contains the {@link android.app.SearchManager#QUERY} extra, which is a string that can
     * contain any type of unstructured text search, like the name of a book or magazine, an author
     * a genre, a publisher, or any combination of these.
     * <p>
     * Because this intent includes an open-ended unstructured search string, it makes the most
     * sense for apps that can support large-scale search of text media, such as services connected
     * to an online database of books and/or magazines which can be read on the device.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_TEXT_OPEN_FROM_SEARCH =
            "android.media.action.TEXT_OPEN_FROM_SEARCH";

    /**
     * An intent to perform a search for video media and automatically play content from the
     * result when possible. This can be fired, for example, by the result of a voice recognition
     * command to play movies.
     * <p>
     * Contains the {@link android.app.SearchManager#QUERY} extra, which is a string that can
     * contain any type of unstructured video search, like the name of a movie, one or more actors,
     * a genre, or any combination of these.
     * <p>
     * Because this intent includes an open-ended unstructured search string, it makes the most
     * sense for apps that can support large-scale search of video, such as services connected to an
     * online database of videos which can be streamed and played on the device.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_VIDEO_PLAY_FROM_SEARCH =
            "android.media.action.VIDEO_PLAY_FROM_SEARCH";

    /**
     * The name of the Intent-extra used to define the artist
     */
    public static final String EXTRA_MEDIA_ARTIST = "android.intent.extra.artist";
    /**
     * The name of the Intent-extra used to define the album
     */
    public static final String EXTRA_MEDIA_ALBUM = "android.intent.extra.album";
    /**
     * The name of the Intent-extra used to define the song title
     */
    public static final String EXTRA_MEDIA_TITLE = "android.intent.extra.title";
    /**
     * The name of the Intent-extra used to define the search focus. The search focus
     * indicates whether the search should be for things related to the artist, album
     * or song that is identified by the other extras.
     */
    public static final String EXTRA_MEDIA_FOCUS = "android.intent.extra.focus";

    /**
     * The name of the Intent-extra used to control the orientation of a ViewImage or a MovieView.
     * This is an int property that overrides the activity's requestedOrientation.
     * @see android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
     */
    public static final String EXTRA_SCREEN_ORIENTATION = "android.intent.extra.screenOrientation";

    /**
     * The name of an Intent-extra used to control the UI of a ViewImage.
     * This is a boolean property that overrides the activity's default fullscreen state.
     */
    public static final String EXTRA_FULL_SCREEN = "android.intent.extra.fullScreen";

    /**
     * The name of an Intent-extra used to control the UI of a ViewImage.
     * This is a boolean property that specifies whether or not to show action icons.
     */
    public static final String EXTRA_SHOW_ACTION_ICONS = "android.intent.extra.showActionIcons";

    /**
     * The name of the Intent-extra used to control the onCompletion behavior of a MovieView.
     * This is a boolean property that specifies whether or not to finish the MovieView activity
     * when the movie completes playing. The default value is true, which means to automatically
     * exit the movie player activity when the movie completes playing.
     */
    public static final String EXTRA_FINISH_ON_COMPLETION = "android.intent.extra.finishOnCompletion";

    /**
     * M: The name of Intent-extra used to control the loop behavoir of a MovieView.
     * This is a boolean property that specifies whether or not to loop the sent uris.
     * If you sent one uri, it will loop one. otheriwise, it will loop the list.
     * 
     * @hide
     */
    public static final String EXTRA_LOOP_PLAYBACK = "android.intent.extra.loopPlayback";
    
    /**
     * M: The name of Intent-extra used to tell MovieView to play a list of Uris.
     * It can be used with {@link MediaStore#EXTRA_LOOP_PLAYBACK}.
     * 
     * @hide
     */
    public static final String EXTRA_URI_LIST = "android.intent.extra.uriList";

    /**
     * The name of the Intent action used to launch a camera in still image mode.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_STILL_IMAGE_CAMERA = "android.media.action.STILL_IMAGE_CAMERA";

    /**
     * The name of the Intent action used to launch a camera in still image mode
     * for use when the device is secured (e.g. with a pin, password, pattern,
     * or face unlock). Applications responding to this intent must not expose
     * any personal content like existing photos or videos on the device. The
     * applications should be careful not to share any photo or video with other
     * applications or internet. The activity should use {@link
     * android.view.WindowManager.LayoutParams#FLAG_SHOW_WHEN_LOCKED} to display
     * on top of the lock screen while secured. There is no activity stack when
     * this flag is used, so launching more than one activity is strongly
     * discouraged.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";

    /**
     * The name of the Intent action used to launch a camera in video mode.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String INTENT_ACTION_VIDEO_CAMERA = "android.media.action.VIDEO_CAMERA";

    /**
     * Standard Intent action that can be sent to have the camera application
     * capture an image and return it.
     * <p>
     * The caller may pass an extra EXTRA_OUTPUT to control where this image will be written.
     * If the EXTRA_OUTPUT is not present, then a small sized image is returned as a Bitmap
     * object in the extra field. This is useful for applications that only need a small image.
     * If the EXTRA_OUTPUT is present, then the full-sized image will be written to the Uri
     * value of EXTRA_OUTPUT.
     * @see #EXTRA_OUTPUT
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public final static String ACTION_IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE";

    /**
     * Intent action that can be sent to have the camera application capture an image and return
     * it when the device is secured (e.g. with a pin, password, pattern, or face unlock).
     * Applications responding to this intent must not expose any personal content like existing
     * photos or videos on the device. The applications should be careful not to share any photo
     * or video with other applications or internet. The activity should use {@link
     * android.view.WindowManager.LayoutParams#FLAG_SHOW_WHEN_LOCKED} to display on top of the
     * lock screen while secured. There is no activity stack when this flag is used, so
     * launching more than one activity is strongly discouraged.
     * <p>
     * The caller may pass an extra EXTRA_OUTPUT to control where this image will be written.
     * If the EXTRA_OUTPUT is not present, then a small sized image is returned as a Bitmap
     * object in the extra field. This is useful for applications that only need a small image.
     * If the EXTRA_OUTPUT is present, then the full-sized image will be written to the Uri
     * value of EXTRA_OUTPUT.
     *
     * @see #ACTION_IMAGE_CAPTURE
     * @see #EXTRA_OUTPUT
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
            "android.media.action.IMAGE_CAPTURE_SECURE";

    /**
     * Standard Intent action that can be sent to have the camera application
     * capture a video and return it.
     * <p>
     * The caller may pass in an extra EXTRA_VIDEO_QUALITY to control the video quality.
     * <p>
     * The caller may pass in an extra EXTRA_OUTPUT to control
     * where the video is written. If EXTRA_OUTPUT is not present the video will be
     * written to the standard location for videos, and the Uri of that location will be
     * returned in the data field of the Uri.
     * @see #EXTRA_OUTPUT
     * @see #EXTRA_VIDEO_QUALITY
     * @see #EXTRA_SIZE_LIMIT
     * @see #EXTRA_DURATION_LIMIT
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public final static String ACTION_VIDEO_CAPTURE = "android.media.action.VIDEO_CAPTURE";

    /**
     * The name of the Intent-extra used to control the quality of a recorded video. This is an
     * integer property. Currently value 0 means low quality, suitable for MMS messages, and
     * value 1 means high quality. In the future other quality levels may be added.
     */
    public final static String EXTRA_VIDEO_QUALITY = "android.intent.extra.videoQuality";

    /**
     * Specify the maximum allowed size.
     */
    public final static String EXTRA_SIZE_LIMIT = "android.intent.extra.sizeLimit";

    /**
     * Specify the maximum allowed recording duration in seconds.
     */
    public final static String EXTRA_DURATION_LIMIT = "android.intent.extra.durationLimit";

    /**
     * The name of the Intent-extra used to indicate a content resolver Uri to be used to
     * store the requested image or video.
     */
    public final static String EXTRA_OUTPUT = "output";

    /**
      * The string that is used when a media attribute is not known. For example,
      * if an audio file does not have any meta data, the artist and album columns
      * will be set to this value.
      */
    public static final String UNKNOWN_STRING = "<unknown>";

    /**
     * Common fields for most MediaProvider tables
     */
    public interface MediaColumns extends BaseColumns, DrmColumns {
        /**
         * The data stream for the file
         * <P>Type: DATA STREAM</P>
         */
        public static final String DATA = "_data";

        /**
         * The size of the file in bytes
         * <P>Type: INTEGER (long)</P>
         */
        public static final String SIZE = "_size";

        /**
         * The display name of the file
         * <P>Type: TEXT</P>
         */
        public static final String DISPLAY_NAME = "_display_name";

        /**
         * The title of the content
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * The time the file was added to the media provider
         * Units are seconds since 1970.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_ADDED = "date_added";

        /**
         * The time the file was last modified
         * Units are seconds since 1970.
         * NOTE: This is for internal use by the media scanner.  Do not modify this field.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_MODIFIED = "date_modified";

        /**
         * The MIME type of the file
         * <P>Type: TEXT</P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * The MTP object handle of a newly transfered file.
         * Used to pass the new file's object handle through the media scanner
         * from MTP to the media provider
         * For internal use only by MTP, media scanner and media provider.
         * <P>Type: INTEGER</P>
         * @hide
         */
        public static final String MEDIA_SCANNER_NEW_OBJECT_ID = "media_scanner_new_object_id";

        /**
         * Non-zero if the media file is drm-protected
         * <P>Type: INTEGER (boolean)</P>
         * @hide
         */
        public static final String IS_DRM = "is_drm";

        /**
         * The width of the image/video in pixels.
         */
        public static final String WIDTH = "width";

        /**
         * The height of the image/video in pixels.
         */
        public static final String HEIGHT = "height";
     }

    /**
     * Media provider table containing an index of all files in the media storage,
     * including non-media files.  This should be used by applications that work with
     * non-media file types (text, HTML, PDF, etc) as well as applications that need to
     * work with multiple media file types in a single query.
     */
    public static final class Files {

        /**
         * Get the content:// style URI for the files table on the
         * given volume.
         *
         * @param volumeName the name of the volume to get the URI for
         * @return the URI to the files table on the given volume
         */
        public static Uri getContentUri(String volumeName) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                    "/file");
        }

        /**
         * Get the content:// style URI for a single row in the files table on the
         * given volume.
         *
         * @param volumeName the name of the volume to get the URI for
         * @param rowId the file to get the URI for
         * @return the URI to the files table on the given volume
         */
        public static final Uri getContentUri(String volumeName,
                long rowId) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                    + "/file/" + rowId);
        }

        /**
         * For use only by the MTP implementation.
         * @hide
         */
        public static Uri getMtpObjectsUri(String volumeName) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                    "/object");
        }

        /**
         * For use only by the MTP implementation.
         * @hide
         */
        public static final Uri getMtpObjectsUri(String volumeName,
                long fileId) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                    + "/object/" + fileId);
        }

        /**
         * Used to implement the MTP GetObjectReferences and SetObjectReferences commands.
         * @hide
         */
        public static final Uri getMtpReferencesUri(String volumeName,
                long fileId) {
            return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                    + "/object/" + fileId + "/references");
        }

        /**
         * Fields for master table for all media files.
         * Table also contains MediaColumns._ID, DATA, SIZE and DATE_MODIFIED.
         */
        public interface FileColumns extends MediaColumns, FileExtensionColumns {
            /**
             * The MTP storage ID of the file
             * <P>Type: INTEGER</P>
             * @hide
             */
            public static final String STORAGE_ID = "storage_id";

            /**
             * The MTP format code of the file
             * <P>Type: INTEGER</P>
             * @hide
             */
            public static final String FORMAT = "format";

            /**
             * The index of the parent directory of the file
             * <P>Type: INTEGER</P>
             */
            public static final String PARENT = "parent";

            /**
             * The MIME type of the file
             * <P>Type: TEXT</P>
             */
            public static final String MIME_TYPE = "mime_type";

            /**
             * The title of the content
             * <P>Type: TEXT</P>
             */
            public static final String TITLE = "title";

            /**
             * The media type (audio, video, image or playlist)
             * of the file, or 0 for not a media file
             * <P>Type: TEXT</P>
             */
            public static final String MEDIA_TYPE = "media_type";

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file
             * is not an audio, image, video or playlist file.
             */
            public static final int MEDIA_TYPE_NONE = 0;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is an image file.
             */
            public static final int MEDIA_TYPE_IMAGE = 1;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is an audio file.
             */
            public static final int MEDIA_TYPE_AUDIO = 2;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is a video file.
             */
            public static final int MEDIA_TYPE_VIDEO = 3;

            /**
             * Constant for the {@link #MEDIA_TYPE} column indicating that file is a playlist file.
             */
            public static final int MEDIA_TYPE_PLAYLIST = 4;
        }
    }

    /**
     * This class is used internally by Images.Thumbnails and Video.Thumbnails, it's not intended
     * to be accessed elsewhere.
     */
    private static class InternalThumbnails implements BaseColumns {
        private static final int MINI_KIND = 1;
        private static final int FULL_SCREEN_KIND = 2;
        private static final int MICRO_KIND = 3;
        private static final String[] PROJECTION = new String[] {_ID, MediaColumns.DATA};
        private static final String[] SELECTION = new String[] {_ID, MediaColumns.DATA, Images.Thumbnails.WIDTH, 
                Images.Thumbnails.HEIGHT};
        static final int DEFAULT_GROUP_ID = 0;
        private static final Object sThumbBufLock = new Object();
        private static byte[] sThumbBuf;

        private static Bitmap getMiniThumbFromFile(
                Cursor c, Uri baseUri, ContentResolver cr, BitmapFactory.Options options) {
            Bitmap bitmap = null;
            Uri thumbUri = null;
            try {
                long thumbId = c.getLong(0);
                String filePath = c.getString(1);
                thumbUri = ContentUris.withAppendedId(baseUri, thumbId);
                ParcelFileDescriptor pfdInput = cr.openFileDescriptor(thumbUri, "r");
                bitmap = BitmapFactory.decodeFileDescriptor(
                        pfdInput.getFileDescriptor(), null, options);
                pfdInput.close();
            } catch (FileNotFoundException ex) {
                Xlog.e(TAG, "couldn't open thumbnail " + thumbUri + "; " + ex);
            } catch (IOException ex) {
                Xlog.e(TAG, "couldn't open thumbnail " + thumbUri + "; " + ex);
            } catch (OutOfMemoryError ex) {
                Xlog.e(TAG, "failed to allocate memory for thumbnail "
                        + thumbUri + "; " + ex);
            }
            return bitmap;
        }

        /**
         * This method cancels the thumbnail request so clients waiting for getThumbnail will be
         * interrupted and return immediately. Only the original process which made the getThumbnail
         * requests can cancel their own requests.
         *
         * @param cr ContentResolver
         * @param origId original image or video id. use -1 to cancel all requests.
         * @param groupId the same groupId used in getThumbnail
         * @param baseUri the base URI of requested thumbnails
         */
        static void cancelThumbnailRequest(ContentResolver cr, long origId, Uri baseUri,
                long groupId) {
            Uri cancelUri = baseUri.buildUpon().appendQueryParameter("cancel", "1")
                    .appendQueryParameter("orig_id", String.valueOf(origId))
                    .appendQueryParameter("group_id", String.valueOf(groupId)).build();
            Cursor c = null;
            try {
                c = cr.query(cancelUri, PROJECTION, null, null, null);
            }
            finally {
                if (c != null) c.close();
            }
        }

        /**
         * This method ensure thumbnails associated with origId are generated and decode the byte
         * stream from database (MICRO_KIND) or file (MINI_KIND).
         *
         * Special optimization has been done to avoid further IPC communication for MICRO_KIND
         * thumbnails.
         *
         * @param cr ContentResolver
         * @param origId original image or video id
         * @param kind could be MINI_KIND or MICRO_KIND
         * @param options this is only used for MINI_KIND when decoding the Bitmap
         * @param baseUri the base URI of requested thumbnails
         * @param groupId the id of group to which this request belongs
         * @return Bitmap bitmap of specified thumbnail kind
         */
        static Bitmap getThumbnail(ContentResolver cr, long origId, long groupId, int kind,
                BitmapFactory.Options options, Uri baseUri, boolean isVideo) {
            Bitmap bitmap = null;
            String filePath = null;
            long thumb_ID = 0;
            Xlog.v(TAG, "getThumbnail: origId=" + origId + ", kind=" + kind + ", Uri=" + baseUri + ", isVideo=" + isVideo);
            /// M: If the magic is non-zero, we simply return thumbnail if it does exist.
            /// querying MediaProvider and simply return thumbnail.
            MiniThumbFile thumbFile = new MiniThumbFile(isVideo ? Video.Media.EXTERNAL_CONTENT_URI
                    : Images.Media.EXTERNAL_CONTENT_URI);
            Cursor c = null;
            MiniThumbFile.ThumbResult result = new MiniThumbFile.ThumbResult();
            try {
                long magic = thumbFile.getMagic(origId);
                if (magic != 0) {
                    if (kind == MICRO_KIND) {
                        if (!isVideo) {
                            thumb_ID = getImageThumbnailId(cr, baseUri, origId);
                        } else {
                            thumb_ID = getVideoThumbnailId(cr, baseUri, origId);
                        }

                        if (magic == thumb_ID ) {
                            synchronized (sThumbBufLock) {
                                if (sThumbBuf == null) {
                                    sThumbBuf = new byte[MiniThumbFile.BYTES_PER_MINTHUMB];
                                }
                                if (thumbFile.getMiniThumbFromFile(origId, sThumbBuf, result) != null) {
//                                    bitmap = BitmapFactory.decodeByteArray(sThumbBuf, 0, sThumbBuf.length);
                                    bitmap = BitmapFactory.decodeByteArray(sThumbBuf, 0, sThumbBuf.length, options);
                                    if (bitmap == null) {
                                        Xlog.w(TAG, "couldn't decode byte array.");
                                    }
                                }
                            }
                            if (result.getDetail() == MiniThumbFile.ThumbResult.WRONG_CHECK_CODE) {
                                /// M: wrong check code, let media provider to re-create the thumbnail from file.
                                ContentValues values = new ContentValues();
                                values.put(Images.Media.MINI_THUMB_MAGIC, "0");
                                String where = "_id=? ";
                                String[] whereArgs = new String[]{String.valueOf(origId)};
                                if (!isVideo) {
                                    cr.update(Images.Media.EXTERNAL_CONTENT_URI, values, where, whereArgs);
                                } else {
                                    cr.update(Video.Media.EXTERNAL_CONTENT_URI, values, where, whereArgs);
                                }
                                /// M: reget the thumbnail from MiniThumbFile.
                                thumb_ID = 0;
                            } else {
                                /// M: original logic
                                return bitmap;
                            }
                        }
                    } else if (kind == MINI_KIND) {
                        String column = isVideo ? "video_id=" : "image_id=";
                        c = cr.query(baseUri, PROJECTION, column + origId, null, null);
                        if (c != null && c.moveToFirst()) {
                            bitmap = getMiniThumbFromFile(c, baseUri, cr, options);
                            if (bitmap != null) {
                                return bitmap;
                            }
                        }
                    }
                }

                Uri blockingUri = baseUri.buildUpon().appendQueryParameter("blocking", "1")
                        .appendQueryParameter("orig_id", String.valueOf(origId))
                        .appendQueryParameter("group_id", String.valueOf(groupId)).build();
                if (c != null) c.close();
                //c = cr.query(blockingUri, PROJECTION, null, null, null);
                c = cr.query(blockingUri, SELECTION, null, null, null);
                // This happens when original image/video doesn't exist.
                if (c == null) return null;

                // Assuming thumbnail has been generated, at least original image exists.
                /// M: If check code is wrong, here to get the new thumbnail created by provider.
                if (kind == MICRO_KIND && (thumb_ID == 0)) {
                    if (!isVideo) {
                        thumb_ID = getImageThumbnailId(cr, baseUri, origId);
                    } else {
                        thumb_ID = getVideoThumbnailId(cr, baseUri, origId); 
                    }
                    
                    long thumb_id = thumbFile.getMagic(origId);
                    if (0 != thumb_ID && thumb_id == thumb_ID ) {
                        synchronized (sThumbBufLock) {
                            if (sThumbBuf == null) {
                                sThumbBuf = new byte[MiniThumbFile.BYTES_PER_MINTHUMB];
                            }
                            if (thumbFile.getMiniThumbFromFile(origId, sThumbBuf) != null) {
//                                bitmap = BitmapFactory.decodeByteArray(sThumbBuf, 0, sThumbBuf.length);
                                bitmap = BitmapFactory.decodeByteArray(sThumbBuf, 0, sThumbBuf.length, options);
                                if (bitmap == null) {
                                    Xlog.w(TAG, "couldn't decode byte array.");
                                }
                            }
                        }
                    }
                } else if (kind == MINI_KIND) {
                    if (c.moveToFirst()) {
                        bitmap = getMiniThumbFromFile(c, baseUri, cr, options);
                    }
                } else if (thumb_ID != 0) {
                    Xlog.w(TAG, "------for thumb_ID !=null------");
                } else {
                    throw new IllegalArgumentException("Unsupported kind: " + kind);
                }

                // We probably run out of space, so create the thumbnail in memory.
                if (bitmap == null) {
                    Xlog.v(TAG, "Create the thumbnail in memory: origId=" + origId
                            + ", kind=" + kind + ", isVideo="+isVideo);
                    Uri uri = Uri.parse(
                            baseUri.buildUpon().appendPath(String.valueOf(origId))
                                    .toString().replaceFirst("thumbnails", "media"));
                    if (filePath == null) {
                        if (c != null) c.close();
                        c = cr.query(uri, PROJECTION, null, null, null);
                        if (c == null || !c.moveToFirst()) {
                            return null;
                        }
                        filePath = c.getString(1);
                    }
                    if (isVideo) {
                        bitmap = ThumbnailUtils.createVideoThumbnail(filePath, kind);
                    } else {
                        bitmap = ThumbnailUtils.createImageThumbnail(filePath, kind);

                        /// M: for GIF file format, first draw the 8888 bitmap to a
                        /// white bitmap to create its white background. @{
                        String mimeType = android.media.MediaFile.getMimeTypeForFile(filePath);
                        if (bitmap != null && "image/gif".equals(mimeType)){
                            Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), 
                                                    bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                            if (b != null) {
                                android.graphics.Canvas canvas = new android.graphics.Canvas(b);
                                //draw buffer white
                                canvas.drawColor(0xFFFFFFFF);
                                //draw original bitmap onto white buffer
                                canvas.drawBitmap(bitmap,new android.graphics.Matrix(),null);
                                bitmap.recycle();
                                bitmap = b;
                                b = null;
                            }
                        }
                        /// @}
                    }
                }
            } catch (SQLiteException ex) {
                Xlog.w(TAG, "", ex);
            } finally {
                if (c != null) c.close();
                // To avoid file descriptor leak in application process.
                thumbFile.deactivate();
                thumbFile = null;
            }
            return bitmap;
        }
    }

    /**
     * Contains meta data for all available images.
     */
    public static final class Images {
        public interface ImageColumns extends MediaColumns, ImageExtensionColumns {
            /**
             * The description of the image
             * <P>Type: TEXT</P>
             */
            public static final String DESCRIPTION = "description";

            /**
             * The picasa id of the image
             * <P>Type: TEXT</P>
             */
            public static final String PICASA_ID = "picasa_id";

            /**
             * Whether the video should be published as public or private
             * <P>Type: INTEGER</P>
             */
            public static final String IS_PRIVATE = "isprivate";

            /**
             * The latitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LATITUDE = "latitude";

            /**
             * The longitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LONGITUDE = "longitude";

            /**
             * The date & time that the image was taken in units
             * of milliseconds since jan 1, 1970.
             * <P>Type: INTEGER</P>
             */
            public static final String DATE_TAKEN = "datetaken";

            /**
             * The orientation for the image expressed as degrees.
             * Only degrees 0, 90, 180, 270 will work.
             * <P>Type: INTEGER</P>
             */
            public static final String ORIENTATION = "orientation";

            /**
             * The mini thumb id.
             * <P>Type: INTEGER</P>
             */
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";

            /**
             * The bucket id of the image. This is a read-only property that
             * is automatically computed from the DATA column.
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_ID = "bucket_id";

            /**
             * The bucket display name of the image. This is a read-only property that
             * is automatically computed from the DATA column.
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";
        }

        public static final class Media implements ImageColumns {
            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection) {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }

            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection,
                    String where, String orderBy) {
                return cr.query(uri, projection, where,
                                             null, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }

            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection,
                    String selection, String [] selectionArgs, String orderBy) {
                return cr.query(uri, projection, selection,
                        selectionArgs, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }

            /**
             * Retrieves an image for the given url as a {@link Bitmap}.
             *
             * @param cr The content resolver to use
             * @param url The url of the image
             * @throws FileNotFoundException
             * @throws IOException
             */
            public static final Bitmap getBitmap(ContentResolver cr, Uri url)
                    throws FileNotFoundException, IOException {
                InputStream input = cr.openInputStream(url);
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                return bitmap;
            }

            /**
             * Insert an image and create a thumbnail for it.
             *
             * @param cr The content resolver to use
             * @param imagePath The path to the image to insert
             * @param name The name of the image
             * @param description The description of the image
             * @return The URL to the newly created image
             * @throws FileNotFoundException
             */
            public static final String insertImage(ContentResolver cr, String imagePath,
                    String name, String description) throws FileNotFoundException {
                // Check if file exists with a FileInputStream
                FileInputStream stream = new FileInputStream(imagePath);
                try {
                    Bitmap bm = BitmapFactory.decodeFile(imagePath);
                    String ret = insertImage(cr, bm, name, description);
                    bm.recycle();
                    return ret;
                } finally {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        Xlog.e(TAG, "insertImage: IOException! path=" + imagePath, e);
                    }
                }
            }

            private static final Bitmap StoreThumbnail(
                    ContentResolver cr,
                    Bitmap source,
                    long id,
                    float width, float height,
                    int kind) {
                // create the matrix to scale it
                Matrix matrix = new Matrix();

                float scaleX = width / source.getWidth();
                float scaleY = height / source.getHeight();

                matrix.setScale(scaleX, scaleY);

                Bitmap thumb = Bitmap.createBitmap(source, 0, 0,
                                                   source.getWidth(),
                                                   source.getHeight(), matrix,
                                                   true);

                ContentValues values = new ContentValues(4);
                values.put(Images.Thumbnails.KIND,     kind);
                values.put(Images.Thumbnails.IMAGE_ID, (int)id);
                values.put(Images.Thumbnails.HEIGHT,   thumb.getHeight());
                values.put(Images.Thumbnails.WIDTH,    thumb.getWidth());

                Uri url = cr.insert(Images.Thumbnails.EXTERNAL_CONTENT_URI, values);

                try {
                    OutputStream thumbOut = cr.openOutputStream(url);

                    thumb.compress(Bitmap.CompressFormat.JPEG, 100, thumbOut);
                    thumbOut.close();
                    return thumb;
                } catch (FileNotFoundException ex) {
                    Xlog.e(TAG, "StoreThumbnail: FileNotFoundException! uri=" + url, ex);
                    return null;
                } catch (IOException ex) {
                    Xlog.e(TAG, "StoreThumbnail: IOException! uri=" + url, ex);
                    return null;
                }
            }

            /// M: Add to make sure insert image file has exist to avoid cts fail.
            private static boolean ensureFileExists(String path) {
                File file = new File(path);
                if (file.exists()) {
                    return true;
                } else {
                    // we will not attempt to create the first directory in the path
                    // (for example, do not create /sdcard if the SD card is not mounted)
                    int secondSlash = path.indexOf('/', 1);
                    if (secondSlash < 1) return false;
                    String directoryPath = path.substring(0, secondSlash);
                    File directory = new File(directoryPath);
                    if (!directory.exists())
                        return false;
                    file.getParentFile().mkdirs();
                    try {
                        return file.createNewFile();
                    } catch(IOException ioe) {
                        Xlog.e(TAG, "File creation failed", ioe);
                    }
                    return false;
                }
            }

            /**
             * Insert an image and create a thumbnail for it.
             *
             * @param cr The content resolver to use
             * @param source The stream to use for the image
             * @param title The name of the image
             * @param description The description of the image
             * @return The URL to the newly created image, or <code>null</code> if the image failed to be stored
             *              for any reason.
             */
            public static final String insertImage(ContentResolver cr, Bitmap source,
                                                   String title, String description) {
                ContentValues values = new ContentValues();
                values.put(Images.Media.TITLE, title);
                values.put(Images.Media.DESCRIPTION, description);
                values.put(Images.Media.MIME_TYPE, "image/jpeg");

                /// M: Google not create file when insert values not contain data in 4.3(MR2),
                /// so we need make sure these insert file exist before call openOutputStream
                /// to avoid CTS fail. @}
                String data = Environment.getExternalStorageDirectory().getPath()
                    + "/DCIM/Camera/" + String.valueOf(System.currentTimeMillis()) + ".jpg";
                values.put(Images.Media.DATA, data);
                if (!ensureFileExists(data)) {
                    throw new IllegalStateException("Unable to create new file: " + data);
                }
                /// @}

                Uri url = null;
                String stringUrl = null;    /* value to be returned */

                try {
                    url = cr.insert(EXTERNAL_CONTENT_URI, values);

                    if (source != null) {
                        OutputStream imageOut = cr.openOutputStream(url);
                        try {
                            source.compress(Bitmap.CompressFormat.JPEG, 50, imageOut);
                        } finally {
                            imageOut.close();
                        }

                        long id = ContentUris.parseId(url);
                        // Wait until MINI_KIND thumbnail is generated.
                        Bitmap miniThumb = Images.Thumbnails.getThumbnail(cr, id,
                                Images.Thumbnails.MINI_KIND, null);
                        // This is for backward compatibility.
                        Bitmap microThumb = StoreThumbnail(cr, miniThumb, id, 50F, 50F,
                                Images.Thumbnails.MICRO_KIND);
                    } else {
                        Xlog.e(TAG, "Failed to create thumbnail, removing original");
                        cr.delete(url, null, null);
                        url = null;
                    }
                } catch (Exception e) {
                    Xlog.e(TAG, "Failed to insert image", e);
                    if (url != null) {
                        cr.delete(url, null, null);
                        url = null;
                    }
                }

                if (url != null) {
                    stringUrl = url.toString();
                }

                return stringUrl;
            }

            /**
             * Get the content:// style URI for the image media table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the image media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/images/media");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type of of this directory of
             * images.  Note that each entry in this directory will have a standard
             * image MIME type as appropriate -- for example, image/jpeg.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = ImageColumns.BUCKET_DISPLAY_NAME;
        }

        /**
         * This class allows developers to query and get two kinds of thumbnails:
         * MINI_KIND: 512 x 384 thumbnail
         * MICRO_KIND: 96 x 96 thumbnail
         */
        public static class Thumbnails implements BaseColumns {
            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection) {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }

            public static final Cursor queryMiniThumbnails(ContentResolver cr, Uri uri, int kind,
                    String[] projection) {
                return cr.query(uri, projection, "kind = " + kind, null, DEFAULT_SORT_ORDER);
            }

            public static final Cursor queryMiniThumbnail(ContentResolver cr, long origId, int kind,
                    String[] projection) {
                return cr.query(EXTERNAL_CONTENT_URI, projection,
                        IMAGE_ID + " = " + origId + " AND " + KIND + " = " +
                        kind, null, null);
            }

            /**
             * This method cancels the thumbnail request so clients waiting for getThumbnail will be
             * interrupted and return immediately. Only the original process which made the getThumbnail
             * requests can cancel their own requests.
             *
             * @param cr ContentResolver
             * @param origId original image id
             */
            public static void cancelThumbnailRequest(ContentResolver cr, long origId) {
                InternalThumbnails.cancelThumbnailRequest(cr, origId, EXTERNAL_CONTENT_URI,
                        InternalThumbnails.DEFAULT_GROUP_ID);
            }

            /**
             * This method checks if the thumbnails of the specified image (origId) has been created.
             * It will be blocked until the thumbnails are generated.
             *
             * @param cr ContentResolver used to dispatch queries to MediaProvider.
             * @param origId Original image id associated with thumbnail of interest.
             * @param kind The type of thumbnail to fetch. Should be either MINI_KIND or MICRO_KIND.
             * @param options this is only used for MINI_KIND when decoding the Bitmap
             * @return A Bitmap instance. It could be null if the original image
             *         associated with origId doesn't exist or memory is not enough.
             */
            public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                    BitmapFactory.Options options) {
                return InternalThumbnails.getThumbnail(cr, origId,
                        InternalThumbnails.DEFAULT_GROUP_ID, kind, options,
                        EXTERNAL_CONTENT_URI, false);
            }

            /**
             * This method cancels the thumbnail request so clients waiting for getThumbnail will be
             * interrupted and return immediately. Only the original process which made the getThumbnail
             * requests can cancel their own requests.
             *
             * @param cr ContentResolver
             * @param origId original image id
             * @param groupId the same groupId used in getThumbnail.
             */
            public static void cancelThumbnailRequest(ContentResolver cr, long origId, long groupId) {
                InternalThumbnails.cancelThumbnailRequest(cr, origId, EXTERNAL_CONTENT_URI, groupId);
            }

            /**
             * This method checks if the thumbnails of the specified image (origId) has been created.
             * It will be blocked until the thumbnails are generated.
             *
             * @param cr ContentResolver used to dispatch queries to MediaProvider.
             * @param origId Original image id associated with thumbnail of interest.
             * @param groupId the id of group to which this request belongs
             * @param kind The type of thumbnail to fetch. Should be either MINI_KIND or MICRO_KIND.
             * @param options this is only used for MINI_KIND when decoding the Bitmap
             * @return A Bitmap instance. It could be null if the original image
             *         associated with origId doesn't exist or memory is not enough.
             */
            public static Bitmap getThumbnail(ContentResolver cr, long origId, long groupId,
                    int kind, BitmapFactory.Options options) {
                return InternalThumbnails.getThumbnail(cr, origId, groupId, kind, options,
                        EXTERNAL_CONTENT_URI, false);
            }

            /**
             * Get the content:// style URI for the image media table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the image media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/images/thumbnails");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = "image_id ASC";

            /**
             * The data stream for the thumbnail
             * <P>Type: DATA STREAM</P>
             */
            public static final String DATA = "_data";

            /**
             * The original image for the thumbnal
             * <P>Type: INTEGER (ID from Images table)</P>
             */
            public static final String IMAGE_ID = "image_id";

            /**
             * The kind of the thumbnail
             * <P>Type: INTEGER (One of the values below)</P>
             */
            public static final String KIND = "kind";

            public static final int MINI_KIND = 1;
            public static final int FULL_SCREEN_KIND = 2;
            public static final int MICRO_KIND = 3;
            /**
             * The blob raw data of thumbnail
             * <P>Type: DATA STREAM</P>
             */
            public static final String THUMB_DATA = "thumb_data";

            /**
             * The width of the thumbnal
             * <P>Type: INTEGER (long)</P>
             */
            public static final String WIDTH = "width";

            /**
             * The height of the thumbnail
             * <P>Type: INTEGER (long)</P>
             */
            public static final String HEIGHT = "height";
        }
    }

    /**
     * Container for all audio content.
     */
    public static final class Audio {
        /**
         * Columns for audio file that show up in multiple tables.
         */
        public interface AudioColumns extends MediaColumns, AudioExtensionColumns {

            /**
             * A non human readable key calculated from the TITLE, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String TITLE_KEY = "title_key";

            /**
             * The duration of the audio file, in ms
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DURATION = "duration";

            /**
             * The position, in ms, playback was at when playback for this file
             * was last stopped.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String BOOKMARK = "bookmark";

            /**
             * The id of the artist who created the audio file, if any
             * <P>Type: INTEGER (long)</P>
             */
            public static final String ARTIST_ID = "artist_id";

            /**
             * The artist who created the audio file, if any
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * The artist credited for the album that contains the audio file
             * <P>Type: TEXT</P>
             * @hide
             */
            public static final String ALBUM_ARTIST = "album_artist";

            /**
             * Whether the song is part of a compilation
             * <P>Type: TEXT</P>
             * @hide
             */
            public static final String COMPILATION = "compilation";

            /**
             * A non human readable key calculated from the ARTIST, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST_KEY = "artist_key";

            /**
             * The composer of the audio file, if any
             * <P>Type: TEXT</P>
             */
            public static final String COMPOSER = "composer";

            /**
             * The id of the album the audio file is from, if any
             * <P>Type: INTEGER (long)</P>
             */
            public static final String ALBUM_ID = "album_id";

            /**
             * The album the audio file is from, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM = "album";

            /**
             * A non human readable key calculated from the ALBUM, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM_KEY = "album_key";

            /**
             * The track number of this song on the album, if any.
             * This number encodes both the track number and the
             * disc number. For multi-disc sets, this number will
             * be 1xxx for tracks on the first disc, 2xxx for tracks
             * on the second disc, etc.
             * <P>Type: INTEGER</P>
             */
            public static final String TRACK = "track";

            /**
             * The year the audio file was recorded, if any
             * <P>Type: INTEGER</P>
             */
            public static final String YEAR = "year";

            /**
             * Non-zero if the audio file is music
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_MUSIC = "is_music";

            /**
             * Non-zero if the audio file is a podcast
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_PODCAST = "is_podcast";

            /**
             * Non-zero if the audio file may be a ringtone
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_RINGTONE = "is_ringtone";

            /**
             * Non-zero if the audio file may be an alarm
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_ALARM = "is_alarm";

            /**
             * Non-zero if the audio file may be a notification sound
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_NOTIFICATION = "is_notification";

            /**
             * The genre of the audio file, if any
             * <P>Type: TEXT</P>
             * Does not exist in the database - only used by the media scanner for inserts.
             * @hide
             */
            public static final String GENRE = "genre";
        }

        /**
         * Converts a name to a "key" that can be used for grouping, sorting
         * and searching.
         * The rules that govern this conversion are:
         * - remove 'special' characters like ()[]'!?.,
         * - remove leading/trailing spaces
         * - convert everything to lowercase
         * - remove leading "the ", "an " and "a "
         * - remove trailing ", the|an|a"
         * - remove accents. This step leaves us with CollationKey data,
         *   which is not human readable
         *
         * @param name The artist or album name to convert
         * @return The "key" for the given name.
         */
        public static String keyFor(String name) {
            if (name != null)  {
                boolean sortfirst = false;
                if (name.equals(UNKNOWN_STRING)) {
                    return "\001";
                }
                // Check if the first character is \001. We use this to
                // force sorting of certain special files, like the silent ringtone.
                if (name.startsWith("\001")) {
                    sortfirst = true;
                }
                name = name.trim().toLowerCase();
                if (name.startsWith("the ")) {
                    name = name.substring(4);
                }
                if (name.startsWith("an ")) {
                    name = name.substring(3);
                }
                if (name.startsWith("a ")) {
                    name = name.substring(2);
                }
                if (name.endsWith(", the") || name.endsWith(",the") ||
                    name.endsWith(", an") || name.endsWith(",an") ||
                    name.endsWith(", a") || name.endsWith(",a")) {
                    name = name.substring(0, name.lastIndexOf(','));
                }
                name = name.replaceAll("[\\[\\]\\(\\)\"'.,?!]", "").trim();
                if (name.length() > 0) {
                    // Insert a separator between the characters to avoid
                    // matches on a partial character. If we ever change
                    // to start-of-word-only matches, this can be removed.
                    StringBuilder b = new StringBuilder();
                    b.append('.');
                    int nl = name.length();
                    for (int i = 0; i < nl; i++) {
                        b.append(name.charAt(i));
                        b.append('.');
                    }
                    name = b.toString();
                    String key = DatabaseUtils.getCollationKey(name);
                    if (sortfirst) {
                        key = "\001" + key;
                    }
                    return key;
               } else {
                    return "";
                }
            }
            return null;
        }

        public static final class Media implements AudioColumns {

            private static final String[] EXTERNAL_PATHS;

            static {
                String secondary_storage = System.getenv("SECONDARY_STORAGE");
                if (secondary_storage != null) {
                    EXTERNAL_PATHS = secondary_storage.split(":");
                } else {
                    EXTERNAL_PATHS = new String[0];
                }
            }

            /**
             * Get the content:// style URI for the audio media table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/media");
            }

            public static Uri getContentUriForPath(String path) {
                for (String ep : EXTERNAL_PATHS) {
                    if (path.startsWith(ep)) {
                        return EXTERNAL_CONTENT_URI;
                    }
                }

                return (path.startsWith(Environment.getExternalStorageDirectory().getPath()) ?
                        EXTERNAL_CONTENT_URI : INTERNAL_CONTENT_URI);
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/audio";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = TITLE_KEY;

            /**
             * Activity Action: Start SoundRecorder application.
             * <p>Input: nothing.
             * <p>Output: An uri to the recorded sound stored in the Media Library
             * if the recording was successful.
             * May also contain the extra EXTRA_MAX_BYTES.
             * @see #EXTRA_MAX_BYTES
             */
            public static final String RECORD_SOUND_ACTION =
                    "android.provider.MediaStore.RECORD_SOUND";

            /**
             * The name of the Intent-extra used to define a maximum file size for
             * a recording made by the SoundRecorder application.
             *
             * @see #RECORD_SOUND_ACTION
             */
             public static final String EXTRA_MAX_BYTES =
                    "android.provider.MediaStore.extra.MAX_BYTES";
        }

        /**
         * Columns representing an audio genre
         */
        public interface GenresColumns {
            /**
             * The name of the genre
             * <P>Type: TEXT</P>
             */
            public static final String NAME = "name";
        }

        /**
         * Contains all genres for audio files
         */
        public static final class Genres implements BaseColumns, GenresColumns {
            /**
             * Get the content:// style URI for the audio genres table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio genres table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/genres");
            }

            /**
             * Get the content:// style URI for querying the genres of an audio file.
             *
             * @param volumeName the name of the volume to get the URI for
             * @param audioId the ID of the audio file for which to retrieve the genres
             * @return the URI to for querying the genres for the audio file
             * with the given the volume and audioID
             */
            public static Uri getContentUriForAudioId(String volumeName, int audioId) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/media/" + audioId + "/genres");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/genre";

            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/genre";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = NAME;

            /**
             * Sub-directory of each genre containing all members.
             */
            public static final class Members implements AudioColumns {

                public static final Uri getContentUri(String volumeName,
                        long genreId) {
                    return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                            + "/audio/genres/" + genreId + "/members");
                }

                /**
                 * A subdirectory of each genre containing all member audio files.
                 */
                public static final String CONTENT_DIRECTORY = "members";

                /**
                 * The default sort order for this table
                 */
                public static final String DEFAULT_SORT_ORDER = TITLE_KEY;

                /**
                 * The ID of the audio file
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String AUDIO_ID = "audio_id";

                /**
                 * The ID of the genre
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String GENRE_ID = "genre_id";
            }
        }

        /**
         * Columns representing a playlist
         */
        public interface PlaylistsColumns extends PlaylistsExtensionColumns {
            /**
             * The name of the playlist
             * <P>Type: TEXT</P>
             */
            public static final String NAME = "name";

            /**
             * The data stream for the playlist file
             * <P>Type: DATA STREAM</P>
             */
            public static final String DATA = "_data";

            /**
             * The time the file was added to the media provider
             * Units are seconds since 1970.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DATE_ADDED = "date_added";

            /**
             * The time the file was last modified
             * Units are seconds since 1970.
             * NOTE: This is for internal use by the media scanner.  Do not modify this field.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DATE_MODIFIED = "date_modified";
        }

        /**
         * Contains playlists for audio files
         */
        public static final class Playlists implements BaseColumns,
                PlaylistsColumns {
            /**
             * Get the content:// style URI for the audio playlists table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio playlists table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/playlists");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/playlist";

            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/playlist";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = NAME;

            /**
             * Sub-directory of each playlist containing all members.
             */
            public static final class Members implements AudioColumns {
                public static final Uri getContentUri(String volumeName,
                        long playlistId) {
                    return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                            + "/audio/playlists/" + playlistId + "/members");
                }

                /**
                 * Convenience method to move a playlist item to a new location
                 * @param res The content resolver to use
                 * @param playlistId The numeric id of the playlist
                 * @param from The position of the item to move
                 * @param to The position to move the item to
                 * @return true on success
                 */
                public static final boolean moveItem(ContentResolver res,
                        long playlistId, int from, int to) {
                    Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                            playlistId)
                            .buildUpon()
                            .appendEncodedPath(String.valueOf(from))
                            .appendQueryParameter("move", "true")
                            .build();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, to);
                    return res.update(uri, values, null, null) != 0;
                }

                /**
                 * The ID within the playlist.
                 */
                public static final String _ID = "_id";

                /**
                 * A subdirectory of each playlist containing all member audio
                 * files.
                 */
                public static final String CONTENT_DIRECTORY = "members";

                /**
                 * The ID of the audio file
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String AUDIO_ID = "audio_id";

                /**
                 * The ID of the playlist
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String PLAYLIST_ID = "playlist_id";

                /**
                 * The order of the songs in the playlist
                 * <P>Type: INTEGER (long)></P>
                 */
                public static final String PLAY_ORDER = "play_order";

                /**
                 * The default sort order for this table
                 */
                public static final String DEFAULT_SORT_ORDER = PLAY_ORDER;
            }
        }

        /**
         * Columns representing an artist
         */
        public interface ArtistColumns extends ArtistExtensionColumns {
            /**
             * The artist who created the audio file, if any
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * A non human readable key calculated from the ARTIST, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST_KEY = "artist_key";

            /**
             * The number of albums in the database for this artist
             */
            public static final String NUMBER_OF_ALBUMS = "number_of_albums";

            /**
             * The number of albums in the database for this artist
             */
            public static final String NUMBER_OF_TRACKS = "number_of_tracks";
        }

        /**
         * Contains artists for audio files
         */
        public static final class Artists implements BaseColumns, ArtistColumns {
            /**
             * Get the content:// style URI for the artists table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio artists table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/artists");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/artists";

            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/artist";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = ARTIST_KEY;

            /**
             * Sub-directory of each artist containing all albums on which
             * a song by the artist appears.
             */
            public static final class Albums implements AlbumColumns {
                public static final Uri getContentUri(String volumeName,
                        long artistId) {
                    return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                            + "/audio/artists/" + artistId + "/albums");
                }
            }
        }

        /**
         * Columns representing an album
         */
        public interface AlbumColumns extends AlbumExtensionColumns {

            /**
             * The id for the album
             * <P>Type: INTEGER</P>
             */
            public static final String ALBUM_ID = "album_id";

            /**
             * The album on which the audio file appears, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM = "album";

            /**
             * The artist whose songs appear on this album
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * The number of songs on this album
             * <P>Type: INTEGER</P>
             */
            public static final String NUMBER_OF_SONGS = "numsongs";

            /**
             * This column is available when getting album info via artist,
             * and indicates the number of songs on the album by the given
             * artist.
             * <P>Type: INTEGER</P>
             */
            public static final String NUMBER_OF_SONGS_FOR_ARTIST = "numsongs_by_artist";

            /**
             * The year in which the earliest songs
             * on this album were released. This will often
             * be the same as {@link #LAST_YEAR}, but for compilation albums
             * they might differ.
             * <P>Type: INTEGER</P>
             */
            public static final String FIRST_YEAR = "minyear";

            /**
             * The year in which the latest songs
             * on this album were released. This will often
             * be the same as {@link #FIRST_YEAR}, but for compilation albums
             * they might differ.
             * <P>Type: INTEGER</P>
             */
            public static final String LAST_YEAR = "maxyear";

            /**
             * A non human readable key calculated from the ALBUM, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM_KEY = "album_key";

            /**
             * Cached album art.
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM_ART = "album_art";
        }

        /**
         * Contains artists for audio files
         */
        public static final class Albums implements BaseColumns, AlbumColumns {
            /**
             * Get the content:// style URI for the albums table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio albums table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/albums");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/albums";

            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/album";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = ALBUM_KEY;
        }
    }

    public static final class Video {

        /**
         * The default sort order for this table.
         */
        public static final String DEFAULT_SORT_ORDER = MediaColumns.DISPLAY_NAME;

        public static final Cursor query(ContentResolver cr, Uri uri, String[] projection) {
            return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
        }

        public interface VideoColumns extends MediaColumns, VideoExtensionColumns {

            /**
             * The duration of the video file, in ms
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DURATION = "duration";

            /**
             * The artist who created the video file, if any
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * The album the video file is from, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM = "album";

            /**
             * The resolution of the video file, formatted as "XxY"
             * <P>Type: TEXT</P>
             */
            public static final String RESOLUTION = "resolution";

            /**
             * The description of the video recording
             * <P>Type: TEXT</P>
             */
            public static final String DESCRIPTION = "description";

            /**
             * Whether the video should be published as public or private
             * <P>Type: INTEGER</P>
             */
            public static final String IS_PRIVATE = "isprivate";

            /**
             * The user-added tags associated with a video
             * <P>Type: TEXT</P>
             */
            public static final String TAGS = "tags";

            /**
             * The YouTube category of the video
             * <P>Type: TEXT</P>
             */
            public static final String CATEGORY = "category";

            /**
             * The language of the video
             * <P>Type: TEXT</P>
             */
            public static final String LANGUAGE = "language";

            /**
             * The latitude where the video was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LATITUDE = "latitude";

            /**
             * The longitude where the video was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LONGITUDE = "longitude";

            /**
             * The date & time that the video was taken in units
             * of milliseconds since jan 1, 1970.
             * <P>Type: INTEGER</P>
             */
            public static final String DATE_TAKEN = "datetaken";

            /**
             * The mini thumb id.
             * <P>Type: INTEGER</P>
             */
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";

            /**
             * The bucket id of the video. This is a read-only property that
             * is automatically computed from the DATA column.
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_ID = "bucket_id";

            /**
             * The bucket display name of the video. This is a read-only property that
             * is automatically computed from the DATA column.
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";

            /**
             * The bookmark for the video. Time in ms. Represents the location in the video that the
             * video should start playing at the next time it is opened. If the value is null or
             * out of the range 0..DURATION-1 then the video should start playing from the
             * beginning.
             * <P>Type: INTEGER</P>
             */
            public static final String BOOKMARK = "bookmark";
        }

        public static final class Media implements VideoColumns {
            /**
             * Get the content:// style URI for the video media table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the video media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/video/media");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = TITLE;
        }

        /**
         * This class allows developers to query and get two kinds of thumbnails:
         * MINI_KIND: 512 x 384 thumbnail
         * MICRO_KIND: 96 x 96 thumbnail
         *
         */
        public static class Thumbnails implements BaseColumns {
            /**
             * This method cancels the thumbnail request so clients waiting for getThumbnail will be
             * interrupted and return immediately. Only the original process which made the getThumbnail
             * requests can cancel their own requests.
             *
             * @param cr ContentResolver
             * @param origId original video id
             */
            public static void cancelThumbnailRequest(ContentResolver cr, long origId) {
                InternalThumbnails.cancelThumbnailRequest(cr, origId, EXTERNAL_CONTENT_URI,
                        InternalThumbnails.DEFAULT_GROUP_ID);
            }

            /**
             * This method checks if the thumbnails of the specified image (origId) has been created.
             * It will be blocked until the thumbnails are generated.
             *
             * @param cr ContentResolver used to dispatch queries to MediaProvider.
             * @param origId Original image id associated with thumbnail of interest.
             * @param kind The type of thumbnail to fetch. Should be either MINI_KIND or MICRO_KIND.
             * @param options this is only used for MINI_KIND when decoding the Bitmap
             * @return A Bitmap instance. It could be null if the original image
             *         associated with origId doesn't exist or memory is not enough.
             */
            public static Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                    BitmapFactory.Options options) {
                return InternalThumbnails.getThumbnail(cr, origId,
                        InternalThumbnails.DEFAULT_GROUP_ID, kind, options,
                        EXTERNAL_CONTENT_URI, true);
            }

            /**
             * This method checks if the thumbnails of the specified image (origId) has been created.
             * It will be blocked until the thumbnails are generated.
             *
             * @param cr ContentResolver used to dispatch queries to MediaProvider.
             * @param origId Original image id associated with thumbnail of interest.
             * @param groupId the id of group to which this request belongs
             * @param kind The type of thumbnail to fetch. Should be either MINI_KIND or MICRO_KIND
             * @param options this is only used for MINI_KIND when decoding the Bitmap
             * @return A Bitmap instance. It could be null if the original image associated with
             *         origId doesn't exist or memory is not enough.
             */
            public static Bitmap getThumbnail(ContentResolver cr, long origId, long groupId,
                    int kind, BitmapFactory.Options options) {
                return InternalThumbnails.getThumbnail(cr, origId, groupId, kind, options,
                        EXTERNAL_CONTENT_URI, true);
            }

            /**
             * This method cancels the thumbnail request so clients waiting for getThumbnail will be
             * interrupted and return immediately. Only the original process which made the getThumbnail
             * requests can cancel their own requests.
             *
             * @param cr ContentResolver
             * @param origId original video id
             * @param groupId the same groupId used in getThumbnail.
             */
            public static void cancelThumbnailRequest(ContentResolver cr, long origId, long groupId) {
                InternalThumbnails.cancelThumbnailRequest(cr, origId, EXTERNAL_CONTENT_URI, groupId);
            }

            /**
             * Get the content:// style URI for the image media table on the
             * given volume.
             *
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the image media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/video/thumbnails");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");

            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = "video_id ASC";

            /**
             * The data stream for the thumbnail
             * <P>Type: DATA STREAM</P>
             */
            public static final String DATA = "_data";

            /**
             * The original image for the thumbnal
             * <P>Type: INTEGER (ID from Video table)</P>
             */
            public static final String VIDEO_ID = "video_id";

            /**
             * The kind of the thumbnail
             * <P>Type: INTEGER (One of the values below)</P>
             */
            public static final String KIND = "kind";

            public static final int MINI_KIND = 1;
            public static final int FULL_SCREEN_KIND = 2;
            public static final int MICRO_KIND = 3;

            /**
             * The width of the thumbnal
             * <P>Type: INTEGER (long)</P>
             */
            public static final String WIDTH = "width";

            /**
             * The height of the thumbnail
             * <P>Type: INTEGER (long)</P>
             */
            public static final String HEIGHT = "height";
        }
    }

    /**
     * Uri for querying the state of the media scanner.
     */
    public static Uri getMediaScannerUri() {
        return Uri.parse(CONTENT_AUTHORITY_SLASH + "none/media_scanner");
    }

    /**
     * Name of current volume being scanned by the media scanner.
     */
    public static final String MEDIA_SCANNER_VOLUME = "volume";

    /**
     * Name of the file signaling the media scanner to ignore media in the containing directory
     * and its subdirectories. Developers should use this to avoid application graphics showing
     * up in the Gallery and likewise prevent application sounds and music from showing up in
     * the Music app.
     */
    public static final String MEDIA_IGNORE_FILENAME = ".nomedia";

    /**
     * Get the media provider's version.
     * Applications that import data from the media provider into their own caches
     * can use this to detect that the media provider changed, and reimport data
     * as needed. No other assumptions should be made about the meaning of the version.
     * @param context Context to use for performing the query.
     * @return A version string, or null if the version could not be determined.
     */
    public static String getVersion(Context context) {
        Cursor c = context.getContentResolver().query(
                Uri.parse(CONTENT_AUTHORITY_SLASH + "none/version"),
                null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getString(0);
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    /**
     * M: Streaming setting info for OMA. @{
     * 
     * @hide
     */
    public static final class Streaming {
        
        /**
         * Key for oma rtsp setting. All this will be stored in Settings.db
         * 
         * @hide
         */
        public interface OmaRtspSettingColumns {
            /**
             * User displayable name for Streaming settings.
             * <br>Type: TEXT
             */
            public static final String NAME = "mtk_rtsp_name";
            /**
             * Allows application settings to be binded to this specific RTSP setting.
             * <br>Type: TEXT
             */
            public static final String PROVIDER_ID = "mtk_rtsp_provider_id";
            /**
             * Logical proxy ID for the RTSP proxy to use. To avoid confusion
             * with other proxies, streaming should use a separate logical 
             * proxy (PLogICAL) containing only one physical proxy (PXPHYSICAL).
             * It conrrespondes to the proxyid of apn setting.
             * <br>Type: TEXT
             */
            public static final String TO_PROXY = "mtk_rtsp_to_proxy";
            /**
             * Required if direct use of Network Access Point supported.
             * It conrespondes to the napid of apn setting.
             * <br>Type: TEXT
             */
            public static final String TO_NAPID = "mtk_rtsp_to_napid";
            /**
             * Maximum sustainable bandwidth for all transfer media, in bits
             * per second, indicating the maximum media data throughput in
             * the network. The default value is product-specific.  
             * If transfer medium specific bandwidth values are used,
             * this value is the absolute maximum value for all media.
             * <br>Type: TEXT
             */
            public static final String MAX_BANDWIDTH = "mtk_rtsp_max_bandwidth";
            /**
             * Network performance characteristics for a transfer medium. 
             * The NETINFO parameter defines the performance the client should
             * expect for audio/video streaming, and may differ from the 
             * theoretical network capabilities.
             * 
             * The parameters defined for NAPDEF characteristics are not sufficient 
             * for a number of reasons: The actual transfer medium 
             * and hence the bitrate used may vary even when the same NAP is used, 
             * while NAPDEF only defines a single bandwidth value. 
             * For example, even if a network supports WCDMA, 
             * only GSM coverage may be currently available. 
             * NAPDEF/BEARER also does not differentiate between GSM-GPRS and EDGE-GPRS, 
             * but their performance is different. 
             * Finally, the operator may desire to limit the bandwidth used 
             * for streaming to a fraction of the total bandwidth available.
             * <br>Type: TEXT
             */
            public static final String NETINFO = "mtk_rtsp_netinfo";
            /**
             * Minimum UDP port number used for media data traffic (RTP). 
             * The default value is product-specific. The value has to be even.   
             * <br>Type: TEXT         
             */
            public static final String MIN_UDP_PORT = "mtk_rtsp_min_udp_port";
            /**
             * Maximum UDP port number used for media data traffic (RTP). 
             * The default value is product-specific. 
             * The value must be at least MIN-UDP-PORT + 5 to have enough ports 
             * for three media streams (audio, video, timed text), preferably much higher.
             * <br>Type: TEXT
             */
            public static final String MAX_UDP_PORT = "mtk_rtsp_max_udp_port";
            /**
             * The sim card id.
             * <br>Type: INTEGER
             */
            public static final String SIM_ID = "mtk_rtsp_sim_id";
        }

        /**
         * Contains all oma rtsp setting. Generally, it only has one row.
         * @hide
         */
        public static final class OmaRtspSetting implements OmaRtspSettingColumns {
            /**
             * The content:// style URI for the oma rts setting.
             */
            public static final Uri CONTENT_URI = 
                Uri.parse(CONTENT_AUTHORITY_SLASH + "internal/streaming/omartspsetting");
            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/omartspsetting";
            
        }

        /**
         * Keys for streaming settings.
         * Oma has defined many keys for streamign, so here we just define some other keys.
         * @hide
         */
        public interface SettingColumns {
            /**
             * Whether enable rtsp proxy or not.
             */
            public static final String RTSP_PROXY_ENABLED = "mtk_rtsp_proxy_enabled";
            /**
             * The rtsp proxy host.
             * <br>Type: TEXT
             */
            public static final String RTSP_PROXY_HOST = "mtk_rtsp_proxy_host";
            /**
             * The rtsp proxy port
             * <br>Type: INTEGER
             */
            public static final String RTSP_PROXY_PORT = "mtk_rtsp_proxy_port";
            /**
             * Whether enable http proxy or not.
             */
            public static final String HTTP_PROXY_ENABLED = "mtk_http_proxy_enabled";
            /**
             * The http proxy host.
             * <br>Type: TEXT
             */
            public static final String HTTP_PROXY_HOST = "mtk_http_proxy_host";
            /**
             * The http proxy port
             * <br>Type: INTEGER
             */
            public static final String HTTP_PROXY_PORT = "mtk_http_proxy_port";
        }

        public static final class Setting implements OmaRtspSettingColumns, SettingColumns {
            
        }
    }
    /** M: @} */

    /**
     * M: Columns for Drm media files. @{
     * 
     * @hide
     */
    public interface DrmColumns {
        //should be synced with MediaColumns.IS_DRM
        //here do not delete it just for compatibility
        /**
         * <br>Type: INTEGER (boolean)
         */
        public static final String IS_DRM = "is_drm";
        /**
         * <br>Type: TEXT
         */
        public static final String DRM_CONTENT_URI = "drm_content_uri";
        /**
         * <br>Type: INTEGER (numeric)
         */
        public static final String DRM_OFFSET = "drm_offset";
        /**
         * <br>Type: INTEGER (numeric)
         */
        public static final String DRM_DATALEN = "drm_dataLen";
        /**
         * <br>Type: TEXT
         */
        public static final String DRM_RIGHTS_ISSUER = "drm_rights_issuer";
        /**
         * <br>Type: TEXT
         */
        public static final String DRM_CONTENT_NAME = "drm_content_name";
        /**
         * <br>Type: TEXT
         */
        public static final String DRM_CONTENT_DESCRIPTION = "drm_content_description";
        /**
         * <br>Type: TEXT
         */
        public static final String DRM_CONTENT_VENDOR = "drm_content_vendor";
        /**
         * <br>Type: TEXT
         */
        public static final String DRM_ICON_URI = "drm_icon_uri";
        /**
         * <br>Type: INTEGER (numeric)
         */
        public static final String DRM_METHOD = "drm_method";
    }
    /** M: @}*/

    /**
     * M: Uri for querying the file path that being transferred through MTP.
     * 
     * @hide
     * @internal
     */
    public static Uri getMtpTransferFileUri() {
        return Uri.parse(CONTENT_AUTHORITY_SLASH + "none/mtp_transfer_file");
    }

    /**
     * M: Path of file being transferred.
     * 
     * @hide
     */
    public static final String MTP_TRANSFER_FILE_PATH = "mtp_transfer_file_path";

    /// M: Extend media database table columns. @{
    /**
     * @hide
     */
    public interface AudioExtensionColumns {
        /**
         * Indicates the DURATION is accurate or not.
         * <P>Type: INTEGER (boolean)</P>
         */
        public static final String IS_ACCURATE_DURATION = "isaccurateduration";

        /**
         * A pinyin key calculated from the TITLE, used for
         * sorting and grouping
         * <P>Type: TEXT</P>
         */
        public static final String TITLE_PINYIN_KEY = "title_pinyin_key";
    }

    /**
     * @hide
     */
    public interface PlaylistsExtensionColumns {
        /**
         * A pinyin key calculated from the NAME, used for
         * sorting and grouping
         * <P>Type: TEXT</P>
         */
        public static final String NAME_PINYIN_KEY = "name_pinyin_key";
    }

    /**
     * @hide
     */
    public interface AlbumExtensionColumns {
        /**
         * A pinyin key calculated from the ALBUM, used for
         * sorting and grouping
         * <P>Type: TEXT</P>
         */
        public static final String ALBUM_PINYIN_KEY = "album_pinyin_key";
    }

    /**
     * @hide
     */
    public interface ArtistExtensionColumns {
        /**
         * A pinyin key calculated from the ARTIST, used for
         * sorting and grouping
         * <P>Type: TEXT</P>
         */
        public static final String ARTIST_PINYIN_KEY = "artist_pinyin_key";
    }

    /**
     * @hide
     */
    public interface ThreeDimensionColumns {
        /**
         * The stereo type of video.
         * <P>Type: INTEGER</P>
         */
        public static final String STEREO_TYPE = "stereo_type";
        
        /**
         * Constant for the {@link #STEREO_TYPE} column indicating
         * video stereo type is 2d.
         */
        public static final int STEREO_TYPE_2D = 0;
        
        /**
         * Constant for the {@link #STEREO_TYPE} column indicating
         * video stereo type is frame_sequence 3d.
         */
        public static final int STEREO_TYPE_FRAME_SEQUENCE = 1;
        
        /**
         * Constant for the {@link #STEREO_TYPE} column indicating
         * video stereo type is side_by_side 3d.
         */
        public static final int STEREO_TYPE_SIDE_BY_SIDE = 2;
        
        /**
         * Constant for the {@link #STEREO_TYPE} column indicating
         * video stereo type is top_botton 3d.
         */
        public static final int STEREO_TYPE_TOP_BOTTOM = 3;
        /**
         * Constant for the {@link #STEREO_TYPE} column indicating
         * the left and rigth was swapped by user.
         */
        public static final int STEREO_TYPE_SWAP_LEFT_RIGHT = 4;

        /**
         * Constant for the {@link #STEREO_TYPE} column indicating
         * the top and bottom was swapped by user.
         */
        public static final int STEREO_TYPE_SWAP_TOP_BOTTOM = 5;

        /**
         * Constant for the {@link #STEREO_TYPE} column indicating
         * the stereo type is unknown.
         */
        public static final int STEREO_TYPE_UNKNOWN = -1;

        /**
         * Convergence rate of stereo images.
         * <P>Type: INTEGER</P>
         */
        public static final String CONVERGENCE = "convergence";
        /**
         * Indicates if the file genarate by N3D.
         * <P>Type: INTEGER</P>
         */
        public static final String IS_MTK_3D = "is_mtk_3d";
    }

    /**
     * @hide
     */
    public interface ImageExtensionColumns extends ThreeDimensionColumns {
        /**
         * Indicates specific type of mpo images.
         * <P>Type: INTEGER</P>
         */
        public static final String MPO_TYPE = "mpo_type";

        /**
         * Indicates group id of continuous shots images.
         * <P>Type: INTEGER</P>
         */
        public static final String GROUP_ID = "group_id";

        /**
         * Indicates index of continuous shots images within a group.
         * <P>Type: INTEGER</P>
         */
        public static final String GROUP_INDEX = "group_index";

        /**
         * Indicates focus valus of best shots images.
         * <P>Type: INTEGER</P>
         */
        public static final String FOCUS_VALUE = "focus_value";
	   
        /**
         * Indicates high focus valus of best shots images.
         * <P>Type: INTEGER</P>
         */
        public static final String FOCUS_VALUE_HIGH = "focus_value_high";
        
        /**
         * Indicates low focus valus of best shots images.
         * <P>Type: INTEGER</P>
         */
        public static final String FOCUS_VALUE_LOW = "focus_value_low";
        
        /**
         * Indicates whether marked as best shot.
         * <P>Type: INTEGER</P>
         */
        public static final String IS_BEST_SHOT = "is_best_shot";
        
        /**
         * Indicates continus shot group count.
         * <P>Type: INTEGER</P>
         */
        public static final String GROUP_COUNT = "group_count";
    }

    /**
     * M: File extend columns.
     * @hide
     */
    public interface FileExtensionColumns {
        /**
         * File name with extension.
         * <P>Type: TEXT</P>
         */
        public static final String FILE_NAME = "file_name";
        
        /**
         * File type.
         * <P>Type: INTEGER</P>
         */
        public static final String FILE_TYPE = "file_type";
    }
    /// M: @}
    /**
     * @hide
     */
    public interface VideoExtensionColumns extends ThreeDimensionColumns {
        /**
         * Indicates whether marked as live photo.
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String IS_LIVE_PHOTO = "is_live_photo";
        /**
         * Slow motion speed.
         */
        public static final String SLOW_MOTION_SPEED = "slow_motion_speed";
    }

    /// M: @}

    /**
     * M: Gets thumbnail's magic for an image.
     */
    private static long getImageThumbnailId(ContentResolver cr, Uri baseUri, long origId) {
        String tmpUri = baseUri.toString();
        Uri imagesUri = Uri.parse("content://media/external/images/media/");
        long thumb_Id = 0;
        Cursor c = null;
        try {
            c = cr.query(imagesUri, new String[]{Images.ImageColumns.MINI_THUMB_MAGIC}, "_id = " + origId, null, null);
            if (c == null) {
                Xlog.e(TAG, "getImageThumbnailId: Null cursor! id=" + origId);
                return thumb_Id;
            }
            if (c.moveToFirst()) {
                thumb_Id = c.getLong(0);
            }
        } catch (SQLiteException ex) {
            Xlog.e(TAG, "getImageThumbnailId: SQLiteException!", ex);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return thumb_Id;
    }

    /**
     * M: Gets thumbnail's magic for a video.
     */
    private static long getVideoThumbnailId(ContentResolver cr, Uri baseUri, long origId) {
        String tmpUri = baseUri.toString();
        Uri imagesUri = Uri.parse("content://media/external/video/media/");
        long thumb_Id = 0;
        Cursor c = null;
        try {
            c = cr.query(imagesUri, new String[]{Video.VideoColumns.MINI_THUMB_MAGIC}, "_id = " + origId, null, null);
            if (c == null) {
                Xlog.e(TAG, "getVideoThumbnailId: Null cursor! id=" + origId);
                return thumb_Id;
            }
            if (c.moveToFirst()) {
                thumb_Id = c.getLong(0);
            }
        } catch (SQLiteException ex) {
            Xlog.e(TAG, "getVideoThumbnailId: SQLiteException!", ex);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return thumb_Id;
    }
}
