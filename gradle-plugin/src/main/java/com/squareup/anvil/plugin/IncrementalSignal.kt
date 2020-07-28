@file:Suppress("UnstableApiUsage")

package com.squareup.anvil.plugin

import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.ide.common.process.ProcessException
import com.google.common.io.Closer
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters.None
import java.io.IOException
import java.util.UUID

/** Used to get unique build service name. Each class loader will initialize it's onw version. */
private val ANVIL_INCREMENTAL_SIGNAL_BUILD_SERVICE_NAME =
  "anvil-incremental-signal-build-service" + UUID.randomUUID()

/**
 * Registers incremental signal build service and returns a service key that can be used to access
 * the service later.
 */
fun registerIncrementalSignalBuildService(project: Project): IncrementalSignalServiceKey {
  val buildServiceProvider = project.gradle.sharedServices.registerIfAbsent(
      ANVIL_INCREMENTAL_SIGNAL_BUILD_SERVICE_NAME,
      IncrementalSignalBuildService::class.java
  ) {}
  return buildServiceProvider.get()
      .registerIncrementalSignalService(project.path)
}

/**
 * Service registry used to store IncrementalSignal services so they are accessible from the worker
 * actions.
 */
var incrementalSignalServiceRegistry: WorkerActionServiceRegistry = WorkerActionServiceRegistry()

/** Intended for use from worker actions. */
@Throws(ProcessException::class, IOException::class)
fun useIncrementalSignalService(
  incrementalSignalServiceKey: IncrementalSignalServiceKey,
  serviceRegistry: WorkerActionServiceRegistry = incrementalSignalServiceRegistry,
  block: (IncrementalSignalService) -> Unit
) = serviceRegistry.getService(incrementalSignalServiceKey).service.let(block)

fun getIncrementalSignalService(
  incrementalSignalServiceKey: IncrementalSignalServiceKey,
  serviceRegistry: WorkerActionServiceRegistry = incrementalSignalServiceRegistry
): IncrementalSignalService = serviceRegistry.getService(incrementalSignalServiceKey).service

data class IncrementalSignalServiceKey(
  val projectPath: String
) : WorkerActionServiceRegistry.ServiceKey<IncrementalSignalService> {
  override val type: Class<IncrementalSignalService> get() = IncrementalSignalService::class.java
}

data class IncrementalSignalService(var incremental: Boolean? = null)

/** This signal is used to share state between the task above and Kotlin compile tasks. */
abstract class IncrementalSignalBuildService : BuildService<None>, AutoCloseable {
  private val registeredServices = mutableSetOf<IncrementalSignalServiceKey>()
  private val closer = Closer.create()

  @Synchronized
  fun registerIncrementalSignalService(
    projectPath: String,
    serviceRegistry: WorkerActionServiceRegistry = incrementalSignalServiceRegistry
  ): IncrementalSignalServiceKey {
    val key = IncrementalSignalServiceKey(projectPath)

    if (registeredServices.add(key)) {
      closer.register(serviceRegistry.registerServiceAsCloseable(key, IncrementalSignalService()))
    }

    return key
  }

  override fun close() {
    closer.close()
  }
}
