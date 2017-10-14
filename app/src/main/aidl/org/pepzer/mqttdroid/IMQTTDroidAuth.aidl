// IMQTTDroidAuth.aidl
package org.pepzer.mqttdroid;

import org.pepzer.mqttdroid.IMQTTDroidAuthCallback;

interface IMQTTDroidAuth {

    void authRequest(in Map topics, boolean update);
    void registerCallback(IMQTTDroidAuthCallback callback);
}
