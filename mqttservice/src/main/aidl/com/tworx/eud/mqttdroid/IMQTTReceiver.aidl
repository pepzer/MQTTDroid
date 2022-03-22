package com.tworx.eud.mqttdroid;

interface IMQTTReceiver {

    void msgArrived(String topic, int id, in byte[] payload, boolean duplicated, boolean retained);
    void deliveryResult(String topic, int id, boolean success);

}
