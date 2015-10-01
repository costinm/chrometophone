/**
 * Extracted from oauth_interstitial.js
 */
function handleLoginToken() {
  var params = getQueryStringParams();
  if (params['auth']) {
    // Redirected at end of login flow, with a short lived
    // token. Send the token to server for decoding to register
    var params = {
      "deviceId": localStorage['deviceRegistrationId'],
      "devregid": localStorage['deviceRegistrationId'], //TODO: use Chrome GCM, or a local token
      "auth": params['auth'],
      "deviceType": "chrome2",
      "ver": apiVersion
    };
    var data = JSON.stringify(params);

    // Unlike previous auth scheme with Oauth1, we don't authenticate
    // with the account on each request.
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function(data) {
      if (xhr.readyState == 4) {
        if (xhr.status == 200) {
          console.log("Response: " + xhr.responseText);
          var resp = JSON.parse(xhr.responseText);
          localStorage['token'] = resp.token;
          localStorage['account'] = resp.account;
        } else {
          // Should display an error message, now it just stays
          // not logged in
        }
      }
    }
    xhr.open('POST', baseUrl + "/register", true);
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
}

document.addEventListener("DOMContentLoaded", function() {
  // localize easy stuff
  Array.prototype.forEach.call(document.querySelectorAll("*[i18n-message]"),
    function(node) {
      node.textContent = chrome.i18n.getMessage(node.getAttribute(
        'i18n-message'));
    });

  // localize tos link
  document.querySelector('#gallery_tos_link').href =
    'http://chrome.google.com/extensions/intl/' +
    navigator.language.substring(0, 2) + '/gallery_tos.html';

  handleLoginToken();

  if (localStorage["token"]) {
    var link = document.createElement('a');
    link.href = 'help.html';
    link.onclick = function() {
      //oauth.clearTokens();
      // TODO: unregister with the server !!
      localStorage.removeItem("token");
    };
    link.text = chrome.i18n.getMessage('sign_out_message');
    document.querySelector('#sign_in_out_div').appendChild(link);

    if (document.location.hash == '#just_signed_in') {
      var p = document.createElement('p');
      p.style.fontWeight = 'bold';
      p.style.color = '#0a0';
      p.textContent = chrome.i18n.getMessage('signed_in_message');
      document.querySelector('#just_signed_in_div').appendChild(p);
    }
  } else {

    // Login
    var sign_in_message = chrome.i18n.getMessage('sign_in_message');
    sign_in_message = sign_in_message.substring(0, 1).toUpperCase() +
      sign_in_message.substring(1); // TODO: Get a new title case string translated
    var link = document.createElement('a');

    var deviceRegistrationId = localStorage['deviceRegistrationId'];
    if (deviceRegistrationId == undefined || deviceRegistrationId == null) {
      deviceRegistrationId = (Math.random() + '').substring(3);
      localStorage['deviceRegistrationId'] = deviceRegistrationId;
    }

    link.href = baseUrl + "/login.html?dev=" + deviceRegistrationId +
      "&api=" + apiVersion;
    //link.href = 'oauth_interstitial.html';
    link.textContent = sign_in_message;
    document.querySelector('#sign_in_out_div').appendChild(link);
  }
});

// Extracted from chrome_ex_oauth.js

/**
 * Decodes a string that has been encoded according to RFC3986.
 * @param {String} val The string to decode.
 */
var fromRfc3986 = function(val) {
  var tmp = val
    .replace(/%21/g, "!")
    .replace(/%2A/g, "*")
    .replace(/%27/g, "'")
    .replace(/%28/g, "(")
    .replace(/%29/g, ")");
  return decodeURIComponent(tmp);
};

/**
 * Decodes a URL-encoded string into key/value pairs.
 * @param {String} encoded An URL-encoded string.
 * @return {Object} An object representing the decoded key/value pairs found
 *     in the encoded string.
 */
var formDecode = function(encoded) {
  var params = encoded.split("&");
  var decoded = {};
  for (var i = 0, param; param = params[i]; i++) {
    var keyval = param.split("=");
    if (keyval.length == 2) {
      var key = fromRfc3986(keyval[0]);
      var val = fromRfc3986(keyval[1]);
      decoded[key] = val;
    }
  }
  return decoded;
};
/**
 * Returns the current window's querystring decoded into key/value pairs.
 * @return {Object} A object representing any key/value pairs found in the
 *     current window's querystring.
 */
var getQueryStringParams = function() {
  var urlparts = window.location.href.split("?");
  if (urlparts.length >= 2) {
    var querystring = urlparts.slice(1).join("?");
    return formDecode(querystring);
  }
  return {};
};
