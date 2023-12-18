package okhttp3

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.http2.Http2
import okhttp3.testing.Flaky
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class OkHttpClientTestRule : TestRule {
  private val clientEventsList = mutableListOf<String>()
  private var testClient: OkHttpClient? = null
  private var uncaughtException: Throwable? = null
  var logger: Logger? = null
  lateinit var testName: String

  var recordEvents = true
  var recordTaskRunner = false
  var recordFrames = false
  var recordSslDebug = false

  private val testLogHandler = object : Handler() {
    override fun publish(record: LogRecord) {
      val name = record.loggerName
      val recorded = when (name) {
        TaskRunner::class.java.name -> recordTaskRunner
        Http2::class.java.name -> recordFrames
        "javax.net.ssl" -> recordSslDebug
        else -> false
      }

      if (recorded) {
        synchronized(clientEventsList) {
          clientEventsList.add(record.message)

          if (record.loggerName == "javax.net.ssl") {
            val parameters = record.parameters

            if (parameters != null) {
              clientEventsList.add(parameters.first().toString())
            }
          }
        }
      }
    }

    override fun flush() {
    }

    override fun close() {
    }
  }.apply {
    level = Level.FINEST
  }

  private fun applyLogger(fn: Logger.() -> Unit) {
    Logger.getLogger(OkHttpClient::class.java.`package`.name).fn()
    Logger.getLogger(OkHttpClient::class.java.name).fn()
    Logger.getLogger(Http2::class.java.name).fn()
    Logger.getLogger(TaskRunner::class.java.name).fn()
    Logger.getLogger("javax.net.ssl").fn()
  }

  fun wrap(eventListener: EventListener) =
    EventListener.Factory { ClientRuleEventListener(eventListener, ::addEvent) }

  fun wrap(eventListenerFactory: EventListener.Factory) =
    EventListener.Factory { call -> ClientRuleEventListener(eventListenerFactory.create(call), ::addEvent) }

  fun newClient(): OkHttpClient {
    var client = testClient
    if (client == null) {
      client = OkHttpClient.Builder()
          .dns(SINGLE_INET_ADDRESS_DNS) // Prevent unexpected fallback addresses.
          .eventListenerFactory(
              EventListener.Factory { ClientRuleEventListener(logger = ::addEvent) })
          .build()
      testClient = client
    }
    return client
  }

  fun newClientBuilder(): OkHttpClient.Builder {
    return newClient().newBuilder()
  }

  @Synchronized private fun addEvent(event: String) {
    if (recordEvents) {
      logger?.info(event)

      synchronized(clientEventsList) {
        clientEventsList.add(event)
      }
    }
  }

  @Synchronized private fun initUncaughtException(throwable: Throwable) {
    if (uncaughtException == null) {
      uncaughtException = throwable
    }
  }

  fun ensureAllConnectionsReleased() {
    testClient?.let {
      val connectionPool = it.connectionPool

      connectionPool.evictAll()
      if (connectionPool.connectionCount() > 0) {
        println("Delaying to avoid flakes")
        Thread.sleep(500L)
        println("After delay: " + connectionPool.connectionCount())
      }

      assertEquals(0, connectionPool.connectionCount())
    }
  }

  private fun ensureAllTaskQueuesIdle() {
    val entryTime = System.nanoTime()

    for (queue in TaskRunner.INSTANCE.activeQueues()) {
      val waitTime = (entryTime + 1_000_000_000L - System.nanoTime())
      if (!queue.idleLatch().await(waitTime, TimeUnit.NANOSECONDS)) {
        TaskRunner.INSTANCE.cancelAll()
        fail("Queue still active after 1000 ms")
      }
    }
  }

  override fun apply(
    base: Statement,
    description: Description
  ): Statement {
    return object : Statement() {
      override fun evaluate() {
        testName = description.methodName

        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
          initUncaughtException(throwable)
        }
        val taskQueuesWereIdle = TaskRunner.INSTANCE.activeQueues().isEmpty()
        var failure: Throwable? = null
        try {
          applyLogger {
            addHandler(testLogHandler)
            level = Level.FINEST
            useParentHandlers = false
          }

          base.evaluate()
          if (uncaughtException != null) {
            throw AssertionError("uncaught exception thrown during test", uncaughtException)
          }
          logEventsIfFlaky(description)
        } catch (t: Throwable) {
          failure = t
          logEvents()
          throw t
        } finally {
          LogManager.getLogManager().reset()

          Thread.setDefaultUncaughtExceptionHandler(defaultUncaughtExceptionHandler)
          try {
            ensureAllConnectionsReleased()
            releaseClient()
          } catch (ae: AssertionError) {
            // Prefer keeping the inflight failure, but don't release this in-use client.
            if (failure != null) {
              failure.addSuppressed(ae)
            } else {
              failure = ae
            }
          }

          try {
            if (taskQueuesWereIdle) {
              ensureAllTaskQueuesIdle()
            }
          } catch (ae: AssertionError) {
            // Prefer keeping the inflight failure, but don't release this in-use client.
            if (failure != null) {
              failure.addSuppressed(ae)
            } else {
              failure = ae
            }
          }

          if (failure != null) {
            throw failure
          }
        }
      }

      private fun releaseClient() {
        testClient?.dispatcher?.executorService?.shutdown()
      }
    }
  }

  private fun logEventsIfFlaky(description: Description) {
    if (isTestFlaky(description)) {
      logEvents()
    }
  }

  private fun isTestFlaky(description: Description): Boolean {
    return description.annotations.any { it.annotationClass == Flaky::class } ||
        description.testClass.annotations.any { it.annotationClass == Flaky::class }
  }

  @Synchronized private fun logEvents() {
    synchronized(clientEventsList) {
      println("$testName Events (${clientEventsList.size})")

      for (e in clientEventsList) {
        println(e)
      }
    }
  }

  companion object {
    private val SINGLE_INET_ADDRESS_DNS = object : Dns {
      override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        return listOf(addresses[0])
      }
    }
  }
}