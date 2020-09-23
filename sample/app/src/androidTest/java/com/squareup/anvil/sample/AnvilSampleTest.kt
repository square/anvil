package com.squareup.anvil.sample

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.google.common.truth.Truth.assertThat
import com.squareup.scopes.ComponentHolder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnvilSampleTest {

  @Rule @JvmField
  val activityRule = ActivityTestRule(MainActivity::class.java)

  @Test fun text_is_displayed() {
    onView(withText("Hephaestus, son of (No Father) and Hera")).check(matches(isDisplayed()))
  }

  @Test fun fatherProvider_is_fake_instance() {
    val fatherProvider = ComponentHolder.component<DescriptionComponent>().fatherProvider()
    assertThat(fatherProvider).isSameInstanceAs(FakeFatherProvider)
  }
}
