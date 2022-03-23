/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid.sqlite;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;

@RunWith(AndroidJUnit4.class)

public class ProxyDataSourceTest {
    private ProxyDataSource proxyDataSource;
    private String sender  = "org.mqttdroid.test.sender";
    private String receiver = "org.mqttdroid.test.receiver";
    private int profile = 99999;
    private int msgId = 9999;

    @Before
    public void setUp() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        proxyDataSource = new ProxyDataSource(appContext);
        proxyDataSource.open();
    }

    @After
    public void tearDown() throws Exception {
        //Try to clean the test config in case an error occurred.
        MqttConfig mqttConfigDB = proxyDataSource.getMqttConfig(profile);
        if (mqttConfigDB != null) {
            proxyDataSource.deleteMqttConfig(mqttConfigDB);
        }

        //Try to clean any test msg in case an error occurred.
        List<MqttStoredMsg> msgList = proxyDataSource.getStoredMsgByRcv(receiver);
        for (MqttStoredMsg msg : msgList) {
            proxyDataSource.deleteStoredMsg(msg);
        }

        //Try to clean the test delivery in case an error occurred.
        MqttMsgDelivery delivery = proxyDataSource.getMsgDeliveryByMsgId(msgId);
        if (delivery != null) {
            proxyDataSource.deleteMsgDelivery(delivery);
        }

        proxyDataSource.close();
    }

    /**
     * Assert equality of two MqttConfig objects field by field.
     *
     * @param mqttConfigDB
     *   The first object to compare with the target.
     * @param mqttConfig
     *   The target to compare the first object with.
     * @throws Exception
     */
    private void assertEqualsMqttConfig(MqttConfig mqttConfigDB, MqttConfig mqttConfig)
            throws Exception {
        assertThat(mqttConfigDB.getProfileId(), is(mqttConfig.getProfileId()));
        assertThat(mqttConfigDB.getClientId(), is(mqttConfig.getClientId()));
        assertThat(mqttConfigDB.getUsername(), is(mqttConfig.getUsername()));
        assertThat(mqttConfigDB.getPassword(), is(mqttConfig.getPassword()));
        assertThat(mqttConfigDB.getProtocol(), is(mqttConfig.getProtocol()));
        assertThat(mqttConfigDB.getBrokerAddr(), is(mqttConfig.getBrokerAddr()));
        assertThat(mqttConfigDB.getBrokerPort(), is(mqttConfig.getBrokerPort()));
        assertThat(mqttConfigDB.isCleanSession(), is(mqttConfig.isCleanSession()));
        assertThat(mqttConfigDB.isAutoReconnect(), is(mqttConfig.isAutoReconnect()));
        assertThat(mqttConfigDB.getKeepalive(), is(mqttConfig.getKeepalive()));
        assertThat(mqttConfigDB.getComplTimeout(), is(mqttConfig.getComplTimeout()));
        assertThat(mqttConfigDB.isCustomCA(), is(mqttConfig.isCustomCA()));
        assertThat(mqttConfigDB.getCrtPath(), is(mqttConfig.getCrtPath()));
    }

    /**
     * Insert, retrieve, modify, delete a configuration row.
     *
     * This test should revert all changes to the database before termination.
     *
     * @throws Exception
     */
    @Test
    public void testMqttConfig() throws Exception {

        MqttConfig mqttConfig = new MqttConfig();

        mqttConfig.setProfileId(profile);
        mqttConfig.setClientId("testClientId");
        mqttConfig.setUsername("testUser");
        mqttConfig.setPassword("testPass");
        mqttConfig.setProtocol("TCP");
        mqttConfig.setBrokerAddr("localhost");
        mqttConfig.setBrokerPort(1883);
        mqttConfig.setCleanSession(true);
        mqttConfig.setAutoReconnect(false);
        mqttConfig.setKeepalive(60);
        mqttConfig.setComplTimeout(3000);
        mqttConfig.setCustomCA(false);
        mqttConfig.setCrtPath("");

        //Create a new config row.
        proxyDataSource.createMqttConfig(mqttConfig);

        MqttConfig mqttConfigDB = proxyDataSource.getMqttConfig(profile);

        //Verify it got saved and retrieved correctly.
        assertEqualsMqttConfig(mqttConfigDB, mqttConfig);

        mqttConfig.setClientId("newTestClientId");
        mqttConfig.setProtocol("SSL");

        //Update the row on the db.
        int count = proxyDataSource.updateMqttConfig(mqttConfig);
        assertThat(count, is(1));

        mqttConfigDB = proxyDataSource.getMqttConfig(profile);

        //Verify it got updated correctly.
        assertEqualsMqttConfig(mqttConfigDB, mqttConfig);

        //Delete the config row.
        count = proxyDataSource.deleteMqttConfig(mqttConfig);
        assertThat(count, is(1));

        mqttConfigDB = proxyDataSource.getMqttConfig(profile);

        //Verify the row got deleted.
        assertEquals(mqttConfigDB, null);
    }

    /**
     * Assert equality of two MqttStoredMsg objects field by field.
     *
     * @param storedMsgDB
     *   The first object to compare with the target.
     * @param storedMsg
     *   The target to compare the first object with.
     * @throws Exception
     */
    private void assertEqualsStoredMsg(MqttStoredMsg storedMsgDB, MqttStoredMsg storedMsg)
            throws Exception {
        assertThat(storedMsgDB.getId(), is(storedMsg.getId()));
        assertThat(storedMsgDB.getReceiver(), is(storedMsg.getReceiver()));
        assertThat(storedMsgDB.getTopic(), is(storedMsg.getTopic()));
        assertThat(storedMsgDB.getMsgId(), is(storedMsg.getMsgId()));
        assertThat(storedMsgDB.getQos(), is(storedMsg.getQos()));
        assertThat(storedMsgDB.getPayload(), is(storedMsg.getPayload()));
        assertThat(storedMsgDB.isDuplicate(), is(storedMsg.isDuplicate()));
        assertThat(storedMsgDB.isRetained(), is(storedMsg.isRetained()));
        assertThat(storedMsgDB.getTimestamp(), is(storedMsg.getTimestamp()));
    }

    /**
     * Insert, retrieve, delete a message row.
     *
     * This test should revert all changes to the database before termination.
     *
     * @throws Exception
     */
    @Test
    public void testStoredMsg() throws Exception {

        MqttStoredMsg msg = new MqttStoredMsg();
        msg.setReceiver(receiver);
        msg.setTopic("/test/storedmsg");
        msg.setMsgId(99);
        msg.setQos(0);
        msg.setPayload("test payload".getBytes());
        msg.setDuplicate(false);
        msg.setRetained(false);
        msg.setTimestamp((new Date()).getTime());

        //Create a new msg row.
        long rowId = proxyDataSource.createStoredMsg(msg);
        msg.setId(rowId);

        //Retrieve all messages for the receiver.
        List<MqttStoredMsg> msgList = proxyDataSource.getStoredMsgByRcv(receiver);

        assertThat(msgList.size(), is(1));

        //Verify it got saved and retrieved correctly.
        assertEqualsStoredMsg(msgList.get(0), msg);

        //Delete the message from the db.
        int count = proxyDataSource.deleteStoredMsg(msg);
        assertThat(count, is(1));

        //Retrieve all messages for the receiver.
        msgList = proxyDataSource.getStoredMsgByRcv(receiver);

        //The list should be empty.
        assertThat(msgList.size(), is(0));
    }

    /**
     * Assert equality of two MqttMsgDelivery objects field by field.
     *
     * @param deliveryDB
     *   The first object to compare with the target.
     * @param delivery
     *   The target to compare the first object with.
     * @throws Exception
     */
    private void assertEqualsMsgDelivery(MqttMsgDelivery deliveryDB, MqttMsgDelivery delivery)
        throws Exception {
        assertThat(deliveryDB.getId(), is(delivery.getId()));
        assertThat(deliveryDB.getSender(), is(delivery.getSender()));
        assertThat(deliveryDB.getSenderMsgId(), is(delivery.getSenderMsgId()));
        assertThat(deliveryDB.getTopic(), is(delivery.getTopic()));
        assertThat(deliveryDB.getMsgId(), is(delivery.getMsgId()));
        assertThat(deliveryDB.isComplete(), is(delivery.isComplete()));
        assertThat(deliveryDB.isSuccess(), is(delivery.isSuccess()));
        assertThat(deliveryDB.getTimestamp(), is(delivery.getTimestamp()));
    }

    /**
     * Insert, retrieve, modify, delete a delivery receipt row.
     *
     * This test should revert all changes to the database before termination.
     *
     * @throws Exception
     */
    @Test
    public void testMsgDelivery() throws Exception {

        MqttMsgDelivery delivery = new MqttMsgDelivery();
        delivery.setSender(sender);
        delivery.setSenderMsgId(0);
        delivery.setTopic("/test/delivery");
        delivery.setMsgId(msgId);
        delivery.setComplete(false);
        delivery.setSuccess(false);
        delivery.setTimestamp((new Date()).getTime());

        //Create delivery row.
        long rowId = proxyDataSource.createMsgDelivery(delivery);
        delivery.setId(rowId);

        //Retrieve the delivery by msgId.
        MqttMsgDelivery deliveryDB = proxyDataSource.getMsgDeliveryByMsgId(msgId);

        //Assert the objects match.
        assertEqualsMsgDelivery(deliveryDB, delivery);

        delivery.setSuccess(true);
        delivery.setComplete(true);

        //Update the delivery in the db.
        int count = proxyDataSource.updateMsgDelivery(delivery);
        assertThat(count, is(1));

        //Find all complete deliveries relative to messages from sender.
        List<MqttMsgDelivery> deliveryList = proxyDataSource.getCompleteDeliveryBySender(sender);

        assertThat(deliveryList.size(), is(1));

        //Check the delivery got updated.
        assertEqualsMsgDelivery(deliveryList.get(0), delivery);

        //Delete the row from the delivery table.
        count = proxyDataSource.deleteMsgDelivery(delivery);
        assertThat(count, is(1));

        deliveryDB = proxyDataSource.getMsgDeliveryByMsgId(msgId);

        //Assert that the delivery is now deleted.
        assertEquals(deliveryDB, null);
    }
}
