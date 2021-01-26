package no.nav.helse

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.io.StringWriter
import java.util.concurrent.TimeUnit

fun webserver(collectorRegistry: CollectorRegistry, isAlive: () -> Boolean,
              isReady: () -> Boolean) {
    val server = embeddedServer(Netty, 8080) {
        platform(collectorRegistry, isAlive, isReady)
    }.start(wait = false)

    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun Application.platform(collectorRegistry: CollectorRegistry,
                         isAlive: () -> Boolean,
                         isReady: () -> Boolean) {
    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(
            PrometheusConfig.DEFAULT,
            collectorRegistry,
            Clock.SYSTEM
        )
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics()
        )
    }

    routing {
        get("/isalive") {
            if (isAlive()) {
                call.respondText("ALIVE", ContentType.Text.Plain)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "NOT_ALIVE")
            }
        }
        get("/isready") {
            if (isReady()) {
                call.respondText("READY", ContentType.Text.Plain)
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, "READY")
            }
        }
        get("/metrics") {
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: emptySet()
            val text = StringWriter()
            TextFormat.write004(text, collectorRegistry.filteredMetricFamilySamples(names))
            call.respondText(text = text.toString(), contentType = ContentType.parse(TextFormat.CONTENT_TYPE_004))
        }
    }
}

