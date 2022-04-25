package com.cachigo

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.io.File

val SUPPLIERS_FILE_PATH = "suppliers.yml"
val PORT = 9000

@Serializable data class Suppliers(val data: Map<String, String>)

fun loadSuppliers(): Suppliers {
    return Yaml.default.decodeFromString(Suppliers.serializer(), File(SUPPLIERS_FILE_PATH).readText())
}

fun main() {
    val server = Cachigo(SupplierRequestor(), loadSuppliers()).server()
    server.start()
}
