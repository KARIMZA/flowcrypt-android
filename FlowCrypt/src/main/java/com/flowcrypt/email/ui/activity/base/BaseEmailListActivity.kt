/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.base

import androidx.annotation.VisibleForTesting
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.sync.SyncErrorTypes
import com.flowcrypt.email.jobscheduler.ForwardedAttachmentsDownloaderJobService
import com.flowcrypt.email.jobscheduler.MessagesSenderJobService
import com.flowcrypt.email.service.EmailSyncService
import com.flowcrypt.email.ui.activity.fragment.EmailListFragment
import com.flowcrypt.email.util.idling.SingleIdlingResources

/**
 * The base [android.app.Activity] for displaying messages.
 *
 * @author Denis Bondarenko
 * Date: 26.04.2018
 * Time: 16:45
 * E-mail: DenBond7@gmail.com
 */
abstract class BaseEmailListActivity : BaseSyncActivity(), EmailListFragment.OnManageEmailsListener {
  @JvmField
  @VisibleForTesting
  val msgsIdlingResource = SingleIdlingResources()
  private var hasMoreMsgs = true

  abstract fun refreshFoldersFromCache()

  @VisibleForTesting
  override val msgsLoadingIdlingResource: SingleIdlingResources
    get() = msgsIdlingResource

  override fun onReplyReceived(requestCode: Int, resultCode: Int, obj: Any?) {
    when (requestCode) {
      R.id.syns_request_code_load_next_messages -> {
        refreshFoldersFromCache()
        when (resultCode) {
          EmailSyncService.REPLY_RESULT_CODE_NEED_UPDATE -> {
            hasMoreMsgs = true
            onNextMsgsLoaded()
          }

          else -> {
            hasMoreMsgs = false
            onNextMsgsLoaded()
          }
        }

        msgsIdlingResource.setIdleState(true)
      }
    }
  }

  override fun onErrorHappened(requestCode: Int, errorType: Int, e: Exception) {
    when (requestCode) {
      R.id.syns_request_code_load_next_messages -> {
        onErrorOccurred(requestCode, errorType, e)
        msgsIdlingResource.setIdleState(true)
      }
    }
  }

  override fun onProgressReplyReceived(requestCode: Int, resultCode: Int, obj: Any?) {
    when (requestCode) {
      R.id.syns_request_code_load_next_messages -> when (resultCode) {
        R.id.progress_id_start_of_loading_new_messages -> updateActionProgressState(0, "Starting")

        R.id.progress_id_adding_task_to_queue -> updateActionProgressState(10, "Queuing")

        R.id.progress_id_queue_is_not_empty -> updateActionProgressState(15, "Queue is not empty")

        R.id.progress_id_thread_is_cancelled_and_done -> updateActionProgressState(15, "Thread is cancelled and done")

        R.id.progress_id_thread_is_done -> updateActionProgressState(15, "Thread is done")

        R.id.progress_id_thread_is_cancelled -> updateActionProgressState(15, "Thread is cancelled")

        R.id.progress_id_running_task -> updateActionProgressState(20, "Running task")

        R.id.progress_id_resetting_connection -> updateActionProgressState(30, "Resetting connection")

        R.id.progress_id_connecting_to_email_server -> updateActionProgressState(40, "Connecting")

        R.id.progress_id_running_smtp_action -> updateActionProgressState(50, "Running SMTP action")

        R.id.progress_id_running_imap_action -> updateActionProgressState(60, "Running IMAP action")

        R.id.progress_id_opening_store -> updateActionProgressState(70, "Opening store")

        R.id.progress_id_getting_list_of_emails -> updateActionProgressState(80, "Getting list of emails")
      }
    }
  }

  override fun hasMoreMsgs(): Boolean {
    return hasMoreMsgs
  }

  override fun onSyncServiceConnected() {
    syncServiceConnected()
  }

  /**
   * Notify [EmailListFragment] that the activity already connected to the [EmailSyncService]
   */
  protected fun syncServiceConnected() {
    val emailListFragment = supportFragmentManager
        .findFragmentById(R.id.emailListFragment) as EmailListFragment?

    emailListFragment?.onSyncServiceConnected()
  }

  /**
   * Handle an error from the sync service.
   *
   * @param requestCode The unique request code for the reply to [android.os.Messenger].
   * @param errorType   The [SyncErrorTypes]
   * @param e           The exception which happened.
   */
  protected fun onErrorOccurred(requestCode: Int, errorType: Int, e: Exception) {
    val emailListFragment = supportFragmentManager
        .findFragmentById(R.id.emailListFragment) as EmailListFragment?

    if (emailListFragment != null) {
      emailListFragment.onErrorOccurred(requestCode, errorType, e)
      updateActionProgressState(100, null)
    }
  }

  /**
   * Update the list of emails after changing the folder.
   */
  protected fun onFolderChanged(isForceClearCacheNeeded: Boolean) {
    toolbar?.title = currentFolder?.folderAlias

    val emailListFragment = supportFragmentManager
        .findFragmentById(R.id.emailListFragment) as EmailListFragment?

    emailListFragment?.updateList(true, isForceClearCacheNeeded)
    updateActionProgressState(100, null)

    if (currentFolder != null) {
      val isOutbox = JavaEmailConstants.FOLDER_OUTBOX.equals(currentFolder!!.fullName, ignoreCase = true)
      if (currentFolder != null && isOutbox) {
        ForwardedAttachmentsDownloaderJobService.schedule(applicationContext)
        MessagesSenderJobService.schedule(applicationContext)
      } else {
        //run the tasks which maybe not completed last time
        archiveMsgs()
        changeMsgsReadState()
        deleteMsgs()
        moveMsgsToINBOX()
      }
    }
  }

  /**
   * Update a progress of some action.
   *
   * @param progress The action progress.
   * @param message  The user friendly message.
   */
  private fun updateActionProgressState(progress: Int, message: String?) {
    val emailListFragment = supportFragmentManager
        .findFragmentById(R.id.emailListFragment) as EmailListFragment?

    emailListFragment?.setActionProgress(progress, message)
  }

  /**
   * Handle a result from the load next messages action.
   */
  protected fun onNextMsgsLoaded() {
    val emailListFragment = supportFragmentManager
        .findFragmentById(R.id.emailListFragment) as EmailListFragment?

    if (emailListFragment != null) {
      emailListFragment.onNextMsgsLoaded()
      emailListFragment.setActionProgress(100, null)
    }
  }
}
