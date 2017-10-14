/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class AuthSQLiteHelper extends SQLiteOpenHelper {
    public static final String TABLE_AUTH_DETAILS = "auth_details";
    public static final String TABLE_AUTH_PUB = "auth_pub";
    public static final String TABLE_AUTH_SUB = "auth_sub";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_APP_PACKAGE = "app_package";
    public static final String COLUMN_APP_LABEL = "app_label";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_AUTH_STATUS = "auth_status";
    public static final String COLUMN_TOPIC = "topic";
    public static final String COLUMN_QOS = "qos";
    public static final String COLUMN_ACTIVE = "active";

    private static final String DATABASE_NAME = "auth.db";
    private static final int DATABASE_VERSION = 1;

    // Database creation sql statement
    private static final String DATABASE_CREATE_TABLE_DETAIL = "create table "
            + TABLE_AUTH_DETAILS + "( " +
            COLUMN_ID + " integer primary key autoincrement, " +
            COLUMN_APP_PACKAGE + " text not null unique, " +
            COLUMN_APP_LABEL + " text not null, " +
            COLUMN_TIMESTAMP + " integer not null, " +
            COLUMN_AUTH_STATUS + " integer not null);";

    private static final String DATABASE_CREATE_TABLE_PUB = "create table "
            + TABLE_AUTH_PUB + "( " +
            COLUMN_ID + " integer primary key autoincrement, " +
            COLUMN_APP_PACKAGE + " text not null, " +
            COLUMN_TOPIC + " text not null);";


    private static final String DATABASE_CREATE_TABLE_SUB = "create table "
            + TABLE_AUTH_SUB + "( " +
            COLUMN_ID + " integer primary key autoincrement, " +
            COLUMN_APP_PACKAGE + " text not null, " +
            COLUMN_TOPIC + " text not null, " +
            COLUMN_QOS + " integer not null, " +
            COLUMN_ACTIVE + " text not null);";

    public AuthSQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE_TABLE_DETAIL);
        database.execSQL(DATABASE_CREATE_TABLE_PUB);
        database.execSQL(DATABASE_CREATE_TABLE_SUB);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(AuthSQLiteHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_AUTH_DETAILS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_AUTH_PUB);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_AUTH_SUB);
        onCreate(db);
    }
}
