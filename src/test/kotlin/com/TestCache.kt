package com.cachigo

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCache {
    val cache = Cache()

    @Nested
    inner class Get {
        @Test
        fun `get returns null when the key is not found`() {
            assertThat(cache.get("bob"), equalTo(null))
        }

        @Test
        fun `get returns null when the key has expired`() {
            val cache = Cache(expiry_minutes = 0)
            val supplierOffers = listOf(SupplierOffer(property = "abc", price = 288.88, supplierId = "1"))

            cache.set("key1", supplierOffers)
            assertThat(cache.get("bob"), equalTo(null))
        }

        @Test
        fun `get returns the value when the key is found and is not expired`() {
            val supplierOffers = listOf(SupplierOffer(property = "abc", price = 288.88, supplierId = "1"))

            cache.set("key1", supplierOffers)
            assertThat(cache.get("key1"), equalTo(supplierOffers))
        }
    }

    @Nested
    inner class Set {
        @Test
        fun `set adds the value to the cache with the default expiry`() {
            val offers = listOf(SupplierOffer(property = "abc", price = 288.88, supplierId = "1"))
            val timeNow = System.currentTimeMillis() / 1000
            cache.set("bob", offers)

            val cacheValue = cache.store.get("bob")
            assertThat(cacheValue?.expiry, equalTo(timeNow + (5 * 60)))
        }

        @Test
        fun `set adds the value to the cache with the parameterised expiry`() {
            val cache = Cache(expiry_minutes = 120)

            val offers = listOf(SupplierOffer(property = "abc", price = 288.88, supplierId = "1"))
            val timeNow = System.currentTimeMillis() / 1000
            cache.set("bob", offers)

            val cacheValue = cache.store.get("bob")
            assertThat(cacheValue?.expiry, equalTo(timeNow + (120 * 60)))
        }
    }
}
