/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import org.pepzer.mqttdroid.sqlite.AppAuthDetails;
import org.pepzer.mqttdroid.sqlite.AppAuthPub;
import org.pepzer.mqttdroid.sqlite.AppAuthSub;
import org.pepzer.mqttdroid.sqlite.AuthDataSource;
import org.pepzer.mqttdroid.sqlite.MqttConfig;
import org.pepzer.mqttdroid.sqlite.MqttMsgDelivery;
import org.pepzer.mqttdroid.sqlite.MqttStoredMsg;
import org.pepzer.mqttdroid.sqlite.ProxyDataSource;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.tworx.eud.mqttdroid.AuthState;
import com.tworx.eud.mqttdroid.IMQTTDroid;
import com.tworx.eud.mqttdroid.IMQTTDroidAuth;
import com.tworx.eud.mqttdroid.IMQTTDroidCallback;
import com.tworx.eud.mqttdroid.IMQTTDroidNet;
import com.tworx.eud.mqttdroid.IMQTTDroidNetCallback;
import com.tworx.eud.mqttdroid.IMQTTReceiver;
import com.tworx.eud.mqttdroid.ProxyState;
import com.tworx.eud.mqttdroid.Utils;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    private static final String CHANNEL_ID = "org.pepzer.mqttdroid.ProxyService";

    private IMQTTDroidCallback controlCallback = null;
    private ProxyState proxyState = ProxyState.PROXY_STOPPED;

    /**
     * Set of all active topic subscriptions.
     */
    private Set<String> subscriptions = new HashSet<>();

    /**
     * Map topic to QoS for all active subscriptions.
     * Store the highest QoS only for each topic.
     */
    private Map<String, Integer> subToQos = new HashMap<>();

    /**
     * Hold previous subscriptions to remove upon restart.
     * Useful if `Clean Session` is unchecked in the settings.
     */
    private Set<String> prevSubscriptions = new HashSet<>();

    /**
     * Map proxy clients' package names to their callback.
     */
    private Map<String, IMQTTDroidNetCallback> packageToCallback = new HashMap<>();

    /**
     * Map package name to a Pattern that matches allowed publish topics.
     */
    private Map<String, Pattern> packageToPubPattern = new HashMap<>();


    /**
     * Map package name to a Pattern that matches allowed subscribe topics.
     */
    private Map<String, Pattern> packageToSubPattern = new HashMap<>();

    /**
     * Map Pattern to package name, used when a message arrives.
     * If the pattern matches the package will receive a copy of the message.
     */
    private Map<Pattern, String> subPatternToPackage = new HashMap<>();

    /**
     * Map package name to current auth state.
     */
    private Map<String, AuthState> packageToAuthState = new HashMap<>();
    private PackageManager pm;

    private AuthDataSource authDataSource;
    private ProxyDataSource proxyDataSource;
    private int activeProfile = 0;
    private MqttConfig mqttConfig;

    private static boolean hasWifi = false;
    private static boolean hasMobile = false;
    private ConnectivityManager mConnMan;
    private volatile IMqttAsyncClient mqttClient;

    private MQTTBroadcastReceiver mqttBroadcastReceiver;
    private static boolean initialized = false;

    private boolean rcvIsBound = false;
    private IMQTTReceiver rcvService = null;
    private String rcvPackage = "";

    private Timer connectionCheckTimer = null;
    private int connectionCheckInterval = 30000;

    private boolean receivedStop = false;

    private NotificationCompat.Builder notifBuilder = null;
    private NotificationManager notifManager = null;
    private int notifId = 15372;

    class MQTTBroadcastReceiver extends BroadcastReceiver {
        /**
         * Handle connectivity events and update connectivity flags.
         * @param context
         * @param intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo infos[] = mConnMan.getAllNetworkInfo();

            for (int i = 0; i < infos.length; i++) {
                if (infos[i].getTypeName().equalsIgnoreCase("MOBILE")) {
                    if((infos[i].isConnected() != hasMobile)){
                        hasMobile = infos[i].isConnected();
                    }
                } else if (infos[i].getTypeName().equalsIgnoreCase("WIFI")) {
                    if ((infos[i].isConnected() != hasWifi)) {
                        hasWifi = infos[i].isConnected();
                    }
                }
            }
        }
    }

    /**
     * Set up the service, connect to the broker and start the service in foreground.
     * Stop immediately if the service was stopped by the user, to avoid running on boot
     * if not requested.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate()");
// if debugging needed uncomment and see https://stackoverflow.com/questions/15640871/how-to-debug-remote-aidl-service-in-android
//        android.os.Debug.waitForDebugger();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(AppUtils.PREF_PROXY_ACTIVE, false)) {

            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notifManager = getSystemService(NotificationManager.class);
            notifManager.createNotificationChannel(channel);

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            notifBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.logo_nobg)
                    //.setContentTitle("MQTTDroid")
                    .setContentText(getResources().getString(R.string.proxy_notification))
                    .setContentIntent(pendingIntent);
            Notification notification = notifBuilder.build();

            startForeground(notifId, notification);

            pm = getApplicationContext().getPackageManager();

            IntentFilter intentf = new IntentFilter();
            intentf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mqttBroadcastReceiver = new MQTTBroadcastReceiver();
            registerReceiver(mqttBroadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            mConnMan = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

            authDataSource = new AuthDataSource(getApplicationContext());
            authDataSource.open();

            proxyDataSource = new ProxyDataSource(getApplicationContext());
            proxyDataSource.open();
            mqttConfig = proxyDataSource.getMqttConfig(activeProfile);

            initialized = true;
            populatePackageMaps();
            proxyState = ProxyState.PROXY_STARTING;
            doConnect();
            connectionCheck();
        } else {
            stopSelf();
        }
    }

    /**
     * Update the notification of the foreground service with the current status.
     */
    private void updateNotification() {
        if (notifBuilder == null) {
            Log.e(TAG, "Notification builder is null!");
            return;
        }

        String content;

        switch (proxyState) {
            case PROXY_CONNECTED:
                content = getResources().getString(R.string.status_proxy_connected);
                break;
            case PROXY_DISCONNECTED:
                content = getResources().getString(R.string.status_proxy_disconnected);
                break;
            case PROXY_STOPPED:
                content = getResources().getString(R.string.status_proxy_stopped);
                break;
            case PROXY_STOPPING:
                content = getResources().getString(R.string.status_proxy_stopping);
                break;
            case PROXY_STARTING:
                content = getResources().getString(R.string.status_proxy_starting);
                break;
            default:
                content = getResources().getString(R.string.proxy_notification);
                break;
        }

        notifManager.notify(notifId, notifBuilder.setContentText(content).build());
    }

    /**
     * Check the status of the mqtt client periodically, try to reconnect if disconnected.
     * Useful if first connection fails, if `Auto Reconnect` is unchecked in the settings or
     * when the client automatically reconnects but the proxy status isn't updated as well.
     */
    private void connectionCheck() {
        try {
            if (connectionCheckTimer != null) {
                connectionCheckTimer.cancel();
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        connectionCheckTimer = new Timer();
        connectionCheckTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                Log.v(TAG, "connectionCheck");
                if (mqttClient != null) {
                    if (mqttClient.isConnected()) {
                        changeProxyState(ProxyState.PROXY_CONNECTED);
                    } else {
                        changeProxyState(ProxyState.PROXY_DISCONNECTED);
                        boolean hasConnectivity = hasMobile || hasWifi;
                        if (hasConnectivity && !receivedStop) {
                            doConnect();
                        }
                    }
                }
                connectionCheck();
            }
        }, connectionCheckInterval);

    }

    /**
     * Close open connections, databases and receivers before stopping.
     * The flag `initialized` is checked to avoids null pointers if the service was started through
     * BIND_AUTO_CREATE, shouldn't happen unless the proxy client is buggy.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy(), initialized: " + initialized);
        if (initialized) {
            try {
                connectionCheckTimer.cancel();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            authDataSource.close();
            proxyDataSource.close();
            unregisterReceiver(mqttBroadcastReceiver);
            doUnbindReceiver();
        }
    }

    /**
     * Answer with the Binder only if the service has been properly initialized.
     * The check is to avoid communications with faulty clients using the BIND_AUTO_CREATE flag.
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        Log.v(TAG, "onBind, initialized: " + initialized);
        if (initialized) {
            return remoteBinder;
        } else {
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand()");
        return Service.START_STICKY;
    }

    /**
     * Save the new proxy state, update the notification and propagate the state to all clients.
     * @param newState
     *   An int matching Utils.PROXY_*.
     */
    private void changeProxyState(ProxyState newState) {
        if (proxyState != newState) {
            // only do processing if the state actually changed
            Log.v(TAG, "changeProxyState, newState: " + newState);
            proxyState = newState;
            if (controlCallback != null) {
                try {
                    controlCallback.proxyStateChanged(proxyState.ordinal());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            updateNotification();

            Iterator<String> packageIterator = packageToCallback.keySet().iterator();
            while (packageIterator.hasNext()) {
                String packageName = packageIterator.next();
                IMQTTDroidNetCallback callback = packageToCallback.get(packageName);
                if (refreshNetCallback(callback, packageName)) {
                    try {
                        callback.proxyStateChanged(proxyState.ordinal());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Disconnect the mqtt client from the broker.
     * Update the state, don't propagate the state change if stopped by the user, to avoid a
     * fast and weird change of the main switch.
     */
    private void doDisconnect() {
        Log.d(TAG, "doDisconnect()");
        IMqttToken token;
        try {
            if (mqttClient != null) {
                token = mqttClient.disconnect();
                token.waitForCompletion(mqttConfig.getComplTimeout());
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }

        if (mqttClient == null || !mqttClient.isConnected()) {
            if (proxyState == ProxyState.PROXY_STARTING || proxyState == ProxyState.PROXY_CONNECTED) {
                if (receivedStop) {
                    proxyState = ProxyState.PROXY_DISCONNECTED;
                } else {
                    changeProxyState(ProxyState.PROXY_DISCONNECTED);
                }
            }
        }
    }

    /**
     * Try to connect to the mqtt server.
     * Force a disconnection if the client is still attached.
     * Load the mqtt configuration from the database.
     * If `Clean Session` is false diff the new subscriptions with the old ones to unsubscribe from
     * unneeded topics.
     * Could cause unnecessary re-subscribe upon reconnection, shouldn't be an issue with the server.
     * TODO: solve the issue when an old client remains attached or the mqtt server considers it still
     *   connected and repeatedly disconnects the new client.
     * TODO: support multiple profiles for the configuration.
     */
    private void doConnect(){
        Log.d(TAG, "doConnect()");
        if (mqttClient != null) {
            try {
                mqttClient.disconnectForcibly();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mqttClient.close();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
        if (receivedStop || proxyState == ProxyState.PROXY_STOPPED) {
            return;
        }

        //test subscription
        //subscriptions.add("/mqttdroid");
        //subToQos.put("/mqttdroid", 0);
        //end test

        mqttConfig = proxyDataSource.getMqttConfig(activeProfile);
        String clientId = mqttConfig.getClientId();
        boolean cleanSession = mqttConfig.isCleanSession();
        if (clientId.equalsIgnoreCase("auto")) {
            clientId = MqttAsyncClient.generateClientId();
        }
        Log.v(TAG, "client_id: " + clientId);
        String server = mqttConfig.getProtocol().toLowerCase() + "://" + mqttConfig.getBrokerAddr() +
                ":" + mqttConfig.getBrokerPort();
        IMqttToken token;
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setAutomaticReconnect(mqttConfig.isAutoReconnect());
        if (mqttConfig.getUsername().length() > 0) {
            options.setUserName(mqttConfig.getUsername());
            options.setPassword(mqttConfig.getPassword().toCharArray());
        }
        int timeout = mqttConfig.getComplTimeout();
        options.setConnectionTimeout(timeout);
        options.setKeepAliveInterval(mqttConfig.getKeepalive());

        if (mqttConfig.isCustomCA()) {
            options.setSocketFactory(getCustomSocketFactory(mqttConfig.getCrtPath()));
        }

        try {
            mqttClient = new MqttAsyncClient(server, clientId, new MemoryPersistence());
            mqttClient.setCallback(new MqttEventCallback());
            token = mqttClient.connect(options);
            token.waitForCompletion(timeout);

            if (cleanSession) {
                Iterator<String> iteratorSubs = subscriptions.iterator();
                Log.v(TAG, "subs.hasNext " + iteratorSubs.hasNext());
                while (iteratorSubs.hasNext()) {
                    String topic = iteratorSubs.next();
                    token = mqttClient.subscribe(topic, subToQos.get(topic));
                    token.waitForCompletion(timeout);
                }
            } else {
                Set<String> unsubDiff = new HashSet<>(prevSubscriptions);
                unsubDiff.removeAll(subscriptions);
                Iterator<String> iteratorUnsub = unsubDiff.iterator();
                while (iteratorUnsub.hasNext()) {
                    token = mqttClient.unsubscribe(iteratorUnsub.next());
                    token.waitForCompletion(timeout);
                }

                Set<String> subDiff = new HashSet<>(subscriptions);
                subDiff.removeAll(prevSubscriptions);
                Iterator<String> iteratorSubs = subDiff.iterator();
                Log.v(TAG, "subs.hasNext " + iteratorSubs.hasNext());
                while (iteratorSubs.hasNext()) {
                    String topic = iteratorSubs.next();
                    token = mqttClient.subscribe(topic, subToQos.get(topic));
                    token.waitForCompletion(timeout);
                }
            }
        } catch (MqttSecurityException e) {
            changeProxyState(ProxyState.PROXY_DISCONNECTED);
            e.printStackTrace();
        } catch (MqttException e) {
            changeProxyState(ProxyState.PROXY_DISCONNECTED);
            switch (e.getReasonCode()) {
                case MqttException.REASON_CODE_BROKER_UNAVAILABLE:
                case MqttException.REASON_CODE_CLIENT_TIMEOUT:
                case MqttException.REASON_CODE_CONNECTION_LOST:
                case MqttException.REASON_CODE_SERVER_CONNECT_ERROR:
                case MqttException.REASON_CODE_FAILED_AUTHENTICATION:
                default:
                    e.printStackTrace();
            }
        }
        if (mqttClient.isConnected()) {
            changeProxyState(ProxyState.PROXY_CONNECTED);
        }
    }

    /**
     * Build a SocketFactory for a custom CA from a certificate file.
     * Adapted from https://developer.android.com/training/articles/security-ssl.html.
     * The certificate must be available on the external storage and specified in the settings.
     * The app must have appropriate permissions to read it.
     * @param crtPath
     *   Path of the certificate relative to the root of the external storage.
     * @return
     */
    private SocketFactory getCustomSocketFactory(String crtPath) {
        SocketFactory socketFactory = null;

        // Load CAs from an InputStream
        CertificateFactory cf = null;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        InputStream caInput = null;
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            File caCertFile = new File(sdcard, crtPath);
            caInput = new BufferedInputStream(new FileInputStream(caCertFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Certificate ca = null;
        try {
            try {
                ca = cf.generateCertificate(caInput);
                System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        } finally {
            try {
                if (caInput != null) {
                    caInput.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Create a KeyStore containing our trusted CAs
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create a TrustManager that trusts the CAs in our KeyStore
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = null;
        try {
            tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }

        // Create an SSLContext that uses our TrustManager
        SSLContext context = null;
        try {
            context = SSLContext.getInstance("TLS");
            if (tmf != null) {
                context.init(null, tmf.getTrustManagers(), null);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        if (context != null) {
            socketFactory = context.getSocketFactory();
        }
        return socketFactory;
    }

    /**
     * Mqtt callback to handle events from the mqtt server.
     * All messages are forwarded to the service Handler.
     */
    private class MqttEventCallback implements MqttCallback {

        @Override
        public void connectionLost(Throwable arg0) {
            Log.v(TAG, "connectionLost");
            mHandler.sendMessage(mHandler.obtainMessage(MSG_PROXY_STATE_CHANGE, ProxyState.PROXY_DISCONNECTED));
        }

        /**
         * Forward a deliveryComplete event to the Handler with a delay.
         * The message is delayed to give time to the publish msg handler to store the corresponding
         * entry in the database.
         * @param token
         *  Mqtt token of the delivery.
         */
        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            Log.v(TAG, "deliveryComplete");
            final int msgId = token.getMessageId();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_DELIVERY_COMPLETE, msgId));
                }
            }, 1000);
        }

        @Override
        @SuppressLint("NewApi")
        public void messageArrived(String topic, final MqttMessage msg) throws Exception {
            Log.i(TAG, "Message arrived from topic" + topic);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_ARRIVED, new MqttMsg(topic, msg)));
        }
    }

    /**
     * Implementation of the control interface.
     * These methods should be invoked by the local process only.
     */
    private final IMQTTDroid.Stub controlBinder = new IMQTTDroid.Stub() {

        /**
         * Return the proxy state.
         * Probably useless because the info is sent through callback.
         * @return
         *   An int matching Utils.PROXY_*.
         */
        public int getProxyState() {
            return proxyState.ordinal();
        }

        /**
         * Forward the MSG_STOP_PROXY to the Handler to be processed on the service thread.
         */
        public void stopProxy() {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_PROXY));
        }

        /**
         * Forward the MSG_RESTART_PROXY to the Handler to be processed on the service thread.
         * This message is received whenever the user allows/refuses a client in the UI.
         */
        public void restartProxy() {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_RESTART_PROXY));
        }

        /**
         * Register the callback for the control interface and immediately report the current state.
         * @param callback
         */
        public void registerCallback(IMQTTDroidCallback callback) {
            Log.v(TAG, "registerCallback ");
            controlCallback = callback;
            try {
                controlCallback.proxyStateChanged(proxyState.ordinal());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    };

    /**
     * Implementation of the unrestricted client interface which exposes mqtt functionalities.
     */
    private final IMQTTDroidNet.Stub remoteBinder = new IMQTTDroidNet.Stub() {

        /**
         *  Return the current authorization state of the calling client.
         * @return
         *   An int matching Utils.APP_*, also present in the utils class of the client library.
         */
        public int getAuthState() {
            String packageName = Utils.getCallerPackage(pm);
            if (packageToAuthState.containsKey(packageName)) {
                return packageToAuthState.get(packageName).ordinal();
            }
            return IMQTTDroidAuth.APP_UNKNOWN;
        }

        /**
         * Return the proxy state.
         * Probably useless because the info is already sent through callback.
         * @return
         *   An int matching Utils.PROXY_*.
         */
        public int getProxyState() {
            return proxyState.ordinal();
        }

        /**
         * Return a Map that has for keys all subscriptions that are active for the calling client.
         * @return
         *   Map\<String, Integer\> of topics to their QoS value, could be empty, or null if the client
         *   isn't allowed.
         */
        public Map getActiveSubscriptions() {
            String packageName = Utils.getCallerPackage(pm);
            if (packageToAuthState.containsKey(packageName)) {
                Map<String, Integer> subs = new HashMap<>();
                AuthState authState = packageToAuthState.get(packageName);
                if (authState == AuthState.APP_ALLOWED) {
                    List<AppAuthSub> activeSubs = authDataSource.getActiveAuthSubsByPkg(packageName);
                    for (int i = 0; i < activeSubs.size(); ++i) {
                        subs.put(activeSubs.get(i).getTopic(), activeSubs.get(i).getQos());
                    }
                    return subs;
                }
            }
            return null;
        }

        /**
         * Expose the mqtt subscribe command to the clients.
         * Topics must match one of those requested by the app, and the app must be allowed by the user.
         * @param topic
         *   String with the topic to subscribe to, could contain an mqtt pattern.
         * @param qos
         *   QoS value for the subscription as required by the mqtt protocol.
         * @return
         *   Boolean telling the client if the request is being processed,
         *   the result is sent through the method `subscribeCallback` of the client's callback.
         */
        public boolean subscribe(String topic, int qos) {

            String packageName = Utils.getCallerPackage(pm);
            if (packageToAuthState.containsKey(packageName)) {
                AuthState authState = packageToAuthState.get(packageName);
                if (authState == AuthState.APP_ALLOWED) {
                    SubscribeMsg msg = new SubscribeMsg(packageName, topic, qos);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SUBSCRIBE, msg));
                    return true;
                }
            }
            return false;
        }

        /**
         * Expose the mqtt unsubscribe command to the clients.
         * Topics must match one of those requested by the app, and the app must be allowed by the user.
         * @param topic
         *   String with the topic to unsubscribe from.
         * @return
         *   Boolean telling the client if the request is being processed,
         *   the result is sent through the method `unsubscribeCallback` of the client's callback.
         */
        public boolean unsubscribe(String topic) {

            String packageName = Utils.getCallerPackage(pm);
            if (packageToAuthState.containsKey(packageName)) {
                AuthState authState = packageToAuthState.get(packageName);
                if (authState == AuthState.APP_ALLOWED) {
                    SubscribeMsg msg = new SubscribeMsg(packageName, topic, 0);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_UNSUBSCRIBE, msg));
                    return true;
                }
            }
            return false;
        }

        /**
         * Expose the mqtt publish command to the clients.
         * Topics must match one of those requested by the app, and the app must be allowed by the user.
         * @param id
         *   Message id at the sender, will be used to notify the delivery.
         * @param topic
         *   Topic to publish to, must be in the list of approved publish topics for the client.
         * @param payload
         *   Payload of the message.
         * @param qos
         *   QoS value for the message.
         * @param retained
         *   Flag to request the broker to retain the message.
         * @return
         *   Boolean telling the client if the request is being processed,
         *   the result is sent through the method `publishCallback` of the client's callback,
         *   if the callback is unavailable the result is sent through the receiver service's
         *   `deliveryComplete` method.
         *   Currently the proxy doesn't buffer the messages, so if the proxy isn't connected to the
         *   broker the publish is refused and this method returns false.
         */
        public boolean publish(int id, String topic, byte[] payload, int qos, boolean retained) {
            if (proxyState != ProxyState.PROXY_CONNECTED) {
                return false;
            }
            String packageName = Utils.getCallerPackage(pm);
            if (packageToAuthState.containsKey(packageName)) {
                AuthState authState = packageToAuthState.get(packageName);
                Pattern pubPat = packageToPubPattern.get(packageName);
                if (authState == AuthState.APP_ALLOWED && pubPat.matcher(topic).matches()) {
                    PublishMsg msg = new PublishMsg(packageName, topic, id, payload, qos, retained);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_PUBLISH, msg));
                    return true;
                }
            }
            return false;
        }

        /**
         * Register the callback for the client.
         * Unbind the receiver service if the package name is the same.
         * The client app must be allowed by the user through the UI.
         * @param callback
         * @return
         *   True if the callback has been saved, false otherwise.
         */
        public boolean registerNetCallback(IMQTTDroidNetCallback callback) {
            Log.v(TAG, "registerNetCallback ");

            if (proxyState == ProxyState.PROXY_STOPPED) {
                return false;
            }
            String packageName = Utils.getCallerPackage(pm);

            if (packageToAuthState.containsKey(packageName)) {
                AuthState authState = packageToAuthState.get(packageName);
                Log.v(TAG, "registerNetCallback pkg: " + packageName + ", auth: " + authState);

                if (authState == AuthState.APP_ALLOWED) {
                    packageToCallback.put(packageName, callback);

                    if (packageName.equals(rcvPackage)) {
                        doUnbindReceiver();
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * Return the control interface binder.
         * Check if the calling client is local (same package) and refuse all other clients.
         * @return
         *   The Binder object relative to the control interface,
         *   return null if the client isn't allowed.
         */
        public IBinder getControlBinder() {
            String callerPackageName = Utils.getCallerPackage(pm);
            if (callerPackageName.equals(getPackageName())) {
                return controlBinder;
            }
            return null;
        }
    };

    /**
     * Class used to encapsulate subscribe args to forward those to the Handler.
     */
    private class SubscribeMsg {
        private String packageName;
        private String topic;
        private int qos;

        SubscribeMsg(String packageName, String topic, int qos) {
            this.packageName = packageName;
            this.topic = topic;
            this.qos = qos;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getTopic() {
            return topic;
        }

        public int getQos() {
            return qos;
        }
    }

    /**
     * Class used to encapsulate publish args to forward those to the Handler.
     */
    private class PublishMsg {
        private String sender;
        private String topic;
        private MqttMessage mqttMessage;

        public PublishMsg(String sender, String topic, int id, byte[] payload, int qos, boolean retained) {
            this.sender = sender;
            this.topic = topic;
            mqttMessage = new MqttMessage();
            mqttMessage.setId(id);
            mqttMessage.setPayload(payload);
            mqttMessage.setQos(qos);
            mqttMessage.setRetained(retained);
        }

        public String getSender() {
            return sender;
        }

        public String getTopic() {
            return topic;
        }

        public MqttMessage getMqttMessage() {
            return mqttMessage;
        }
    }

    /**
     * Container class for an MqttMessage and the corresponding topic, used to forward to the Handler.
     */
    private class MqttMsg {
        private String topic;
        private MqttMessage msg;

        public MqttMsg(String topic, MqttMessage msg) {
            this.topic = topic;
            this.msg = msg;
        }

        public String getTopic() {
            return topic;
        }

        public MqttMessage getMsg() {
            return msg;
        }
    }

    private static final int MSG_SUBSCRIBE = 1;
    private static final int MSG_UNSUBSCRIBE = 2;
    private static final int MSG_PUBLISH = 3;
    private static final int MSG_ARRIVED = 4;
    private static final int MSG_STOP_PROXY = 5;
    private static final int MSG_RESTART_PROXY = 6;
    private static final int MSG_DELIVERY_COMPLETE = 7;
    private static final int MSG_PROXY_STATE_CHANGE = 8;

    private boolean success = false;

    /**
     * Handler that receives and processes messages from the clients and from the mqtt server.
     */
    private Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            success = false;
            IMQTTDroidNetCallback callback;
            switch (msg.what) {
                /**
                 * Invoke `handleSubscribe()` to update the subscription as active in the database.
                 * Try to send the command to the server if the mqtt client is connected.
                 * Report the result to the client through callback.
                 * Even when `success == false` upon reconnection the subscription will be sent if
                 * it has been successfully saved in the db.
                 */
                case MSG_SUBSCRIBE:
                    Log.v(TAG, "Subscribe received");
                    SubscribeMsg smsg = (SubscribeMsg) msg.obj;
                    if (handleSubscribe(smsg) && proxyState == ProxyState.PROXY_CONNECTED) {
                        try {
                            IMqttToken token;
                            token = mqttClient.subscribe(smsg.getTopic(), smsg.getQos());
                            token.waitForCompletion(mqttConfig.getComplTimeout());
                            success = true;
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                    }
                    callback = packageToCallback.get(smsg.getPackageName());
                    if (refreshNetCallback(callback, smsg.getPackageName())) {
                        try {
                            callback.subscribeCallback(smsg.getTopic(), success);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;

                /**
                 * Invoke `handleUnsubscribe()` to update the subscription as inactive in the database.
                 * Try to send the command to the server if the mqtt client is connected.
                 * Report the result to the client through callback.
                 * Even when `success == false` upon reconnection the unsubscribe should be sent.
                 * TODO: Store unsubscribe messages reliably,
                 *   currently if the user restarts the service the unsubscribe is not sent,
                 *   this is an issue if it happens when `Clean Session` is false.
                 */
                case MSG_UNSUBSCRIBE:
                    Log.v(TAG, "Unsubscribe received");
                    SubscribeMsg umsg = (SubscribeMsg) msg.obj;
                    if (handleUnsubscribe(umsg) && proxyState == ProxyState.PROXY_CONNECTED) {
                        try {
                            IMqttToken token;
                            token = mqttClient.unsubscribe(umsg.getTopic());
                            token.waitForCompletion(mqttConfig.getComplTimeout());
                            success = true;
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                    }
                    callback = packageToCallback.get(umsg.getPackageName());
                    if (refreshNetCallback(callback, umsg.getPackageName())) {
                        try {
                            callback.unsubscribeCallback(umsg.getTopic(), success);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    break;

                /**
                 * Try to publish the message, notify an eventual failure to the client.
                 * The notification to the client happens either through callback or receiver service.
                 * If the publish succeeds the notification is delayed until the deliveryComplete is
                 * received from the mqtt server.
                 */
                case MSG_PUBLISH:
                    Log.v(TAG, "Publish msg");
                    PublishMsg pmsg = (PublishMsg) msg.obj;
                    int tokenId = -1;
                    boolean success = false;

                    MqttMsgDelivery delivery = new MqttMsgDelivery();
                    delivery.setSender(pmsg.getSender());
                    delivery.setSenderMsgId(pmsg.getMqttMessage().getId());
                    delivery.setMsgId(tokenId);
                    delivery.setTopic(pmsg.getTopic());
                    delivery.setComplete(false);
                    delivery.setSuccess(false);
                    delivery.setTimestamp((new Date()).getTime());
                    long id = proxyDataSource.createMsgDelivery(delivery);
                    delivery.setId(id);

                    try {
                        IMqttToken token;
                        token = mqttClient.publish(pmsg.getTopic(), pmsg.getMqttMessage());
                        tokenId = token.getMessageId();
                        token.waitForCompletion(mqttConfig.getComplTimeout());
                        success = true;
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }

                    delivery.setMsgId(tokenId);
                    if (success) {
                        proxyDataSource.updateMsgDelivery(delivery);
                    } else {
                        delivery.setComplete(true);
                        if (!sendReceiptToCallback(delivery)) {
                            proxyDataSource.updateMsgDelivery(delivery);
                            sendReceiptsToReceiver(delivery.getSender(), false);
                        }
                    }

                    break;

                /**
                 * Notify the sender client about the delivery complete of a publish.
                 * Try to send the notification through callback, in case of failure send it
                 * by binding to the receiver service of the client.
                 */
                case MSG_DELIVERY_COMPLETE:
                    Log.v(TAG, "MSG_DELIVERY_COMPLETE");
                    int msgId = (int) msg.obj;
                    MqttMsgDelivery msgDelivery = proxyDataSource.getMsgDeliveryByMsgId(msgId);
                    if (msgDelivery == null) {
                        Log.e(TAG, "MSG_DELIVERY_COMPLETE, message not found!");
                        break;
                    }

                    msgDelivery.setComplete(true);
                    msgDelivery.setSuccess(true);
                    proxyDataSource.updateMsgDelivery(msgDelivery);

                    if (!sendReceiptToCallback(msgDelivery)) {
                        sendReceiptsToReceiver(msgDelivery.getSender(), false);
                    }
                    break;

                /**
                 * Forward the received msg to all the clients with a matching active subscription.
                 * Try to send the message through the `msgArrived()` method of the client's callback.
                 * If the callback is absent or the transmission fails, send the message by binding
                 * to the receiver service of the client an invoking the `msgArrived()` on
                 * that interface.
                 */
                case MSG_ARRIVED:
                    MqttMsg mqttMsg = (MqttMsg) msg.obj;
                    String topic = mqttMsg.getTopic();
                    MqttMessage inMsg = mqttMsg.getMsg();

                    MqttStoredMsg storedMsg = new MqttStoredMsg();
                    storedMsg.setTopic(topic);
                    storedMsg.setMsgId(inMsg.getId());
                    storedMsg.setQos(inMsg.getQos());
                    storedMsg.setPayload(inMsg.getPayload());
                    storedMsg.setDuplicate(inMsg.isDuplicate());
                    storedMsg.setRetained(inMsg.isRetained());
                    storedMsg.setTimestamp((new Date()).getTime());

                    List<String> packageNames = findMatchingSubs(topic);
                    Log.v(TAG, "Forward to: " + packageNames);

                    for (int i=0; i < packageNames.size(); ++i) {
                        String packageName = packageNames.get(i);
                        storedMsg.setReceiver(packageName);
                        proxyDataSource.createStoredMsg(storedMsg);

                        if (!sendMsgToCallback(storedMsg)) {
                            sendMsgsToReceiver(packageName, false);
                        }
                    }

                    break;

                /**
                 * Shut down the entire service, try to disconnect first.
                 * Inform all connected clients before the `stopSelf()`.
                 */
                case MSG_STOP_PROXY:
                    receivedStop = true;
                    connectionCheckTimer.cancel();
                    doDisconnect();
                    changeProxyState(ProxyState.PROXY_STOPPED);
                    stopForeground(true);
                    stopSelf();
                    break;

                /**
                 * Reload the state of the service and reconnect to the mqtt server.
                 * Received after authorization changes on the main activity.
                 * TODO: use it for configuration changes.
                 */
                case MSG_RESTART_PROXY:
                    Log.v(TAG, "MSG_RESTART_PROXY");
//                    try {
//                        connectionCheckTimer.cancel();
//                    } catch (IllegalStateException e) {
//                        e.printStackTrace();
//                    }
                    doDisconnect();
                    clearPackageMaps();
                    populatePackageMaps();
                    proxyState = ProxyState.PROXY_STARTING;
                    doConnect();
                    connectionCheck();
                    break;

                case MSG_PROXY_STATE_CHANGE:
                    int newState = (int) msg.obj;
                    changeProxyState(Utils.proxyStateIntToEnum(newState));
                    break;

                default:
                    Log.w(TAG, "unknown message: " + msg.toString());
            }
            return true;
        }
    });


    /**
     * Check if the callback is still active, otherwise remove it from the `packageToCallback` Map.
     * This refresh is useful to clean an inactive callback and avoid useless attempted transmissions.
     * @param callback
     *   The callback that the client registered through `registerCallback`.
     * @param packageName
     *   The packageName of the client owner of the callback.
     * @return
     *   True if the callback is active, false if it was cleaned from the state.
     */
    private boolean refreshNetCallback(IMQTTDroidNetCallback callback, String packageName) {
        if (callback != null && callback.asBinder().pingBinder()) {
            return true;
        }
        Log.v(TAG, "Clean net callback for " + packageName);
        packageToCallback.remove(packageName);
        return false;
    }

    /**
     * Clear all maps used to support publish, subscribe and message delivery.
     */
    private void clearPackageMaps() {
        packageToAuthState.clear();
        packageToPubPattern.clear();
        packageToSubPattern.clear();
        subPatternToPackage.clear();
        subToQos.clear();
    }

    /**
     * Populate all data structures that support publish, subscribe and message delivery.
     * Store the authorization state for each known client package in packageToAuthState.
     * Store a pattern for each allowed client that matches allowed topics for publishing in
     * packageToPubPattern.
     * Store a pattern for each allowed client that matches the topic field of a received message
     * in subPatternToPackage.
     * Populate subscriptions and corresponding QoS values.
     */
    private void populatePackageMaps() {
        List<AppAuthDetails> detailsList = authDataSource.getAllAuthDetails();
        Set<String> newSubscriptions = new HashSet<>();
        for (int i = 0; i < detailsList.size(); ++i) {
            AppAuthDetails authDetails = detailsList.get(i);
            String packageName = authDetails.getAppPackage();
            AuthState authStatus = authDetails.getAuthStatus();
            packageToAuthState.put(packageName, authStatus);

            if (authStatus == AuthState.APP_ALLOWED) {
                List<AppAuthPub> pubList = authDataSource.getAuthPubsByPkg(packageName);
                Pattern pubPat = buildPubsPattern(pubList);
                if (pubPat != null) {
                    packageToPubPattern.put(packageName, pubPat);
                }
                List<AppAuthSub> subList = authDataSource.getAuthSubsByPkg(packageName);
                List<AppAuthSub> activeSubList = new ArrayList<>();
                for (AppAuthSub sub : subList) {
                    if (sub.isActive()) {
                        activeSubList.add(sub);
                        String topic = sub.getTopic();
                        newSubscriptions.add(topic);
                        int qos = sub.getQos();
                        if (subToQos.containsKey(topic)) {
                            if (qos > subToQos.get(topic)) {
                                subToQos.put(topic, qos);
                            }
                        } else {
                            subToQos.put(topic, qos);
                        }
                    }
                }
                Pattern subPat = buildSubsPattern(subList);
                if (subPat != null) {
                    packageToSubPattern.put(packageName, subPat);
                }
                Pattern activeSubPat = buildSubsPattern(activeSubList);
                if (activeSubPat != null) {
                    subPatternToPackage.put(activeSubPat, packageName);
                }
            }
        }
        prevSubscriptions = new HashSet<>(subscriptions);
        subscriptions = newSubscriptions;
    }

    /**
     * Build a single Pattern from a list of allowed topics for a publish.
     * The pattern matches if a topic is allowed, so that could be used to authorize a publish.
     * @param pubs
     *   List of `AppAuthPub` containing the allowed topics for a publish.
     * @return
     *   A Pattern that matches an allowed topic to publish to.
     */
    private Pattern buildPubsPattern(List<AppAuthPub> pubs) {
        if (pubs.size() < 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder(subToRegex(pubs.get(0).getTopic()));
        for (int i = 1; i < pubs.size(); ++i) {
            sb.append("|");
            sb.append(subToRegex(pubs.get(i).getTopic()));
        }
        return Pattern.compile(sb.toString());
    }

    /**
     * Build a Pattern that matches if a topic is present or implied in an active subscription.
     * Used to match the topic of received messages, will be the key with value the
     * corresponding client package.
     * MQTT supported patterns are expanded in regex form.
     * @param subs
     *   List `AppAuthSub` specifying the allowed topics for subscriptions.
     * @return
     *   A pattern that matches for topics subscribed to.
     */
    private Pattern buildSubsPattern(List<AppAuthSub> subs) {
        if (subs.size() < 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder(subToRegex(subs.get(0).getTopic()));
        for (int i = 1; i < subs.size(); ++i) {
            sb.append("|");
            sb.append(subToRegex(subs.get(i).getTopic()));
        }
        Log.v(TAG, "pattern: " + sb.toString());
        return Pattern.compile(sb.toString());
    }

    /**
     * Expand MQTT patterns to regex equivalents.
     * @param sub
     *   Topic string from a subscription.
     * @return
     */
    public static String subToRegex(String sub) {
        return sub.replaceAll("/\\+", "(/[^/]+)").replaceFirst("/#", "(/[^/]+)*");
    }

    /**
     * Return the list of package names that are subscribed to the given topic.
     * @param topic
     *   The topic to match.
     * @return
     *   List of package names of the subscribed clients.
     */
    private List<String> findMatchingSubs(String topic) {
        List<String> packageNames = new ArrayList<>();
        Set<Pattern> pats = subPatternToPackage.keySet();
        Iterator<Pattern> ipats = pats.iterator();
        while (ipats.hasNext()) {
            Pattern pat = ipats.next();
            if (pat.matcher(topic).matches()) {
                packageNames.add(subPatternToPackage.get(pat));
            }
        }
        return packageNames;
    }

    /**
     * Handle a new subscribe message by updating the active status in the database.
     * Return false if a client is not allowed to subscribe to the the requested topic.
     * Ignore already active subscriptions unless the requested QoS is different.
     * @param smsg
     *   Subscribe request received from the remote client.
     * @return
     *   Boolean telling if the subscription must be sent to the broker.
     */
    private boolean handleSubscribe(SubscribeMsg smsg) {
        String topic = smsg.getTopic();
        int qos = smsg.getQos();
        String packageName = smsg.getPackageName();
        AppAuthSub authSub = authDataSource.getAuthSub(packageName, topic);
        if (authSub == null) {
            Log.w(TAG, "handleSubscribe, authSub is null! Package: " + packageName);
            Pattern subPat = packageToSubPattern.get(packageName);
            if (subPat != null && subPat.matcher(topic).matches()) {
                authSub = new AppAuthSub();
                authSub.setTopic(topic);
                authSub.setQos(qos);
                authSub.setActive(false);
                authSub.setAppPackage(packageName);

                long id = authDataSource.createAuthSub(packageName, topic, qos, false);
                authSub.setId(id);
            } else {
                return false;
            }
        }

        if (authSub.isActive()) {
            if (authSub.getQos() == qos) {
                return false;
            } else {
                IMqttToken token;
                try {
                    token = mqttClient.unsubscribe(topic);
                    token.waitForCompletion(mqttConfig.getComplTimeout());
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
        authSub.setQos(qos);
        authSub.setActive(true);
        authDataSource.updateAuthSub(authSub);

        clearPackageMaps();
        populatePackageMaps();

        return true;
    }

    /**
     * Handle the request to unsubscribe by updating the status as inactive in the database.
     * @param umsg
     *   Unsubscribe request received from the remote client.
     * @return
     *   Boolean that specifies if the unsubscribe must be sent to the broker.
     */
    private boolean handleUnsubscribe(SubscribeMsg umsg) {
        String topic = umsg.getTopic();
        AppAuthSub authSub = authDataSource.getAuthSub(umsg.getPackageName(), topic);

        if (authSub == null) {
            Log.w(TAG, "handleUnsubscribe, authSub is null!");
            return false;
        }
        if (authSub.isActive()) {
            authSub.setActive(false);
            authDataSource.updateAuthSub(authSub);
            clearPackageMaps();
            populatePackageMaps();

            if (!subscriptions.contains(topic)) {
                //TODO: check if the QoS could be lowered (in an else block).
                return true;
            }
        }
        return false;
    }

    /**
     * Send the result of a publish through the registered client callback.
     * Check if the interface is available/working and invoke `publishCallback` on it,
     * on success remove the message info from the database.
     * @param msgDelivery
     *   Container for the message information and delivery status.
     * @return
     *   True if successfully transmitted, false otherwise.
     */
    private boolean sendReceiptToCallback(MqttMsgDelivery msgDelivery) {
        String sender = msgDelivery.getSender();
        Log.v(TAG, "sendReceiptToCallback, sender: " + sender);
        boolean success = false;
        IMQTTDroidNetCallback callback = packageToCallback.get(sender);

        if (refreshNetCallback(callback, sender)) {
            try {
                callback.publishCallback(msgDelivery.getTopic(), msgDelivery.getSenderMsgId(),
                        msgDelivery.isSuccess());
                success = true;
            } catch (RemoteException re) {
                re.printStackTrace();
            }
        }
        if (success) {
            proxyDataSource.deleteMsgDelivery(msgDelivery);
        }
        return success;
    }

    /**
     * Try to send all complete delivery receipts to the intended receiver.
     * If the receiver service isn't bound call `doBindReceiver()` and return.
     * This method is invoked upon connection inside the `onServiceConnected()`.
     * TODO: check for stored receipts on [re]start, currently the receipts are preserved in the db,
     *   but if the service is restarted only another transmission to the receiver service would
     *   trigger the flush af all stored receipts.
     * @param receiver
     *   Package name of the client app that has to receive the data.
     * @param skipCheck
     *   True to skip testing the connection to the receiver, false to do the check.
     */
    private void sendReceiptsToReceiver(String receiver, boolean skipCheck) {
        if(skipCheck || checkRcvConnection(receiver)) {
            List<MqttMsgDelivery> deliveryList = proxyDataSource.getCompleteDeliveryBySender(receiver);
            for (int i = 0; i < deliveryList.size(); ++i) {
                MqttMsgDelivery delivery = deliveryList.get(i);
                success = false;
                try {
                    rcvService.deliveryResult(delivery.getTopic(), delivery.getSenderMsgId(),
                            delivery.isSuccess());
                    success = true;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                if (success) {
                    proxyDataSource.deleteMsgDelivery(delivery);
                }
            }
        } else {
            doBindReceiver(receiver);
        }
    }

    /**
     * Try to deliver a message to a package through its callback.
     * @param msg
     *   Container of the message to be forwarded, as inserted in the database.
     * @return
     *   Boolean representing success/failure.
     */
    private boolean sendMsgToCallback(MqttStoredMsg msg) {
        String packageName = msg.getReceiver();
        Log.v(TAG, "sendMsgToCallback for " + packageName);
        boolean success = false;
        IMQTTDroidNetCallback callback = packageToCallback.get(packageName);
        if (refreshNetCallback(callback, packageName)) {
            try {
                callback.msgArrived(msg.getTopic(), msg.getMsgId(),
                        msg.getPayload(), msg.isDuplicate(), msg.isRetained());
                success = true;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (success) {
            proxyDataSource.deleteStoredMsg(msg);
        }
        return success;
    }

    /**
     * Try to deliver all pending messages to the intended client through its receiver service.
     * If the receiver service isn't bound call `doBindReceiver()` and return.
     * This method is invoked upon connection inside the `onServiceConnected()`.
     * TODO: check for stored messages on [re]start, currently the messages are preserved in the db,
     *   but if the service is restarted only another transmission to the receiver service would
     *   trigger the flush of all stored messages.
     * @param receiver
     *   Package name of the client app that has to receive the messages.
     * @param skipCheck
     *   True to skip testing the connection to the receiver, false to do the check.
     */
    private void sendMsgsToReceiver(String receiver, boolean skipCheck) {

        if (skipCheck || checkRcvConnection(receiver)) {
            List<MqttStoredMsg> msgList = proxyDataSource.getStoredMsgByRcv(receiver);
            boolean success = false;
            for (int i = 0; i < msgList.size(); ++i) {
                MqttStoredMsg msg = msgList.get(i);
                success = false;
                try {
                    rcvService.msgArrived(msg.getTopic(), msg.getMsgId(),
                            msg.getPayload(), msg.isDuplicate(), msg.isRetained());
                    success = true;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                if (success) {
                    proxyDataSource.deleteStoredMsg(msg);
                }
            }
        } else {
            doBindReceiver(receiver);
        }
    }

    /**
     * Bind explicitly to a service named `ProxyReceiverService` in the receiver package.
     * @param receiver
     *   The package name of the receiver.
     */
    private void doBindReceiver(String receiver) {
        Intent i = new Intent();
        i.setClassName(receiver, receiver + ".ProxyReceiverService");
        bindService(i, rcvConnection, Context.BIND_AUTO_CREATE);
        rcvIsBound = true;
    }

    /**
     * Unbind from the receiver service.
     * This should also cause the receiver service to stop because of the BIND_AUTO_CREATE flag.
     */
    private void doUnbindReceiver() {
        if (rcvIsBound) {
            unbindService(rcvConnection);
            rcvIsBound = false;
            rcvPackage = "";
        }
    }

    /**
     * This `ServiceConnection` is reused for packages that don't have registered callbacks.
     */
    private ServiceConnection rcvConnection = new ServiceConnection() {
        /**
         * Send all stored messages and delivery receipts addressed to this client.
         * @param className
         * @param service
         */
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            rcvService = IMQTTReceiver.Stub.asInterface(service);

            String receiver = className.getPackageName();
            Log.v(TAG, "onServiceConnected RCV pkgName:" + receiver);

            rcvPackage = receiver;

            sendMsgsToReceiver(receiver, true);
            sendReceiptsToReceiver(receiver, true);
        }

        public void onServiceDisconnected(ComponentName className) {
            rcvService = null;
            unbindService(rcvConnection);
            rcvIsBound = false;
        }
    };

    /**
     * Check if the connection with the receiver service of the specified package is active.
     * @param receiver
     *   Package name of the receiver we want to communicate with.
     * @return
     *   True if the service is connected and matches the intended receiver, false otherwise.
     */
    public boolean checkRcvConnection(String receiver) {
        if (!rcvIsBound) {
            return false;
        }
        if (rcvService == null || !rcvService.asBinder().isBinderAlive()) {
            doUnbindReceiver();
            return false;
        }

        return rcvPackage.equals(receiver);
    }
}
