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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.iid.InstanceID;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

// Refactored from AppengineClient - it no longer has Appengine-specific code

// replaced apache with platform client

/**
 *
 */
public class HttpClient {

    public static final String TAG = "HttpClient";
    private final Context mContext;

    static HttpClient sClient;

    public static HttpClient get(Context context) {
        if (sClient == null) {
            return new HttpClient(context);
        }
        return sClient;
    }

    private HttpClient(Context context) {
        this.mContext = context;
    }

    /**
     * Http post, with url-encoded parameters.
     * <p/>
     * Will authenticate using the instanceId and account name.
     * This is used for unregister and share.
     */
    public int makeSimpleRequest(String url, String bearerToken,
                                 String... params) {

        return makeSimpleRequest(null, url, bearerToken, params);
    }

    public int makeSimpleRequest(StringBuffer response, String url, String bearerToken,
                String... params){
        int sc = 500;
        // Need to consume the response
        response = response == null ? new StringBuffer(): response;
        HttpURLConnection con = null;

        try {
            url = Prefs.getPrefs(mContext).getBaseUrl() + url;
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            String bearerHeader = "";
            if (bearerToken != null) {
                con.setRequestProperty("Authorization", "Bearer " + bearerToken);
                bearerHeader = " -H 'Authorization:Bearer " + bearerToken + "' ";
            }
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("X-Same-Domain", "1");
            con.setDoOutput(true);

            StringBuffer sb = new StringBuffer();


            final String accountName = Prefs.getPrefs(mContext).getAccount();
            if (accountName != null) {
                param(sb, "account", accountName);
            }
            // Cached by InstanceID
            String token = InstanceID.getInstance(mContext).getToken(Prefs.SENDER_ID, "GCM");
            param(sb, "devregid", token);

            param(sb, "gcm", "true");
            param(sb, "deviceType", "ac2dm");
            PackageManager manager = mContext.getPackageManager();
            try {
                PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), 0);
                param(sb, "ver", Integer.toString(info.versionCode));
            } catch (PackageManager.NameNotFoundException e) {
                // ingore, can't happen
            }
            // For debugging and for naming
            param(sb, "build.sdk", Integer.toString(Build.VERSION.SDK_INT));
            param(sb, "build.brand", Build.BRAND);
            param(sb, "build.manufacturer", Build.MANUFACTURER);
            param(sb, "build.device", Build.DEVICE);
            param(sb, "build.model", Build.MODEL);

            for (int i = 0; i < params.length / 2; i++) {
                String k = params[2 * i];
                String v = params[2 * i + 1];
                param(sb, k, v);
            }

            OutputStream out = con.getOutputStream();
            out.write(sb.toString().getBytes());
            out.close();

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "curl -d '" + sb.toString() + "' " + bearerHeader +
                        url);
            }
            // Will cause request to be executed. Some status codes
            // result in IOException when calling getInputStream()
            sc = con.getResponseCode();

            read(response, con.getInputStream());

            return sc;
        } catch (IOException ex) {
            Log.w(TAG, "Error making http request " + ex.toString());
            if (con == null) {
                return HttpURLConnection.HTTP_BAD_REQUEST; // bad URL
            }
            try {
                read(response, con.getErrorStream());
            } catch (IOException ex1) {
                // no error stream
            }
            return sc;
        }
    }

    private void read(StringBuffer out, InputStream is) throws IOException {
        if (is == null) {
            return;
        }
        BufferedReader in = new BufferedReader(
                new InputStreamReader(is));
        String inputLine;

        while ((inputLine = in.readLine()) != null) {
            out.append(inputLine);
        }
        in.close();
    }


    private void param(StringBuffer out, String k, String v) throws IOException {
        if (out.length() != 0) {
            out.append("&");
        }
        out.append(k).append("=").append(URLEncoder.encode(v, "UTF-8"));
    }



}