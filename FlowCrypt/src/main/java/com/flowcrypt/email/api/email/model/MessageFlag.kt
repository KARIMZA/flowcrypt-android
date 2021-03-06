/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.model

/**
 * The message flags. This flags will be used in the local database.
 *
 * @author DenBond7
 * Date: 20.06.2017
 * Time: 17:47
 * E-mail: DenBond7@gmail.com
 */
enum class MessageFlag constructor(val value: String) {
  ANSWERED("\\ANSWERED"),
  DELETED("\\DELETED"),
  DRAFT("\\DRAFT"),
  FLAGGED("\\FLAGGED"),
  RECENT("\\RECENT"),
  SEEN("\\SEEN")
}
