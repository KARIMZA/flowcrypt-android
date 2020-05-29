/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.flowcrypt.email.DoesNotNeedMailserver
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.base.BaseTest
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.model.PgpContact
import com.flowcrypt.email.rules.AddAccountToDatabaseRule
import com.flowcrypt.email.rules.AddContactsToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.viewaction.CustomActions.Companion.doNothing
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.*


/**
 * @author Denis Bondarenko
 * Date: 23.02.2018
 * Time: 10:34
 * E-mail: DenBond7@gmail.com
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SelectContactsActivityTest : BaseTest() {

  override val activityTestRule: ActivityTestRule<*>? = ActivityTestRule(SelectContactsActivity::class.java)

  @get:Rule
  var ruleChain: TestRule = RuleChain
      .outerRule(ClearAppSettingsRule())
      .around(AddAccountToDatabaseRule())
      .around(AddContactsToDatabaseRule(CONTACTS))
      .around(activityTestRule)

  @Test
  fun testShowEmptyView() {
    clearContactsFromDatabase()

    //Need to wait a little while data will be updated
    Thread.sleep(2000)

    onView(withId(R.id.emptyView))
        .check(matches(isDisplayed())).check(matches(withText(R.string.no_results)))
  }

  @Test
  @DoesNotNeedMailserver
  fun testShowListContacts() {
    onView(withId(R.id.emptyView))
        .check(matches(not(isDisplayed())))

    for (i in EMAILS.indices) {
      if (i % 2 == 0) {
        onView(withId(R.id.recyclerViewContacts)).perform(actionOnItem<RecyclerView.ViewHolder>
        (hasDescendant(allOf(withId(R.id.textViewName), withText(getUserName(EMAILS[i])))), doNothing()))
      } else {
        onView(withId(R.id.recyclerViewContacts)).perform(actionOnItem<RecyclerView.ViewHolder>
        (hasDescendant(allOf(withId(R.id.textViewOnlyEmail), withText(EMAILS[i]))), doNothing()))
      }
    }
  }

  @Test
  fun testCheckSearchExistingContact() {
    onView(withId(R.id.menuSearch))
        .check(matches(isDisplayed()))
        .perform(click())

    for (i in EMAILS.indices) {
      if (i % 2 == 0) {
        checkIsTypedUserFound(R.id.textViewName, getUserName(EMAILS[i]))
      } else {
        checkIsTypedUserFound(R.id.textViewOnlyEmail, EMAILS[i])
      }
    }
  }

  @Test
  fun testNoResults() {
    onView(withId(R.id.menuSearch))
        .check(matches(isDisplayed()))
        .perform(click())
    onView(withId(com.google.android.material.R.id.search_src_text))
        .perform(clearText(), typeText("some email"))
    closeSoftKeyboard()
    onView(withId(R.id.emptyView))
        .check(matches(isDisplayed())).check(matches(withText(R.string.no_results)))
  }

  private fun clearContactsFromDatabase() {
    val dao = FlowCryptRoomDatabase.getDatabase(getTargetContext()).contactsDao()
    for (email in EMAILS) {
      val contact = dao.getContactByEmails(email) ?: continue
      dao.delete(contact)
    }
  }

  private fun checkIsTypedUserFound(viewId: Int, viewText: String) {
    onView(withId(com.google.android.material.R.id.search_src_text))
        .perform(clearText(), typeText(viewText))
    closeSoftKeyboard()
    onView(withId(viewId))
        .check(matches(isDisplayed())).check(matches(withText(viewText)))
  }

  companion object {
    private val EMAILS = arrayOf(
        "contact_0@denbond7.com",
        "contact_1@denbond7.com",
        "contact_2@denbond7.com",
        "contact_3@denbond7.com")
    private val CONTACTS = ArrayList<PgpContact>()

    init {
      for (i in EMAILS.indices) {
        val email = EMAILS[i]
        val pgpContact = if (i % 2 == 0) {
          PgpContact(email, getUserName(email), "publicKey", true, null, null, null, null, 0)
        } else {
          PgpContact(email, null, "publicKey", true, null, null, null, null, 0)
        }
        CONTACTS.add(pgpContact)
      }
    }

    private fun getUserName(email: String): String {
      return email.substring(0, email.indexOf(TestConstants.COMMERCIAL_AT_SYMBOL))
    }
  }
}
