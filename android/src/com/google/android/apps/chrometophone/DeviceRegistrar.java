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

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * Register/unregister with GCM and the Chrome to Phone App Engine server.
 */
public class DeviceRegistrar extends IntentService {

    public static final String EXTRA_AUTH_TOKEN = "authtoken";

    public static final String STATUS_EXTRA = "Status";
    public static final int REGISTERED_STATUS = 1;
    public static final int AUTH_ERROR_STATUS = 2;
    public static final int UNREGISTERED_STATUS = 3;
    public static final int ERROR_STATUS = 4;

    static final String REGISTER_PATH = "/register";
    static final String UPDATE_PATH = "/update";
    static final String UNREGISTER_PATH = "/unregister";

    // Extra indicating the action to perform in the service
    static final String ACTION = "action";

    static final int REGISTER_ACTION = 1;
    static final int UNREGISTER_ACTION = 2;
    // Called to update the token, on token refresh
    static final int UPDATE_TOKEN_ACTION = 3;

    private static final String TAG = "DeviceRegistrar";


    public DeviceRegistrar() {
        super("DeviceRegistrar");
    }

    /**
     * Called from HistoryActivity and SetupActivity, to determine
     * if it needs setup. The preference is set after registration
     * with server, reset on unregister.
     */
    public static boolean isRegisteredWithServer(final Context context) {
        final String iid = Prefs.getPrefs(context).getIid();
        return !iid.equals("");
    }

    static boolean isTablet(Context context) {
        // TODO: This hacky stuff goes away when we allow users to target devices
        int xlargeBit = 4; // Configuration.SCREENLAYOUT_SIZE_XLARGE;  // upgrade to HC SDK to get this
        Configuration config = context.getResources().getConfiguration();
        return (config.screenLayout & xlargeBit) == xlargeBit;
    }

    /**
     * Make the http request to register with the server. This is blocking, will run in a thread.
     * <p/>
     * Will send a message when completed to resume the activity.
     *
     * @param context
     * @param token
     * @param handler
     * @return
     * @throws Exception
     */
    void registerWithServer(final Context context,
                            final String token,
                            Messenger handler) {
        Message msg = Message.obtain();

        try {
            // Check if this is an update from C2DM to GCM - if it is, remove the
            // old registration id.
            SharedPreferences settings = Prefs.get(context);

            // If we had a previous c2dm or GCM registration ID
            String c2dmRegId = settings.getString(Prefs.OLD_REGID, "");

            int sc = HttpClient.get(context).makeSimpleRequest(
                    DeviceRegistrar.REGISTER_PATH, token,
                    "deviceName", isTablet(context) ? "Tablet" : "Phone",
                    "updatedIID", c2dmRegId);


            if (sc == 200) {
                SharedPreferences.Editor editor = settings.edit();

                String iid = InstanceID.getInstance(this).getToken(Prefs.SENDER_ID, "GCM");
                editor.putString(Prefs.IID, iid);

                if (c2dmRegId.length() > 0) {
                    Log.i(TAG, "Removing old C2DM registration id");
                    editor.remove("deviceRegistrationID");
                }
                editor.commit();
                msg.what = SetupActivity.MSG_REGISTERED;
            } else {
                msg.what = SetupActivity.MSG_REG_ERR;
                msg.arg1 = sc;
                Log.w(TAG, "Registration error " + sc);
            }

        } catch (Exception e) {
            msg.what = SetupActivity.MSG_REG_ERR;
            msg.arg1 = 501; // dummy status code
            Log.w(TAG, "Registration error " + e.getMessage());
        }

        sendResponse(handler, msg);
    }

    /**
     * Called when GCM requests a token update.
     *
     * Will send the saved token and a fresh token. Server will
     * update the registration entry, looking up by the old token.
     *
     * If the server detects a different identity, will create a new
     * registration entry. This would happen in case of backup/restore
     * or if device ID is reset.
     */
    private void updateToken() {
        try {
            // Send the old token, if any - the server may need to update/track
            String old = Prefs.getPrefs(this).getIid();

            // Current token is sent in all requests

            int sc = HttpClient.get(this).makeSimpleRequest(
                    DeviceRegistrar.UPDATE_PATH, null,
                    "updatedIID", old);
            if (sc == 200) {
                return;
            } else {
                Log.w(TAG, "Update error " + sc);
            }
        } catch (Exception e) {
            Log.w(TAG, "Update error " + e.getMessage());
        }
    }

    /**
     * Call unregister - will unregister with the
     * server.
     */
    void startUnregister(final Context context, Messenger messenger) {
        final String iid = Prefs.getPrefs(context).getIid();
        if (iid.equals("")) {
            return;
        }
        Message msg = new Message();

        try {
            int sc = HttpClient.get(context).makeSimpleRequest(UNREGISTER_PATH,
                    null);
            if (sc == 200) {
                InstanceID.getInstance(context).deleteToken(Prefs.SENDER_ID, "GCM");

                Prefs.getPrefs(context).setIid("");
                Prefs.getPrefs(context).setAccount("");

                msg.what = SetupActivity.MSG_UNREGISTERED;
            } else {
                // Update dialog activity
                msg.what = SetupActivity.MSG_UNREGISTER_ERROR;
                msg.obj = "Status: " + sc;
            }
        } catch (Exception e) {
            msg.what = SetupActivity.MSG_UNREGISTER_ERROR;
            msg.obj = e.toString();
        }
        sendResponse(messenger, msg);
    }

    private void sendResponse(Messenger messenger, Message msg) {
        if (messenger != null) {
            try {
                messenger.send(msg);
            } catch (RemoteException e) {
                // should be alive - it's in same process
                Log.i(TAG, "Unexpected messenger error", e);
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int action = intent.getIntExtra(ACTION, 0);
        Messenger messenger = (Messenger) intent.getParcelableExtra("messenger");
        switch (action) {
            case REGISTER_ACTION:
                registerWithServer(this, intent.getStringExtra(EXTRA_AUTH_TOKEN),
                        messenger);
                break;
            case UNREGISTER_ACTION:
                startUnregister(this, messenger);
                break;
            case UPDATE_TOKEN_ACTION:
                updateToken();
                break;

        }

    }

    /**
     * Called when version is updated ( app upgraded ), will update
     * registration so server knows current version.
     */
    public static final class Updater extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Intent i = new Intent(context, DeviceRegistrar.class);
            i.putExtra(ACTION, UPDATE_TOKEN_ACTION);
            context.startService(i);
        }
    }

    public static final class IIDListener extends InstanceIDListenerService {
        @Override
        public void onTokenRefresh() {
            super.onTokenRefresh();
            Intent i = new Intent(this, DeviceRegistrar.class);
            i.putExtra(ACTION, UPDATE_TOKEN_ACTION);
            startService(i);
        }
    }
}
