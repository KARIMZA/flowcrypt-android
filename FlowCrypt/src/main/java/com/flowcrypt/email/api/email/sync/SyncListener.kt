/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.sync

import android.content.Context
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.api.email.sync.tasks.CheckIsLoadedMessagesEncryptedSyncTask
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.database.entity.AccountEntity
import com.sun.mail.imap.IMAPFolder
import javax.mail.Folder
import javax.mail.Message

/**
 * This class can be used for communication with [EmailSyncManager]
 *
 * @author DenBond7
 * Date: 19.06.2017
 * Time: 13:35
 * E-mail: DenBond7@gmail.com
 */
interface SyncListener {

  /**
   * Get the service context.
   *
   * @return The service context
   */
  val context: Context

  /**
   * This method called when a message with a backup to the key owner was sent.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to
   * [android.os.Messenger].
   * @param isSent      true if the message was sent, false otherwise.
   */
  fun onMsgWithBackupToKeyOwnerSent(
      account: AccountEntity,
      ownerKey: String,
      requestCode: Int,
      isSent: Boolean)

  /**
   * This method called when the private keys found.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param keys        The private keys list.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onPrivateKeysFound(
      account: AccountEntity,
      keys: List<NodeKeyDetails>,
      ownerKey: String,
      requestCode: Int)

  /**
   * This method called when a message was sent.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to
   * [android.os.Messenger].
   * @param isSent      true if the message was sent, false otherwise.
   */
  fun onMsgSent(
      account: AccountEntity,
      ownerKey: String,
      requestCode: Int,
      isSent: Boolean
  )

  /**
   * This method called when new messages received from some folder.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param srcFolder   The source folder where the messages exist.
   * @param destFolder  The destination folder where the messages were moved.
   * @param msgs        The moved messages.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onMsgsMoved(
      account: AccountEntity,
      srcFolder: IMAPFolder,
      destFolder: IMAPFolder,
      msgs: List<Message>,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when new messages received from some folder.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param srcFolder   The source folder where the messages exist.
   * @param destFolder  The destination folder where the messages were moved.
   * @param msg         The moved message.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onMsgMoved(
      account: AccountEntity,
      srcFolder: IMAPFolder,
      destFolder: IMAPFolder,
      msg: Message?,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when new messages received from some folder.
   *
   * @param account           The [AccountEntity] object which contains information about an email
   * account;
   * @param localFolder       The local implementation of the remote folder;
   * @param remoteFolder      The folder where the new messages exist;
   * @param uid               The UID of the message;
   * @param id                Unique id of the row in the local database which identifies a message
   * @param msg               The received message;
   * @param ownerKey          The name of the reply to [android.os.Messenger];
   * @param requestCode       The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onMsgDetailsReceived(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      uid: Long,
      id: Long,
      msg: Message?,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when new messages received from some folder.
   *
   * @param account           The [AccountEntity] object which contains information about an email
   * account;
   * @param localFolder       The local implementation of the remote folder;
   * @param remoteFolder      The folder where the new messages exist;
   * @param uid               The UID of the message;
   * @param ownerKey          The name of the reply to [android.os.Messenger];
   * @param requestCode       The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onAttsInfoReceived(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      uid: Long,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when new messages received from some folder.
   *
   * @param account      The [AccountEntity] object which contains information about an
   * email account.
   * @param localFolder  The local implementation of the remote folder
   * @param remoteFolder The remote folder where the new messages exist.
   * @param msgs         The new messages.
   * @param ownerKey     The name of the reply to [android.os.Messenger].
   * @param requestCode  The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onMsgsReceived(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      msgs: Array<Message>,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when received information about new messages in some folder.
   *
   * @param account              The [AccountEntity] object which contains information about an email account.
   * @param localFolder          The local implementation of the remote folder.
   * @param remoteFolder         The folder where the new  messages exist.
   * @param newMsgs              The new messages.
   * @param msgsEncryptionStates An array which contains info about a message encryption state
   * @param ownerKey             The name of the reply to [android.os.Messenger].
   * @param requestCode          The unique request code for the reply to [android.os.Messenger].
   */
  fun onNewMsgsReceived(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      newMsgs: Array<Message>,
      msgsEncryptionStates: Map<Long, Boolean>,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when new messages received from some folder.
   *
   * @param account      The [AccountEntity] object which contains information about an
   * email account.
   * @param localFolder  The local implementation of the remote folder.
   * @param remoteFolder The folder where the messages exist.
   * @param msgs         The new messages.
   * @param ownerKey     The name of the reply to [android.os.Messenger].
   * @param requestCode  The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onSearchMsgsReceived(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      msgs: Array<Message>,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when received information about messages which already exist in the local database.
   *
   * @param account      The [AccountEntity] object which contains information about an email account.
   * @param localFolder  The local implementation of the remote folder.
   * @param remoteFolder The folder where the messages exist.
   * @param newMsgs      The refreshed messages.
   * @param updateMsgs   The messages which will must be updated.
   * @param ownerKey     The name of the reply to [android.os.Messenger].
   * @param requestCode  The unique request code for the reply to [android.os.Messenger].
   */
  fun onRefreshMsgsReceived(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      newMsgs: Array<Message>,
      updateMsgs: Array<Message>,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when new folders list received.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param folders     The new folders list.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to [android.os.Messenger].
   */
  fun onFoldersInfoReceived(
      account: AccountEntity,
      folders: Array<Folder>,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * Handle an error of synchronization.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param errorType   The error type code.
   * @param e           The exception that occurred during synchronization.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to [android.os.Messenger].
   */
  fun onError(
      account: AccountEntity,
      errorType: Int,
      e: Exception,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method can be used for debugging. Using this method we can identify a progress of some operation.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to [android.os.Messenger].
   * @param resultCode  The unique result code for the reply which identifies the progress of some request.
   * @param value       The value of the happened action.
   */
  fun onActionProgress(
      account: AccountEntity?,
      ownerKey: String,
      requestCode: Int,
      resultCode: Int,
      value: Int = 0
  )

  /**
   * This method can be used for debugging. Using this method we can notify listeners that this
   * action was cancelled.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to [android.os.Messenger].
   * @param resultCode  The unique result code for the reply which identifies the progress of some request.
   * @param value       The value of the happened action.
   */
  fun onActionCanceled(
      account: AccountEntity?,
      ownerKey: String,
      requestCode: Int,
      resultCode: Int,
      value: Int = 0
  )

  /**
   * This method can be used for debugging. Using this method we can notify listeners that this
   * action was completed.
   *
   * @param account     The [AccountEntity] object which contains information about an email account.
   * @param ownerKey    The name of the reply to [android.os.Messenger].
   * @param requestCode The unique request code for the reply to [android.os.Messenger].
   * @param resultCode  The unique result code for the reply which identifies the progress of some request.
   * @param value       The value of the happened action.
   */
  fun onActionCompleted(
      account: AccountEntity?,
      ownerKey: String,
      requestCode: Int,
      resultCode: Int = 0,
      value: Int = 0
  )

  /**
   * This method will be called when some message was changed.
   *
   * @param account      The [AccountEntity] object which contains information about an email account.
   * @param localFolder  The local implementation of the remote folder
   * @param remoteFolder The remote folder where the new messages exist.
   * @param msg          The message which was changed.
   * @param ownerKey     The name of the reply to [android.os.Messenger].
   * @param requestCode  The unique request code for the reply to [android.os.Messenger].
   */
  fun onMsgChanged(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      msg: Message,
      ownerKey: String,
      requestCode: Int
  )

  /**
   * This method called when [CheckIsLoadedMessagesEncryptedSyncTask] was completed.
   *
   * @param account      The [AccountEntity] object which contains information about an email account.
   * @param localFolder  The local implementation of the remote folder
   * @param remoteFolder The remote folder where the new messages exist.
   * @param ownerKey     The name of the reply to [android.os.Messenger].
   * @param requestCode  The unique request code for the reply to
   * [android.os.Messenger].
   */
  fun onIdentificationToEncryptionCompleted(
      account: AccountEntity,
      localFolder: LocalFolder,
      remoteFolder: IMAPFolder,
      ownerKey: String,
      requestCode: Int
  )
}
