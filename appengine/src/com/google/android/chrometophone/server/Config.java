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

import com.google.appengine.api.datastore.Key;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

// Refactored from C2DMConfig

/**
 * Persistent config info for the server - authentication token 
 */
@PersistenceCapable
public final class Config {
    /**
     * Config ID - "default" for the primary configuration
     * ( in future may include "staging" and other values )
     *
     * Configs can be created/modified using the admin console
     */
    @PrimaryKey
    @Persistent
    private Key key;

    @Persistent
    private String authToken;

    @Persistent
    private String legacyClientLogin;

    @Persistent
    private String oauth2RefreshToken;
    
    @Persistent 
    private String clientId;
   
    @Persistent 
    private String clientSecret;

    @Persistent 
    private String c2dmUrl;

    public String getAuthToken() {
        return (authToken == null) ? "" : authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
    
    public void setKey(Key key) {
        this.key = key;
    }

    public Key getKey() { return key; }

    public void setOauth2RefreshToken(String oauth2RefreshToken) {
        this.oauth2RefreshToken = oauth2RefreshToken;
    }

    public String getOauth2RefreshToken() {
        return oauth2RefreshToken;
    }

    public void setOAuth2ClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getOAuth2ClientId() {
        return clientId;
    }

    public void setOAuth2ClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getOAuth2ClientSecret() {
        return clientSecret;
    }

    public String getLegacyClientLogin() { return legacyClientLogin;}

    public void setLegacyClientLogin(String token) {legacyClientLogin = token;}
}