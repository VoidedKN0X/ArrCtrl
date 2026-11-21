package com.vknxworks.arrctrl.data.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileEditsTest {
    private fun json(text: String) = ApiJson.parseToJsonElement(text) as JsonObject

    private val profile = json(
        """{"id":4,"name":"HD","upgradeAllowed":true,"cutoff":1001,"minFormatScore":0,"cutoffFormatScore":100,"somethingNew":{"kept":true},
        "items":[
          {"quality":{"id":1,"name":"SDTV","source":"television","resolution":480},"items":[],"allowed":false},
          {"quality":{"id":9,"name":"HDTV-1080p"},"items":[],"allowed":true},
          {"id":1001,"name":"WEB 1080p","allowed":true,"items":[
            {"quality":{"id":3,"name":"WEBDL-1080p"},"items":[],"allowed":true},
            {"quality":{"id":15,"name":"WEBRip-1080p"},"items":[],"allowed":true}]}],
        "formatItems":[{"format":7,"name":"x265","score":-50},{"format":8,"name":"Repack","score":5}]}""",
    )

    @Test fun rowsAreSingleQualitiesAndGroups() {
        val rows = ProfileEdits.qualityRows(profile)
        assertEquals(listOf("SDTV", "HDTV-1080p", "WEB 1080p"), rows.map { it.name })
        assertEquals(listOf(false, true, true), rows.map { it.allowed })
        assertEquals(listOf(1, 9, 1001), rows.map { it.cutoffId })
        assertEquals(listOf("WEBDL-1080p", "WEBRip-1080p"), rows[2].members)
    }

    @Test fun allowingARowKeepsEverythingElse() {
        val changed = ProfileEdits.setAllowed(profile, 0, true)
        assertEquals(listOf(true, true, true), ProfileEdits.qualityRows(changed).map { it.allowed })
        // Fields the app doesn't know, and the rest of the row, go back as they came.
        assertEquals(profile["somethingNew"], changed["somethingNew"])
        assertEquals("television", changed.arr("items").objects()[0].obj("quality").str("source"))
        assertEquals(profile, ProfileEdits.setAllowed(changed, 0, false))
        assertEquals(profile, ProfileEdits.setAllowed(profile, 99, true))
    }

    @Test fun aGroupTakesItsQualitiesWithIt() {
        val off = ProfileEdits.setAllowed(profile, 2, false)
        val group = off.arr("items").objects()[2]
        assertEquals(false, group.bool("allowed"))
        assertEquals(listOf(false, false), group.arr("items").objects().map { it.bool("allowed") })
    }

    @Test fun whatTheServerWouldRefuseIsCaughtFirst() {
        assertNull(ProfileEdits.problem(profile))
        // The quality to upgrade until was switched off.
        assertEquals(ProfileProblem.CUTOFF_NOT_ALLOWED, ProfileEdits.problem(ProfileEdits.setAllowed(profile, 2, false)))
        assertNull(ProfileEdits.problem(ProfileEdits.setCutoff(ProfileEdits.setAllowed(profile, 2, false), 9)))
        val none = ProfileEdits.setAllowed(ProfileEdits.setAllowed(profile, 1, false), 2, false)
        assertEquals(ProfileProblem.NOTHING_ALLOWED, ProfileEdits.problem(none))
        assertEquals(ProfileProblem.NAME_BLANK, ProfileEdits.problem(with(ProfileEdits) { profile.with("name", "  ") }))
    }

    @Test fun formatScores() {
        assertEquals(listOf(FormatScore(7, "x265", -50), FormatScore(8, "Repack", 5)), ProfileEdits.formatScores(profile))
        val changed = ProfileEdits.setFormatScore(profile, 7, 25)
        assertEquals(listOf(25, 5), ProfileEdits.formatScores(changed).map { it.score })
        assertEquals(profile, ProfileEdits.setFormatScore(profile, 999, 1))
    }

    private val format = json(
        """{"id":7,"name":"x265","includeCustomFormatWhenRenaming":false,"specifications":[
          {"name":"x265","implementation":"ReleaseTitleSpecification","implementationName":"Release Title","negate":false,"required":true,
           "fields":[{"order":0,"name":"value","label":"Regular Expression","value":"[xh]\\.?265","type":"textbox"}]},
          {"name":"Not 4K","implementation":"ResolutionSpecification","negate":true,"required":false,
           "fields":[{"order":0,"name":"value","value":2160,"type":"select","selectOptions":[{"value":1080,"name":"R1080p"},{"value":2160,"name":"R2160p"}]}]}]}""",
    )

    @Test fun conditionsCanBeChangedRemovedAndAdded() {
        assertEquals(2, ProfileEdits.specs(format).size)
        val edited = ProfileEdits.setSpec(format, 0) { ProfileEdits.setField(it, "value", JsonPrimitive("hevc")) }
        assertEquals("hevc", ProfileEdits.fields(ProfileEdits.specs(edited)[0])[0].str("value"))
        // The other condition, and the field's other properties, are untouched.
        assertEquals(ProfileEdits.specs(format)[1], ProfileEdits.specs(edited)[1])
        assertEquals("Regular Expression", ProfileEdits.fields(ProfileEdits.specs(edited)[0])[0].str("label"))

        val removed = ProfileEdits.removeSpec(format, 0)
        assertEquals(listOf("Not 4K"), ProfileEdits.specs(removed).map { it.str("name") })

        val schema = json("""{"id":0,"implementation":"SizeSpecification","implementationName":"Size","negate":false,"required":false,"presets":[],
            "fields":[{"name":"min","value":0,"type":"number","isFloat":true},{"name":"max","value":0,"type":"number","isFloat":true}]}""")
        val added = ProfileEdits.addSpec(ProfileEdits.newFormat(), schema)
        val spec = ProfileEdits.specs(added).single()
        assertEquals("Size", spec.str("name"))
        assertNull(spec["presets"])
        assertNull(spec["id"])
        assertEquals(2, ProfileEdits.fields(spec).size)
    }

    @Test fun numbersKeepTheirKind() {
        assertEquals(JsonPrimitive(1080L), ProfileEdits.number(" 1080 ", isFloat = false))
        assertEquals(JsonPrimitive(2.5), ProfileEdits.number("2,5", isFloat = true))
        assertEquals(JsonPrimitive(3.0), ProfileEdits.number("3", isFloat = true))
        assertNull(ProfileEdits.number("", isFloat = false))
        assertNull(ProfileEdits.number("abc", isFloat = true))
    }
}
