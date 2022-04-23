package com.cachigo

import com.charleskorn.kaml.Yaml
import com.fasterxml.jackson.module.kotlin.*
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters
import org.http4k.server.Http4kServer
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer


val SUPPLIERS_FILE_PATH = "suppliers.yml"
val PORT = 9000

@Serializable data class Suppliers(val data: Map<String, String>)

class SupplierRequestor { 
    fun makeRequest(supplierUrl: String): String {
        val request = Request(Method.GET, supplierUrl)
        val client: HttpHandler = JavaHttpClient()
        val response = client(request)
        return response.bodyString()
    }
}

fun loadSuppliers(): Suppliers { 
    return Yaml.default.decodeFromString(Suppliers.serializer(), File(SUPPLIERS_FILE_PATH).readText())
}

fun main() {
    val approvedSuppliers = loadSuppliers()
    val supplierRequestor = SupplierRequestor()

    val server = Cachigo(supplierRequestor, approvedSuppliers).server()
    server.start()
}


data class CacheValue(val offers: List<SupplierOffer>, val expiry: Long)

data class RequestParameters(
    val checkinDate: LocalDate,
    val checkoutDate: LocalDate,
    val destination: String,
    val numGuests: Int,
    val requestedSuppliers: Map<String, String>
)

data class SupplierOffer(val property: String, val price: Double, var supplierId: String = "")

class Cachigo(
    val supplierRequestor: SupplierRequestor,
    val approvedSuppliers: Suppliers,
    val port: Int = PORT
) {
    val MAPPER = jacksonObjectMapper()
    val CACHE = mutableMapOf<String, CacheValue>()
    val CACHE_EXPIRY_MINUTES = 5
    val routing: RoutingHttpHandler =
        routes(
            "/health" bind GET to { Response(OK).body("OK") },
            "api/hotels" bind GET to { request: Request -> hotelsHandler(request) }
        )

    fun server(): Http4kServer {
        return DebuggingFilters.PrintRequest()
            .then(
                ServerFilters.CatchLensFailure {
                    Response(Status.BAD_REQUEST).body(it.message + "\n" + it.cause.toString())
                }
            )
            .then(routing)
            .asServer(Jetty(port))
    }

    private fun hotelsHandler(request: Request): Response {
        val checkinQuery = Query.required("checkin")
        val checkoutQuery = Query.required("checkout")
        val destinationQuery = Query.required("destination")
        val guestsQuery = Query.int().required("guests")
        val suppliersQuery = Query.optional("suppliers")

        val checkin = checkinQuery(request)
        val checkout = checkoutQuery(request)
        val destination = destinationQuery(request)
        val guests = guestsQuery(request)
        val supplierParameter = suppliersQuery(request)

        var checkinDate = LocalDate.parse(checkin)
        var checkoutDate = LocalDate.parse(checkout)

        val requestParams =
            RequestParameters(
                checkinDate = checkinDate,
                checkoutDate = checkoutDate,
                destination = destination,
                numGuests = guests,
                requestedSuppliers = suppliersToCall(supplierParameter)
            )

        return Response(OK).body(buildResponse(requestParams))
    }

    private fun buildResponse(requestParams: RequestParameters): String {
        val allOffers = mutableListOf<SupplierOffer>()

        (requestParams.requestedSuppliers).forEach { (supplierName, supplierUrl) ->
            val key = cacheKey(requestParams, supplierName)

            val cacheValue = CACHE.get(key)
            if (cacheValue != null && cacheValue.expiry > timeNowUnix()) {
                cacheValue.offers.forEach { allOffers.add(it) }
            } else {
                val responseBody = supplierRequestor.makeRequest(supplierUrl)
                val supplierOffers: List<SupplierOffer> = MAPPER.readValue(responseBody)

                supplierOffers.forEach { supplierOffer ->
                    supplierOffer.supplierId = supplierName
                    allOffers.add(supplierOffer)
                }

                CACHE.put(key, CacheValue(offers = supplierOffers, expiry = expiryTime()))
            }
        }

        return MAPPER.writeValueAsString(mapOf("data" to findBestPriceByHotel(allOffers).values))
    }

    private fun suppliersToCall(suppliers: String?): Map<String, String> {
        val suppliersToCall =
            if (suppliers == null) approvedSuppliers.data.keys.toList() else suppliers.split(",")

        return (approvedSuppliers.data).filter { (supplierName, _) ->
            suppliersToCall.contains(supplierName) == true
        }
    }

    private fun findBestPriceByHotel(allOffers: MutableList<SupplierOffer>): MutableMap<String, SupplierOffer> {
        val bestPriceByHotel = mutableMapOf<String, SupplierOffer>()
        allOffers.forEach {
            if (bestPriceByHotel.containsKey(it.property) == true) {
                if (it.price < bestPriceByHotel[it.property]!!.price) {
                    bestPriceByHotel[it.property] = it
                }
            } else {
                bestPriceByHotel[it.property] = it
            }
        }

        return bestPriceByHotel
    }

    private fun cacheKey(requestParams: RequestParameters, supplierName: String): String {
        val key = StringBuilder()

        key.append(requestParams.checkinDate.toString())
            .append(requestParams.checkoutDate.toString())
            .append(requestParams.destination)
            .append(requestParams.numGuests)
            .append(supplierName)

        return key.toString()
    }

    private fun expiryTime(): Long {
        return (timeNowUnix() + (CACHE_EXPIRY_MINUTES * 60))
    }

    private fun timeNowUnix(): Long {
        return (System.currentTimeMillis() / 1000)
    }
}
