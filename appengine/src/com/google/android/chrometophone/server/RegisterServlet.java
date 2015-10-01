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

import com.google.appengine.api.channel.ChannelServiceFactory;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class RegisterServlet extends HttpServlet {
    private static final Logger log =
        Logger.getLogger(RegisterServlet.class.getName());
    private static final String OK_STATUS = "OK";
    private static final String ERROR_STATUS = "ERROR";

    private static int MAX_DEVICES = 10;

    /**
     * Return all devices and associated info, allows device selection
     * and management.
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        RequestInfo reqInfo = RequestInfo.processRequest(req, resp, getServletContext());
        if (reqInfo == null) {
            // Not authenticated or other errors
            return;
        }

        resp.setContentType("application/json");
        JSONObject regs = new JSONObject();
        try {
            regs.put("user", reqInfo.userName);

            JSONArray devices = new JSONArray();
            for (DeviceInfo di: reqInfo.devices) {
                JSONObject dijson = new JSONObject();
                dijson.put("key", di.getKey().toString());
                dijson.put("name", di.getName());
                dijson.put("type", di.getType());
                dijson.put("gcm", di.getGcm());
                dijson.put("regid", di.getDeviceRegistrationID());
                dijson.put("ts", di.getRegistrationTimestamp());
                devices.add(dijson);
            }
            regs.put("devices", devices);

            PrintWriter out = resp.getWriter();
            regs.writeJSONString(out);
        } catch (Exception e) {
            throw new IOException(e);
        }

    }


    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain");

        RequestInfo reqInfo = RequestInfo.processRequest(req, resp,
                getServletContext());
        if (reqInfo == null) {
            return;
        }

        if (reqInfo.deviceRegistrationID == null) {
            resp.setStatus(400);
            resp.getWriter().println(ERROR_STATUS + "(Must specify devregid)");
            log.severe("Missing registration id ");
            return;
        }

        // TODO: generate the device name by adding a number suffix for multiple
        // devices of same type. Change android app to send model/type.

        String deviceType = reqInfo.getParameter("deviceType");
        if (deviceType == null) {
            deviceType = "ac2dm";
        }

        // Because the deviceRegistrationId isn't static, we use a static
        // identifier for the device. (Can be null in older clients)
        //String deviceId = reqInfo.getParameter("deviceId");

        List<DeviceInfo> registrations = reqInfo.devices;
        if (registrations.size() > MAX_DEVICES) {
            // we could return an error - but user can't handle it yet.
            // we can't let it grow out of bounds.
            // TODO: we should also define a 'ping' message and expire/remove
            // unused registrations
            DeviceInfo oldest = registrations.get(0);
            if (oldest.getRegistrationTimestamp() == null) {
                reqInfo.deleteRegistration(oldest.getDeviceRegistrationID(), deviceType);
            } else {
                long oldestTime = oldest.getRegistrationTimestamp().getTime();
                for (int i = 1; i < registrations.size(); i++) {
                    if (registrations.get(i).getRegistrationTimestamp().getTime() <
                            oldestTime) {
                        oldest = registrations.get(i);
                        oldestTime = oldest.getRegistrationTimestamp().getTime();
                    }
                }
                reqInfo.deleteRegistration(oldest.getDeviceRegistrationID(), deviceType);
            }
        }

        try {
            DeviceInfo device = Storage.get(getServletContext()).saveDevice(reqInfo, deviceType);

            if (device.getType().equals(DeviceInfo.TYPE_CHROME)) {
                String channelId =
                    ChannelServiceFactory.getChannelService().createChannel(reqInfo.deviceRegistrationID);
                resp.getWriter().println(OK_STATUS + " " + channelId);
            } else if (device.getType().equals(DeviceInfo.TYPE_CHROME2)) {
                // Json response format, return the email and the token
                JSONObject obj = new JSONObject();
                obj.put("account", reqInfo.userName);
                obj.put("token", reqInfo.deviceRegistrationID);
                resp.getWriter().write(obj.toJSONString());
            } else {
                resp.getWriter().println(OK_STATUS);

            }
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().println(ERROR_STATUS + " (Error registering device)");
            log.log(Level.WARNING, "Error registering device.", e);
        }
    }
}
