/*
 * Business Source License 1.0 © 2017 FlowCrypt Limited (tom@cryptup.org). Use limitations apply.
 * See https://github.com/FlowCrypt/flowcrypt-android/blob/master/LICENSE
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.flowcrypt.email.BuildConfig;
import com.flowcrypt.email.R;
import com.flowcrypt.email.api.email.model.GeneralMessageDetails;
import com.flowcrypt.email.ui.activity.base.BaseBackStackActivity;
import com.flowcrypt.email.ui.activity.fragment.MessageDetailsFragment;

/**
 * This activity describe details of some message.
 *
 * @author DenBond7
 *         Date: 03.05.2017
 *         Time: 16:29
 *         E-mail: DenBond7@gmail.com
 */
public class MessageDetailsActivity extends BaseBackStackActivity {
    public static final int RESULT_CODE_MESSAGE_MOVED_TO_ANOTHER_FOLDER = 100;
    public static final int RESULT_CODE_MESSAGE_SEEN = 101;

    public static final String EXTRA_KEY_GENERAL_MESSAGE_DETAILS = BuildConfig.APPLICATION_ID + "" +
            ".EXTRA_KEY_GENERAL_MESSAGE_DETAILS";
    public static final String EXTRA_KEY_CURRENT_FOLDER = BuildConfig.APPLICATION_ID + "" +
            ".EXTRA_KEY_CURRENT_FOLDER";

    private GeneralMessageDetails generalMessageDetails;
    private String currentFolder;

    public static Intent getIntent(Context context, GeneralMessageDetails generalMessageDetails,
                                   String currentFolder) {
        Intent intent = new Intent(context, MessageDetailsActivity.class);
        intent.putExtra(EXTRA_KEY_GENERAL_MESSAGE_DETAILS, generalMessageDetails);
        intent.putExtra(EXTRA_KEY_CURRENT_FOLDER, currentFolder);
        return intent;
    }

    @Override
    public View getRootView() {
        return null;
    }

    @Override
    public int getContentViewResourceId() {
        return R.layout.activity_message_details;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null) {
            this.generalMessageDetails = getIntent().getParcelableExtra
                    (EXTRA_KEY_GENERAL_MESSAGE_DETAILS);

            this.currentFolder = getIntent().getStringExtra(
                    (EXTRA_KEY_CURRENT_FOLDER));
        }

        initViews();
    }

    @Override
    public void onBackPressed() {
        MessageDetailsFragment messageDetailsFragment = (MessageDetailsFragment)
                getSupportFragmentManager()
                        .findFragmentById(R.id.messageDetailsFragment);

        if (messageDetailsFragment == null || messageDetailsFragment.isBackPressedEnable()) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, R.string.please_wait_while_action_will_be_completed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMessageDetailsFragment(Account account) {
        MessageDetailsFragment messageDetailsFragment = (MessageDetailsFragment)
                getSupportFragmentManager()
                        .findFragmentById(R.id.messageDetailsFragment);

        if (messageDetailsFragment != null) {
            messageDetailsFragment.setGeneralMessageDetails(generalMessageDetails);
            messageDetailsFragment.updateAccount(account);
        }
    }

    private void updateFolderInMessageDetailsFragment(String folderName) {
        MessageDetailsFragment messageDetailsFragment = (MessageDetailsFragment)
                getSupportFragmentManager()
                        .findFragmentById(R.id.messageDetailsFragment);

        if (messageDetailsFragment != null) {
            messageDetailsFragment.setFolder(folderName);
        }
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        updateFolderInMessageDetailsFragment(currentFolder);
    }

}
