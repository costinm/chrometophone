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
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public final class Prefs {
    /**
     * No consent screen - but requires the signing keys to be registered.
     * http://android-developers.blogspot.de/2013/01/verifying-back-end-calls-from-android.html
     * <p/>
     * The official server (chrometophone.appspot.com) is configured with
     * the certificates used to sign the PlayStore version.
     */
    public static final String SCOPE =
            "audience:server:client_id:" +
                    "467296570994-jhoc110ncirlgk0dmuv82gr64aq1059g" +
                    ".apps.googleusercontent.com";

    // TODO: add the 'dev' cert to the gradle build.
    // We may also add a 'dev' sender id and key
    public static final String DEV_SCOPE =
            "audience:server:client_id:" +
                    "922510834487-26lfgg41esep0j076be36vtt8282id9p" +
                    ".apps.googleusercontent.com";

    static final String BASE_URL = "https://chrometophone.appspot.com";
    static final String DEV_URL = "https://datamessaginghdr.appspot.com";
    static final String LOCAL_URL = "http://10.0.2.2:8080";

    static final String SENDER_ID = "206147423037";

    public static final String PREF_ACCOUNT_NAME = "accountName";

    public static final String BROWSER_OR_MAPS = "launchBrowserOrMaps";

    public static final String OLD_REGID = "deviceRegistrationID";

    // , separated string - getStringSet is API11+
    public static final String QUEUED_URLS = "queuedUrlsList";

    public static final String IID = "iid";

    public static final String PROD_CERT = "24bb24c05e47e0aefa68a58a766179d9b613a600";
    public static final String DEV_CERT = "FE51F95820CDE90B5334588B1F7666A1627180CE";
    private final SharedPreferences sharedPreferences;
    private final Context context;

    public Prefs(Context context) {
        this.context = context;
        this.sharedPreferences = get(context);
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences("CTP_PREFS", 0);
    }

    static Prefs prefs;

    public static Prefs getPrefs(Context context) {
        if (prefs == null) {
            prefs = new Prefs(context);
        }
        return prefs;

    }

    public void setIid(String iid) {
        sharedPreferences.edit().putString("iid", "").commit();
    }

    public void setAccount(String account) {
        sharedPreferences.edit().putString(PREF_ACCOUNT_NAME, account).commit();
    }

    public String getAccount() {
        return sharedPreferences.getString(PREF_ACCOUNT_NAME, "");
    }

    /**
     * Return the registration token that was registered with the
     * chrometophone server on setup.
     * <p/>
     * If empty, we never registered - or we unregistered, and
     * SetupActivity must be run again.
     */
    public String getIid() {
        return sharedPreferences.getString(IID, "");
    }

    /**
     * For testing - or to point to a different server - allow overriding
     * the base URL.
     * <p/>
     * Use http://10.0.2.2:8080 for emulator, if the server runs on the
     * local machine.
     * <p/>
     * https://datamessaginghdr.appspot.com is a test server configured with
     * a test certificate and the test client ID.
     */
    public String getBaseUrl() {
        // Emulator
        if (Build.PRODUCT.startsWith("google_sdk")) {
            return LOCAL_URL;
        }
        String cert = getCert(context);

        if (PROD_CERT.equalsIgnoreCase(cert)) {
            return sharedPreferences.getString("url", BASE_URL);
        } else {
            return sharedPreferences.getString("url", DEV_URL);
        }
    }

    static String cert = null;

    private static String getCert(Context context) {
        if (cert == null) {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            int flags = PackageManager.GET_SIGNATURES;

            PackageInfo packageInfo = null;

            try {
                packageInfo = pm.getPackageInfo(packageName, flags);
            } catch (PackageManager.NameNotFoundException e) {
                return "";
            }
            Signature[] signatures = packageInfo.signatures;
            byte[] certB = signatures[0].toByteArray();

            // Do the dance to get the DER-encoded certificate,
            try {
                InputStream input = new ByteArrayInputStream(certB);
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                X509Certificate c = (X509Certificate) cf.generateCertificate(input);

                System.out.println("Orig: " +
                        byte2HexFormatted(certB));
                System.out.println("Encoded: " +
                        byte2HexFormatted(c.getEncoded()));

                MessageDigest md = MessageDigest.getInstance("SHA1");
                byte[] publicKey = md.digest(c.getEncoded());
                cert = byte2HexFormatted(publicKey);

                System.out.println("SHA1: " + cert);
            } catch (CertificateException|NoSuchAlgorithmException e) {
                e.printStackTrace();
                return "";
            }
        }
        return cert;
    }

    public static String byte2HexFormatted(byte[] arr) {
        StringBuilder str = new StringBuilder(arr.length * 2);
        for (int i = 0; i < arr.length; i++) {
            String h = Integer.toHexString(arr[i]);
            int l = h.length();
            if (l == 1) h = "0" + h;
            if (l > 2) h = h.substring(l - 2, l);
            str.append(h.toUpperCase());
        }
        return str.toString();
    }

    public String getScope() {
        if (getBaseUrl().startsWith("https://chrometophone.appstpot.com")) {
            return SCOPE;
        } else {
            return DEV_SCOPE;
        }
    }
}
