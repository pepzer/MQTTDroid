// IMQTTDroidAuth.aidl
package com.tworx.eud.mqttdroid;

import com.tworx.eud.mqttdroid.IMQTTDroidAuthCallback;

interface IMQTTDroidAuth {

    // see AuthState enum for use in apps
    const int APP_ALLOWED = 0;
    const int APP_REFUSED = 1;
    const int APP_UNKNOWN = 2;

    void authRequest(in Map topics, boolean update);
    void registerCallback(IMQTTDroidAuthCallback callback);
}
