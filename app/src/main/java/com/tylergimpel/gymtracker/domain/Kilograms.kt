package com.tylergimpel.gymtracker.domain

/**
 * A weight in kilograms, the canonical unit everything is stored in.
 *
 * Storage is canonical so that one gym's kg lat pulldown and another's lb machine can both be
 * logged honestly without putting a false step change in the progress graph. Display units are a
 * separate concern that arrives with its own ticket; nothing here converts anything.
 */
@JvmInline
value class Kilograms(val value: Double)
