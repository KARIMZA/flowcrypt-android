/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.Constants
import com.flowcrypt.email.DoesNotNeedMailserver
import com.flowcrypt.email.R
import com.flowcrypt.email.ReadyForCIAnnotation
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.model.KeyDetails
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.util.AccountDaoManager
import com.flowcrypt.email.util.PrivateKeysManager
import com.flowcrypt.email.util.TestGeneralUtil
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File

/**
 * @author Denis Bondarenko
 * Date: 17.08.2018
 * Time: 16:28
 * E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class BackupKeysActivityTest : BaseTest() {
  override val useIntents: Boolean = true
  override val activityScenarioRule = activityScenarioRule<BackupKeysActivity>()

  @get:Rule
  var ruleChain: TestRule = RuleChain
      .outerRule(ClearAppSettingsRule())
      .around(AddAccountToDatabaseRule())
      .around(RetryRule())
      .around(activityScenarioRule)
      .around(ScreenshotTestRule())

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testEmailOptionHint() {
    onView(withId(R.id.radioButtonEmail))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withText(getResString(R.string.backup_as_email_hint)))
        .check(matches(isDisplayed()))
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testDownloadOptionHint() {
    onView(withId(R.id.radioButtonDownload))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withText(getResString(R.string.backup_as_download_hint)))
        .check(matches(isDisplayed()))
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testNoKeysEmailOption() {
    onView(withId(R.id.radioButtonEmail))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withText(getResString(R.string.there_are_no_private_keys, AccountDaoManager.getDefaultAccountDao().email)))
        .check(matches(isDisplayed()))
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testNoKeysDownloadOption() {
    onView(withId(R.id.radioButtonDownload))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withText(getResString(R.string.there_are_no_private_keys, AccountDaoManager.getDefaultAccountDao().email)))
        .check(matches(isDisplayed()))
  }

  @Test
  @ReadyForCIAnnotation
  fun testSuccessEmailOption() {
    addFirstKeyWithStrongPassword()
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    assertTrue(activityScenarioRule.scenario.result.resultCode == Activity.RESULT_OK)
  }

  @Test
  @ReadyForCIAnnotation
  fun testSuccessWithTwoKeysEmailOption() {
    addSecondKeyWithStrongPassword()
    testSuccessEmailOption()
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testSuccessDownloadOption() {
    addFirstKeyWithStrongPassword()
    onView(withId(R.id.radioButtonDownload))
        .check(matches(isDisplayed()))
        .perform(click())

    val file = TestGeneralUtil.createFile("key.asc", "")

    intendingFileChoose(file)
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())

    assertTrue(activityScenarioRule.scenario.result.resultCode == Activity.RESULT_OK)

    TestGeneralUtil.deleteFiles(listOf(file))
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testSuccessWithTwoKeysDownloadOption() {
    addSecondKeyWithStrongPassword()
    testSuccessDownloadOption()
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testShowWeakPasswordHintForDownloadOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.radioButtonDownload))
        .check(matches(isDisplayed()))
        .perform(click())
    intendingFileChoose(File(""))
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withText(getResString(R.string.pass_phrase_is_too_weak)))
        .check(matches(isDisplayed()))
  }

  @Test
  @ReadyForCIAnnotation
  fun testShowWeakPasswordHintForEmailOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withText(getResString(R.string.pass_phrase_is_too_weak)))
        .check(matches(isDisplayed()))
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testFixWeakPasswordForDownloadOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.radioButtonDownload))
        .check(matches(isDisplayed()))
        .perform(click())
    intendingFileChoose(File(""))
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.pass_phrase_is_too_weak))

    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  @Test
  @ReadyForCIAnnotation
  fun testFixWeakPasswordForEmailOption() {
    addFirstKeyWithDefaultPassword()
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.pass_phrase_is_too_weak))
    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  @Test
  @ReadyForCIAnnotation
  fun testDiffPassphrasesForEmailOption() {
    addFirstKeyWithStrongPassword()
    addSecondKeyWithStrongSecondPassword()
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.different_pass_phrases))
    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  @Test
  @DoesNotNeedMailserver
  @ReadyForCIAnnotation
  fun testDiffPassphrasesForDownloadOption() {
    addFirstKeyWithStrongPassword()
    addSecondKeyWithStrongSecondPassword()
    onView(withId(R.id.radioButtonDownload))
        .check(matches(isDisplayed()))
        .perform(click())
    intendingFileChoose(File(""))
    onView(withId(R.id.buttonBackupAction))
        .check(matches(isDisplayed()))
        .perform(click())
    intending(hasComponent(ComponentName(getTargetContext(), ChangePassPhraseActivity::class.java)))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    checkIsSnackbarDisplayedAndClick(getResString(R.string.different_pass_phrases))
    assertTrue(activityScenarioRule.scenario.state == Lifecycle.State.RESUMED)
  }

  private fun intendingFileChoose(file: File) {
    val resultData = Intent()
    resultData.data = Uri.fromFile(file)
    intending(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT),
        hasCategories(hasItem(equalTo(Intent.CATEGORY_OPENABLE))),
        hasType(Constants.MIME_TYPE_PGP_KEY)))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, resultData))
  }

  private fun addFirstKeyWithDefaultPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase("node/default@denbond7.com_fisrtKey_prv_default.json",
        TestConstants.DEFAULT_PASSWORD, KeyDetails.Type.EMAIL)
  }

  private fun addFirstKeyWithStrongPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase("node/default@denbond7.com_fisrtKey_prv_strong.json",
        TestConstants.DEFAULT_STRONG_PASSWORD, KeyDetails.Type.EMAIL)
  }

  private fun addSecondKeyWithStrongPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase("node/default@denbond7.com_secondKey_prv_strong.json",
        TestConstants.DEFAULT_STRONG_PASSWORD, KeyDetails.Type.EMAIL)
  }

  private fun addSecondKeyWithStrongSecondPassword() {
    PrivateKeysManager.saveKeyFromAssetsToDatabase("node/default@denbond7.com_secondKey_prv_strong_second.json",
        TestConstants.DEFAULT_SECOND_STRONG_PASSWORD, KeyDetails.Type.EMAIL)
  }
}
