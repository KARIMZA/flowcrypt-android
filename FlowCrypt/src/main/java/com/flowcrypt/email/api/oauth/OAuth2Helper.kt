/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.oauth

import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * @author Denis Bondarenko
 *         Date: 7/16/20
 *         Time: 4:10 PM
 *         E-mail: DenBond7@gmail.com
 */
class OAuth2Helper {
  companion object {
    const val OAUTH2_GRANT_TYPE = "authorization_code"
    const val OAUTH2_GRANT_TYPE_REFRESH_TOKEN = "refresh_token"

    /**************** Microsoft ****************/
    /**
     * openid - Allows users to sign in to the app with their work or school accounts and allows the app to see basic user profile information.
     *
     * offline_access - Allows the app to see and update the data you gave it access to, even when users are not currently using the app. This does not give the app any additional permissions.
     *
     * https://outlook.office.com/IMAP.AccessAsUser.All - Allows the app to have the same access to mailboxes as the signed-in user via IMAP protocol.
     *
     * https://outlook.office.com/SMTP.Send - Allows the app to be able to send emails from the user’s mailbox using the SMTP AUTH client submission protocol.
     */
    const val SCOPE_MICROSOFT_OAUTH2_FOR_MAIL = "offline_access https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send"
    const val SCOPE_MICROSOFT_OAUTH2_FOR_PROFILE = "openid profile email"

    const val MICROSOFT_OAUTH2_AUTHORIZE_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    const val MICROSOFT_OAUTH2_TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    const val MICROSOFT_REDIRECT_URI = "msauth://com.flowcrypt.email.denys/04gM%2BEAfhnq4ALbhOX8jG5oRuow%3D"
    const val MICROSOFT_AZURE_APP_ID = "3be51534-5f76-4970-9a34-40ef197aa018"
    const val MICROSOFT_OAUTH2_SCHEMA = "msauth"

    fun getMicrosoftAuthorizationRequest(): AuthorizationRequest {
      val configuration = AuthorizationServiceConfiguration(Uri.parse(MICROSOFT_OAUTH2_AUTHORIZE_URL), Uri.parse(MICROSOFT_OAUTH2_TOKEN_URL))

      return AuthorizationRequest.Builder(
          configuration,
          MICROSOFT_AZURE_APP_ID,
          ResponseTypeValues.CODE,
          Uri.parse(MICROSOFT_REDIRECT_URI))
          .setScope("$SCOPE_MICROSOFT_OAUTH2_FOR_PROFILE $SCOPE_MICROSOFT_OAUTH2_FOR_MAIL")
          .build()
    }

    val SUPPORTED_SCHEMAS = listOf(MICROSOFT_OAUTH2_SCHEMA)
  }
}