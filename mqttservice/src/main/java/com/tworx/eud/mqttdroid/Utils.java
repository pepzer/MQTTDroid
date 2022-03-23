package com.tworx.eud.mqttdroid;

import android.content.pm.PackageManager;
import android.os.Binder;

public class Utils {
    final public static String REQ_PUB = "Publish";
    final public static String REQ_SUB = "Subscribe";

    /**
     * Obtain the package name of the process that invoked an interface method.
     * @param pm
     *   PackageManager instance.
     * @return
     *   The package name of the calling process.
     */
    public static String getCallerPackage(PackageManager pm) {
        int caller = Binder.getCallingUid();
        if (caller == 0) {
            return "n/a";
        }
        String[] packages = pm.getPackagesForUid(caller);
        if (packages != null && packages.length > 0) {
            return packages[0];
        }
        return "n/a";
    }

    public static AuthState authStateIntToEnum(int authState) {
        switch (authState) {
            case IMQTTDroidAuth.APP_ALLOWED:
                return AuthState.APP_ALLOWED;
            case IMQTTDroidAuth.APP_REFUSED:
                return AuthState.APP_REFUSED;
            case IMQTTDroidAuth.APP_UNKNOWN:
                return AuthState.APP_UNKNOWN;
            default:
                return AuthState.UNKNOWN;
        }
    }

    public static ProxyState proxyStateIntToEnum(int proxyState) {
        switch (proxyState) {
            case IMQTTDroidNet.PROXY_CONNECTED:
                return ProxyState.PROXY_CONNECTED;
            case IMQTTDroidNet.PROXY_DISCONNECTED:
                return ProxyState.PROXY_DISCONNECTED;
            case IMQTTDroidNet.PROXY_STARTING:
                return ProxyState.PROXY_STARTING;
            case IMQTTDroidNet.PROXY_STOPPING:
                return ProxyState.PROXY_STOPPING;
            case IMQTTDroidNet.PROXY_STOPPED:
                return ProxyState.PROXY_STOPPED;
            default:
                return ProxyState.UNKNOWN;
        }
    }

}
