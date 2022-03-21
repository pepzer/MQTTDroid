// IMQTTDroidAuth.aidl
package com.tworx.eud.mqttdroid;

import com.tworx.eud.mqttdroid.IMQTTDroidAuthCallback;

interface IMQTTDroidAuth {

    void authRequest(in Map topics, boolean update);
    void registerCallback(IMQTTDroidAuthCallback callback);
}
