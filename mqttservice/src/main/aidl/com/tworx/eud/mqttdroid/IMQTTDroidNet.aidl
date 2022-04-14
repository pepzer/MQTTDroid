// IMQTTDroidNet.aidl
package com.tworx.eud.mqttdroid;

// Declare any non-default types here with import statements
import com.tworx.eud.mqttdroid.IMQTTDroidNetCallback;

interface IMQTTDroidNet {

    // see ProxyState enum for use in apps
    const int PROXY_STARTING = 0;
    const int PROXY_STOPPED = 1;
    const int PROXY_CONNECTED = 2;
    const int PROXY_DISCONNECTED = 3;
    const int PROXY_STOPPING = 4;

    void tryConnect();
    int getAuthState();
    int getProxyState();
    Map getActiveSubscriptions();
    boolean subscribe(String topic, int qos);
    boolean unsubscribe(String topic);
    boolean publish(int id, String topic, in byte[] payload, int qos, boolean retained);
    boolean registerNetCallback(IMQTTDroidNetCallback callback);
    IBinder getControlBinder();
}
