package com.squareup.anvil.sample

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.squareup.anvil.sample.God.HEPHAESTUS
import com.squareup.anvil.sample.R.id
import com.squareup.anvil.sample.R.layout

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(layout.activity_main)

    findViewById<TextView>(id.textView).text = Description.of(HEPHAESTUS)
  }
}
