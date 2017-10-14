// IMQTTDroid.aidl
package org.pepzer.mqttdroid;

import org.pepzer.mqttdroid.IMQTTDroidCallback;

interface IMQTTDroid {

    int getProxyState();
    void stopProxy();
    void restartProxy();
    void registerCallback(IMQTTDroidCallback callback);
}
