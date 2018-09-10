/*
 * © 2016-2018 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;

import com.flowcrypt.email.Constants;
import com.flowcrypt.email.api.email.EmailUtil;
import com.flowcrypt.email.api.email.JavaEmailConstants;
import com.flowcrypt.email.api.email.model.AttachmentInfo;
import com.flowcrypt.email.api.email.model.MessageFlag;
import com.flowcrypt.email.api.email.model.OutgoingMessageInfo;
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper;
import com.flowcrypt.email.database.dao.source.AccountDao;
import com.flowcrypt.email.database.dao.source.AccountDaoSource;
import com.flowcrypt.email.database.dao.source.UserIdEmailsKeysDaoSource;
import com.flowcrypt.email.database.dao.source.imap.AttachmentDaoSource;
import com.flowcrypt.email.database.dao.source.imap.MessageDaoSource;
import com.flowcrypt.email.jobscheduler.JobIdManager;
import com.flowcrypt.email.js.Js;
import com.flowcrypt.email.js.PgpContact;
import com.flowcrypt.email.js.PgpKey;
import com.flowcrypt.email.js.PgpKeyInfo;
import com.flowcrypt.email.model.MessageEncryptionType;
import com.flowcrypt.email.security.SecurityStorageConnector;
import com.flowcrypt.email.util.GeneralUtil;
import com.flowcrypt.email.util.exception.NoKeyAvailableException;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.common.util.CollectionUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

/**
 * This service creates a new outgoing message using the given {@link OutgoingMessageInfo}.
 *
 * @author DenBond7
 * Date: 22.05.2017
 * Time: 22:25
 * E-mail: DenBond7@gmail.com
 */

public class PrepareOutgoingMessagesJobIntentService extends JobIntentService {
    private static final String EXTRA_KEY_OUTGOING_MESSAGE_INFO = GeneralUtil.generateUniqueExtraKey
            ("EXTRA_KEY_OUTGOING_MESSAGE_INFO", PrepareOutgoingMessagesJobIntentService.class);
    private static final String TAG = PrepareOutgoingMessagesJobIntentService.class.getSimpleName();

    private MessageDaoSource messageDaoSource;
    private Js js;
    private Session session;
    private Store store;
    private AccountDao accountDao;
    private File pgpCacheDirectory;

    /**
     * Enqueue a new task for {@link PrepareOutgoingMessagesJobIntentService}.
     *
     * @param context             Interface to global information about an application environment.
     * @param outgoingMessageInfo {@link OutgoingMessageInfo} which contains information about an outgoing message.
     */
    public static void enqueueWork(Context context, OutgoingMessageInfo outgoingMessageInfo) {
        if (outgoingMessageInfo != null) {
            Intent intent = new Intent(context, PrepareOutgoingMessagesJobIntentService.class);
            intent.putExtra(EXTRA_KEY_OUTGOING_MESSAGE_INFO, outgoingMessageInfo);

            enqueueWork(context, PrepareOutgoingMessagesJobIntentService.class, JobIdManager
                    .JOB_TYPE_PREPARE_OUT_GOING_MESSAGE, intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        messageDaoSource = new MessageDaoSource();
        accountDao = new AccountDaoSource().getActiveAccountInformation(getApplicationContext());
        session = OpenStoreHelper.getSessionForAccountDao(getApplicationContext(), accountDao);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public boolean onStopCurrentWork() {
        Log.d(TAG, "onStopCurrentWork");
        return super.onStopCurrentWork();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(TAG, "onHandleWork");
        if (intent.hasExtra(EXTRA_KEY_OUTGOING_MESSAGE_INFO)) {
            OutgoingMessageInfo outgoingMessageInfo = intent.getParcelableExtra(EXTRA_KEY_OUTGOING_MESSAGE_INFO);

            Log.d(TAG, "Received a new job: " + outgoingMessageInfo);

            setupIfNeed();

            try {
                String[] pubKeys = outgoingMessageInfo.getMessageEncryptionType() == MessageEncryptionType.ENCRYPTED ?
                        getPubKeys(outgoingMessageInfo) : null;

                String rawMessage = EmailUtil.generateRawMessageWithoutAttachments(outgoingMessageInfo, js, pubKeys);
                long generatedUID = messageDaoSource.getLastUIDOfMessageInLabel(getApplicationContext(),
                        accountDao.getEmail(), JavaEmailConstants.FOLDER_OUTBOX) + 1;

                Uri newMessageUri = messageDaoSource.addRow(getApplicationContext(),
                        accountDao.getEmail(), JavaEmailConstants.FOLDER_OUTBOX, generatedUID,
                        new MimeMessage(session, IOUtils.toInputStream(rawMessage, StandardCharsets.UTF_8)), false);

                if (newMessageUri != null) {
                    updateMessage(outgoingMessageInfo, rawMessage, generatedUID);
                    addAttachmentsToCache(outgoingMessageInfo, generatedUID, pubKeys);
                }
            } catch (Exception e) {
                e.printStackTrace();
                //todo-denbond7 need to handle this
            }
        }
    }

    private void updateMessage(OutgoingMessageInfo outgoingMessageInfo, String rawMessage, long generatedUID) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MessageDaoSource.COL_RAW_MESSAGE_WITHOUT_ATTACHMENTS, rawMessage);
        contentValues.put(MessageDaoSource.COL_FLAGS, MessageFlag.SEEN);
        contentValues.put(MessageDaoSource.COL_SENT_DATE, System.currentTimeMillis());
        contentValues.put(MessageDaoSource.COL_IS_MESSAGE_HAS_ATTACHMENTS,
                !CollectionUtils.isEmpty(outgoingMessageInfo.getAttachmentInfoArrayList())
                        || !CollectionUtils.isEmpty(outgoingMessageInfo.getForwardedAttachmentInfoList()));
        contentValues.put(MessageDaoSource.COL_IS_ENCRYPTED,
                outgoingMessageInfo.getMessageEncryptionType() == MessageEncryptionType.ENCRYPTED);

        messageDaoSource.updateMessage(getApplicationContext(), accountDao.getEmail(),
                JavaEmailConstants.FOLDER_OUTBOX, generatedUID, contentValues);
    }

    private void addAttachmentsToCache(OutgoingMessageInfo outgoingMessageInfo, long generatedUID, String[] pubKeys)
            throws IOException {
        AttachmentDaoSource attachmentDaoSource = new AttachmentDaoSource();
        if (outgoingMessageInfo.getMessageEncryptionType() == MessageEncryptionType.ENCRYPTED) {
            List<AttachmentInfo> encryptedFiles = new ArrayList<>();
            List<AttachmentInfo> allAttachments = new ArrayList<>();

            if (!CollectionUtils.isEmpty(outgoingMessageInfo.getAttachmentInfoArrayList())) {
                allAttachments.addAll(outgoingMessageInfo.getAttachmentInfoArrayList());
            }

            if (!CollectionUtils.isEmpty(outgoingMessageInfo.getForwardedAttachmentInfoList())) {
                allAttachments.addAll(outgoingMessageInfo.getForwardedAttachmentInfoList());
            }

            for (AttachmentInfo attachmentInfo : allAttachments) {
                InputStream inputStream = getContentResolver().openInputStream(attachmentInfo.getUri());
                if (inputStream != null) {
                    File encryptedTempFile = generateTempFile(pgpCacheDirectory, attachmentInfo.getName());
                    byte[] encryptedBytes = js.crypto_message_encrypt(pubKeys, IOUtils.toByteArray
                            (inputStream), attachmentInfo.getName());
                    FileUtils.writeByteArrayToFile(encryptedTempFile, encryptedBytes);
                    attachmentInfo.setUri(FileProvider.getUriForFile(getApplicationContext(),
                            Constants.FILE_PROVIDER_AUTHORITY, encryptedTempFile));
                    attachmentInfo.setName(encryptedTempFile.getName());
                    encryptedFiles.add(attachmentInfo);
                }
            }

            attachmentDaoSource.addRows(getApplicationContext(), accountDao.getEmail(),
                    JavaEmailConstants.FOLDER_OUTBOX, generatedUID, encryptedFiles);
        } else {
            attachmentDaoSource.addRows(getApplicationContext(),
                    accountDao.getEmail(), JavaEmailConstants.FOLDER_OUTBOX, generatedUID,
                    outgoingMessageInfo.getAttachmentInfoArrayList());

            attachmentDaoSource.addRows(getApplicationContext(),
                    accountDao.getEmail(), JavaEmailConstants.FOLDER_OUTBOX, generatedUID,
                    outgoingMessageInfo.getForwardedAttachmentInfoList());
        }
    }

    /**
     * Generate a temp file for IO operations.
     *
     * @param parentDirectory The parent directory where a new file will be created.
     * @param fileName        The name of the created file
     * @return Generated {@link File}
     */
    private File generateTempFile(File parentDirectory, String fileName) {
        return new File(parentDirectory, fileName + ".pgp");
    }

    private void setupIfNeed() {
        if (js == null) {
            try {
                js = new Js(getApplicationContext(), new SecurityStorageConnector(getApplicationContext()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (store == null || !store.isConnected()) {
            try {
                store = OpenStoreHelper.openAndConnectToStore(getApplicationContext(), accountDao, session);
            } catch (MessagingException | IOException | GoogleAuthException e) {
                e.printStackTrace();
            }
        }

        if (pgpCacheDirectory == null) {
            pgpCacheDirectory = new File(getCacheDir(), Constants.PGP_ATTACHMENTS_CACHE_DIR);
            if (!pgpCacheDirectory.exists()) {
                if (!pgpCacheDirectory.mkdirs()) {
                    throw new IllegalStateException("Create cache directory " + pgpCacheDirectory.getName() +
                            " filed!");
                }
            }
        }
    }

    /**
     * Get public keys for recipients + keys of the sender;
     *
     * @return <tt>String[]</tt> An array of public keys.
     */
    private String[] getPubKeys(OutgoingMessageInfo outgoingMessageInfo) throws NoKeyAvailableException {
        ArrayList<String> publicKeys = new ArrayList<>();
        for (PgpContact pgpContact : EmailUtil.getAllRecipients(outgoingMessageInfo)) {
            if (!TextUtils.isEmpty(pgpContact.getPubkey())) {
                publicKeys.add(pgpContact.getPubkey());
            }
        }

        publicKeys.add(getAccountPublicKey(outgoingMessageInfo));

        return publicKeys.toArray(new String[0]);
    }

    /**
     * Get a public key of the sender;
     *
     * @return <tt>String</tt> The sender public key.
     */
    private String getAccountPublicKey(OutgoingMessageInfo outgoingMessageInfo) throws NoKeyAvailableException {
        UserIdEmailsKeysDaoSource userIdEmailsKeysDaoSource = new UserIdEmailsKeysDaoSource();
        List<String> longIds = userIdEmailsKeysDaoSource.getLongIdsByEmail(getApplicationContext(),
                outgoingMessageInfo.getFromPgpContact().getEmail());

        if (longIds.isEmpty()) {
            if (accountDao.getEmail().equalsIgnoreCase(outgoingMessageInfo.getFromPgpContact().getEmail())) {
                throw new NoKeyAvailableException(getApplicationContext(), accountDao.getEmail(), null);
            } else {
                longIds = userIdEmailsKeysDaoSource.getLongIdsByEmail(getApplicationContext(), accountDao.getEmail());
                if (longIds.isEmpty()) {
                    throw new NoKeyAvailableException(getApplicationContext(), accountDao.getEmail(),
                            outgoingMessageInfo.getFromPgpContact().getEmail());
                }
            }
        }

        PgpKeyInfo pgpKeyInfo = new SecurityStorageConnector(getApplicationContext()).getPgpPrivateKey(longIds.get(0));
        if (pgpKeyInfo != null) {
            PgpKey pgpKey = js.crypto_key_read(pgpKeyInfo.getPrivate());
            if (pgpKey != null) {
                return pgpKey.toPublic().armor();
            }
        }

        throw new IllegalArgumentException("Internal error: PgpKeyInfo is null!");
    }
}
