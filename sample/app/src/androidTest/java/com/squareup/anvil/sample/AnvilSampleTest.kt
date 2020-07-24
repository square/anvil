package com.squareup.anvil.sample

import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnvilSampleTest {

  @Rule @JvmField
  val activityRule = ActivityTestRule(MainActivity::class.java)

  @Test fun text_is_displayed() {
    Espresso.onView(ViewMatchers.withText("Hephaestus, son of (No Father) and Hera"))
        .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
  }
}
