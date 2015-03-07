/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The DRM provider contains forward locked DRM content.
 *
 * @hide
 */
public final class DrmStore
{
    private static final String TAG = "DrmStore";

    public static final String AUTHORITY = "drm";

    /**
     * This is in the Manifest class of the drm provider, but that isn't visible
     * in the framework.
     */
    private static final String ACCESS_DRM_PERMISSION = "android.permission.ACCESS_DRM";

    /**
     * Fields for DRM database
     */

    public interface Columns extends BaseColumns {
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
         * The title of the file content
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * The MIME type of the file
         * <P>Type: TEXT</P>
         */
        public static final String MIME_TYPE = "mime_type";

    }

    public interface Images extends Columns {

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/images");
    }

    public interface Audio extends Columns {

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/audio");
    }

}
