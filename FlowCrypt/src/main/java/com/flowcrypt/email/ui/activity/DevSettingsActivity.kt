/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.view.View

import com.flowcrypt.email.R
import com.flowcrypt.email.ui.activity.base.BaseBackStackActivity

/**
 * The developer setting activity. This activity will be used to setup the debug process.
 *
 * @author Denis Bondarenko
 * Date: 10.07.2017
 * Time: 10:55
 * E-mail: DenBond7@gmail.com
 */
class DevSettingsActivity : BaseBackStackActivity() {
  override val contentViewResourceId: Int
    get() = R.layout.activity_dev_settings

  override val rootView: View
    get() = View(this)
}
