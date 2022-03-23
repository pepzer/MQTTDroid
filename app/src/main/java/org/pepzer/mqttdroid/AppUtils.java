/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

public class AppUtils {
    final public static String PREF_PROXY_ACTIVE = "proxy_active";
    final public static String PREF_FIRST_RUN = "first_run";
    final public static String PREF_CONFIG_CHANGE = "config_change";

    public enum SortBy {
        NAME,
        PACKAGE,
        DATE
    }
}
