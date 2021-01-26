package no.nav.helse

import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.prometheus.client.CollectorRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@io.ktor.util.KtorExperimentalAPI
class WebTest {

    @Test
    fun `reports isalive status for nais`() {
        withTestApplication({
            platform(CollectorRegistry(), isAlive = { true }, isReady = { true })
        }) {
            handleRequest(HttpMethod.Get, "/isalive").apply {
                assertTrue { response.status()?.isSuccess() ?: false }
                assertEquals("ALIVE", response.content)
            }
        }
    }

    @Test
    fun `reports negative isalive status for nais`() {
        withTestApplication({
            platform(CollectorRegistry.defaultRegistry, isReady = { true }, isAlive = { false })
        }) {
            handleRequest(HttpMethod.Get, "/isalive").apply {
                Assertions.assertEquals(false, response.status()?.isSuccess())
            }
        }
    }

    @Test
    fun `reports isready status for nais`() {
        withTestApplication({
            platform(CollectorRegistry(), isAlive = { true }, isReady = { true })
        }) {
            handleRequest(HttpMethod.Get, "/isready").apply {
                assertTrue { response.status()?.isSuccess() ?: false }
                assertEquals("READY", response.content)
            }
        }
    }

    @Test
    fun `reports negative isready status for nais`() {
        withTestApplication({
            platform(CollectorRegistry.defaultRegistry, isReady = { false }, isAlive = { true })
        }) {
            handleRequest(HttpMethod.Get, "/isready").apply {
                Assertions.assertEquals(false, response.status()?.isSuccess())
            }
        }
    }

    @Test
    fun `reports metrics`() {
        withTestApplication({
            platform(CollectorRegistry(), isAlive = { true }, isReady = { true })
        }) {
            handleRequest(HttpMethod.Get, "/metrics").apply {
                assertTrue { response.status()?.isSuccess() ?: false }
            }
        }

    }

}
