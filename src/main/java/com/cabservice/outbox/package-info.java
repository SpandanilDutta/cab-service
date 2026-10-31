/**
 * Transactional outbox: entities/repositories used by both the API (to insert events)
 * and the separate Worker module (to poll with SELECT ... FOR UPDATE SKIP LOCKED).
 */
package com.cabservice.outbox;
