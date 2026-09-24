package com.tylergimpel.gymtracker.domain

/**
 * Whether the app is light or dark, or follows the phone.
 *
 * Following the phone is the default and is stored as no choice at all, so a lifter who never
 * opens Settings goes on getting whatever their phone does, including when it changes at dusk.
 */
enum class Theme { SYSTEM, LIGHT, DARK }
