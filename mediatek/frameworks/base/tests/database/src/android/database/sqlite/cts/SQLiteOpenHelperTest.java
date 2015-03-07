/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.database.sqlite.cts;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.test.AndroidTestCase;

/**
 * Test {@link SQLiteOpenHelper}.
 */
public class SQLiteOpenHelperTest extends AndroidTestCase {
    private static final String TEST_DATABASE_NAME = "database_test.db";
    private static final int TEST_VERSION = 1;
    private static final int TEST_ILLEGAL_VERSION = 0;
    private MockOpenHelper mOpenHelper;
    private SQLiteDatabase.CursorFactory mFactory = new SQLiteDatabase.CursorFactory() {
        public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                String editTable, SQLiteQuery query) {
            return new MockCursor(db, masterQuery, editTable, query);
        }
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mOpenHelper = getOpenHelper();
    }

    public void testConstructor() {
        new MockOpenHelper(mContext, TEST_DATABASE_NAME, mFactory, TEST_VERSION);

        // Test with illegal version number.
        try {
            new MockOpenHelper(mContext, TEST_DATABASE_NAME, mFactory, TEST_ILLEGAL_VERSION);
            fail("Constructor of SQLiteOpenHelp should throws a IllegalArgumentException here.");
        } catch (IllegalArgumentException e) {
        }

        // Test with null factory
        new MockOpenHelper(mContext, TEST_DATABASE_NAME, null, TEST_VERSION);
    }

    public void testGetDatabase() {
        SQLiteDatabase database = null;
        assertFalse(mOpenHelper.hasCalledOnOpen());
        // Test getReadableDatabase.
        database = mOpenHelper.getReadableDatabase();
        assertNotNull(database);
        assertTrue(database.isOpen());
        assertTrue(mOpenHelper.hasCalledOnOpen());

        // Database has been opened, so onOpen can not be invoked.
        mOpenHelper.resetStatus();
        assertFalse(mOpenHelper.hasCalledOnOpen());
        // Test getWritableDatabase.
        SQLiteDatabase database2 = mOpenHelper.getWritableDatabase();
        assertSame(database, database2);
        assertTrue(database.isOpen());
        assertFalse(mOpenHelper.hasCalledOnOpen());

        mOpenHelper.close();
        assertFalse(database.isOpen());

        // After close(), onOpen() will be invoked by getWritableDatabase.
        mOpenHelper.resetStatus();
        assertFalse(mOpenHelper.hasCalledOnOpen());
        SQLiteDatabase database3 = mOpenHelper.getWritableDatabase();
        assertNotNull(database);
        assertNotSame(database, database3);
        assertTrue(mOpenHelper.hasCalledOnOpen());
        assertTrue(database3.isOpen());
        mOpenHelper.close();
        assertFalse(database3.isOpen());
    }

    private MockOpenHelper getOpenHelper() {
        return new MockOpenHelper(mContext, TEST_DATABASE_NAME, mFactory, TEST_VERSION);
    }

    private class MockOpenHelper extends SQLiteOpenHelper {
        private boolean mHasCalledOnOpen = false;

        public MockOpenHelper(Context context, String name, CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            mHasCalledOnOpen = true;
        }

        public boolean hasCalledOnOpen() {
            return mHasCalledOnOpen;
        }

        public void resetStatus() {
            mHasCalledOnOpen = false;
        }
    }

    private class MockCursor extends SQLiteCursor {
        public MockCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable,
                SQLiteQuery query) {
            super(db, driver, editTable, query);
        }
    }
}
