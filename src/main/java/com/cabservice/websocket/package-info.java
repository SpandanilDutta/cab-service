/**
 * WebSocket/STOMP endpoints, the Redis-backed connection registry (conn:driver:{id} -> serverId),
 * and Pub/Sub relay for both durable event delivery and ephemeral live-location streaming.
 * These two message types share transport but should use separate processing paths —
 * see design doc section 6.
 */
package com.cabservice.websocket;
