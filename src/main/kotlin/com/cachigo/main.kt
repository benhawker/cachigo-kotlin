package com.cachigo

import com.charleskorn.kaml.Yaml
import com.fasterxml.jackson.module.kotlin.*

import java.io.File
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.serialization.Serializable
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer

import org.http4k.core.Method
import org.http4k.core.HttpHandler
import org.http4k.client.JavaHttpClient

@Serializable data class Suppliers(val data: Map<String, String>)

val MAPPER = jacksonObjectMapper()
val PORT = 9000

val SUPPLIERS_FILE_PATH = "suppliers.yml"
val SUPPLIERS: Suppliers =
    Yaml.default.decodeFromString(Suppliers.serializer(), File(SUPPLIERS_FILE_PATH).readText())

val CACHE = mutableMapOf<String, CacheValue>()
val CACHE_EXPIRY_MINUTES = 5

data class CacheValue(val offers: List<SupplierOffer>, val expiry: Long)

val routing: RoutingHttpHandler =
    routes(
        "/health" bind GET to { Response(OK).body("OK") },
        "api/hotels" bind GET to { request: Request -> hotelsHandler(request) }
    )

fun main() {
    routing.asServer(Jetty(PORT)).start()
}

data class RequestParameters(
    val checkinDate: LocalDate,
    val checkoutDate: LocalDate,
    val destination: String,
    val numGuests: Int,
    val requestedSuppliers: Map<String, String>
)


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

    var ci = LocalDate.parse(checkin)
    var co = LocalDate.parse(checkout)

    val requestParams =
        RequestParameters(
            checkinDate = ci,
            checkoutDate = co,
            destination = destination,
            numGuests = guests,
            requestedSuppliers = suppliersToCall(supplierParameter)
        )

    return Response(OK).body(buildResponse(requestParams))
}

private fun suppliersToCall(suppliers: String?): Map<String, String> {
    val suppliersToCall =
        if (suppliers == null) SUPPLIERS.data.keys.toList() else suppliers.split(",")

    return (SUPPLIERS.data).filter { (supplierName, _) ->
        suppliersToCall.contains(supplierName) == true
    }
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


data class SupplierOffer(val property: String, val price: Double, var supplierId: String = "")
class SupplierResponse(val property: String, val price: Double, var supplierId: String = "")

private fun buildResponse(requestParams: RequestParameters): String {
    val allOffers = mutableListOf<SupplierOffer>()

    (requestParams.requestedSuppliers).forEach { (supplierName, supplierUrl) ->
        val key = cacheKey(requestParams, supplierName)

        val cacheValue = CACHE.get(key)
        if (cacheValue != null && cacheValue.expiry > timeNowUnix()) {
            cacheValue.offers.forEach { allOffers.add(it) }
        } else {
            val supplierOffers = mutableListOf<SupplierOffer>()

            val request = Request(Method.GET, supplierUrl)
            val client: HttpHandler = JavaHttpClient()
            val response = client(request)

            println(response.bodyString())
            val obj: List<SupplierOffer> = MAPPER.readValue(response.bodyString())
            println(obj)
            
            // val messageLens = Body.auto<List<SupplierResponse>>().toLens()
            // val request = Request(Method.GET, supplierUrl)
            // val client: HttpHandler = JavaHttpClient()
            // val response = client(request)
            // val extractedMessage = messageLens(requestWithEmail)

            // val x =
            //     mapOf(
            //         "abcd" to Random.nextDouble(300.0),
            //         "defg" to Random.nextDouble(300.0),
            //         "mnop" to Random.nextDouble(300.0)
            //     )
            obj.forEach { sr ->
                sr.supplierId = supplierName
                allOffers.add(sr)
                supplierOffers.add(sr)
            }


            // x.forEach { (propertyId, price) ->
            //     val offer =
            //         SupplierOffer(propertyId = propertyId, price = price, supplierId = supplierName)
            //     allOffers.add(offer)
            //     supplierOffers.add(offer)
            // }

            CACHE.put(key, CacheValue(offers = supplierOffers, expiry = expiryTime()))
        }
    }

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

    return MAPPER.writeValueAsString(mapOf("data" to bestPriceByHotel.values))
}
