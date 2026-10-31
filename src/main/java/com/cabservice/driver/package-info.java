/**
 * Driver profile, vehicle registration, driver status (online/offline/on_trip/blocked).
 * Does NOT own matching logic (see matching/) or balance mutation logic (see payment/) —
 * only owns the driver's own identity/state.
 */
package com.cabservice.driver;
