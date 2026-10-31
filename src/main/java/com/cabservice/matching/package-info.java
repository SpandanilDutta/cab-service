/**
 * Geospatial nearest-driver search (Redis GEOSEARCH) and the accept-race-condition lock
 * (SET NX EX + fencing-token release via Lua EVAL). This package owns trip:{id}:lock only —
 * it does not own trip state transitions (see trip/).
 */
package com.cabservice.matching;
