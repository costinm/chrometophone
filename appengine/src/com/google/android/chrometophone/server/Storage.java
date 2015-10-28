package com.google.android.chrometophone.server;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.JDOHelper;
import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.servlet.ServletContext;

/**
 * Helpers for data storage. Hides PersistentManager - for possible
 * migration.
 */
public class Storage {
    // refactored from C2DMessaging
    private static final String TOKEN_FILE = "/WEB-INF/dataMessagingToken.txt";
    private static final Logger log = Logger.getLogger(Storage.class.getName());

    private final ServletContext ctx;

    public Storage(ServletContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Initialize PMF - we use a context attribute, so other servlets can
     * be share the same instance. This is similar with a shared static
     * field, but avoids dependencies.
     */
    private static PersistenceManagerFactory getPMF(ServletContext ctx) {
        PersistenceManagerFactory pmfFactory =
                (PersistenceManagerFactory) ctx.getAttribute(
                        PersistenceManagerFactory.class.getName());
        if (pmfFactory == null) {
            pmfFactory = JDOHelper
                    .getPersistenceManagerFactory("transactions-optional");
            ctx.setAttribute(
                    PersistenceManagerFactory.class.getName(),
                    pmfFactory);
        }
        return pmfFactory;
    }

    public static Storage get(ServletContext ctx) {
        Storage storage = (Storage) ctx.getAttribute(Storage.class.getName());
        if (storage == null) {
            storage = new Storage(ctx);
            ctx.setAttribute(Storage.class.getName(), storage);
        }
        return storage;
    }

    public C2PConfig getConfig() {
        PersistenceManager pm = getPMF(ctx).getPersistenceManager();
        Key key = KeyFactory.createKey(C2PConfig.class.getSimpleName(), "default");
        C2PConfig config = null;
        try {
            config = pm.getObjectById(C2PConfig.class, key);
        } catch (JDOObjectNotFoundException e) {
            config = new C2PConfig();
            config.setKey(key);

            // First invocation or in local test mode
            // Must be in classpath, before sending. Do not checkin !
            try {
                // https://cloud.google.com/appengine/docs/java/config/appconfig
                // Use ServletContext
                InputStream is = ctx.getResourceAsStream(TOKEN_FILE);
                String token;
                if (is != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    token = reader.readLine();
                } else {
                    // happens on developement: delete entity from viewer, change
                    // token below, and run it again
                    log.log(Level.WARNING, "File " + TOKEN_FILE +
                            " not found on classpath, using hardcoded token");
                    token = "please_change_me";
                }
                config.setAuthToken(token);
            } catch (Throwable t) {
                log.log(Level.SEVERE,
                        "Can't load initial token, use admin console", t);
            }

            pm.makePersistent(config);
        } finally {
            pm.close();
        }

        return config;
    }

    public void saveConfig(C2PConfig cfg) {
        PersistenceManager pm = getPMF(ctx).getPersistenceManager();
        try {
            pm.makePersistent(cfg);
        } finally {
            pm.close();
        }
    }

    public void updateRegistration(String userName, String regId, String canonicalRegId) {
        if (ctx == null) {
            return;
        }
        log.fine("Updating regId " + regId + " to canonical " + canonicalRegId);
        PersistenceManager pm = Storage.getPMF(ctx).getPersistenceManager();

        Query query = pm.newQuery(DeviceInfo.class);
        query.setFilter("deviceRegistrationID == '" + regId + "'");

        @SuppressWarnings("unchecked")
        List<DeviceInfo> result = (List<DeviceInfo>) query.execute();
        DeviceInfo device = (result == null || result.isEmpty()) ? null : result.get(0);
        query.closeAll();

        device.setDeviceRegistrationID(canonicalRegId);

        pm.currentTransaction().begin();
        pm.makePersistent(device);
        pm.currentTransaction().commit();
    }


    public void deleteRegistration(String userName, String regId, String type) {
        if (ctx == null) {
            return;
        }
        PersistenceManager pm =
                Storage.getPMF(ctx).getPersistenceManager();
        try {
            List<DeviceInfo> registrations = getDeviceInfoForUser(pm, userName);
            for (int i = 0; i < registrations.size(); i++) {
                DeviceInfo deviceInfo = registrations.get(i);
                if (deviceInfo.getDeviceRegistrationID().equals(regId)) {
                    pm.deletePersistent(deviceInfo);
                    // Keep looping in case of duplicates
                }
            }
        } catch (JDOObjectNotFoundException e) {
            log.warning("User unknown");
        } catch (Exception e) {
            log.warning("Error unregistering device: " + e.getMessage());
        } finally {
            pm.close();
        }

    }

    /**
     * Helper function - will query all registrations for a user.
     */
    public static List<DeviceInfo> getDeviceInfoForUser(PersistenceManager pm, String user) {
        Query query = pm.newQuery(DeviceInfo.class);
        query.setFilter("key >= '" +
                user + "' && key < '" + user + "$'");
        @SuppressWarnings("unchecked")
        List<DeviceInfo> qresult = (List<DeviceInfo>) query.execute();
        // Copy to array - we need to close the query
        List<DeviceInfo> result = new ArrayList<DeviceInfo>();
        for (DeviceInfo di : qresult) {
            result.add(di);
        }
        query.closeAll();
        return result;
    }

    List<DeviceInfo> loadDevices(String userName) {
        // Context-shared PMF.
        PersistenceManager pm =
                Storage.getPMF(ctx).getPersistenceManager();
        List<DeviceInfo> devices = null;
        try {
             devices = getDeviceInfoForUser(pm,
                    userName);
            // cleanup for multi-device
            if (devices.size() > 1) {
                // Make sure there is no 'bare' registration
                // Keys are sorted - check the first
                DeviceInfo first = devices.get(0);
                Key oldKey = first.getKey();
                if (oldKey.toString().indexOf("#") < 0) {
                    log.warning("Removing old-style key " + oldKey.toString());
                    // multiple devices, first is old-style.
                    devices.remove(0);
                    pm.deletePersistent(first);
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Error loading registrations ", e);
        } finally {
            pm.close();
        }

        return devices;
    }

    public DeviceInfo saveDevice(RequestInfo reqInfo, String deviceType) throws Exception {

        String gcm = reqInfo.getParameter("gcm");
        boolean isGcm = gcm != null && gcm.equalsIgnoreCase("true");

        String deviceName = reqInfo.getParameter("deviceName");
        if (deviceName == null) {
            deviceName = "Phone";
        }
        // Context-shared PMF.
        PersistenceManager pm =
                Storage.getPMF(ctx).getPersistenceManager();
        try {
            // Get device if it already exists, else create
            Key key = KeyFactory.createKey(DeviceInfo.class.getSimpleName(),
                    reqInfo.getKey());

            // TODO: this is redundant, initDevices loads all devices for user
            DeviceInfo device = null;
            try {
                device = pm.getObjectById(DeviceInfo.class, key);
            } catch (JDOObjectNotFoundException e) { }
            if (device == null) {
                device = new DeviceInfo(key, reqInfo.deviceRegistrationID);
                device.setType(deviceType);
            } else {
                // update registration id
                device.setDeviceRegistrationID(reqInfo.deviceRegistrationID);
                // must update type, as this could be a C2DM to GCM migration
                device.setType(deviceType);
                device.setRegistrationTimestamp(new Date());
            }

            device.setName(deviceName);  // update display name
            device.setGcm(isGcm);
            // TODO: only need to write if something changed, for chrome nothing
            // changes, we just create a new channel
            pm.makePersistent(device);

            // Log only non-GCM devices - to track c2dm is still working
            if (!isGcm) {
                log.log(Level.INFO, "Registered device " + reqInfo.userName + " " +
                        deviceType + "(gcm: " + isGcm + ")");
            }
            return device;
         } finally {
            pm.close();
        }

    }

}
