/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.database.entity

import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import android.util.LongSparseArray
import androidx.preference.PreferenceManager
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flowcrypt.email.Constants
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.model.GeneralMessageDetails
import com.flowcrypt.email.api.email.model.MessageFlag
import com.flowcrypt.email.api.email.model.OutgoingMessageInfo
import com.flowcrypt.email.database.MessageState
import com.flowcrypt.email.ui.activity.fragment.preferences.NotificationsSettingsFragment
import com.flowcrypt.email.util.SharedPreferencesHelper
import com.google.android.gms.common.util.CollectionUtils
import com.sun.mail.imap.IMAPFolder
import java.util.*
import javax.mail.Flags
import javax.mail.Message
import javax.mail.MessageRemovedException
import javax.mail.MessagingException
import javax.mail.internet.InternetAddress

/**
 * @author Denis Bondarenko
 *         Date: 12/5/19
 *         Time: 6:30 PM
 *         E-mail: DenBond7@gmail.com
 */
@Entity(tableName = "messages",
    indices = [
      Index(name = "email_in_messages", value = ["email"]),
      Index(name = "email_uid_folder_in_messages", value = ["email", "uid", "folder"], unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = BaseColumns._ID) val id: Long? = null,
    val email: String,
    val folder: String,
    val uid: Long,
    @ColumnInfo(name = "received_date", defaultValue = "NULL") val receivedDate: Long? = null,
    @ColumnInfo(name = "sent_date", defaultValue = "NULL") val sentDate: Long? = null,
    @ColumnInfo(name = "from_address", defaultValue = "NULL") val fromAddress: String? = null,
    @ColumnInfo(name = "to_address", defaultValue = "NULL") val toAddress: String? = null,
    @ColumnInfo(name = "cc_address", defaultValue = "NULL") val ccAddress: String? = null,
    @ColumnInfo(defaultValue = "NULL") val subject: String? = null,
    @ColumnInfo(defaultValue = "NULL") val flags: String? = null,
    @ColumnInfo(name = "raw_message_without_attachments", defaultValue = "NULL") val rawMessageWithoutAttachments: String? = null,
    @ColumnInfo(name = "is_message_has_attachments", defaultValue = "0") val hasAttachments: Boolean? = null,
    @ColumnInfo(name = "is_encrypted", defaultValue = "-1") val isEncrypted: Boolean? = null,
    @ColumnInfo(name = "is_new", defaultValue = "-1") val isNew: Boolean? = null,
    @ColumnInfo(defaultValue = "-1") val state: Int? = null,
    @ColumnInfo(name = "attachments_directory") val attachmentsDirectory: String? = null,
    @ColumnInfo(name = "error_msg", defaultValue = "NULL") val errorMsg: String? = null,
    @ColumnInfo(name = "reply_to", defaultValue = "NULL") val replyTo: String? = null
) {

  @Ignore
  val from: List<InternetAddress> = listOf(*InternetAddress.parse(fromAddress ?: ""))
  @Ignore
  val replyToAddress: List<InternetAddress> = listOf(*InternetAddress.parse(replyTo ?: ""))
  @Ignore
  val to: List<InternetAddress> = listOf(*InternetAddress.parse(toAddress ?: ""))
  @Ignore
  val cc: List<InternetAddress> = listOf(*InternetAddress.parse(ccAddress ?: ""))
  @Ignore
  val msgState: MessageState = MessageState.generate(state ?: MessageState.NONE.value)
  @Ignore
  val isSeen: Boolean = flags?.contains(MessageFlag.SEEN.value) ?: false

  /**
   * Generate a list of the all recipients.
   *
   * @return A list of the all recipients
   */
  val allRecipients: List<String>
    get() {
      val emails = ArrayList<String>()

      for (internetAddress in to) {
        emails.add(internetAddress.address)
      }

      for (internetAddress in cc) {
        emails.add(internetAddress.address)
      }

      return emails
    }

  companion object {
    fun genMessageEntities(context: Context, email: String, label: String, folder: IMAPFolder,
                           msgs: Array<Message>?,
                           msgsEncryptionStates: LongSparseArray<Boolean> = LongSparseArray(),
                           isNew: Boolean, areAllMsgsEncrypted: Boolean): List<MessageEntity> {
      val messageEntities = mutableListOf<MessageEntity>()
      msgs?.let { msgsList ->
        val isNotificationDisabled = NotificationsSettingsFragment.NOTIFICATION_LEVEL_NEVER ==
            SharedPreferencesHelper.getString(PreferenceManager.getDefaultSharedPreferences(context),
                Constants.PREF_KEY_MESSAGES_NOTIFICATION_FILTER, "")

        val onlyEncryptedMsgs = NotificationsSettingsFragment.NOTIFICATION_LEVEL_ENCRYPTED_MESSAGES_ONLY ==
            SharedPreferencesHelper.getString(PreferenceManager.getDefaultSharedPreferences(context),
                Constants.PREF_KEY_MESSAGES_NOTIFICATION_FILTER, "")

        for (msg in msgsList) {
          try {
            var isEncrypted: Boolean? = null
            var isNewTemp = isNew

            if (isNotificationDisabled) {
              isNewTemp = false
            }

            val isMsgEncrypted: Boolean? = if (areAllMsgsEncrypted) {
              true
            } else {
              msgsEncryptionStates.get(folder.getUID(msg))
            }

            isMsgEncrypted?.let {
              isEncrypted = it

              if (onlyEncryptedMsgs && !it) {
                isNewTemp = false
              }
            }

            messageEntities.add(genMsgEntity(email, label, msg, folder.getUID(msg),
                isNewTemp, isEncrypted))
          } catch (e: MessageRemovedException) {
            e.printStackTrace()
          }
        }
      }

      return messageEntities
    }

    /**
     * Prepare the content values for insert to the database. This method must be called in the
     * non-UI thread.
     *
     * @param email The email that the message linked.
     * @param label The folder label.
     * @param msg   The message which will be added to the database.
     * @param uid   The message UID.
     * @param isNew true if need to mark a given message as new
     * @return generated [ContentValues]
     * @throws MessagingException This exception may be occured when we call methods of thr
     * [Message] object
     */
    fun genMsgEntity(email: String, label: String, msg: Message, uid: Long, isNew: Boolean, isEncrypted: Boolean? = null): MessageEntity {
      return MessageEntity(email = email,
          folder = label,
          uid = uid,
          receivedDate = msg.receivedDate?.time,
          sentDate = msg.sentDate?.time,
          fromAddress = InternetAddress.toString(msg.from),
          replyTo = InternetAddress.toString(msg.replyTo),
          toAddress = InternetAddress.toString(msg.getRecipients(Message.RecipientType.TO)),
          ccAddress = InternetAddress.toString(msg.getRecipients(Message.RecipientType.CC)),
          subject = msg.subject,
          flags = msg.flags.toString().toUpperCase(Locale.getDefault()),
          hasAttachments = EmailUtil.hasAtt(msg),
          isNew = if (!msg.flags.contains(Flags.Flag.SEEN)) {
            isNew
          } else {
            null
          },
          isEncrypted = isEncrypted
      )
    }

    fun genMsgEntity(details: GeneralMessageDetails): MessageEntity {
      return MessageEntity(email = details.email,
          folder = details.label,
          uid = details.uid.toLong(),
          receivedDate = details.receivedDate,
          sentDate = details.sentDate,
          fromAddress = InternetAddress.toString(details.from?.toTypedArray()),
          toAddress = InternetAddress.toString(details.to?.toTypedArray()),
          ccAddress = InternetAddress.toString(details.cc?.toTypedArray()),
          subject = details.subject,
          flags = details.msgFlags.toString().toUpperCase(Locale.getDefault()),
          hasAttachments = details.hasAtts
      )
    }

    fun genMsgEntity(email: String, label: String, uid: Long, info: OutgoingMessageInfo): MessageEntity {
      return MessageEntity(email = email,
          folder = label,
          uid = uid,
          sentDate = System.currentTimeMillis(),
          subject = info.subject,
          flags = MessageFlag.SEEN.value,
          hasAttachments = !CollectionUtils.isEmpty(info.atts) || !CollectionUtils.isEmpty(info.forwardedAtts)
      )
    }
  }
}