package com.cachigo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.time.LocalDate

class Cachigo(
    val supplierRequestor: SupplierRequestor = SupplierRequestor(),
    val approvedSuppliers: Suppliers = loadSuppliers(),
    val sorter: ((allOffers: MutableList<SupplierOffer>) -> List<SupplierOffer>) = ::findBestPriceByHotel,
    val cache: Cache = Cache(),
    val port: Int = PORT
) {

    val mapper = jacksonObjectMapper()
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
            val key = cache.key(requestParams, supplierName)

            val cacheValue = cache.get(key)
            if (cacheValue != null) {
                cacheValue.forEach { allOffers.add(it) }
            } else {
                val responseBody = supplierRequestor.makeRequest(supplierUrl)
                val supplierOffers: List<SupplierOffer> = mapper.readValue(responseBody)

                supplierOffers.forEach { supplierOffer ->
                    supplierOffer.supplierId = supplierName
                    allOffers.add(supplierOffer)
                }

                cache.set(key, supplierOffers)
            }
        }

        return mapper.writeValueAsString(mapOf("data" to sorter(allOffers)))
    }

    private fun suppliersToCall(suppliers: String?): Map<String, String> {
        val suppliersToCall =
            if (suppliers == null) approvedSuppliers.data.keys.toList() else suppliers.split(",")

        return (approvedSuppliers.data).filter { (supplierName, _) ->
            suppliersToCall.contains(supplierName) == true
        }
    }
}

data class RequestParameters(
    val checkinDate: LocalDate,
    val checkoutDate: LocalDate,
    val destination: String,
    val numGuests: Int,
    val requestedSuppliers: Map<String, String>
)

data class SupplierOffer(
    val property: String,
    val price: Double,
    var supplierId: String = ""
)

fun findBestPriceByHotel(allOffers: MutableList<SupplierOffer>): List<SupplierOffer> {
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

    return bestPriceByHotel.values.toList()
}
