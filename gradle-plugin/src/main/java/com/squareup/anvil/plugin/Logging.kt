package com.squareup.anvil.plugin

import org.gradle.api.Task

internal fun Task.log(message: String) {
  logger.info(message)
}
