/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import org.pepzer.mqttdroid.sqlite.MqttConfig;
import org.pepzer.mqttdroid.sqlite.ProxyDataSource;

import java.util.HashSet;
import java.util.Set;

public class MqttSettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    final public static String TAG = "MqttSettings";
    final public static int PROFILE = 0;
    private ProxyDataSource proxyDataSource;

    private String[] summaryKeys;
    private Set<String> skipSummary;
    private Set<String> skipAll;

    /**
     * Prepare the settings activity for the visualization.
     * Update the PREF_FIRST_RUN flag in the shared preferences.
     * Check if the configuration is present in the database, if not store it.
     * Update the summary for the text field.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.mqtt_preferences);
        proxyDataSource = new ProxyDataSource(this);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(AppUtils.PREF_FIRST_RUN, true)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(AppUtils.PREF_FIRST_RUN, false).commit();
        }
        summaryKeys = new String[] {"pref_client_id", "pref_username",
                "pref_protocol", "pref_broker_addr", "pref_broker_port",
                "pref_keepalive", "pref_compl_timeout", "pref_crt_path"};

        skipSummary = new HashSet<>();
        skipSummary.add("pref_password");
        skipSummary.add("pref_clean_session");
        skipSummary.add("pref_auto_reconnect");
        skipSummary.add("pref_custom_ca");

        skipAll = new HashSet<>();
        skipAll.add(AppUtils.PREF_FIRST_RUN);
        skipAll.add(AppUtils.PREF_CONFIG_CHANGE);

        checkDB(sharedPreferences);
        updateSummary(sharedPreferences);
    }

    /**
     * Update the summary of text fields to reflect their values.
     * @param sharedPreferences
     */
    public void updateSummary(SharedPreferences sharedPreferences) {
        for (int i = 0; i < summaryKeys.length; ++i) {
           Preference pref = findPreference(summaryKeys[i]);
            pref.setSummary(sharedPreferences.getString(summaryKeys[i], ""));
        }

    }

    /**
     * Check if the db contains a configuration, if not store the current one.
     * @param sharedPreferences
     */
    private void checkDB(SharedPreferences sharedPreferences) {
        proxyDataSource.open();
        if (proxyDataSource.getMqttConfig(PROFILE) == null) {
            storeConfig(sharedPreferences, true);
        }
    }

    /**
     * Store the current configuration in the database.
     * TODO: allow the user to chose/manage different profiles.
     * @param sharedPreferences
     * @param create
     *   True if the config must be created, false if it's an update.
     */
    private void storeConfig(SharedPreferences sharedPreferences, boolean create) {
        MqttConfig mqttConfig = new MqttConfig();
        mqttConfig.setProfileId(PROFILE);
        mqttConfig.setClientId(sharedPreferences.getString("pref_client_id", ""));
        mqttConfig.setUsername(sharedPreferences.getString("pref_username", ""));
        mqttConfig.setPassword(sharedPreferences.getString("pref_password", ""));
        mqttConfig.setProtocol(sharedPreferences.getString("pref_protocol", ""));
        mqttConfig.setBrokerAddr(sharedPreferences.getString("pref_broker_addr", ""));
        mqttConfig.setBrokerPort(Integer.parseInt(sharedPreferences.getString("pref_broker_port", "")));
        mqttConfig.setCleanSession(sharedPreferences.getBoolean("pref_clean_session", false));
        mqttConfig.setAutoReconnect(sharedPreferences.getBoolean("pref_auto_reconnect", false));
        mqttConfig.setKeepalive(Integer.parseInt(sharedPreferences.getString("pref_keepalive", "")));
        mqttConfig.setComplTimeout(Integer.parseInt(sharedPreferences.getString("pref_compl_timeout", "")));
        mqttConfig.setCustomCA(sharedPreferences.getBoolean("pref_custom_ca", false));
        mqttConfig.setCrtPath(sharedPreferences.getString("pref_crt_path", ""));

        Log.v(TAG, "create: " + create + ", profile: " + mqttConfig.getProfileId() + ", port: " + mqttConfig.getBrokerPort());

        if (create) {
            proxyDataSource.createMqttConfig(mqttConfig);
        } else {
            proxyDataSource.updateMqttConfig(mqttConfig);
            if (!sharedPreferences.getBoolean(AppUtils.PREF_CONFIG_CHANGE, false)) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(AppUtils.PREF_CONFIG_CHANGE, true).commit();
            }
//            Toast.makeText(this, R.string.restart_to_apply,
//                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update the summary of a preference if a change occurred, and store the new config in the db.
     * Use a skip set to leave the summary empty for selected fields.
     * @param sharedPreferences
     * @param key
     */
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {

        if (!skipAll.contains(key)) {

            if (!skipSummary.contains(key)) {
                Preference connectionPref = findPreference(key);
                connectionPref.setSummary(sharedPreferences.getString(key, ""));
            }

            storeConfig(sharedPreferences, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        proxyDataSource.open();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        proxyDataSource.close();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
