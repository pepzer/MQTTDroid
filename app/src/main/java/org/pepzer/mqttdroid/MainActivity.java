/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import org.pepzer.mqttdroid.sqlite.AppAuthDetails;
import org.pepzer.mqttdroid.sqlite.AuthDataSource;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.tworx.eud.mqttdroid.IMQTTDroid;
import com.tworx.eud.mqttdroid.IMQTTDroidAuth;
import com.tworx.eud.mqttdroid.IMQTTDroidAuthCallback;
import com.tworx.eud.mqttdroid.IMQTTDroidCallback;
import com.tworx.eud.mqttdroid.IMQTTDroidNet;
import com.tworx.eud.mqttdroid.ProxyState;
import com.tworx.eud.mqttdroid.Utils;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MqttDroidMain";

    private IMQTTDroid proxyService = null;
    private boolean proxyIsBound = false;

    private IMQTTDroidAuth authService = null;
    private boolean authIsBound = false;
    private AuthDataSource authDataSource;

    public AuthDataSource getDataSource() {
        return authDataSource;
    }

    private ProxyState proxyState = ProxyState.PROXY_STOPPED;

    private CustomArrayAdapter adapter;

    private SwitchCompat mainSwitch;
    private TextView proxyStatusTextView;

    private SharedPreferences sharedPreferences;

    private AppUtils.SortBy sortBy = AppUtils.SortBy.NAME;

    private boolean filterShowAllowed = true;
    private MenuItem filterAllowed;
    private boolean filterShowRefused = true;
    private MenuItem filterRefused;

    public void restartProxyService() {
        if (proxyService != null && proxyIsBound) {
            try {
                proxyService.restartProxy();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create the adapter and populate the listview.
     */
    private void populateListView() {
        List<AppAuthDetails> detailsList = authDataSource.getAllAuthDetails();
        final ListView listview = (ListView) findViewById(R.id.list_view);
        adapter = new CustomArrayAdapter(this, detailsList);
        listview.setAdapter(adapter);
        adapter.sortItems(sortBy);
    }

    /**
     * Clear the adapter and repopulate the listview.
     */
    private void rePopulateListView() {
        List<AppAuthDetails> detailsList = authDataSource.getAllAuthDetails();
        adapter.clear();
        adapter.addAll(detailsList);
        adapter.notifyDataSetChanged();
    }

    /**
     * Update status textView and optionally the mainSwitch according to proxy state.
     * @param state
     *   An int representing the proxy state, one of Utils.PROXY_*.
     * @param changeSwitch
     *   True if the mainSwitch checked state must be updated.
     */
    private void updateStatusUI(ProxyState state, boolean changeSwitch) {
        if (changeSwitch && state != ProxyState.PROXY_STOPPED) {
            mainSwitch.setChecked(true);
        }
        switch (state) {
            case PROXY_CONNECTED:
                proxyStatusTextView.setText(R.string.status_proxy_connected);
                proxyStatusTextView.setBackgroundColor(getResources().getColor(R.color.colorConnected));
                break;
            case PROXY_DISCONNECTED:
                proxyStatusTextView.setText(R.string.status_proxy_disconnected);
                proxyStatusTextView.setBackgroundColor(getResources().getColor(R.color.colorDisconnected));
                break;
            case PROXY_STOPPED:
                if (changeSwitch) {
                    mainSwitch.setChecked(false);
                }
                proxyStatusTextView.setText(R.string.status_proxy_stopped);
                proxyStatusTextView.setBackgroundColor(getResources().getColor(R.color.colorStopped));
                break;
            case PROXY_STARTING:
                proxyStatusTextView.setText(R.string.status_proxy_starting);
                proxyStatusTextView.setBackgroundColor(getResources().getColor(R.color.colorBusy));
                break;
            case PROXY_STOPPING:
                proxyStatusTextView.setText(R.string.status_proxy_stopping);
                proxyStatusTextView.setBackgroundColor(getResources().getColor(R.color.colorBusy));
                break;
            default:
                proxyStatusTextView.setText("Unknown error!");
                break;
        }
    }

    /**
     * If it's the first run invoke the settings activity.
     * Request the permissions to the user if not already granted.
     * Start the proxy service if enabled and bind to it.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        PreferenceManager.setDefaultValues(this, R.xml.mqtt_preferences, false);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(AppUtils.PREF_FIRST_RUN, true)) {
            startActivity(new Intent(this, MqttSettingsActivity.class));
        }

        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this,
                "org.pepzer.mqttdroid.BIND_RCV")
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add("org.pepzer.mqttdroid.BIND_RCV");
        }

        if (ContextCompat.checkSelfPermission(this,
                READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(READ_EXTERNAL_STORAGE);
        }

        if (permissions.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    0);
        }

        authDataSource = new AuthDataSource(this);
        authDataSource.open();

        doStartAuthService();
        if (sharedPreferences.getBoolean(AppUtils.PREF_PROXY_ACTIVE, false)) {
            doStartProxyService();
            doBindServices();
        } else {
            doBindAuth();
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mainSwitch = findViewById(R.id.main_switch);
        proxyStatusTextView = (TextView) findViewById(R.id.proxy_status);

        updateStatusUI(proxyState, false);

        mainSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                if (checked) {
                    editor.putBoolean(AppUtils.PREF_PROXY_ACTIVE, true).commit();
                    if (proxyState == ProxyState.PROXY_STOPPED) {
                        updateStatusUI(ProxyState.PROXY_STARTING, false);
                        doUnbindProxy();
                        doStartProxyService();
                        doBindProxy();
                    }
                } else {
                    editor.putBoolean(AppUtils.PREF_PROXY_ACTIVE, false).commit();
                    if (proxyState != ProxyState.PROXY_STOPPED) {
                        updateStatusUI(ProxyState.PROXY_STOPPING, false);
                        try {
                            proxyService.stopProxy();
                            doUnbindProxy();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        populateListView();

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        filterAllowed = menu.findItem(R.id.action_filter_allowed);
        filterRefused = menu.findItem(R.id.action_filter_refused);

        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            /**
             * Hide the virtual keyboard when the search field is submitted.
             * The filtering already happens in `onQueryTextChange`.
             * @param query
             * @return
             */
            @Override
            public boolean onQueryTextSubmit(String query) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                return true;
            }

            /**
             * Filter applications while typing in the search input field.
             * @param query
             *   Current query string.
             * @return
             */
            @Override
            public boolean onQueryTextChange(String query) {
                Log.v(TAG, "onQueryTextChange: " + query);
                adapter.getFilter().filter(query);
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_settings:
                startActivity(new Intent(this, MqttSettingsActivity.class));
                return true;

            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;

            /**
             * Change the checked status and filter in/out the allowed packages.
             */
            case R.id.action_filter_allowed:
                if (filterShowAllowed) {
                    item.setChecked(false);
                    filterShowAllowed = false;
                    adapter.getFilter().filter(buildFilterString());
                } else {
                    item.setChecked(true);
                    filterShowAllowed = true;
                    adapter.getFilter().filter(buildFilterString());
                }
                return true;

            /**
             * Change the checked status and filter in/out the refused packages.
             */
            case R.id.action_filter_refused:
                if (filterShowRefused) {
                    item.setChecked(false);
                    filterShowRefused = false;
                    adapter.getFilter().filter(buildFilterString());
                } else {
                    item.setChecked(true);
                    filterShowRefused = true;
                    adapter.getFilter().filter(buildFilterString());
                }
                return true;

            /**
             * Enable both categories when filtering with search.
             * For consistency because the search by string ignores the filtered categories.
             */
            case R.id.action_search:
                filterShowAllowed = true;
                filterAllowed.setChecked(true);
                filterShowRefused = true;
                filterRefused.setChecked(true);
                return true;

            /**
             * Sort applications by label name.
             */
            case R.id.action_sort_name:
                item.setChecked(true);
                sortBy = AppUtils.SortBy.NAME;
                adapter.sortItems(sortBy);
                return true;

            /**
             * Sort applications by package name.
             */
            case R.id.action_sort_package:
                item.setChecked(true);
                sortBy = AppUtils.SortBy.PACKAGE;
                adapter.sortItems(sortBy);
                return true;

            /**
             * Sort applications by time of request.
             */
            case R.id.action_sort_date:
                item.setChecked(true);
                sortBy = AppUtils.SortBy.DATE;
                adapter.sortItems(sortBy);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public AppUtils.SortBy getSortBy() {
        return sortBy;
    }

    /**
     * Build a special string to filter refused/allowed apps according to their show flags.
     * @return
     *   A string to pass to the filter in the listview adapter.
     */
    private String buildFilterString() {
        char[] chars = new char[] {'[', 'f', 'f', ']'};
        if (filterShowAllowed) {
            chars[1] = 't';
        }
        if (filterShowRefused) {
            chars[2] = 't';
        }
        return new String(chars);
    }

    /**
     * ServiceConnection to communicate with the ProxyService,
     * upon connection the Binder with the control API is obtained
     * by calling the method getControlBinder of the unprivileged interface.
     */
    private ServiceConnection proxyConnection = new ServiceConnection() {

        /**
         * Register the control callback and request the current proxy service state.
         * @param className
         * @param service
         */
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.
            Log.v(TAG, "onServiceConnected proxyService");
            IMQTTDroidNet netService = IMQTTDroidNet.Stub.asInterface(service);
            try {
                proxyService = IMQTTDroid.Stub.asInterface(netService.getControlBinder());

                proxyService.registerCallback(proxyCallback);
                proxyState = Utils.proxyStateIntToEnum(proxyService.getProxyState());
                mHandler.sendMessage(mHandler.obtainMessage(MSG_PROXY_STATE_CHANGE, proxyState));

                if (sharedPreferences.getBoolean(AppUtils.PREF_CONFIG_CHANGE, false)) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(AppUtils.PREF_CONFIG_CHANGE, false).commit();
                    restartProxyService();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            Snackbar.make(findViewById(R.id.main_switch), "proxy_service_connected", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }

        /**
         * Signal to the user if the proxy server disconnects.
         * @param className
         */
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            proxyService = null;

            Snackbar.make(findViewById(R.id.main_switch), R.string.snack_proxy_disconnected, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    };

    /**
     * ServiceConnection to communicate with the authService,
     * registerCallback is called immediately,
     * necessary for the refresh signal when the MainActivity is running.
     */
    private ServiceConnection authConnection = new ServiceConnection() {

        /**
         * Register the refresh callback immediately.
         * @param className
         * @param service
         */
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            authService = IMQTTDroidAuth.Stub.asInterface(service);

            Log.v(TAG, "onServiceConnected authService");
            try {
                authService.registerCallback(authCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            Snackbar.make(findViewById(R.id.main_switch), "auth_service_connected", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }

        /**
         * Signal to the user if the auth service disconnects.
         * @param className
         */
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            authService = null;

            Snackbar.make(findViewById(R.id.main_switch), R.string.snack_auth_disconnected, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    };

    /**
     * Bind to the proxy service.
     */
    void doBindProxy() {
        proxyIsBound = bindService(new Intent(MainActivity.this,
                ProxyService.class), proxyConnection, Context.BIND_ABOVE_CLIENT);
    }

    /**
     * Bind to the authorization service, create it if not running.
     */
    void doBindAuth() {
        authIsBound = bindService(new Intent(MainActivity.this,
                AuthService.class), authConnection, Context.BIND_AUTO_CREATE);
    }

    void doBindServices() {
        doBindProxy();
        doBindAuth();
    }

    /**
     * Explicitly start the proxy service.
     */
    void doStartProxyService() {
        Intent intent = new Intent(MainActivity.this, ProxyService.class);
        startService(intent);
    }

    /**
     * Unbind from the proxy service.
     */
    void doUnbindProxy() {
        if (proxyIsBound) {
            // Detach our existing connection.
            unbindService(proxyConnection);
            proxyIsBound = false;
        }
    }

    /**
     * Explicitly start the auth service.
     */
    void doStartAuthService() {
        Intent intent = new Intent(MainActivity.this, AuthService.class);
        startService(intent);
    }

    /**
     * Unbind from the auth service.
     */
    void doUnbindAuth() {
        if (authIsBound) {
            // Detach our existing connection.
            unbindService(authConnection);
            authIsBound = false;
        }
    }

    void doUnbindServices() {
        doUnbindProxy();
        doUnbindAuth();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindServices();
    }

    /**
     * Unbind all connected services and close the DataSource.
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
        doUnbindServices();
        authDataSource.close();
    }

    /**
     * Open the connection with the DB,
     * bind to AuthService and to ProxyService if started.
     */
    @Override
    protected void onResume() {
        super.onResume();
        authDataSource.open();
        rePopulateListView();
        if (sharedPreferences.getBoolean(AppUtils.PREF_PROXY_ACTIVE, false)) {
            doBindServices();
        } else {
            doBindAuth();
        }
        Log.v(TAG, "onResume ");
    }

    /**
     * Callback for the ProxyService control interface.
     */
    private final IMQTTDroidCallback proxyCallback = new IMQTTDroidCallback.Stub() {

        /**
         * Callback method to get a notification of the current state of ProxyService
         * whenever a change occurs.
         * Redispatch the message to an Handler to hop onto the UI thread.
         * @param proxyState
         */
        public void proxyStateChanged(int proxyState) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_PROXY_STATE_CHANGE,
                    Utils.proxyStateIntToEnum(proxyState)));
        }
    };

    private final IMQTTDroidAuthCallback authCallback = new IMQTTDroidAuthCallback.Stub() {
        /**
         * Invoked on the AuthService when a new auth request has been received,
         * Redispatch the message to an Handler to hop onto the UI thread.
         * @param packageName
         */
        public void newAuthRequest(String packageName) {
            Log.v(TAG, "newAuthRequest " + packageName);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_NEW_AUTH_REQ, packageName));
        }
    };

    private static final int MSG_PROXY_STATE_CHANGE = 1;
    private static final int MSG_NEW_AUTH_REQ = 2;


    private Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override public boolean handleMessage(Message msg) {
            switch (msg.what) {

                /**
                 * Handle the proxy state change event by updating the UI.
                 */
                case MSG_PROXY_STATE_CHANGE:
                    proxyState = (ProxyState)msg.obj;
                    updateStatusUI(proxyState, true);
                    break;

                /**
                 * Handle a new auth request by updating the list view.
                 */
                case MSG_NEW_AUTH_REQ:
                    Log.v(TAG, "Auth requested by: " + msg.obj);
                    rePopulateListView();
                    break;

                default:
                    Log.w(TAG, "unknown message: " + msg.toString());
            }
            return true;
        }
    });
}
