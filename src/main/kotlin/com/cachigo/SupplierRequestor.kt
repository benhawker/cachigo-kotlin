package com.cachigo

import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Method.GET
import org.http4k.core.Request

class SupplierRequestor {
    fun makeRequest(supplierUrl: String): String {
        val request = Request(Method.GET, supplierUrl)
        val client: HttpHandler = JavaHttpClient()
        val response = client(request)
        return response.bodyString()
    }
}
