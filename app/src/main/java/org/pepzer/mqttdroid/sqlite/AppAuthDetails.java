/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid.sqlite;

import com.tworx.eud.mqttdroid.AuthState;

public class AppAuthDetails {
    private long id;
    private String appPackage;
    private String appLabel;
    private long timestamp;
    private AuthState authStatus;

    public AppAuthDetails() {}

    public AppAuthDetails(AppAuthDetails clone) {
        this.id = clone.getId();
        this.appPackage = clone.getAppPackage();
        this.appLabel = clone.getAppLabel();
        this.timestamp = clone.getTimestamp();
        this.authStatus = clone.getAuthStatus();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAppPackage() {
        return appPackage;
    }

    public void setAppPackage(String appPackage) {
        this.appPackage = appPackage;
    }

    public String getAppLabel() {
        return appLabel;
    }

    public void setAppLabel(String appLabel) {
        this.appLabel = appLabel;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public AuthState getAuthStatus() {
        return authStatus;
    }

    public void setAuthStatus(AuthState authStatus) {
        this.authStatus = authStatus;
    }

    @Override
    public String toString() {
        return appPackage;
    }
}
