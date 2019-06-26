/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jobscheduler

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.flowcrypt.email.Constants
import com.flowcrypt.email.api.email.FoldersManager
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.api.email.model.GeneralMessageDetails
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper
import com.flowcrypt.email.api.email.protocol.SmtpProtocolUtil
import com.flowcrypt.email.database.MessageState
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.database.dao.source.AccountDaoSource
import com.flowcrypt.email.database.dao.source.imap.AttachmentDaoSource
import com.flowcrypt.email.database.dao.source.imap.ImapLabelsDaoSource
import com.flowcrypt.email.database.dao.source.imap.MessageDaoSource
import com.flowcrypt.email.util.FileAndDirectoryUtils
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.LogsUtil
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.google.android.gms.common.util.CollectionUtils
import com.google.api.services.gmail.Gmail
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.util.MailConnectException
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.net.SocketException
import java.nio.charset.StandardCharsets
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.BodyPart
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.net.ssl.SSLException

/**
 * @author Denis Bondarenko
 * Date: 11.09.2018
 * Time: 18:43
 * E-mail: DenBond7@gmail.com
 */
class MessagesSenderJobService : JobService() {

  override fun onCreate() {
    super.onCreate()
    LogsUtil.d(TAG, "onCreate")
  }

  override fun onDestroy() {
    super.onDestroy()
    LogsUtil.d(TAG, "onDestroy")
  }

  override fun onStartJob(jobParameters: JobParameters): Boolean {
    LogsUtil.d(TAG, "onStartJob")
    SendMessagesAsyncTask(this).execute(jobParameters)
    return true
  }

  override fun onStopJob(jobParameters: JobParameters): Boolean {
    LogsUtil.d(TAG, "onStopJob")
    jobFinished(jobParameters, true)
    return false
  }

  /**
   * This is an implementation of [AsyncTask] which sends the outgoing messages.
   */
  private class SendMessagesAsyncTask internal constructor(jobService: MessagesSenderJobService) : AsyncTask<JobParameters, Boolean, JobParameters>() {
    private val weakRef: WeakReference<MessagesSenderJobService> = WeakReference(jobService)

    private var sess: Session? = null
    private var store: Store? = null
    private var isFailed: Boolean = false

    override fun doInBackground(vararg params: JobParameters): JobParameters {
      LogsUtil.d(TAG, "doInBackground")
      try {
        if (weakRef.get() != null) {
          val context = weakRef.get()!!.applicationContext
          val account = AccountDaoSource().getActiveAccountInformation(context)
          val msgDaoSource = MessageDaoSource()
          val imapLabelsDaoSource = ImapLabelsDaoSource()

          val attsCacheDir = File(context.cacheDir, Constants.ATTACHMENTS_CACHE_DIR)

          if (account != null) {
            msgDaoSource.resetMsgsWithSendingState(context, account.email)

            val queuedMsgs = msgDaoSource.getOutboxMsgs(context, account.email, MessageState.QUEUED)

            val sentButNotSavedMsgs = msgDaoSource.getOutboxMsgs(context, account.email, MessageState.SENT_WITHOUT_LOCAL_COPY)

            if (!CollectionUtils.isEmpty(queuedMsgs) || !CollectionUtils.isEmpty(sentButNotSavedMsgs)) {
              sess = OpenStoreHelper.getAccountSess(context, account)
              store = OpenStoreHelper.openStore(context, account, sess!!)
            }

            if (!CollectionUtils.isEmpty(queuedMsgs)) {
              sendQueuedMsgs(context, account, msgDaoSource, imapLabelsDaoSource, attsCacheDir)
            }

            if (!CollectionUtils.isEmpty(sentButNotSavedMsgs)) {
              saveCopyOfAlreadySentMsgs(context, account, msgDaoSource, attsCacheDir)
            }

            if (store != null && store!!.isConnected) {
              store!!.close()
            }
          }
        }

        publishProgress(false)
      } catch (e: Exception) {
        e.printStackTrace()
        publishProgress(true)
      }

      return params[0]
    }

    override fun onPostExecute(jobParameters: JobParameters) {
      LogsUtil.d(TAG, "onPostExecute")
      try {
        weakRef.get()?.jobFinished(jobParameters, isFailed)
      } catch (e: NullPointerException) {
        e.printStackTrace()
      }

    }

    override fun onProgressUpdate(vararg values: Boolean?) {
      super.onProgressUpdate(*values)
      isFailed = values[0]!!
    }

    private fun sendQueuedMsgs(context: Context, account: AccountDao, msgDaoSource: MessageDaoSource,
                               imapLabelsDaoSource: ImapLabelsDaoSource, attsCacheDir: File) {
      var list: List<GeneralMessageDetails>
      var lastMsgUID = 0
      val email = account.email
      while (true) {
        list = msgDaoSource.getOutboxMsgs(context, email, MessageState.QUEUED)
        if (CollectionUtils.isEmpty(list)) {
          break
        }
        val iterator = list.iterator()
        var msgDetails: GeneralMessageDetails? = null

        while (iterator.hasNext()) {
          val tempMsgDetails = iterator.next()
          if (tempMsgDetails.uid > lastMsgUID) {
            msgDetails = tempMsgDetails
            break
          }
        }

        if (msgDetails == null) {
          msgDetails = list[0]
        }

        lastMsgUID = msgDetails.uid
        val msgUid = msgDetails.uid
        val msgEmail = msgDetails.email
        val msgLabel = msgDetails.label

        try {
          msgDaoSource.resetMsgsWithSendingState(context, email)
          msgDaoSource.updateMsgState(context, msgEmail, msgLabel, msgUid.toLong(), MessageState.SENDING)
          Thread.sleep(2000)

          val attsDaoSource = AttachmentDaoSource()
          val attInfoList = attsDaoSource.getAttInfoList(context, email,
              JavaEmailConstants.FOLDER_OUTBOX, msgUid.toLong())

          val isMsgSent = sendMsg(context, account, msgDaoSource, msgDetails, attInfoList)

          if (!isMsgSent) {
            continue
          }

          msgDetails = msgDaoSource.getMsg(context, email, JavaEmailConstants.FOLDER_OUTBOX, msgUid.toLong())

          if (msgDetails != null && msgDetails.msgState === MessageState.SENT) {
            msgDaoSource.deleteMsg(context, email, JavaEmailConstants.FOLDER_OUTBOX, msgUid.toLong())

            if (!CollectionUtils.isEmpty(attInfoList)) {
              deleteMsgAtts(context, account, attsCacheDir, msgDetails, attsDaoSource)
            }

            val msgsCount = msgDaoSource.getOutboxMsgs(context, msgEmail).size
            imapLabelsDaoSource.updateLabelMsgsCount(context, email, JavaEmailConstants.FOLDER_OUTBOX, msgsCount)
          }
        } catch (e: Exception) {
          e.printStackTrace()
          ExceptionUtil.handleError(e)

          if (!GeneralUtil.isConnected(context)) {
            if (msgDetails.msgState !== MessageState.SENT) {
              msgDaoSource.updateMsgState(context, msgEmail, msgLabel, msgUid.toLong(), MessageState.QUEUED)
            }

            publishProgress(true)

            break
          } else {
            var newMsgState = MessageState.ERROR_SENDING_FAILED

            if (e is MailConnectException) {
              newMsgState = MessageState.QUEUED
            }

            if (e is MessagingException && e.cause != null) {
              if (e.cause is SSLException || e.cause is SocketException) {
                newMsgState = MessageState.QUEUED
              }
            }

            if (e.cause != null) {
              if (e.cause is FileNotFoundException) {
                newMsgState = MessageState.ERROR_CACHE_PROBLEM
              }
            }

            msgDaoSource.updateMsgState(context, msgEmail, msgLabel, msgUid.toLong(), newMsgState)
          }

          Thread.sleep(5000)
        }

      }
    }

    private fun saveCopyOfAlreadySentMsgs(context: Context, account: AccountDao, msgDaoSource: MessageDaoSource,
                                          attsCacheDir: File) {
      var list: List<GeneralMessageDetails>
      val email = account.email
      while (true) {
        list = msgDaoSource.getOutboxMsgs(context, email, MessageState.SENT_WITHOUT_LOCAL_COPY)
        if (CollectionUtils.isEmpty(list)) {
          break
        }
        val details = list.first()
        try {
          val attDaoSource = AttachmentDaoSource()
          val atts = attDaoSource.getAttInfoList(context, email,
              JavaEmailConstants.FOLDER_OUTBOX, details.uid.toLong())

          val mimeMsg = createMimeMsg(context, sess, details, atts)
          val isMsgSaved = saveCopyOfSentMsg(account, store, context, mimeMsg)

          if (!isMsgSaved) {
            continue
          }

          msgDaoSource.deleteMsg(context, email, JavaEmailConstants.FOLDER_OUTBOX, details.uid.toLong())

          if (!CollectionUtils.isEmpty(atts)) {
            deleteMsgAtts(context, account, attsCacheDir, details, attDaoSource)
          }
        } catch (e: Exception) {
          e.printStackTrace()
          ExceptionUtil.handleError(e)

          if (!GeneralUtil.isConnected(context)) {
            msgDaoSource.updateMsgState(context, details.email, details.label, details.uid.toLong(),
                MessageState.SENT_WITHOUT_LOCAL_COPY)
            publishProgress(true)
            break
          }

          if (e.cause != null) {
            if (e.cause is FileNotFoundException) {
              msgDaoSource.deleteMsg(context, email, JavaEmailConstants.FOLDER_OUTBOX, details.uid.toLong())
            } else {
              msgDaoSource.updateMsgState(context, details.email, details.label,
                  details.uid.toLong(), MessageState.SENT_WITHOUT_LOCAL_COPY)
            }
          } else {
            msgDaoSource.deleteMsg(context, email, JavaEmailConstants.FOLDER_OUTBOX, details.uid.toLong())
          }
        }
      }
    }

    private fun deleteMsgAtts(context: Context, account: AccountDao, attsCacheDir: File,
                              details: GeneralMessageDetails, attDaoSource: AttachmentDaoSource) {
      attDaoSource.deleteAtts(context, account.email, JavaEmailConstants.FOLDER_OUTBOX, details.uid.toLong())

      if (!TextUtils.isEmpty(details.attsDir)) {
        FileAndDirectoryUtils.deleteDir(File(attsCacheDir, details.attsDir))
      }
    }

    private fun sendMsg(context: Context, account: AccountDao, msgDaoSource: MessageDaoSource,
                        details: GeneralMessageDetails, atts: List<AttachmentInfo>): Boolean {
      val mimeMsg = createMimeMsg(context, sess, details, atts)
      val detEmail = details.email
      val detLabel = details.label

      when (account.accountType) {
        AccountDao.ACCOUNT_TYPE_GOOGLE -> {
          if (account.email.equals(details.from!!.first().address, ignoreCase = true)) {
            val transport = SmtpProtocolUtil.prepareSmtpTransport(context, sess!!, account)
            transport.sendMessage(mimeMsg, mimeMsg.allRecipients)
          } else {
            val gmail = GmailApiHelper.generateGmailApiService(context, account)
            val outputStream = ByteArrayOutputStream()
            mimeMsg.writeTo(outputStream)

            var threadId: String? = null
            val replyMsgId = mimeMsg.getHeader(JavaEmailConstants.HEADER_IN_REPLY_TO, null)

            if (!TextUtils.isEmpty(replyMsgId)) {
              threadId = getGmailMsgThreadID(gmail, replyMsgId)
            }

            var sentMsg = com.google.api.services.gmail.model.Message()
            sentMsg.raw = Base64.encodeToString(outputStream.toByteArray(), Base64.URL_SAFE)

            if (!TextUtils.isEmpty(threadId)) {
              sentMsg.threadId = threadId
            }

            sentMsg = gmail
                .users()
                .messages()
                .send(GmailApiHelper.DEFAULT_USER_ID, sentMsg)
                .execute()

            if (sentMsg.id == null) {
              return false
            }
          }

          msgDaoSource.updateMsgState(context, detEmail, detLabel, details.uid.toLong(), MessageState.SENT)
          //Gmail automatically save a copy of the sent message.
        }

        AccountDao.ACCOUNT_TYPE_OUTLOOK -> {
          val outlookTransport = SmtpProtocolUtil.prepareSmtpTransport(context, sess!!, account)
          outlookTransport.sendMessage(mimeMsg, mimeMsg.allRecipients)

          msgDaoSource.updateMsgState(context, detEmail, detLabel, details.uid.toLong(), MessageState.SENT)
        }

        else -> {
          val defaultTransport = SmtpProtocolUtil.prepareSmtpTransport(context, sess!!, account)
          defaultTransport.sendMessage(mimeMsg, mimeMsg.allRecipients)

          msgDaoSource.updateMsgState(context, detEmail, detLabel, details.uid.toLong(),
              MessageState.SENT_WITHOUT_LOCAL_COPY)

          if (saveCopyOfSentMsg(account, store, context, mimeMsg)) {
            msgDaoSource.updateMsgState(context, detEmail, detLabel, details.uid.toLong(), MessageState.SENT)
          }
        }
      }

      return true
    }

    /**
     * Create [MimeMessage] from the given [GeneralMessageDetails].
     *
     * @param sess    Will be used to create [MimeMessage]
     * @param context Interface to global information about an application environment.
     * @throws IOException
     * @throws MessagingException
     */
    private fun createMimeMsg(context: Context, sess: Session?, details: GeneralMessageDetails,
                              atts: List<AttachmentInfo>): MimeMessage {
      val stream = IOUtils.toInputStream(details.rawMsgWithoutAtts!!, StandardCharsets.UTF_8)
      val mimeMsg = MimeMessage(sess, stream)

      if (mimeMsg.content is MimeMultipart && !CollectionUtils.isEmpty(atts)) {
        val mimeMultipart = mimeMsg.content as MimeMultipart

        for (att in atts) {
          val attBodyPart = genBodyPartWithAtt(context, att)
          mimeMultipart.addBodyPart(attBodyPart)
        }

        mimeMsg.setContent(mimeMultipart)
        mimeMsg.saveChanges()
      }

      return mimeMsg
    }

    /**
     * Generate a [BodyPart] with an attachment.
     *
     * @param context Interface to global information about an application environment.
     * @param att     The [AttachmentInfo] object, which contains general information about the
     * attachment.
     * @return Generated [MimeBodyPart] with the attachment.
     * @throws MessagingException
     */
    private fun genBodyPartWithAtt(context: Context, att: AttachmentInfo): BodyPart {
      val attBodyPart = MimeBodyPart()
      attBodyPart.dataHandler = DataHandler(AttachmentInfoDataSource(context, att))
      attBodyPart.fileName = att.name
      attBodyPart.contentID = att.id

      return attBodyPart
    }

    /**
     * Retrieve a Gmail message thread id.
     *
     * @param service          A [Gmail] reference.
     * @param rfc822msgidValue An rfc822 Message-Id value of the input message.
     * @return The input message thread id.
     * @throws IOException
     */
    private fun getGmailMsgThreadID(service: Gmail, rfc822msgidValue: String): String? {
      val response = service
          .users()
          .messages()
          .list(GmailApiHelper.DEFAULT_USER_ID)
          .setQ("rfc822msgid:$rfc822msgidValue")
          .execute()

      return if (response.messages != null && response.messages.size == 1) {
        response.messages[0].threadId
      } else null

    }

    /**
     * Save a copy of the sent message to the account SENT folder.
     *
     * @param account The object which contains information about an email account.
     * @param store   The connected and opened [Store] object.
     * @param context Interface to global information about an application environment.
     * @param mimeMsg The original [MimeMessage] which will be saved to the SENT folder.
     */
    private fun saveCopyOfSentMsg(account: AccountDao, store: Store?, context: Context, mimeMsg: MimeMessage): Boolean {
      val foldersManager = FoldersManager.fromDatabase(context, account.email)
      val sentLocalFolder = foldersManager.folderSent

      try {
        if (sentLocalFolder != null) {
          val sentRemoteFolder = store!!.getFolder(sentLocalFolder.fullName) as IMAPFolder

          if (!sentRemoteFolder.exists()) {
            throw IllegalArgumentException("The SENT folder doesn't exists. Can't create a " + "copy of the sent message!")
          }

          sentRemoteFolder.open(Folder.READ_WRITE)
          mimeMsg.setFlag(Flags.Flag.SEEN, true)
          sentRemoteFolder.appendMessages(arrayOf<Message>(mimeMsg))
          sentRemoteFolder.close(false)
          return true
        } else {
          val accountDao = AccountDaoSource().getAccountInformation(context, account.email)
          if (accountDao == null) {
            throw IllegalArgumentException("The SENT folder is not defined. The account is null!")
          } else {
            throw IllegalArgumentException("An error occurred during saving a copy of the outgoing message. " +
                "Provider: " + account.email.substring(account.email.indexOf("@")))
          }
        }
      } catch (e: MessagingException) {
        e.printStackTrace()
      }

      return false
    }
  }

  /**
   * The [DataSource] realization for a file which received from [Uri]
   */
  private class AttachmentInfoDataSource internal constructor(private val context: Context, private val att: AttachmentInfo) : DataSource {

    override fun getInputStream(): InputStream? {
      val inputStream: InputStream?
      if (att.uri == null) {
        if (att.rawData != null) {
          inputStream = IOUtils.toInputStream(att.rawData!!, StandardCharsets.UTF_8)
        } else {
          inputStream = null
        }
      } else {
        inputStream = context.contentResolver.openInputStream(att.uri!!)
      }

      return if (inputStream == null) null else BufferedInputStream(inputStream)
    }

    override fun getOutputStream(): OutputStream? {
      return null
    }

    /**
     * If a content type is unknown we return "application/octet-stream".
     * http://www.rfc-editor.org/rfc/rfc2046.txt (section 4.5.1.  Octet-Stream Subtype)
     */
    override fun getContentType(): String? {
      return if (TextUtils.isEmpty(att.type)) Constants.MIME_TYPE_BINARY_DATA else att.type
    }

    override fun getName(): String? {
      return att.name
    }
  }

  companion object {

    private val TAG = MessagesSenderJobService::class.java.simpleName

    @JvmStatic
    fun schedule(context: Context) {
      val jobInfoBuilder = JobInfo.Builder(JobIdManager.JOB_TYPE_SEND_MESSAGES,
          ComponentName(context, MessagesSenderJobService::class.java))
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
          .setPersisted(true)

      val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

      for (jobInfo in scheduler.allPendingJobs) {
        if (jobInfo.id == JobIdManager.JOB_TYPE_SEND_MESSAGES) {
          //skip schedule a new job if we already have another one
          LogsUtil.d(TAG, "A job has already scheduled! Skip scheduling a new job.")
          return
        }
      }

      val result = scheduler.schedule(jobInfoBuilder.build())
      if (result == JobScheduler.RESULT_SUCCESS) {
        LogsUtil.d(TAG, "A job has scheduled successfully")
      } else {
        val errorMsg = "Error. Can't schedule a job"
        Log.e(TAG, errorMsg)
        ExceptionUtil.handleError(IllegalStateException(errorMsg))
      }
    }
  }
}