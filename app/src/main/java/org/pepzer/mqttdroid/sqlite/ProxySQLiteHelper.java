/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


public class ProxySQLiteHelper extends SQLiteOpenHelper {
    public static final String TABLE_MQTT_CONFIG = "mqtt_config";
    public static final String TABLE_MQTT_STORED_MSG = "mqtt_stored_msg";
    public static final String TABLE_MQTT_PENDING_DELIVERY = "mqtt_pending_delivery";
    //public static final String TABLE_MQTT_PROFILE = "mqtt_profile";
    //public static final String TABLE_MQTT_EXT_SOURCES = "mqtt_ext_sources";

    public static final String COLUMN_PROFILE_ID = "profile_id";
    public static final String COLUMN_CLIENT_ID = "client_id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_PROTOCOL = "protocol";
    public static final String COLUMN_BROKER_ADDR = "broker_addr";
    public static final String COLUMN_BROKER_PORT = "broker_port";
    public static final String COLUMN_CLEAN_SESSION = "clean_session";
    public static final String COLUMN_AUTO_RECONNECT = "auto_reconnect";
    public static final String COLUMN_KEEPALIVE = "keepalive";
    public static final String COLUMN_COMPL_TIMEOUT = "compl_timeout";
    public static final String COLUMN_CUSTOM_CA = "custom_ca";
    public static final String COLUMN_CRT_PATH = "crt_path";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_RECEIVER = "receiver";
    public static final String COLUMN_MSG_TOPIC = "msg_topic";
    public static final String COLUMN_MSG_ID = "msg_id";
    public static final String COLUMN_MSG_QOS = "msg_qos";
    public static final String COLUMN_MSG_PAYLOAD = "msg_payload";
    public static final String COLUMN_MSG_DUPLICATE = "msg_duplicate";
    public static final String COLUMN_MSG_RETAINED = "msg_retained";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    public static final String COLUMN_SENDER = "sender";
    public static final String COLUMN_SENDER_MSG_ID = "sender_msg_id";
    public static final String COLUMN_COMPLETE = "complete";
    public static final String COLUMN_SUCCESS = "success";

    private static final String DATABASE_NAME = "mqtt.db";
    private static final int DATABASE_VERSION = 1;

    // Database creation sql statement
    private static final String DATABASE_CREATE_TABLE_CONFIG = "create table "
            + TABLE_MQTT_CONFIG + "( " +
            COLUMN_PROFILE_ID + " integer primary key, " +
            COLUMN_CLIENT_ID + " text not null, " +
            COLUMN_USERNAME + " text not null, " +
            COLUMN_PASSWORD + " text not null, " +
            COLUMN_PROTOCOL + " text not null, " +
            COLUMN_BROKER_ADDR + " text not null, " +
            COLUMN_BROKER_PORT + " integer not null, " +
            COLUMN_CLEAN_SESSION + " text not null, " +
            COLUMN_AUTO_RECONNECT + " text not null, " +
            COLUMN_KEEPALIVE + " integer not null, " +
            COLUMN_COMPL_TIMEOUT + " integer not null, " +
            COLUMN_CUSTOM_CA + " text not null, " +
            COLUMN_CRT_PATH + " text not null);";

    private static final String DATABASE_CREATE_TABLE_STORED_MSG = "create table "
            + TABLE_MQTT_STORED_MSG + "( " +
            COLUMN_ID + " integer primary key autoincrement, " +
            COLUMN_RECEIVER + " text not null, " +
            COLUMN_MSG_TOPIC + " text not null, " +
            COLUMN_MSG_ID + " integer not null, " +
            COLUMN_MSG_QOS + " integer not null, " +
            COLUMN_MSG_PAYLOAD + " blob not null, " +
            COLUMN_MSG_DUPLICATE + " text not null, " +
            COLUMN_MSG_RETAINED + " text not null, " +
            COLUMN_TIMESTAMP + " integer not null);";

    private static final String DATABASE_CREATE_TABLE_PENDING_DELIVERY = "create table "
            + TABLE_MQTT_PENDING_DELIVERY + "( " +
            COLUMN_ID + " integer primary key autoincrement, " +
            COLUMN_SENDER + " text not null, " +
            COLUMN_SENDER_MSG_ID + " integer not null, " +
            COLUMN_MSG_TOPIC + " text not null, " +
            COLUMN_MSG_ID + " integer not null, " +
            COLUMN_COMPLETE + " text not null, " +
            COLUMN_SUCCESS + " text not null, " +
            COLUMN_TIMESTAMP + " integer not null);";

    public ProxySQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE_TABLE_CONFIG);
        database.execSQL(DATABASE_CREATE_TABLE_STORED_MSG);
        database.execSQL(DATABASE_CREATE_TABLE_PENDING_DELIVERY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(ProxySQLiteHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MQTT_CONFIG);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MQTT_STORED_MSG);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MQTT_PENDING_DELIVERY);
        onCreate(db);
    }
}
