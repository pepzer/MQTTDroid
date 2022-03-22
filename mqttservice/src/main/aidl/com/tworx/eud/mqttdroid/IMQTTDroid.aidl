// IMQTTDroid.aidl
package com.tworx.eud.mqttdroid;

import com.tworx.eud.mqttdroid.IMQTTDroidCallback;

interface IMQTTDroid {

    int getProxyState();
    void stopProxy();
    void restartProxy();
    void registerCallback(IMQTTDroidCallback callback);
}
