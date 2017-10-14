/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView textView = (TextView) findViewById(R.id.icon_app_url);
        textView.setTypeface(FontManager.getTypeface(this, FontManager.FONTAWESOME));

        textView = (TextView) findViewById(R.id.icon_author);
        textView.setTypeface(FontManager.getTypeface(this, FontManager.FONTAWESOME));

        textView = (TextView) findViewById(R.id.icon_mail);
        textView.setTypeface(FontManager.getTypeface(this, FontManager.FONTAWESOME));

        textView = (TextView) findViewById(R.id.icon_license_url);
        textView.setTypeface(FontManager.getTypeface(this, FontManager.FONTAWESOME));

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.about_activity_title);
        actionBar.setDisplayHomeAsUpEnabled(true);

        PackageManager pm = getPackageManager();
        String versionName = "n/a";
        int versionCode = 0;
        try {
            PackageInfo pInfo = pm.getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
            versionCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        textView = (TextView) findViewById(R.id.about_version);
        textView.setText(versionName + "  build: " + versionCode);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
