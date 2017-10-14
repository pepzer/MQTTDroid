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

import java.util.ArrayList;
import java.util.List;

public class ProxyDataSource {

    private SQLiteDatabase database;
    private ProxySQLiteHelper dbHelper;
    private String[] allConfigColumns = {
            ProxySQLiteHelper.COLUMN_PROFILE_ID, ProxySQLiteHelper.COLUMN_CLIENT_ID,
            ProxySQLiteHelper.COLUMN_USERNAME, ProxySQLiteHelper.COLUMN_PASSWORD,
            ProxySQLiteHelper.COLUMN_PROTOCOL, ProxySQLiteHelper.COLUMN_BROKER_ADDR,
            ProxySQLiteHelper.COLUMN_BROKER_PORT, ProxySQLiteHelper.COLUMN_CLEAN_SESSION,
            ProxySQLiteHelper.COLUMN_AUTO_RECONNECT, ProxySQLiteHelper.COLUMN_KEEPALIVE,
            ProxySQLiteHelper.COLUMN_COMPL_TIMEOUT, ProxySQLiteHelper.COLUMN_CUSTOM_CA,
            ProxySQLiteHelper.COLUMN_CRT_PATH};

    private String[] allStoredMsgColumns = {
            ProxySQLiteHelper.COLUMN_ID, ProxySQLiteHelper.COLUMN_RECEIVER,
            ProxySQLiteHelper.COLUMN_MSG_TOPIC, ProxySQLiteHelper.COLUMN_MSG_ID,
            ProxySQLiteHelper.COLUMN_MSG_QOS, ProxySQLiteHelper.COLUMN_MSG_PAYLOAD,
            ProxySQLiteHelper.COLUMN_MSG_DUPLICATE, ProxySQLiteHelper.COLUMN_MSG_RETAINED,
            ProxySQLiteHelper.COLUMN_TIMESTAMP};

    private String[] allPendingDeliveryColumns = {
            ProxySQLiteHelper.COLUMN_ID, ProxySQLiteHelper.COLUMN_SENDER,
            ProxySQLiteHelper.COLUMN_SENDER_MSG_ID, ProxySQLiteHelper.COLUMN_MSG_TOPIC,
            ProxySQLiteHelper.COLUMN_MSG_ID, ProxySQLiteHelper.COLUMN_COMPLETE,
            ProxySQLiteHelper.COLUMN_SUCCESS, ProxySQLiteHelper.COLUMN_TIMESTAMP};

    public ProxyDataSource(Context context) {
        dbHelper = new ProxySQLiteHelper(context);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public long createMqttConfig(MqttConfig mqttConfig)
            throws SQLiteConstraintException {
        ContentValues values = new ContentValues();
        values.put(ProxySQLiteHelper.COLUMN_PROFILE_ID, mqttConfig.getProfileId());
        values.put(ProxySQLiteHelper.COLUMN_CLIENT_ID, mqttConfig.getClientId());
        values.put(ProxySQLiteHelper.COLUMN_USERNAME, mqttConfig.getUsername());
        values.put(ProxySQLiteHelper.COLUMN_PASSWORD, mqttConfig.getPassword());
        values.put(ProxySQLiteHelper.COLUMN_PROTOCOL, mqttConfig.getProtocol());
        values.put(ProxySQLiteHelper.COLUMN_BROKER_ADDR, mqttConfig.getBrokerAddr());
        values.put(ProxySQLiteHelper.COLUMN_BROKER_PORT, mqttConfig.getBrokerPort());
        values.put(ProxySQLiteHelper.COLUMN_CLEAN_SESSION, Boolean.toString(mqttConfig.isCleanSession()));
        values.put(ProxySQLiteHelper.COLUMN_AUTO_RECONNECT, Boolean.toString(mqttConfig.isAutoReconnect()));
        values.put(ProxySQLiteHelper.COLUMN_KEEPALIVE, mqttConfig.getKeepalive());
        values.put(ProxySQLiteHelper.COLUMN_COMPL_TIMEOUT, mqttConfig.getComplTimeout());
        values.put(ProxySQLiteHelper.COLUMN_CUSTOM_CA, Boolean.toString(mqttConfig.isCustomCA()));
        values.put(ProxySQLiteHelper.COLUMN_CRT_PATH, mqttConfig.getCrtPath());

        long insertId = database.insertOrThrow(ProxySQLiteHelper.TABLE_MQTT_CONFIG, null,
                values);
        return insertId;
    }

    public int deleteMqttConfig(MqttConfig mqttConfig) {
        long profileId = mqttConfig.getProfileId();
        int count = database.delete(ProxySQLiteHelper.TABLE_MQTT_CONFIG,
                ProxySQLiteHelper.COLUMN_PROFILE_ID + " = " + profileId, null);
        return count;
    }

    public int updateMqttConfig(MqttConfig mqttConfig) {
        ContentValues values = new ContentValues();
        values.put(ProxySQLiteHelper.COLUMN_PROFILE_ID, mqttConfig.getProfileId());
        values.put(ProxySQLiteHelper.COLUMN_CLIENT_ID, mqttConfig.getClientId());
        values.put(ProxySQLiteHelper.COLUMN_USERNAME, mqttConfig.getUsername());
        values.put(ProxySQLiteHelper.COLUMN_PASSWORD, mqttConfig.getPassword());
        values.put(ProxySQLiteHelper.COLUMN_PROTOCOL, mqttConfig.getProtocol());
        values.put(ProxySQLiteHelper.COLUMN_BROKER_ADDR, mqttConfig.getBrokerAddr());
        values.put(ProxySQLiteHelper.COLUMN_BROKER_PORT, mqttConfig.getBrokerPort());
        values.put(ProxySQLiteHelper.COLUMN_CLEAN_SESSION, Boolean.toString(mqttConfig.isCleanSession()));
        values.put(ProxySQLiteHelper.COLUMN_AUTO_RECONNECT, Boolean.toString(mqttConfig.isAutoReconnect()));
        values.put(ProxySQLiteHelper.COLUMN_KEEPALIVE, mqttConfig.getKeepalive());
        values.put(ProxySQLiteHelper.COLUMN_COMPL_TIMEOUT, mqttConfig.getComplTimeout());
        values.put(ProxySQLiteHelper.COLUMN_CUSTOM_CA, Boolean.toString(mqttConfig.isCustomCA()));
        values.put(ProxySQLiteHelper.COLUMN_CRT_PATH, mqttConfig.getCrtPath());

        int count = database.update(ProxySQLiteHelper.TABLE_MQTT_CONFIG, values,
                ProxySQLiteHelper.COLUMN_PROFILE_ID + " = " + mqttConfig.getProfileId(), null);
        return count;
    }

    public MqttConfig getMqttConfig(long profileId) {
        Cursor cursor = database.query(ProxySQLiteHelper.TABLE_MQTT_CONFIG,
                allConfigColumns, ProxySQLiteHelper.COLUMN_PROFILE_ID + " = " + profileId,
                null, null, null, null);
        cursor.moveToFirst();
        if (cursor.isAfterLast()) {
            return null;
        }
        MqttConfig mqttConfig = cursorToMqttConfig(cursor);
        cursor.close();
        return mqttConfig;
    }

    private MqttConfig cursorToMqttConfig(Cursor cursor) {
        MqttConfig mqttConfig = new MqttConfig();
        mqttConfig.setProfileId(cursor.getInt(0));
        mqttConfig.setClientId(cursor.getString(1));
        mqttConfig.setUsername(cursor.getString(2));
        mqttConfig.setPassword(cursor.getString(3));
        mqttConfig.setProtocol(cursor.getString(4));
        mqttConfig.setBrokerAddr(cursor.getString(5));
        mqttConfig.setBrokerPort(cursor.getInt(6));
        mqttConfig.setCleanSession(Boolean.parseBoolean(cursor.getString(7)));
        mqttConfig.setAutoReconnect(Boolean.parseBoolean(cursor.getString(8)));
        mqttConfig.setKeepalive(cursor.getInt(9));
        mqttConfig.setComplTimeout(cursor.getInt(10));
        mqttConfig.setCustomCA(Boolean.parseBoolean(cursor.getString(11)));
        mqttConfig.setCrtPath(cursor.getString(12));
        return mqttConfig;
    }

    public long createStoredMsg(MqttStoredMsg msg)
            throws SQLiteConstraintException {
        ContentValues values = new ContentValues();
        values.put(ProxySQLiteHelper.COLUMN_RECEIVER, msg.getReceiver());
        values.put(ProxySQLiteHelper.COLUMN_MSG_TOPIC, msg.getTopic());
        values.put(ProxySQLiteHelper.COLUMN_MSG_ID, msg.getMsgId());
        values.put(ProxySQLiteHelper.COLUMN_MSG_QOS, msg.getQos());
        values.put(ProxySQLiteHelper.COLUMN_MSG_PAYLOAD, msg.getPayload());
        values.put(ProxySQLiteHelper.COLUMN_MSG_DUPLICATE, Boolean.toString(msg.isDuplicate()));
        values.put(ProxySQLiteHelper.COLUMN_MSG_RETAINED, Boolean.toString(msg.isRetained()));
        values.put(ProxySQLiteHelper.COLUMN_TIMESTAMP, msg.getTimestamp());

        long insertId = database.insertOrThrow(ProxySQLiteHelper.TABLE_MQTT_STORED_MSG, null,
                values);
        return insertId;
    }

    public int deleteStoredMsg(MqttStoredMsg msg) {
        long id = msg.getId();
        int count = database.delete(ProxySQLiteHelper.TABLE_MQTT_STORED_MSG,
                ProxySQLiteHelper.COLUMN_ID + " = " + id, null);
        return count;
    }

    public List<MqttStoredMsg> getStoredMsgByRcv(String receiver) {
        List<MqttStoredMsg> msgList = new ArrayList<>();
        Cursor cursor = database.query(ProxySQLiteHelper.TABLE_MQTT_STORED_MSG,
                allStoredMsgColumns, ProxySQLiteHelper.COLUMN_RECEIVER + " = ?",
                new String[] {receiver} , null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            MqttStoredMsg msg = cursorToStoredMsg(cursor);
            msgList.add(msg);
            cursor.moveToNext();
        }
        cursor.close();
        return msgList;
    }

    private MqttStoredMsg cursorToStoredMsg(Cursor cursor) {
        MqttStoredMsg msg = new MqttStoredMsg();
        msg.setId(cursor.getLong(0));
        msg.setReceiver(cursor.getString(1));
        msg.setTopic(cursor.getString(2));
        msg.setMsgId(cursor.getInt(3));
        msg.setQos(cursor.getInt(4));
        msg.setPayload(cursor.getBlob(5));
        msg.setDuplicate(Boolean.parseBoolean(cursor.getString(6)));
        msg.setRetained(Boolean.parseBoolean(cursor.getString(7)));
        msg.setTimestamp(cursor.getLong(8));
        return msg;
    }

    public long createMsgDelivery(MqttMsgDelivery delivery)
            throws SQLiteConstraintException {
        ContentValues values = new ContentValues();
        values.put(ProxySQLiteHelper.COLUMN_SENDER, delivery.getSender());
        values.put(ProxySQLiteHelper.COLUMN_SENDER_MSG_ID, delivery.getSenderMsgId());
        values.put(ProxySQLiteHelper.COLUMN_MSG_TOPIC, delivery.getTopic());
        values.put(ProxySQLiteHelper.COLUMN_MSG_ID, delivery.getMsgId());
        values.put(ProxySQLiteHelper.COLUMN_COMPLETE, Boolean.toString(delivery.isComplete()));
        values.put(ProxySQLiteHelper.COLUMN_SUCCESS, Boolean.toString(delivery.isSuccess()));
        values.put(ProxySQLiteHelper.COLUMN_TIMESTAMP, delivery.getTimestamp());

        long insertId = database.insertOrThrow(ProxySQLiteHelper.TABLE_MQTT_PENDING_DELIVERY, null,
                values);
        return insertId;
    }

    public int updateMsgDelivery(MqttMsgDelivery delivery) {
        ContentValues values = new ContentValues();
        values.put(ProxySQLiteHelper.COLUMN_ID, delivery.getId());
        values.put(ProxySQLiteHelper.COLUMN_SENDER, delivery.getSender());
        values.put(ProxySQLiteHelper.COLUMN_SENDER_MSG_ID, delivery.getSenderMsgId());
        values.put(ProxySQLiteHelper.COLUMN_MSG_TOPIC, delivery.getTopic());
        values.put(ProxySQLiteHelper.COLUMN_MSG_ID, delivery.getMsgId());
        values.put(ProxySQLiteHelper.COLUMN_COMPLETE, Boolean.toString(delivery.isComplete()));
        values.put(ProxySQLiteHelper.COLUMN_SUCCESS, Boolean.toString(delivery.isSuccess()));
        values.put(ProxySQLiteHelper.COLUMN_TIMESTAMP, delivery.getTimestamp());

        int count = database.update(ProxySQLiteHelper.TABLE_MQTT_PENDING_DELIVERY, values,
                ProxySQLiteHelper.COLUMN_ID + " = " + delivery.getId(), null);

        return count;
    }

    public int deleteMsgDelivery(MqttMsgDelivery delivery) {
        long id = delivery.getId();
        int count = database.delete(ProxySQLiteHelper.TABLE_MQTT_PENDING_DELIVERY,
                ProxySQLiteHelper.COLUMN_ID + " = " + id, null);
        return count;
    }

    public MqttMsgDelivery getMsgDeliveryByMsgId(int msgId) {
        Cursor cursor = database.query(ProxySQLiteHelper.TABLE_MQTT_PENDING_DELIVERY,
                allPendingDeliveryColumns, ProxySQLiteHelper.COLUMN_MSG_ID + " = " + msgId,
                null , null, null, null);
        cursor.moveToFirst();
        MqttMsgDelivery delivery = null;
        if (!cursor.isAfterLast()) {
            delivery = cursorToMsgDelivery(cursor);
            cursor.moveToNext();
        }
        cursor.close();
        return delivery;
    }

    public List<MqttMsgDelivery> getCompleteDeliveryBySender(String sender) {
        List<MqttMsgDelivery> deliveryList = new ArrayList<>();
        Cursor cursor = database.query(ProxySQLiteHelper.TABLE_MQTT_PENDING_DELIVERY,
                allPendingDeliveryColumns, ProxySQLiteHelper.COLUMN_SENDER + " = ? and " +
                ProxySQLiteHelper.COLUMN_COMPLETE + " = ?",
                new String[] {sender, "true"} , null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            MqttMsgDelivery delivery = cursorToMsgDelivery(cursor);
            deliveryList.add(delivery);
            cursor.moveToNext();
        }
        cursor.close();
        return deliveryList;
    }

    private MqttMsgDelivery cursorToMsgDelivery(Cursor cursor) {
        MqttMsgDelivery delivery = new MqttMsgDelivery();
        delivery.setId(cursor.getLong(0));
        delivery.setSender(cursor.getString(1));
        delivery.setSenderMsgId(cursor.getInt(2));
        delivery.setTopic(cursor.getString(3));
        delivery.setMsgId(cursor.getInt(4));
        delivery.setComplete(Boolean.parseBoolean(cursor.getString(5)));
        delivery.setSuccess(Boolean.parseBoolean(cursor.getString(6)));
        delivery.setTimestamp(cursor.getLong(7));
        return delivery;
    }


}
