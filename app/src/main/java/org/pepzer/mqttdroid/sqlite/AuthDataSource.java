/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.tworx.eud.mqttdroid.AuthState;
import com.tworx.eud.mqttdroid.Utils;

import java.util.ArrayList;
import java.util.List;

public class AuthDataSource {
    private static final String TAG = "AuthDataSource";

    private SQLiteDatabase database;
    private AuthSQLiteHelper dbHelper;
    private String[] allAuthDetailsColumns = { AuthSQLiteHelper.COLUMN_ID,
            AuthSQLiteHelper.COLUMN_APP_PACKAGE, AuthSQLiteHelper.COLUMN_APP_LABEL,
            AuthSQLiteHelper.COLUMN_TIMESTAMP, AuthSQLiteHelper.COLUMN_AUTH_STATUS};
    private String[] allAuthPubColumns = { AuthSQLiteHelper.COLUMN_ID,
            AuthSQLiteHelper.COLUMN_APP_PACKAGE, AuthSQLiteHelper.COLUMN_TOPIC};
    private String[] allAuthSubColumns = { AuthSQLiteHelper.COLUMN_ID,
            AuthSQLiteHelper.COLUMN_APP_PACKAGE, AuthSQLiteHelper.COLUMN_TOPIC,
            AuthSQLiteHelper.COLUMN_QOS, AuthSQLiteHelper.COLUMN_ACTIVE};


    public AuthDataSource(Context context) {
        dbHelper = new AuthSQLiteHelper(context);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public long createAuthDetails(String appPackage, String appLabel,
                                             long timestamp, AuthState authStatus)
            throws SQLiteConstraintException {
        ContentValues values = new ContentValues();
        values.put(AuthSQLiteHelper.COLUMN_APP_PACKAGE, appPackage);
        values.put(AuthSQLiteHelper.COLUMN_APP_LABEL, appLabel);
        values.put(AuthSQLiteHelper.COLUMN_TIMESTAMP, timestamp);
        values.put(AuthSQLiteHelper.COLUMN_AUTH_STATUS, authStatus.ordinal());
        long insertId = database.insertOrThrow(AuthSQLiteHelper.TABLE_AUTH_DETAILS, null,
                values);
        return insertId;
    }

    public int deleteAuthDetails(AppAuthDetails authDetails) {
        long id = authDetails.getId();
        int count = database.delete(AuthSQLiteHelper.TABLE_AUTH_DETAILS,
                AuthSQLiteHelper.COLUMN_ID + " = " + id, null);
        return count;
    }

    public int updateAuthDetails(AppAuthDetails authDetails) {
        ContentValues values = new ContentValues();
        values.put(AuthSQLiteHelper.COLUMN_APP_PACKAGE, authDetails.getAppPackage());
        values.put(AuthSQLiteHelper.COLUMN_APP_LABEL, authDetails.getAppLabel());
        values.put(AuthSQLiteHelper.COLUMN_TIMESTAMP, authDetails.getTimestamp());
        values.put(AuthSQLiteHelper.COLUMN_AUTH_STATUS, authDetails.getAuthStatus().ordinal());
        int count = database.update(AuthSQLiteHelper.TABLE_AUTH_DETAILS, values,
                AuthSQLiteHelper.COLUMN_ID + " = " + authDetails.getId(), null);
        return count;
    }

    public List<AppAuthDetails> getAllAuthDetails() {
        List<AppAuthDetails> detailsList = new ArrayList<>();
        Cursor cursor = database.query(AuthSQLiteHelper.TABLE_AUTH_DETAILS,
                allAuthDetailsColumns, null, null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            AppAuthDetails authDetails = cursorToAuthDetails(cursor);
            detailsList.add(authDetails);
            cursor.moveToNext();
        }
        cursor.close();
        return detailsList;
    }

    public AppAuthDetails getAuthDetailsByPkg(String packageName) {
        Cursor cursor = database.query(AuthSQLiteHelper.TABLE_AUTH_DETAILS,
                allAuthDetailsColumns, AuthSQLiteHelper.COLUMN_APP_PACKAGE + " = ?",
                new String[] {packageName}, null, null, null);
        cursor.moveToFirst();
        if (cursor.isAfterLast()) {
            return null;
        }
        AppAuthDetails authDetails = cursorToAuthDetails(cursor);
        cursor.close();
        return authDetails;
    }

    public int deleteAuthByPkg(String packageName) {
        AppAuthDetails authDetails = getAuthDetailsByPkg(packageName);

        int count = 0;

        if (authDetails != null) {
            count += deleteAuthDetails(authDetails);
        } else {
            Log.w(TAG, "deleteAuthByPkg, authDetails is null");
        }

        List<AppAuthPub> pubList = getAuthPubsByPkg(packageName);
        for (int i = 0; i < pubList.size(); ++i) {
            count += deleteAuthPub(pubList.get(i));
        }

        List<AppAuthSub> subList = getAuthSubsByPkg(packageName);
        for (int i = 0; i < subList.size(); ++i) {
            count += deleteAuthSub(subList.get(i));
        }

        return count;
    }

    private AppAuthDetails cursorToAuthDetails(Cursor cursor) {
        AppAuthDetails authDetails = new AppAuthDetails();
        authDetails.setId(cursor.getLong(0));
        authDetails.setAppPackage(cursor.getString(1));
        authDetails.setAppLabel(cursor.getString(2));
        authDetails.setTimestamp(cursor.getLong(3));
        authDetails.setAuthStatus(Utils.authStateIntToEnum(cursor.getInt(4)));
        return authDetails;
    }

    public long createAuthPub(String appPackage, String topic) {
        ContentValues values = new ContentValues();
        values.put(AuthSQLiteHelper.COLUMN_APP_PACKAGE, appPackage);
        values.put(AuthSQLiteHelper.COLUMN_TOPIC, topic);
        long insertId = database.insert(AuthSQLiteHelper.TABLE_AUTH_PUB, null,
                values);
        return insertId;
    }

    public int deleteAuthPub(AppAuthPub authPub) {
        long id = authPub.getId();
        int count = database.delete(AuthSQLiteHelper.TABLE_AUTH_PUB,
                AuthSQLiteHelper.COLUMN_ID + " = " + id, null);
        return count;
    }

   public List<AppAuthPub> getAuthPubsByPkg(String appPackage) {
        List<AppAuthPub> pubList = new ArrayList<>();
        Cursor cursor = database.query(AuthSQLiteHelper.TABLE_AUTH_PUB,
                allAuthPubColumns, AuthSQLiteHelper.COLUMN_APP_PACKAGE + " = ?",
                new String[] {appPackage} , null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            AppAuthPub authPub = cursorToAuthPub(cursor);
            pubList.add(authPub);
            cursor.moveToNext();
        }
        cursor.close();
        return pubList;
    }

    private AppAuthPub cursorToAuthPub(Cursor cursor) {
        AppAuthPub authPub = new AppAuthPub();
        authPub.setId(cursor.getLong(0));
        authPub.setAppPackage(cursor.getString(1));
        authPub.setTopic(cursor.getString(2));
        return authPub;
    }

    public long createAuthSub(String appPackage, String topic, int qos, boolean active) {
        ContentValues values = new ContentValues();
        values.put(AuthSQLiteHelper.COLUMN_APP_PACKAGE, appPackage);
        values.put(AuthSQLiteHelper.COLUMN_TOPIC, topic);
        values.put(AuthSQLiteHelper.COLUMN_QOS, qos);
        values.put(AuthSQLiteHelper.COLUMN_ACTIVE, Boolean.toString(active));
        long insertId = database.insert(AuthSQLiteHelper.TABLE_AUTH_SUB, null,
                values);
        return insertId;
    }

    public int updateAuthSub(AppAuthSub authSub) {
        ContentValues values = new ContentValues();
        values.put(AuthSQLiteHelper.COLUMN_APP_PACKAGE, authSub.getAppPackage());
        values.put(AuthSQLiteHelper.COLUMN_TOPIC, authSub.getTopic());
        values.put(AuthSQLiteHelper.COLUMN_QOS, authSub.getQos());
        values.put(AuthSQLiteHelper.COLUMN_ACTIVE, Boolean.toString(authSub.isActive()));
        int count = database.update(AuthSQLiteHelper.TABLE_AUTH_SUB, values,
                AuthSQLiteHelper.COLUMN_ID + " = " + authSub.getId(), null);
        return count;
    }


    public int deleteAuthSub(AppAuthSub authSub) {
        long id = authSub.getId();
        int count = database.delete(AuthSQLiteHelper.TABLE_AUTH_SUB,
                AuthSQLiteHelper.COLUMN_ID + " = " + id, null);
        return count;
    }

   public List<AppAuthSub> getAuthSubsByPkg(String appPackage) {
        List<AppAuthSub> subList = new ArrayList<>();
        Cursor cursor = database.query(AuthSQLiteHelper.TABLE_AUTH_SUB,
                allAuthSubColumns, AuthSQLiteHelper.COLUMN_APP_PACKAGE + " = ?",
                new String[] {appPackage} , null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            AppAuthSub authSub = cursorToAuthSub(cursor);
            subList.add(authSub);
            cursor.moveToNext();
        }
        cursor.close();
        return subList;
    }

    public AppAuthSub getAuthSub(String appPackage, String topic) {
        AppAuthSub authSub = null;
        Cursor cursor = database.query(AuthSQLiteHelper.TABLE_AUTH_SUB,
                allAuthSubColumns, AuthSQLiteHelper.COLUMN_APP_PACKAGE + " = ? and " +
                AuthSQLiteHelper.COLUMN_TOPIC + " = ?",
                new String[] {appPackage, topic} , null, null, null);
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            authSub = cursorToAuthSub(cursor);
        }
        cursor.close();
        return authSub;
    }

    public List<AppAuthSub> getActiveAuthSubsByPkg(String appPackage) {
        List<AppAuthSub> subList = new ArrayList<>();
        Cursor cursor = database.query(AuthSQLiteHelper.TABLE_AUTH_SUB,
                allAuthSubColumns, AuthSQLiteHelper.COLUMN_APP_PACKAGE + " = ? and " +
                        AuthSQLiteHelper.COLUMN_ACTIVE + " = ?",
                new String[] {appPackage, "true"} , null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            AppAuthSub authSub = cursorToAuthSub(cursor);
            subList.add(authSub);
            cursor.moveToNext();
        }
        cursor.close();
        return subList;
    }

    private AppAuthSub cursorToAuthSub(Cursor cursor) {
        AppAuthSub authSub = new AppAuthSub();
        authSub.setId(cursor.getLong(0));
        authSub.setAppPackage(cursor.getString(1));
        authSub.setTopic(cursor.getString(2));
        authSub.setQos(cursor.getInt(3));
        authSub.setActive(Boolean.parseBoolean(cursor.getString(4)));
        return authSub;
    }
}
