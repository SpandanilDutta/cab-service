/**
 * OTP generation/verification. Redis-only (trip:{id}:otp, otp_attempts, otp_regen_count) —
 * deliberately never persisted to Postgres; see design doc section 8 for why.
 */
package com.cabservice.otp;
