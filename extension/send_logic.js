/*
 * Copyright 2015 Google Inc.
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

var apiVersion = 7;

// For dev purpose can be changed to custom server or specific version,
// use javascript console
var baseUrl = localStorage['chrometophoneUrl'];
if (baseUrl == undefined) {
  // This won't work very well if the URL is x.chrometophone.appspot.com,
  // there is a cert validation issue (cert is for *.appspot.com ),
  // workaround is to open the URL in the browser and accept the cert
  // warnings.
  baseUrl = "https://chrometophone.appspot.com";
  //baseUrl = "https://datamessaging.appspot.com";
  //baseUrl = "http://localhost:8080";
}

var STATUS_SUCCESS = 'success';
var STATUS_LOGIN_REQUIRED = 'login_required';
var STATUS_DEVICE_NOT_REGISTERED = 'device_not_registered';
var STATUS_GENERAL_ERROR = 'general_error';

/** Main logic to send the link.
    Must be called in extension context - either in background page
    or from popup.js.
 */
function sendToPhone(title, url, msgType, selection, listener) {
  if (localStorage['token']) {
    // Account+deviceRegistrationId is the key
    // Server will verify the token
    var params = {
      "title": title,
      "url": url,
      "sel": selection,
      "type": msgType,
      "deviceType": "ac2dm",
      "ver": apiVersion,
      "devregid": localStorage['deviceRegistrationId'], // This will be Webpush or chrome extension regid
      "account": localStorage['account'],
      "deviceId": localStorage['token']
    };

    send(baseUrl + "/send", params, listener);
  } else {
    listener(STATUS_LOGIN_REQUIRED, "Login required");
  }

}

function send(serverUrl, params, listener) {
  var data = JSON.stringify(params);
  var xhr = new XMLHttpRequest();
  xhr.onreadystatechange = function(data) {
    if (xhr.readyState == 4) {
      var req = xhr;
      var responseText = xhr.responseText;
      if (req.status == 200) {
        var body = req.responseText;
        if (body.indexOf('OK') == 0) {
          listener(STATUS_SUCCESS, "");
        } else if (body.indexOf('LOGIN_REQUIRED') == 0) {
          localStorage.removeItem("token"); // to avoid trying again
          listener(STATUS_LOGIN_REQUIRED, responseText);
        } else if (body.indexOf('DEVICE_NOT_REGISTERED') == 0) {
          listener(STATUS_DEVICE_NOT_REGISTERED, responseText);
        }
      } else {
        listener(STATUS_GENERAL_ERROR, responseText);
      }
    }
  }
  xhr.open('POST', serverUrl, true);
  var headers = {
    'X-Same-Domain': 'true',
    'Content-Type': 'application/json;charset=UTF-8',
  }
  for (var header in headers) {
    if (headers.hasOwnProperty(header)) {
      xhr.setRequestHeader(header, headers[header]);
    }
  }
  xhr.send(data);
}

function logout() {
    // Reuse the sendToPhone function
    send(baseUrl + "/unregister",
    {
      "url":"https://chrometophone.appspot.com/unregister",
      "msgType": "UNREGISTER"},
      function(status, responseText) {
        // TODO: only if status=200 ?
        console.log("Signout " + status + " " + responseText);
        localStorage.removeItem("token");
      })


}
