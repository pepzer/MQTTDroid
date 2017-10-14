/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import android.content.pm.PackageManager;
import android.os.Binder;

public class Utils {
    final public static String REQ_PUB = "Publish";
    final public static String REQ_SUB = "Subscribe";

    final public static String PREF_PROXY_ACTIVE = "proxy_active";
    final public static String PREF_FIRST_RUN = "first_run";
    final public static String PREF_CONFIG_CHANGE = "config_change";
    final public static int APP_ALLOWED = 0;
    final public static int APP_REFUSED = 1;
    final public static int APP_UNKNOWN = 2;

    final public static int PROXY_STARTING = 0;
    final public static int PROXY_STOPPED = 1;
    final public static int PROXY_CONNECTED = 2;
    final public static int PROXY_DISCONNECTED = 3;
    final public static int PROXY_STOPPING = 4;

    public enum SortBy {
        NAME,
        PACKAGE,
        DATE
    }

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

}
