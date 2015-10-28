      function onSignIn(googleUser) {
        var id_token = googleUser.getAuthResponse().id_token;
        var urlparts = window.location.href.split("?");
        var qs = "";
        if (urlparts.length >= 2) {
          qs = urlparts.slice(1).join("?");
        }
        // Only allow redirect to official extension (by signature)
        // Replace with dev version for development
	// ex:  "chrome-extension://emenjflcliefjemkadfghppnfpiaoagg/help.html#just_signed_in?auth=" +
        window.location =
          "chrome-extension://oadboiipflhobonjjffjbfekfjcgkhco/help.html#just_signed_in?auth=" +
          id_token + "&" + qs;
      };
