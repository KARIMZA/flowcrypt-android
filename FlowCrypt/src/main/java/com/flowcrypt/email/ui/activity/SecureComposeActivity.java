package com.flowcrypt.email.ui.activity;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;

import com.flowcrypt.email.R;
import com.flowcrypt.email.ui.activity.base.BaseSendingMessageActivity;
import com.flowcrypt.email.ui.activity.fragment.SecureComposeFragment;
import com.flowcrypt.email.util.UIUtil;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;


/**
 * This activity describes a logic of send encrypted message.
 *
 * @author DenBond7
 *         Date: 08.05.2017
 *         Time: 14:44
 *         E-mail: DenBond7@gmail.com
 */
public class SecureComposeActivity extends BaseSendingMessageActivity {
    private View layoutContent;

    @Override
    public View getRootView() {
        return layoutContent;
    }

    @Override
    public void handleSignInResult(GoogleSignInResult googleSignInResult, boolean isOnStartCall) {
        if (googleSignInResult.isSuccess()) {
            GoogleSignInAccount googleSignInAccount = googleSignInResult.getSignInAccount();
            if (googleSignInAccount != null) {
                SecureComposeFragment secureComposeFragment = (SecureComposeFragment)
                        getSupportFragmentManager()
                                .findFragmentById(R.id.secureComposeFragment);

                if (secureComposeFragment != null) {
                    secureComposeFragment.updateAccount(googleSignInAccount.getAccount());
                }
            }
        } else if (!TextUtils.isEmpty(googleSignInResult.getStatus().getStatusMessage())) {
            UIUtil.showInfoSnackbar(getRootView(), googleSignInResult.getStatus()
                    .getStatusMessage());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secure_compose);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        layoutContent = findViewById(R.id.layoutContent);
    }

    @Override
    public boolean isMessageSendingNow() {
        SecureComposeFragment secureComposeFragment = (SecureComposeFragment)
                getSupportFragmentManager()
                        .findFragmentById(R.id.secureComposeFragment);

        return secureComposeFragment != null && secureComposeFragment.isMessageSendingNow();
    }
}