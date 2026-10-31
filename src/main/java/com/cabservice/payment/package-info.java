/**
 * Immutable ledger_entries + cached driver.current_balance. Never mutate balance directly
 * outside an atomic UPDATE or a row-locked transaction — see design doc section 9.
 */
package com.cabservice.payment;
