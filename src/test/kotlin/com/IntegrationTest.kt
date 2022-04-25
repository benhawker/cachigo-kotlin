package com.cachigo

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.http4k.client.OkHttp
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTest {
    val mockSupplierRequestor = mockk<SupplierRequestor>()
    val client = OkHttp()
    val approvedSuppliers: Suppliers = loadSuppliers()
    val server = Cachigo(supplierRequestor = mockSupplierRequestor, approvedSuppliers = approvedSuppliers).server()
    val baseUrl = "http://localhost:9000"

    fun get(url: String): Response {
        return client(Request(GET, url))
    }

    val supplier1 = mapOf("url" to "https://api.jsonbin.io/b/6259d255c5284e31154df64f", "responseBody" to "[{\"property\":\"One\",\"price\":304.2},{\"property\":\"Two\",\"price\":400.22},{\"property\":\"Three\",\"price\":299.3}]")
    val supplier2 = mapOf("url" to "https://api.jsonbin.io/b/6259e155c5284e31154df9d4", "responseBody" to "[{\"property\":\"One\",\"price\":289.2},{\"property\":\"Two\",\"price\":405.22},{\"property\":\"Three\",\"price\":288.3}]")

    @BeforeEach
    internal fun setup() {
        server.start()

        every { mockSupplierRequestor.makeRequest(supplier1.getOrDefault("url", "")) } returns supplier1.getOrDefault("responseBody", "")
        every { mockSupplierRequestor.makeRequest(supplier2.getOrDefault("url", "")) } returns supplier2.getOrDefault("responseBody", "")
    }

    @AfterEach
    internal fun teardown() {
        server.stop()
    }

    @Nested
    inner class Health {
        @Test
        fun `health returns 200 OK`() {
            assertThat(client(Request(GET, baseUrl + "/health")), hasStatus(OK))
        }
    }

    @Nested
    @DisplayName("/api/hotels")
    inner class ApiHotels {
        @Nested
        inner class ValidRequests {
            val validQueryString = "/api/hotels?checkin=2011-12-11&checkout=2018-12-01&destination=instanbul&guests=2"

            @Test
            @DisplayName("/api/hotels returns 200 OK")
            fun `hotels returns 200 OK`() {
                assertThat(get(baseUrl + validQueryString), hasStatus(OK))
            }

            @Test
            @DisplayName("/api/hotels returns the correct body")
            fun `hotels returns the expected body`() {
                val expectedBody = "{\"data\":[{\"property\":\"One\",\"price\":289.2,\"supplierId\":\"supplier2\"},{\"property\":\"Two\",\"price\":400.22,\"supplierId\":\"supplier1\"},{\"property\":\"Three\",\"price\":288.3,\"supplierId\":\"supplier2\"}]}"
                assertThat(client(Request(GET, baseUrl + validQueryString)), hasBody(expectedBody))
            }

            @Test
            @DisplayName("/api/hotels returns 200 OK")
            fun `the suppliers are called`() {
                // when
                val result1 = mockSupplierRequestor.makeRequest(supplier1.getOrDefault("url", ""))
                val result2 = mockSupplierRequestor.makeRequest(supplier2.getOrDefault("url", ""))

                // then
                verify { mockSupplierRequestor.makeRequest(supplier1.getOrDefault("url", "")) }
                verify { mockSupplierRequestor.makeRequest(supplier2.getOrDefault("url", "")) }

                assertThat(supplier1.getOrDefault("responseBody", ""), equalTo(result1))
                assertThat(supplier2.getOrDefault("responseBody", ""), equalTo(result2))
            }
        }

        @Nested
        inner class InvalidRequests {
            @Test
            fun `returns bad request when called with no query string parameters`() {
                val response = client(Request(GET, baseUrl + "/api/hotels"))
                assertThat(response, hasStatus(BAD_REQUEST))
            }

            @Test
            fun `returns a body showing missing parameters when called with missing required parameters`() {
                val response = client(Request(GET, "http://localhost:9000/api/hotels"))
                assertThat(response.bodyString(), containsSubstring("query 'checkin' is required"))
            }
        }
    }
}
