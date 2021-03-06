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

package com.google.android.chrometophone.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class C2DMessaging {
    private static final String UPDATE_CLIENT_AUTH = "Update-Client-Auth";
    
    private static final Logger log = Logger.getLogger(C2DMessaging.class.getName());

    public static final String PARAM_REGISTRATION_ID = "registration_id";

    public static final String PARAM_DELAY_WHILE_IDLE = "delay_while_idle";

    public static final String PARAM_COLLAPSE_KEY = "collapse_key";

    private static final String UTF8 = "UTF-8";

    /**
     * Jitter - random interval to wait before retry.
     */
    public static final int DATAMESSAGING_MAX_JITTER_MSEC = 3000;

    /**
     * This method is deprecated because C2DM has been replaced by GCM, and it
     * provides a library with similar functionality.
     */
    @Deprecated
    public static boolean sendNoRetry(Storage storage,
                                      String registrationId,
            String collapse,
            Map<String, String[]> params,
            boolean delayWhileIdle)
        throws IOException {

        // Send a sync message to this Android device.
        StringBuilder postDataBuilder = new StringBuilder();
        postDataBuilder.append(PARAM_REGISTRATION_ID).
            append("=").append(registrationId);

        if (delayWhileIdle) {
            postDataBuilder.append("&")
                .append(PARAM_DELAY_WHILE_IDLE).append("=1");
        }
        postDataBuilder.append("&").append(PARAM_COLLAPSE_KEY).append("=").
            append(collapse);

        for (Object keyObj: params.keySet()) {
            String key = (String) keyObj;
            if (key.startsWith("data.")) {
                String[] values = params.get(key);
                postDataBuilder.append("&").append(key).append("=").
                    append(URLEncoder.encode(values[0], UTF8));
            }
        }

        byte[] postData = postDataBuilder.toString().getBytes(UTF8);

        // Hit the dm URL.
        URL url = new URL("https://android.apis.google.com/c2dm/send");
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
        String authToken = storage.getConfig().getLegacyClientLogin();
        if (authToken == null) {
            log.warning("Unauthorized - missing ClientLogin token");
            return true; // Ignore legacy if failing
        }
        conn.setRequestProperty("Authorization", "GoogleLogin auth=" + authToken);

        OutputStream out = conn.getOutputStream();
        out.write(postData);
        out.close();

        int responseCode = conn.getResponseCode();

        if (responseCode == HttpServletResponse.SC_UNAUTHORIZED ||
                responseCode == HttpServletResponse.SC_FORBIDDEN) {
            // The token is too old - return false to retry later, will fetch the token
            // from DB. This happens if the password is changed or token expires. Either admin
            // is updating the token, or Update-Client-Auth was received by another server,
            // and next retry will get the good one from database.
            log.warning("Unauthorized - need token");
            C2PConfig cfg = storage.getConfig();
            cfg.setLegacyClientLogin(null);
            storage.saveConfig(cfg);
            return true; // Ignore legacy if failing
        }

        // Check for updated token header
        String updatedAuthToken = conn.getHeaderField(UPDATE_CLIENT_AUTH);
        if (updatedAuthToken != null && !authToken.equals(updatedAuthToken)) {
            log.info("Got updated auth token from datamessaging servers: " +
                    updatedAuthToken);
            C2PConfig cfg = storage.getConfig();
            cfg.setLegacyClientLogin(updatedAuthToken);
            storage.saveConfig(cfg);
        }

        String responseLine = new BufferedReader(new InputStreamReader(conn.getInputStream()))
            .readLine();

        // NOTE: You *MUST* use exponential backoff if you receive a 503 response code.
        // Since App Engine's task queue mechanism automatically does this for tasks that
        // return non-success error codes, this is not explicitly implemented here.
        // If we weren't using App Engine, we'd need to manually implement this.
        if (responseLine == null || responseLine.equals("")) {
            log.info("Got " + responseCode + 
                    " response from Google AC2DM endpoint.");
            throw new IOException("Got empty response from Google AC2DM endpoint.");
        }

        String[] responseParts = responseLine.split("=", 2);
        if (responseParts.length != 2) {
            log.warning("Invalid message from google: " +
                    responseCode + " " + responseLine);
            throw new IOException("Invalid response from Google " +
                    responseCode + " " + responseLine);
        }

        if (responseParts[0].equals("id")) {
            log.info("Successfully sent data message to device: " + responseLine);
            return true;
        }

        if (responseParts[0].equals("Error")) {
            String err = responseParts[1];
            log.warning("Got error response from Google datamessaging endpoint: " + err);
            // No retry.
            // TODO(costin): show a nicer error to the user.
            throw new IOException(err);
        } else {
            // 500 or unparseable response - server error, needs to retry
            log.warning("Invalid response from google " + responseLine + " " +
                    responseCode);
            return false;
        }
    }

  /**
   * This method is deprecated because C2DM has been replaced by GCM, and it
   * provides a library with similar functionality.
   */
  @Deprecated
  public static Object sendNoRetry(Storage storage,
                                   String token, String collapseKey,
            String... nameValues) {

        Map<String, String[]> params = new HashMap<String, String[]>();
        int len = nameValues.length;
        if (len % 2 == 1) {
            len--; // ignore last
        }
        for (int i = 0; i < len; i+=2) {
            String name = nameValues[i];
            String value = nameValues[i + 1];
            if (name != null && value != null) {
                params.put("data." + name, new String[] {value});
            }
        }

        try {
            return sendNoRetry(storage, token, collapseKey, params, true);
        } catch (IOException ex) {
            return ex;
        }
  }

}
