package com.squareup.anvil.sample

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

@Suppress("unused")
class TestRunner : AndroidJUnitRunner() {
  override fun newApplication(
    cl: ClassLoader,
    className: String,
    context: Context
  ): Application = Instrumentation.newApplication(TestApp::class.java, context)
}
