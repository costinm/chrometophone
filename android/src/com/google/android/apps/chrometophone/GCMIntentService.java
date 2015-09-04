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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.gms.gcm.GcmListenerService;

/**
 * Handle received messages.
 */
public class GCMIntentService extends GcmListenerService {

    public GCMIntentService() {
    }

    @Override
    public void onMessageReceived(String from, Bundle extras) {
        Context context = this;
        if (extras != null) {
            String url = (String) extras.get("url");
            String title = (String) extras.get("title");
            String sel = (String) extras.get("sel");

            if ("https://chrometophone.appspot.com/refresh".equals(url)) {
                // Special message sent by the server to request a registration refresh.
                // Used to verify if devices are still alive.
                HttpClient.get(this).makeSimpleRequest(DeviceRegistrar.UPDATE_PATH, null,
                        "title", title);
                return;
            }

            if (title != null && url != null && url.startsWith("http")) {
                Intent launchIntent = LauncherUtils.getLaunchIntent(context, title, url, sel);

                // Notify and optionally start activity
                if (Prefs.get(this).getBoolean(Prefs.BROWSER_OR_MAPS, true) && launchIntent != null) {
                    LauncherUtils.playNotificationSound(context);
                    LauncherUtils.sendIntentToApp(context, launchIntent);
                } else {
                    LauncherUtils.generateNotification(context, url, title, launchIntent);
                }

                // Record history (for link/maps only)
                if (launchIntent != null && launchIntent.getAction().equals(Intent.ACTION_VIEW)) {
                    HistoryDatabase.get(context).insertHistory(title, url);
                }
            }
        }
    }

}
