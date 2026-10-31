/**
 * Trip lifecycle state machine (requested -> assigned -> started -> completed/cancelled),
 * fare locking at acceptance, and live-location relay during an active trip.
 * Writes outbox_events in the same transaction as any state change (see outbox/).
 */
package com.cabservice.trip;
