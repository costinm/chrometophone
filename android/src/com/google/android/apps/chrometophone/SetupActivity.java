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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Setup activity - takes user through the setup, if account and registration
 * are not complete, or to the preferences screen if connected.
 */
public class SetupActivity extends Activity implements Handler.Callback {

    public static final String TAG = "c2p";

    static final int MSG_AUTH_UI = 1;
    static final int MSG_REG_MSG = 2;
    static final int MSG_REGISTERED = 3;
    static final int MSG_REG_ERR = 4;
    static final int MSG_UNREGISTERED = 5;
    static final int MSG_UNREGISTER_ERROR = 6;
    static final int MSG_AUTH_ERR = 2;

    private final Handler mHandler = new Handler(this);
    private final Messenger messenger = new Messenger(mHandler);

    private boolean mPendingAuth = false;
    private int mScreenId = -1;
    private int mAccountSelectedPosition = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showContent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showContent();
        if (mPendingAuth) {
            mPendingAuth = false;

            if (!DeviceRegistrar.isRegisteredWithServer(this)) {
                // registration not done yet - restart registration after
                // intermediate screen.
                register();
            }
        }
    }

    private void showContent() {
        // Decide what to show:
        // - if no account set, intro
        // -
        // - if registered: real config

        if (!DeviceRegistrar.isRegisteredWithServer(this)) {
            // If not registered - show account selection
            if ("".equals(Prefs.getPrefs(this).getAccount())) {
                // First time, or after logout.
                setIntroScreenContent();
            } else {
                // intro shown, selected an account - but register
                // didn't finish. Allow user to change account.
                setSelectAccountScreenContent();
            }
        } else {
            // Account selected - show normal prefs
            setConnectedScreenContent();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.setup, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.help: {
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }


    private void setIntroScreenContent() {
        setContentView(R.layout.intro);
        String introText = getString(R.string.intro_text)
                .replace("{tos_link}", HelpActivity.getTOSLink())
                .replace("{pp_link}", HelpActivity.getPPLink());
        TextView textView = (TextView) findViewById(R.id.intro_text);
        textView.setText(Html.fromHtml(introText));
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        Button exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button nextButton = (Button) findViewById(R.id.next);
        nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelectAccountScreenContent();
            }
        });
    }

    /**
     * Select account, call register() on next.
     */
    private void setSelectAccountScreenContent() {
        setContentView(R.layout.select_account);
        final Button backButton = (Button) findViewById(R.id.back);
        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setIntroScreenContent();
            }
        });

        final Button nextButton = (Button) findViewById(R.id.next);
        nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ListView listView = (ListView) findViewById(R.id.select_account);
                mAccountSelectedPosition = listView.getCheckedItemPosition();
                TextView account = (TextView) listView.getChildAt(mAccountSelectedPosition);
                backButton.setEnabled(false);
                nextButton.setEnabled(false);

                Prefs.getPrefs(SetupActivity.this).setAccount((String) account.getText());

                register();
            }
        });

        // Display accounts
        String accounts[] = getGoogleAccounts();
        if (accounts.length == 0) {
            TextView promptText = (TextView) findViewById(R.id.select_text);
            promptText.setText(R.string.no_accounts);
            TextView nextText = (TextView) findViewById(R.id.click_next_text);
            nextText.setVisibility(TextView.INVISIBLE);
            nextButton.setEnabled(false);
        } else {
            ListView listView = (ListView) findViewById(R.id.select_account);
            listView.setAdapter(new ArrayAdapter<String>(this,
                    R.layout.account, accounts));
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setItemChecked(mAccountSelectedPosition, true);
        }
    }

    private void setSelectLaunchModeScreenContent() {
        setContentView(R.layout.select_launch_mode);
        Button backButton = (Button) findViewById(R.id.back);
        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelectAccountScreenContent();
            }
        });

        Button nextButton = (Button) findViewById(R.id.next);
        nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                storeLaunchModePreference();
                setSetupCompleteScreenContent();
            }
        });

        setLaunchModePreferenceUI();
    }

    private void setSetupCompleteScreenContent() {
        setContentView(R.layout.setup_complete);
        TextView textView = (TextView) findViewById(R.id.setup_complete_text);
        textView.setText(Html.fromHtml(getString((R.string.setup_complete_text))));

        Button backButton = (Button) findViewById(R.id.back);
        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelectLaunchModeScreenContent();
            }
        });

        final Context context = this;
        Button finishButton = (Button) findViewById(R.id.finish);
        finishButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Setup done, return to history screen
                finish();
            }
        });
    }

    /**
     * Shown when registration is complete (iid and account set), to
     * edit active preferences, when called from HistoryActivity menu
     */
    private void setConnectedScreenContent() {
        setContentView(R.layout.connected);
        SharedPreferences prefs = Prefs.get(this);
        TextView statusText = (TextView) findViewById(R.id.connected_with_account_text);
        statusText.setText(getString(R.string.connected_with_account_text) + " " +
                prefs.getString("accountName", "error"));

        setLaunchModePreferenceUI();

        RadioGroup launchMode = (RadioGroup) findViewById(R.id.launch_mode_radio);
        launchMode.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                storeLaunchModePreference();
            }
        });


        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                unregister();
            }
        });
    }

    private void storeLaunchModePreference() {
        SharedPreferences prefs = Prefs.get(this);
        SharedPreferences.Editor editor = prefs.edit();
        RadioGroup launchMode = (RadioGroup) findViewById(R.id.launch_mode_radio);
        editor.putBoolean(Prefs.BROWSER_OR_MAPS,
                launchMode.getCheckedRadioButtonId() == R.id.auto_launch);
        editor.commit();
    }

    private void setLaunchModePreferenceUI() {
        SharedPreferences prefs = Prefs.get(this);
        if (prefs.getBoolean(Prefs.BROWSER_OR_MAPS, true)) {
            RadioButton automaticButton = (RadioButton) findViewById(R.id.auto_launch);
            automaticButton.setChecked(true);
        } else {
            RadioButton manualButton = (RadioButton) findViewById(R.id.manual_launch);
            manualButton.setChecked(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_AUTH) {
            // recoverable auth result, retry to register
            register();
        }
    }

    private static final int RC_AUTH = 1;
    /**
     * Called from 'onClick' when account is selected.
     * <p/>
     * Also called from 'onResume', if pending authentication - to deal
     * with account manger activities.
     * <p/>
     * Will grab the auth token and call the registration functions.
     */
    private void register() {
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        TextView textView = (TextView) findViewById(R.id.connecting_text);
        textView.setVisibility(ProgressBar.VISIBLE);

        final String account = Prefs.getPrefs(SetupActivity.this).getAccount();

        boolean hasPlayServices = true;
        try {
            getPackageManager().getPackageInfo("com.google.android.gms", 0);
        } catch (PackageManager.NameNotFoundException e) {
            hasPlayServices = false;
        }

        final Message msg = Message.obtain(mHandler, MSG_AUTH_ERR);
        // Getting the token may require user interaction.
        // Since we are in an activity, use the interactive version.
        final String scope = Prefs.getPrefs(SetupActivity.this).getScope();

        if (hasPlayServices || Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // On Gingerbread will cause PlayServices to be downloaded.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String token = GoogleAuthUtil.getToken(SetupActivity.this, account, scope);
                        startRegister(token);
                    } catch (IOException e) {
                        msg.obj = e.toString();
                        msg.sendToTarget();

                    } catch (UserRecoverableAuthException e) {
                        startActivityForResult(e.getIntent(), RC_AUTH);

                    } catch (GoogleAuthException e) {

                        msg.obj = e.toString();
                        msg.sendToTarget();
                    }

                }
            }).start();
        } else {
            // Play services not available - ICS to JB MR2. Use AccountManager


            AccountManager.get(this).getAuthToken(new Account(account, "com.google"),
                    scope, null, this,
                    new AccountManagerCallback<Bundle>() {
                        @Override
                        public void run(AccountManagerFuture<Bundle> future) {
                            try {
                                String token = future.getResult().getString(AccountManager.KEY_AUTHTOKEN);
                                AccountManager.get(SetupActivity.this).invalidateAuthToken("com.google", token);

                                AccountManager.get(SetupActivity.this).getAuthToken(
                                        new Account(account, "com.google"),
                                        Prefs.getPrefs(SetupActivity.this).getScope(),
                                        null,
                                        SetupActivity.this,
                                        new AccountManagerCallback<Bundle>() {
                                            @Override
                                            public void run(AccountManagerFuture<Bundle> future) {
                                                try {
                                                    String token = future.getResult().getString(AccountManager.KEY_AUTHTOKEN);

                                                    Log.i(TAG, "Got KEY_AUTHTOKEN: " + token);

                                                    if (token != null) {
                                                        // At the end of the flow will call startActivity.
                                                        startRegister(token);
                                                    } else {
                                                        Message msg = Message.obtain(mHandler, MSG_AUTH_ERR);
                                                        msg.obj = "Missing token";
                                                        msg.sendToTarget();
                                                    }

                                                } catch (OperationCanceledException e) {
                                                    Log.i(TAG, "The user has denied you access to the API");
                                                    Message msg = Message.obtain(mHandler, MSG_AUTH_ERR);
                                                    msg.obj = e.toString();
                                                    msg.sendToTarget();
                                                } catch (Exception e) {
                                                    Log.i(TAG, "Exception: ", e);
                                                    Message msg = Message.obtain(mHandler, MSG_AUTH_ERR);
                                                    msg.obj = e.toString();
                                                    msg.sendToTarget();
                                                }

                                            }
                                        }, null);


                            } catch (OperationCanceledException e) {
                                Log.i(TAG, "The user has denied you access to the API");
                                Message msg = Message.obtain(mHandler, MSG_AUTH_ERR);
                                msg.obj = e.toString();
                                msg.sendToTarget();
                            } catch (Exception e) {
                                Log.i(TAG, "Exception: ", e);
                                Message msg = Message.obtain(mHandler, MSG_AUTH_ERR);
                                msg.obj = e.toString();
                                msg.sendToTarget();
                            }
                        }
                    }, null);
        }

    }

    /**
     * Call register in background, this is called from activities to make sure we are
     * registered.
     */
    void startRegister(final String token) {
        Intent i = new Intent(this, DeviceRegistrar.class);
        i.putExtra(DeviceRegistrar.ACTION, DeviceRegistrar.REGISTER_ACTION);
        i.putExtra("messenger", messenger);
        i.putExtra(DeviceRegistrar.EXTRA_AUTH_TOKEN, token);
        startService(i);
    }

    private void unregister() {
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setVisibility(ProgressBar.VISIBLE);
        TextView textView = (TextView) findViewById(R.id.disconnecting_text);
        textView.setVisibility(ProgressBar.VISIBLE);

        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setEnabled(false);

        Intent i = new Intent(this, DeviceRegistrar.class);
        i.putExtra(DeviceRegistrar.ACTION, DeviceRegistrar.UNREGISTER_ACTION);
        i.putExtra("messenger", messenger);
        startService(i);
    }

    private String[] getGoogleAccounts() {
        ArrayList<String> accountNames = new ArrayList<String>();
        Account[] accounts = AccountManager.get(this).getAccounts();
        for (Account account : accounts) {
            if (account.type.equals("com.google")) {
                accountNames.add(account.name);
            }
        }

        String[] result = new String[accountNames.size()];
        accountNames.toArray(result);
        return result;
    }

    /**
     * While setup screen is active, it'll display a progress bar
     * and interact with DeviceRegistrar service for background
     * operations. Communication happens using messages.
     *
     * @param msg
     */
    @Override
    public boolean handleMessage(Message msg) {
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        TextView textView = (TextView) findViewById(R.id.connecting_text);

        switch (msg.what) {
            case MSG_AUTH_UI:
                Intent authIntent = ((Bundle) msg.obj).getParcelable(AccountManager.KEY_INTENT);
                if (authIntent != null) {
                    mPendingAuth = true;
                    startActivity(authIntent);
                }
                break;

            case MSG_AUTH_ERR: {
                progressBar.setVisibility(ProgressBar.INVISIBLE);
                textView.setText("Authentication error " + msg.obj);
                Button backButton = (Button) findViewById(R.id.back);
                backButton.setEnabled(true);

                Button nextButton = (Button) findViewById(R.id.next);
                nextButton.setEnabled(true);

                break;
            }

            case MSG_UNREGISTERED:
                // Returned after successful unregistration
                setIntroScreenContent();
                break;

            case MSG_UNREGISTER_ERROR:
                progressBar.setVisibility(ProgressBar.INVISIBLE);

                TextView textViewDisconnecting = (TextView) findViewById(R.id.disconnecting_text);
                textViewDisconnecting.setText(R.string.disconnect_error_text);

                Button disconnectButton = (Button) findViewById(R.id.disconnect);
                disconnectButton.setEnabled(true);
                break;

            case MSG_REGISTERED:
                setSelectLaunchModeScreenContent();
                break;
            case MSG_REG_ERR:
                // != 200 status from app engine on registration
                // arg1 == sc

                progressBar.setVisibility(ProgressBar.INVISIBLE);

                textView.setText(R.string.connect_error_text);

                Button backButton = (Button) findViewById(R.id.back);
                backButton.setEnabled(true);

                Button nextButton = (Button) findViewById(R.id.next);
                nextButton.setEnabled(true);
                break;
        }
        return false;
    }
}
