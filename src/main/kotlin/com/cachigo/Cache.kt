package com.cachigo

data class CacheValue(
    val offers: List<SupplierOffer> = listOf<SupplierOffer>(),
    val expiry: Long
)

val DEFAULT_EXPIRY_MINUTES = 5

class Cache(
    val store: MutableMap<String, CacheValue> = mutableMapOf<String, CacheValue>(),
    val expiry_minutes: Int = DEFAULT_EXPIRY_MINUTES
) {
    fun get(key: String): List<SupplierOffer>? {
        val cachedValue = store.get(key)

        if (cachedValue == null) {
            return null
        }

        if (cachedValue.expiry > timeNowUnix()) {
            return cachedValue.offers
        }

        return null
    }

    fun set(key: String, supplierOffers: List<SupplierOffer>) {
        store.put(key, CacheValue(offers = supplierOffers, expiry = expiryTime()))
    }

    fun key(requestParams: RequestParameters, supplierName: String): String {
        val key = StringBuilder()

        key.append(requestParams.checkinDate.toString())
            .append(requestParams.checkoutDate.toString())
            .append(requestParams.destination)
            .append(requestParams.numGuests)
            .append(supplierName)

        return key.toString()
    }

    fun expiryTime(): Long {
        return (timeNowUnix() + (expiry_minutes * 60))
    }

    private fun timeNowUnix(): Long {
        return (System.currentTimeMillis() / 1000)
    }
}
