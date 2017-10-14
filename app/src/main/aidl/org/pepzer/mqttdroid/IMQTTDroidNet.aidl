// IMQTTDroidNet.aidl
package org.pepzer.mqttdroid;

// Declare any non-default types here with import statements
import org.pepzer.mqttdroid.IMQTTDroidNetCallback;

interface IMQTTDroidNet {

     int getAuthState();
     int getProxyState();
     Map getActiveSubscriptions();
     boolean subscribe(String topic, int qos);
     boolean unsubscribe(String topic);
     boolean publish(int id, String topic, in byte[] payload, int qos, boolean retained);
     boolean registerNetCallback(IMQTTDroidNetCallback callback);
     IBinder getControlBinder();
}
