/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.tworx.eud.mqttdroid.AuthState;
import com.tworx.eud.mqttdroid.IMQTTDroidAuth;
import com.tworx.eud.mqttdroid.IMQTTDroidAuthCallback;
import com.tworx.eud.mqttdroid.Utils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pepzer.mqttdroid.sqlite.AuthDataSource;

public class AuthService extends Service {

    private static final String TAG = "AuthService";
    private IMQTTDroidAuthCallback refreshCallback = null;
    private NotificationCompat.Builder notifBuilder = null;
    private NotificationManager notifManager = null;
    private static final String CHANNEL_ID = "org.pepzer.mqttdroid.AuthService";
    private int notifId = 15371;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate()");
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
                .setContentText(getResources().getString(R.string.auth_notification))
                .setContentIntent(pendingIntent);
        Notification notification = notifBuilder.build();

        startForeground(notifId, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        Log.v(TAG, "onBind");
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand()");
        return Service.START_NOT_STICKY;
    }

    /**
     * Show a notification about the new authorization request.
     * @param authRequest
     *   Container object for the authorization request.
     */
    private void sendNotification(AuthRequest authRequest) {
        if (notifBuilder == null) {
            Log.e(TAG, "Notification builder is null!");
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        // use System.currentTimeMillis() to have a unique ID for the pending intent
        PendingIntent pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);

//        Drawable icon = null;
//
//        PackageManager pm = getApplicationContext().getPackageManager();
//
//        try {
//            icon = pm.getApplicationIcon(caller);
//        } catch (PackageManager.NameNotFoundException e) {
//            e.printStackTrace();
//        }
//        Bitmap bitmap = ((BitmapDrawable)icon).getBitmap();

        notifManager.notify(notifId,
                notifBuilder
                        .setContentTitle(getResources().getString(R.string.auth_notification_title))
                        .setContentText(authRequest.getAppLabel())
                        .setContentIntent(pIntent)
                        .build());
    }

    /**
     * Save the authorization request in the database.
     * If the request is flagged as update delete all previous references for the package before
     * storing the new data.
     * @param authRequest
     * @return
     */
    private boolean storeAuthRequest (AuthRequest authRequest) {
            //(String callerPackage, String callerLabel,
             //                        HashMap<String, List<String>> topics, boolean update) {
        AuthDataSource dataSource = new AuthDataSource(this);
        dataSource.open();
        boolean insert = false;

        String callerPackage = authRequest.getAppPackage();

        if (authRequest.isUpdate()) {
            dataSource.deleteAuthByPkg(callerPackage);
        }

        try {
            Date date = new Date();
            dataSource.createAuthDetails(callerPackage, authRequest.getAppLabel(),
                    date.getTime(), AuthState.APP_REFUSED);
            insert = true;
        } catch (SQLiteConstraintException e) {
            Log.w(TAG, "SQLiteConstraintException inserting " + callerPackage);
            e.printStackTrace();
        }

        if (insert) {
            Map<String, List<String>> topics = authRequest.getTopics();

            List<String> pubTopics = topics.get(Utils.REQ_PUB);
            for (int i = 0; i < pubTopics.size(); ++i) {
                dataSource.createAuthPub(callerPackage, pubTopics.get(i));
            }

            List<String> subTopics = topics.get(Utils.REQ_SUB);
            for (int i = 0; i < subTopics.size(); ++i) {
                dataSource.createAuthSub(callerPackage, subTopics.get(i), 0, false);
            }
        }
        dataSource.close();
        return insert;
    }

    /**
     * Binder object for the client interface.
     */
    private final IMQTTDroidAuth.Stub mBinder = new IMQTTDroidAuth.Stub() {

        /**
         * Build the AuthRequest from the received data and forward it to the Handler.
         * @param req
         *   A map containing subscriptions and publish topics.
         * @param update
         *   True if the request should overwrite previous requests from the same client.
         */
        public void authRequest(Map req, boolean update) {
            HashMap<String, List<String>> topics = (HashMap<String, List<String>>) req;
            Log.v(TAG, "authRequest, update: " + update);

            PackageManager pm = getApplicationContext().getPackageManager();
            String callerPackage = Utils.getCallerPackage(pm);

            Log.v(TAG, "authRequest, package: " + callerPackage + ", update: " + update);
            String callerLabel = getCallerLabel(pm, callerPackage);
            AuthRequest authRequest = new AuthRequest(callerPackage, callerLabel, topics, update);
            msgHandler.sendMessage(msgHandler.obtainMessage(MSG_NEW_AUTH_REQ, authRequest));
        }

        /**
         * Check if the client package matches the local package, if true save the callback.
         * @param callback
         *   Callback sent by the client.
         */
        public void registerCallback(IMQTTDroidAuthCallback callback) {
            Log.v(TAG, "registerCallback");
            if (isLocalConnection()) {
                refreshCallback = callback;
            }
        }
    };

    /**
     * Return the application label associated with a package name.
     * @param pm
     *   An instance of `PackageManager`.
     * @param packageName
     *   The package name.
     * @return
     *   The application label.
     */
    private String getCallerLabel(PackageManager pm, String packageName) {
        String callerLabel = "n/a";
        try {
            ApplicationInfo ai = pm.getPackageInfo(packageName, pm.GET_ACTIVITIES).applicationInfo;
            callerLabel = pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return callerLabel;
    }

    /**
     * Check if the package name of the client matches the package name of the service.
     * @return
     *   True if the package names match.
     */
    private boolean isLocalConnection() {
        String caller = Utils.getCallerPackage(getApplicationContext().getPackageManager());
        return caller.equals(getPackageName());
    }

    /**
     * Container class for the authorization request.
     */
    private class AuthRequest {
        private String appPackage;
        private String appLabel;
        private Map<String, List<String>> topics;
        private boolean update;

        public AuthRequest(String appPackage, String appLabel, Map<String,
                List<String>> topics, boolean update) {
            this.appPackage = appPackage;
            this.appLabel = appLabel;
            this.topics = topics;
            this.update = update;
        }

        public String getAppPackage() {
            return appPackage;
        }

        public String getAppLabel() {
            return appLabel;
        }

        public Map<String, List<String>> getTopics() {
            return topics;
        }

        public boolean isUpdate() {
            return update;
        }
    }

    private static final int MSG_NEW_AUTH_REQ = 0;

    private final Handler msgHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override public boolean handleMessage(Message msg) {
            switch (msg.what) {

                /**
                 * Handle a new auth request.
                 * Try to store it in the database, if the request is inserted in the db notify the
                 * user through notification and if a callback is present try to notify the activity
                 * through it.
                 */
                case MSG_NEW_AUTH_REQ:
                    Log.v(TAG, "MSG_NEW_AUTH_REQ");
                    AuthRequest authRequest = (AuthRequest) msg.obj;
                    boolean inserted = storeAuthRequest(authRequest);
                    if (inserted) {
                        sendNotification(authRequest);
                        if (refreshCallback != null) {
                            try {
                                refreshCallback.newAuthRequest(authRequest.getAppPackage());
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                default:
                    Log.w(TAG, "unknown message: " + msg.toString());
            }
            return true;
        }
    });
}
