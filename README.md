# Cachigo Kotlin - Hotel Pricing API

### Testing:

Run the specs:
```
$ gradle clean test
```


### Usage:

Build it:
```
./gradlew build --scan
```


Run the server (serving on PORT 9000):
```
$ ./gradlew run
```


Make a GET request to:
```
http://localhost:9000/api/health
http://localhost:9000/api/hotels
```

Expected response:
```
{
  "error": "Payload missing required keys: checkin, checkout, destination, guests"
}
```

Make a GET request to:
```
http://localhost:9000/api/hotels?checkin=dummy_value&checkout=dummy_value&destination=instanbul&guests=2

```

Expected response:
```
{
  "data": [
    {
      "id": "abcd",
      "price": 299.9,
      "supplier": "supplier2"
    },
    {
      "id": "defg",
      "price": 320.49,
      "supplier": "supplier3"
    },
    {
      "id": "mnop",
      "price": 288.3,
      "supplier": "supplier1"
    }
  ]
}
```
===================

## Requirements

### Request

- endpoint needs to accept following parameters: checkin, checkout, destination, guests, and optionally suppliers
- when requested, the server needs to fetch the results either from the cache or directly from the suppliers (explained below)
- checkin, checkout, destination and guests params can take any value, they're only used to form cache key
- the optional "suppliers" parameter allows to determine which suppliers I want to query, and can take following values:
  - empty (not provided any value), which means that all suppliers should be queried
  - "suppliers=supplier2" which means only 1 supplier (supplier 2) should be queried
  - "suppliers=supplier1,supplier3" which means that both supplier 1 and 3 should be queried (but not supplier 2)

### Response

Response should be returned in a following format:

```
[
  {"id": "abcd", "price": 120, "supplier": "supplier1"},
  {"id": "cdef", "price": 200, "supplier": "supplier3"}
]
```

- each hotel should be returned only once (if some hotel is returned by more than 1 supplier, choose the one with lower price)
- caching needs to be done per search parameters, e.g. if I search for destination "Singapore" and then for "Rome", the cache cannot be reused
- cache should expire after 5 minutes

### Caching

For caching response you can use anything you want - whether it's Redis or Memcache or just a simple memory store, everything works. The goal is to cache proper results and to fetch correct content from cache.

### Resources

- there are 2 suppliers, each of them has different url:
  - https://api.jsonbin.io/b/6259d255c5284e31154df64f
  - https://api.jsonbin.io/b/6259e155c5284e31154df9d4
- please note that for the simplification and easiness of testing these are static urls, they always return the same values