/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.chrometophone;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.Iterator;
import java.util.Set;

public class UserPresentReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
            // Added in LauncherUtils if screen is locked.
            SharedPreferences prefs = Prefs.get(context);
            String queuedUrls = prefs.getString(Prefs.QUEUED_URLS, null);

            if (queuedUrls != null) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(Prefs.QUEUED_URLS);
                editor.commit();

                String[] uris = queuedUrls.split(",");
                for (String uri: uris) {
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(viewIntent);
                    } catch (ActivityNotFoundException e) {
                    }
                }
            }

            // Will be enabled back if any message received while screen locked.
            disableMyself(context);
        }
    }

    private void disableMyself(Context context) {
        PackageManager pm = context.getPackageManager();
        ComponentName componentName = new ComponentName(context, UserPresentReceiver.class);
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
