package com.freetia.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionRulesTest {

    @Test
    fun countriesAreSplitUppercasedDedupedAndSorted() {
        val raw = listOf("in", "US;in", " gb , fr ", "", "IN")
        assertEquals(listOf("FR", "GB", "IN", "US"), RegionRules.countries(raw))
    }

    @Test
    fun languagesAreSplitLowercasedAndDeduped() {
        assertEquals(listOf("english", "hindi"), RegionRules.languages(listOf("Hindi;English", "hindi", "")))
    }

    @Test
    fun categoriesComeFromGroupTitleSegments() {
        val raw = listOf("News;Sports", "News", "Undefined", "Kids; Music", "All")
        assertEquals(listOf("Kids", "Music", "News", "Sports"), RegionRules.categories(raw))
        assertEquals(listOf("A", "B"), RegionRules.categoriesOf("A; B ;;undefined"))
        assertEquals(emptyList<String>(), RegionRules.categoriesOf(""))
    }

    @Test
    fun filterRoundTripsThroughTheStoredString() {
        assertEquals("*", RegionFilter().encode())
        assertEquals("IN", RegionFilter("IN").encode())
        assertEquals("IN/hindi", RegionFilter("IN", "hindi").encode())
        assertEquals("*/hindi", RegionFilter("", "hindi").encode())
        for (f in listOf(RegionFilter(), RegionFilter("IN"), RegionFilter("IN", "hindi"), RegionFilter("", "hindi"))) {
            assertEquals(f, RegionFilter.decode(f.encode()))
        }
    }

    @Test
    fun decodeNormalisesCaseAndTreatsBlankAndStarAsAll() {
        assertTrue(RegionFilter.decode("").isAll)
        assertTrue(RegionFilter.decode("*").isAll)
        assertEquals(RegionFilter("IN", "hindi"), RegionFilter.decode("in/Hindi"))
    }

    @Test
    fun firstRunPickerShowsOnlyUntilChosenOrSkipped() {
        assertTrue(RegionFilter.needsFirstRunPicker(""))
        assertFalse(RegionFilter.needsFirstRunPicker("*"))
        assertFalse(RegionFilter.needsFirstRunPicker("IN"))
    }

    @Test
    fun countryFilterMatchesAnyListedCountry() {
        val f = RegionFilter("IN")
        assertTrue(f.matches("IN", ""))
        assertTrue(f.matches("US;in", ""))
        assertFalse(f.matches("US", ""))
    }

    @Test
    fun channelsWithoutACountryOrLanguageAreKept() {
        val f = RegionFilter("IN", "hindi")
        assertTrue(f.matches("", ""))
        assertTrue(f.matches("IN", ""))
        assertFalse(f.matches("IN", "tamil"))
        assertTrue(f.matches("IN", "Tamil;Hindi"))
    }

    @Test
    fun allFilterMatchesEverything() {
        assertTrue(RegionFilter().matches("US", "english"))
    }

    @Test
    fun searchTermLosesLikeWildcardsAndIsBounded() {
        assertEquals("bbc", LibraryRepository.sanitizeQuery("  b%b_c "))
        assertEquals(60, LibraryRepository.sanitizeQuery("x".repeat(200)).length)
    }
}
