/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid.sqlite;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

import com.tworx.eud.mqttdroid.AuthState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;

@RunWith(AndroidJUnit4.class)

public class AuthDataSourceTest {
    private AuthDataSource authDataSource;
    private String appPackage = "org.mqttdroid.test.auth";

    @Before
    public void setUp() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        authDataSource = new AuthDataSource(appContext);
        authDataSource.open();
    }

    @After
    public void tearDown() throws Exception {
        authDataSource.deleteAuthByPkg(appPackage);
        authDataSource.close();
    }

    private void assertEqualsDetails(AppAuthDetails authDetailsDB, AppAuthDetails authDetails)
        throws Exception {
        assertThat(authDetailsDB.getId(), is(authDetails.getId()));
        assertThat(authDetailsDB.getAppPackage(), is(authDetails.getAppPackage()));
        assertThat(authDetailsDB.getAppLabel(), is(authDetails.getAppLabel()));
        assertThat(authDetailsDB.getTimestamp(), is(authDetails.getTimestamp()));
        assertThat(authDetailsDB.getAuthStatus(), is(authDetails.getAuthStatus()));
    }

    /**
     * Insert, retrieve, modify, delete an AppAuthDetails row.
     *
     * This test should revert all changes to the database before termination.
     *
     * @throws Exception
     */
    @Test
    public void testAuthDetails() throws Exception {

        AppAuthDetails authDetails = new AppAuthDetails();
        authDetails.setAppPackage(appPackage);
        authDetails.setAppLabel("Test App");
        authDetails.setTimestamp((new Date()).getTime());
        authDetails.setAuthStatus(AuthState.APP_REFUSED);

        long rowId = authDataSource.createAuthDetails(authDetails.getAppPackage(),
                authDetails.getAppLabel(), authDetails.getTimestamp(),
                authDetails.getAuthStatus());
        authDetails.setId(rowId);

        AppAuthDetails authDetailsDB = authDataSource.getAuthDetailsByPkg(appPackage);

        assertEqualsDetails(authDetailsDB, authDetails);

        authDetails.setAuthStatus(AuthState.APP_ALLOWED);
        int count = authDataSource.updateAuthDetails(authDetails);
        assertThat(count, is(1));

        authDetailsDB = authDataSource.getAuthDetailsByPkg(appPackage);
        assertEqualsDetails(authDetailsDB, authDetails);

        List<AppAuthDetails> detailsList = authDataSource.getAllAuthDetails();
        assertThat((detailsList.size() > 0), is(true));

        count = authDataSource.deleteAuthDetails(authDetails);
        assertThat(count, is(1));

        authDetailsDB = authDataSource.getAuthDetailsByPkg(appPackage);
        assertEquals(authDetailsDB, null);
    }

    private void assertEqualsAuthPub(AppAuthPub authPubDB, AppAuthPub authPub)
        throws Exception {
        assertThat(authPubDB.getId(), is(authPub.getId()));
        assertThat(authPubDB.getAppPackage(), is(authPub.getAppPackage()));
        assertThat(authPubDB.getTopic(), is(authPub.getTopic()));
    }

    /**
     * Insert, retrieve, modify, delete AppAuthPub rows.
     *
     * This test should revert all changes to the database before termination.
     *
     * @throws Exception
     */
    @Test
    public void testAuthPub() throws Exception {

        AppAuthPub authPub1 = new AppAuthPub();
        authPub1.setAppPackage(appPackage);
        authPub1.setTopic("/test/pub/1");

        long rowId = authDataSource.createAuthPub(authPub1.getAppPackage(), authPub1.getTopic());
        authPub1.setId(rowId);

        AppAuthPub authPub2 = new AppAuthPub();
        authPub2.setAppPackage(appPackage);
        authPub2.setTopic("/test/pub/2");

        rowId = authDataSource.createAuthPub(authPub2.getAppPackage(), authPub2.getTopic());
        authPub2.setId(rowId);

        int count = authDataSource.deleteAuthPub(authPub2);
        assertThat(count, is(1));

        List<AppAuthPub> pubList = authDataSource.getAuthPubsByPkg(appPackage);
        assertThat(pubList.size(), is(1));

        assertEqualsAuthPub(pubList.get(0), authPub1);

        count = authDataSource.deleteAuthPub(authPub1);
        assertThat(count, is(1));

        pubList = authDataSource.getAuthPubsByPkg(appPackage);
        assertThat(pubList.size(), is(0));
    }


    private void assertEqualsAuthSub(AppAuthSub authSubDB, AppAuthSub authSub)
            throws Exception {
        assertThat(authSubDB.getId(), is(authSub.getId()));
        assertThat(authSubDB.getAppPackage(), is(authSub.getAppPackage()));
        assertThat(authSubDB.getTopic(), is(authSub.getTopic()));
        assertThat(authSubDB.getQos(), is(authSub.getQos()));
        assertThat(authSubDB.isActive(), is(authSub.isActive()));
    }

    /**
     * Insert, retrieve, modify, delete AppAuthSub rows.
     *
     * This test should revert all changes to the database before termination.
     *
     * @throws Exception
     */
    @Test
    public void testAuthSub() throws Exception {

        AppAuthSub authSub = new AppAuthSub();
        authSub.setAppPackage(appPackage);
        authSub.setTopic("/test/sub");
        authSub.setQos(0);
        authSub.setActive(false);

        long rowId = authDataSource.createAuthSub(authSub.getAppPackage(),
                authSub.getTopic(), authSub.getQos(), authSub.isActive());
        authSub.setId(rowId);

        AppAuthSub authSubDB = authDataSource.getAuthSub(authSub.getAppPackage(),
                authSub.getTopic());

        assertEqualsAuthSub(authSubDB, authSub);

        List<AppAuthSub> subList = authDataSource.getAuthSubsByPkg(appPackage);
        assertThat(subList.size(), is(1));

        assertEqualsAuthSub(subList.get(0), authSub);

        subList = authDataSource.getActiveAuthSubsByPkg(appPackage);
        assertThat(subList.size(), is(0));

        authSub.setActive(true);
        int count = authDataSource.updateAuthSub(authSub);
        assertThat(count, is(1));

        subList = authDataSource.getActiveAuthSubsByPkg(appPackage);
        assertThat(subList.size(), is(1));

        assertEqualsAuthSub(subList.get(0), authSub);

        count = authDataSource.deleteAuthSub(authSub);
        assertThat(count, is(1));

        subList = authDataSource.getAuthSubsByPkg(appPackage);
        assertThat(subList.size(), is(0));
    }
}
