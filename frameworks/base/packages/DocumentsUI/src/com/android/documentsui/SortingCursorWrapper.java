/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.DocumentsActivity.TAG;
import static com.android.documentsui.DocumentsActivity.State.SORT_ORDER_DISPLAY_NAME;
import static com.android.documentsui.DocumentsActivity.State.SORT_ORDER_LAST_MODIFIED;
import static com.android.documentsui.DocumentsActivity.State.SORT_ORDER_SIZE;
import static com.android.documentsui.model.DocumentInfo.getCursorInt;
import static com.android.documentsui.model.DocumentInfo.getCursorLong;
import static com.android.documentsui.model.DocumentInfo.getCursorString;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;
import android.util.Log;

/// M: Add to support drm
import com.mediatek.drm.OmaDrmStore;

/**
 * Cursor wrapper that presents a sorted view of the underlying cursor. Handles
 * common {@link Document} sorting modes, such as ordering directories first.
 */
public class SortingCursorWrapper extends AbstractCursor {
    private final Cursor mCursor;

    private final int[] mPosition;
    private final String[] mValueString;
    private final long[] mValueLong;
    /// M: add to support drm, only show these drm files match given drm level.
    private int mCount;
    private int mDrmLevel = -1;

    public SortingCursorWrapper(Cursor cursor, int sortOrder) {
        this(cursor, sortOrder, -1);
    }

    /**
     * M: init SortingCursorWrapper with given drm level.
     */
    public SortingCursorWrapper(Cursor cursor, int sortOrder, int drmLevel) {
        mCursor = cursor;
        mDrmLevel = drmLevel;

        final int count = cursor.getCount();
        mPosition = new int[count];
        switch (sortOrder) {
            case SORT_ORDER_DISPLAY_NAME:
                mValueString = new String[count];
                mValueLong = null;
                break;
            case SORT_ORDER_LAST_MODIFIED:
            case SORT_ORDER_SIZE:
                mValueString = null;
                mValueLong = new long[count];
                break;
            default:
                throw new IllegalArgumentException();
        }

        /// M: Add to support drm. we only show match given drm level in intent extra,
        /// if don't limit drm level, we will show all drm files to user. {@
        int needShowDrmMethod = 0;
        if (DocumentsFeatureOption.IS_SUPPORT_DRM) {
            switch (mDrmLevel) {
                case OmaDrmStore.DrmExtra.DRM_LEVEL_FL:
                    needShowDrmMethod = OmaDrmStore.DrmMethod.METHOD_FL;
                    break;
                case OmaDrmStore.DrmExtra.DRM_LEVEL_SD:
                    needShowDrmMethod = OmaDrmStore.DrmMethod.METHOD_SD;
                    break;
                case OmaDrmStore.DrmExtra.DRM_LEVEL_ALL:
                    needShowDrmMethod = OmaDrmStore.DrmMethod.METHOD_FL | OmaDrmStore.DrmMethod.METHOD_CD
                            | OmaDrmStore.DrmMethod.METHOD_SD | OmaDrmStore.DrmMethod.METHOD_FLDCF;
                    break;
                default:
                    needShowDrmMethod = OmaDrmStore.DrmMethod.METHOD_FL | OmaDrmStore.DrmMethod.METHOD_CD
                            | OmaDrmStore.DrmMethod.METHOD_SD | OmaDrmStore.DrmMethod.METHOD_FLDCF;
                    break;
            }
        }
        /// @}

        cursor.moveToPosition(-1);
        for (int i = 0; i < count; i++) {
            cursor.moveToNext();
            /// M: If it's no need show drmMethod, ignore it and make it hidden. {@
            if (DocumentsFeatureOption.IS_SUPPORT_DRM) {
                boolean isDrm = getCursorInt(cursor, MediaStore.MediaColumns.IS_DRM) > 0;
                int drmMethod = getCursorInt(cursor, MediaStore.MediaColumns.DRM_METHOD);
                /// M: If IS_DRM is true but drm_method is invalid(-1) with given need show drm level(mDrmLevel>0),
                /// this may happen when drm file has been deleted, we don't need show these drm files.
                if (isDrm && ((mDrmLevel > 0 && drmMethod < 0) || (needShowDrmMethod & drmMethod) == 0)) {
                    continue;
                }
            }
            mPosition[mCount] = cursor.getPosition();
            /// @}

            switch (sortOrder) {
                case SORT_ORDER_DISPLAY_NAME:
                    final String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
                    final String displayName = getCursorString(cursor, Document.COLUMN_DISPLAY_NAME);
                    if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                        mValueString[mCount] = '\001' + displayName;
                    } else {
                        mValueString[mCount] = displayName;
                    }
                    break;
                case SORT_ORDER_LAST_MODIFIED:
                    mValueLong[mCount] = getCursorLong(cursor, Document.COLUMN_LAST_MODIFIED);
                    break;
                case SORT_ORDER_SIZE:
                    mValueLong[mCount] = getCursorLong(cursor, Document.COLUMN_SIZE);
                    break;
            }
            mCount++;
        }

        Log.v(TAG, "SortingCursorWrapper init " + mCount + "(" + count + ") with needShowDrmMethod = " + needShowDrmMethod);

        switch (sortOrder) {
            case SORT_ORDER_DISPLAY_NAME:
                synchronized (SortingCursorWrapper.class) {
                    /// M: Limit sorting scale with mCount because other position has no data(need't show).
                    binarySort(mPosition, mValueString, mCount);
                }
                break;
            case SORT_ORDER_LAST_MODIFIED:
            case SORT_ORDER_SIZE:
                /// M: Limit sorting scale with mCount because other position has no data(need't show).
                binarySort(mPosition, mValueLong, mCount);
                break;
        }
    }

    @Override
    public Bundle getExtras() {
        return mCursor.getExtras();
    }

    @Override
    public void close() {
        super.close();
        mCursor.close();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return mCursor.moveToPosition(mPosition[newPosition]);
    }

    @Override
    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    @Override
    public int getCount() {
        /// M: add to support drm, we filter some drm with given drm level,
        /// so we need return real cursor count.
        return mCount;
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public int getType(int column) {
        return mCursor.getType(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }

    /**
     * M: Modify this method, provide sort scale count from caller, so that it only sort these
     * positions in given scale(need show positions)
     * 
     * Borrowed from TimSort.binarySort(), but modified to sort two column
     * dataset.
     */
    private static void binarySort(int[] position, String[] value, final int count) {
        for (int start = 1; start < count; start++) {
            final int pivotPosition = position[start];
            final String pivotValue = value[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = (left + right) >>> 1;

                final String lhs = pivotValue;
                final String rhs = value[mid];
                final int compare;
                if (lhs == null) {
                    compare = -1;
                } else if (rhs == null) {
                    compare = 1;
                } else {
                    compare = lhs.compareToIgnoreCase(rhs);
                }

                if (compare < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    position[left + 2] = position[left + 1];
                    value[left + 2] = value[left + 1];
                case 1:
                    position[left + 1] = position[left];
                    value[left + 1] = value[left];
                    break;
                default:
                    System.arraycopy(position, left, position, left + 1, n);
                    System.arraycopy(value, left, value, left + 1, n);
            }

            position[left] = pivotPosition;
            value[left] = pivotValue;
        }
    }

    /**
     * M: Modify this method, provide sort scale count from caller, so that it only sort these
     * positions in given scale(need show positions)
     * 
     * Borrowed from TimSort.binarySort(), but modified to sort two column
     * dataset.
     */
    private static void binarySort(int[] position, long[] value, final int count) {
        for (int start = 1; start < count; start++) {
            final int pivotPosition = position[start];
            final long pivotValue = value[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = (left + right) >>> 1;

                final long lhs = pivotValue;
                final long rhs = value[mid];
                final int compare = Long.compare(lhs, rhs);
                if (compare > 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    position[left + 2] = position[left + 1];
                    value[left + 2] = value[left + 1];
                case 1:
                    position[left + 1] = position[left];
                    value[left + 1] = value[left];
                    break;
                default:
                    System.arraycopy(position, left, position, left + 1, n);
                    System.arraycopy(value, left, value, left + 1, n);
            }

            position[left] = pivotPosition;
            value[left] = pivotValue;
        }
    }
}
