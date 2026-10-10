package com.tylergimpel.traintracks.data

import com.tylergimpel.traintracks.domain.SetType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The column default has to be spelled as a literal, because Room's `defaultValue` is an annotation
 * argument and cannot read the enum. This is the guard that keeps the two from drifting apart:
 * rename the stored value without touching the default and sets migrated from version 3 would come
 * back as a type `fromStored` rejects.
 */
class SetEntryDefaultTypeTest {

    @Test
    fun `the column default is a set type the app can read back`() {
        assertEquals(SetType.WORKING, SetType.fromStored(SetEntryEntity.DEFAULT_TYPE))
    }

    @Test
    fun `sets logged before types existed are treated as work`() {
        assertEquals(SetType.WORKING.stored, SetEntryEntity.DEFAULT_TYPE)
    }
}
