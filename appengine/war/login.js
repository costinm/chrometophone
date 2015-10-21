      function onSignIn(googleUser) {
        var id_token = googleUser.getAuthResponse().id_token;
        var urlparts = window.location.href.split("?");
        var qs = "";
        if (urlparts.length >= 2) {
          qs = urlparts.slice(1).join("?");
        }
        window.location =
          "chrome-extension://emenjflcliefjemkadfghppnfpiaoagg/help.html#just_signed_in?auth=" +
          id_token + "&" + qs;
      };
