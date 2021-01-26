package no.nav.helse

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

class Probe(registry: CollectorRegistry) {

    private val topLevelExceptionCounter = Counter
        .build("uncaught", "nr of uncaught exceptions")
        .register(registry)

    fun topLevelError() {
        topLevelExceptionCounter.inc()
    }
}
