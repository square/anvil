package com.squareup.hephaestus.sample

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HephaestusSampleTest {

  @Rule @JvmField
  val activityRule = ActivityTestRule(MainActivity::class.java)

  @Test fun text_is_displayed() {
    onView(withText("Hephaestus, son of (No Father) and Hera")).check(matches(isDisplayed()))
  }
}
