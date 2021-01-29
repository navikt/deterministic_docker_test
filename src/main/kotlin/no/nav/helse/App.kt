package no.nav.helse

import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.*
import no.nav.helse.testgreier.TestNoeXmlGreier
import org.apache.kafka.common.KafkaException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLTransientException
import java.time.Duration
import java.util.concurrent.Executors

fun main() {
    val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
    val probe = Probe(collectorRegistry)
    val log: Logger = LoggerFactory.getLogger("no.nav.helse.App")

    var healthy = true
    fun isHealthy(): Boolean = healthy

    val applicationContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    val exceptionHandler = CoroutineExceptionHandler { _, ex ->
        log.error("Feil boblet helt til topps", ex)
        probe.topLevelError()
        if (shouldCauseRestart(ex)) {
            log.error("Setting status to UN-healthy")
            healthy = false
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationContext.close()
    })

    val grunndata = TestNoeXmlGreier().grunndata

    suspend fun someFunctionality() {
        while (healthy) {
            log.info("Soy yo! Soy su Sam!")
            log.info(grunndata.melding.kontaktperson.samendring.first().rolle.first().person.first().fornavn)
            delay(Duration.ofMinutes(1).toMillis())
        }
    }


    GlobalScope.launch(applicationContext + exceptionHandler) {
        launch { someFunctionality() }
        launch { webserver(collectorRegistry, isReady = ::isHealthy, isAlive = ::isHealthy) }
    }
}

private fun shouldCauseRestart(ex: Throwable): Boolean =
    (ex is KafkaException) ||  (ex is SQLTransientException)
