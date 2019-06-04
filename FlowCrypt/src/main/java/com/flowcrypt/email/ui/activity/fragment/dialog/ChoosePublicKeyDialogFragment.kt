/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.api.retrofit.Status
import com.flowcrypt.email.api.retrofit.node.NodeRepository
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.api.retrofit.response.node.NodeResponseWrapper
import com.flowcrypt.email.api.retrofit.response.node.ParseKeysResult
import com.flowcrypt.email.jetpack.viewmodel.PrivateKeysViewModel
import com.flowcrypt.email.model.PgpContact
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.UIUtil
import com.google.android.gms.common.util.CollectionUtils
import java.util.*

/**
 * This dialog can be used for collecting information about user public keys.
 *
 * @author Denis Bondarenko
 * Date: 24.11.2017
 * Time: 13:13
 * E-mail: DenBond7@gmail.com
 */

class ChoosePublicKeyDialogFragment : BaseDialogFragment(), View.OnClickListener, Observer<NodeResponseWrapper<*>> {

  private var atts: ArrayList<AttachmentInfo>? = null
  private var listViewKeys: ListView? = null
  private var textViewMsg: TextView? = null
  private var progressBar: View? = null
  private var content: View? = null
  private var to: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (arguments != null) {
      this.to = arguments!!.getString(KEY_TO)
    }

    this.atts = ArrayList()
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val view = LayoutInflater.from(context).inflate(R.layout.fragment_send_user_public_key, if ((view != null) and (view is ViewGroup))
      view as ViewGroup?
    else
      null, false)

    textViewMsg = view.findViewById(R.id.textViewMessage)
    progressBar = view.findViewById(R.id.progressBar)
    listViewKeys = view.findViewById(R.id.listViewKeys)
    content = view.findViewById(R.id.groupContent)
    val buttonOk = view.findViewById<View>(R.id.buttonOk)
    buttonOk.setOnClickListener(this)

    val builder = AlertDialog.Builder(context!!)
    builder.setView(view)

    return builder.create()
  }

  override fun onClick(v: View) {
    when (v.id) {
      R.id.buttonOk -> if (atts != null) {
        if (atts!!.size == 1) {
          sendResult(Activity.RESULT_OK, atts!!)
          dismiss()
        } else {
          if (atts!!.isNotEmpty()) {
            sendResult()
          } else {
            dismiss()
          }
        }
      } else {
        dismiss()
      }
    }
  }

  override fun onNodeStateChanged(newState: Boolean?) {
    super.onNodeStateChanged(newState)
    if (newState!!) {
      fetchKeys()
    }
  }

  override fun onChanged(nodeResponseWrapper: NodeResponseWrapper<*>) {
    when (nodeResponseWrapper.requestCode) {
      R.id.live_data_id_fetch_keys -> when (nodeResponseWrapper.status) {
        Status.LOADING -> UIUtil.exchangeViewVisibility(context, true, progressBar!!, content!!)

        Status.SUCCESS -> {
          val parseKeysResult = nodeResponseWrapper.result as ParseKeysResult?
          val nodeKeyDetailsList = parseKeysResult!!.nodeKeyDetails
          if (CollectionUtils.isEmpty(nodeKeyDetailsList)) {
            textViewMsg!!.text = getString(R.string.no_pub_keys)
          } else {
            for (nodeKeyDetails in nodeKeyDetailsList) {
              val att = EmailUtil.genAttInfoFromPubKey(nodeKeyDetails)
              if (att != null) {
                atts!!.add(att)
              }
            }

            UIUtil.exchangeViewVisibility(context, false, progressBar!!, content!!)

            val matchedKeys = getMatchedKeys(nodeKeyDetailsList)
            if (!CollectionUtils.isEmpty(matchedKeys)) {
              atts!!.clear()
              for (nodeKeyDetails in matchedKeys) {
                val att = EmailUtil.genAttInfoFromPubKey(nodeKeyDetails)
                if (att != null) {
                  atts!!.add(att)
                }
              }
            }

            if (atts!!.size > 1) {
              textViewMsg!!.setText(R.string.tell_sender_to_update_their_settings)
              textViewMsg!!.append("\n\n")
              textViewMsg!!.append(getString(R.string.select_key))

              val strings = arrayOfNulls<String>(atts!!.size)
              for (i in atts!!.indices) {
                val (_, email, _, _, _, _, name) = atts!![i]
                strings[i] = email + "\n" + name
              }

              val adapter = ArrayAdapter<String>(context!!,
                  android.R.layout.simple_list_item_single_choice, strings)

              listViewKeys!!.choiceMode = ListView.CHOICE_MODE_SINGLE
              listViewKeys!!.adapter = adapter
            } else {
              textViewMsg!!.setText(R.string.tell_sender_to_update_their_settings)
              listViewKeys!!.visibility = View.GONE
            }
          }
        }

        Status.ERROR -> {
          UIUtil.exchangeViewVisibility(context, false, progressBar!!, textViewMsg!!)
          textViewMsg!!.text = nodeResponseWrapper.result!!.error!!.toString()
        }

        Status.EXCEPTION -> {
          UIUtil.exchangeViewVisibility(context, false, progressBar!!, textViewMsg!!)
          textViewMsg!!.text = nodeResponseWrapper.exception!!.message
        }
      }
    }
  }

  private fun fetchKeys() {
    val viewModel = ViewModelProviders.of(this).get(PrivateKeysViewModel::class.java)
    viewModel.init(NodeRepository())
    viewModel.responsesLiveData.observe(this, this)
  }

  private fun sendResult() {
    val selectedAtts = ArrayList<AttachmentInfo>()
    val checkedItemPositions = listViewKeys!!.checkedItemPositions
    if (checkedItemPositions != null) {
      for (i in 0 until checkedItemPositions.size()) {
        val key = checkedItemPositions.keyAt(i)
        if (checkedItemPositions.get(key)) {
          selectedAtts.add(atts!![key])
        }
      }
    }

    if (selectedAtts.isEmpty()) {
      showToast(getString(R.string.please_select_key))
    } else {
      sendResult(Activity.RESULT_OK, selectedAtts)
      dismiss()
    }
  }

  private fun sendResult(result: Int, atts: ArrayList<AttachmentInfo>) {
    if (targetFragment == null) {
      return
    }

    val intent = Intent()
    intent.putParcelableArrayListExtra(KEY_ATTACHMENT_INFO_LIST, atts)

    targetFragment!!.onActivityResult(targetRequestCode, result, intent)
  }

  /**
   * Get a list with the matched [NodeKeyDetails]. If the sender email matched to the email from
   * [PgpContact] which got from the private key than we return a list with the relevant public key.
   *
   * @return A matched [NodeKeyDetails] or null.
   */
  private fun getMatchedKeys(nodeKeyDetailsList: List<NodeKeyDetails>): List<NodeKeyDetails> {
    val keyDetails = ArrayList<NodeKeyDetails>()

    for (nodeKeyDetails in nodeKeyDetailsList) {
      val (email) = nodeKeyDetails.primaryPgpContact
      if (email.equals(to!!, ignoreCase = true)) {
        keyDetails.add(nodeKeyDetails)
      }
    }

    return keyDetails
  }

  companion object {
    @JvmField
    val KEY_ATTACHMENT_INFO_LIST = GeneralUtil.generateUniqueExtraKey("KEY_ATTACHMENT_INFO_LIST", InfoDialogFragment::class.java)

    private val KEY_TO = GeneralUtil.generateUniqueExtraKey("KEY_TO", InfoDialogFragment::class.java)

    @JvmStatic
    fun newInstance(to: String): ChoosePublicKeyDialogFragment {
      val args = Bundle()
      args.putString(KEY_TO, to)

      val fragment = ChoosePublicKeyDialogFragment()
      fragment.arguments = args
      return fragment
    }
  }
}
