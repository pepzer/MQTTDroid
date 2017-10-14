package org.pepzer.mqttdroid;

interface IMQTTDroidNetCallback {

    void proxyStateChanged(int proxyState);
    void subscribeCallback(String topic, boolean success);
    void unsubscribeCallback(String topic, boolean success);
    void publishCallback(String topic, int msgId, boolean success);
    void msgArrived(String topic, int id, in byte[] payload, boolean duplicated, boolean retained);
}
