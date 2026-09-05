package com.relayhome.launcher

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * TmdbApi keeps these helpers private because they are implementation details. Reflection is
 * used only here so the production files remain within Agent 2's no-edit boundary; all assertions
 * exercise the actual normalization and exact-match implementation without an HTTP request.
 */
class TmdbApiTest {
    @Test
    fun titleNormalization_ignoresCaseAccentsWhitespaceAndPunctuation() {
        assertEquals("amelie2001", normalize("  Amélie (2001) "))
        assertEquals("starwarsanewhope", normalize("Star Wars: A New Hope"))
        assertEquals(normalize("Cafe"), normalize("Café"))
        assertEquals("", normalize("   ...   "))
    }

    @Test
    fun exactMatching_acceptsNormalizedEquivalentTitle_butNotPartialTitle() {
        val results = JSONArray(
            """
            [
              {"name":"The Café", "id":10},
              {"original_name":"A Different Show", "id":11}
            ]
            """.trimIndent()
        )

        val match = findExactResult(results, "the cafe", "name", "original_name")
        assertNotNull(match)
        assertEquals(10, match?.optInt("id"))
        assertNull(findExactResult(results, "the cafe extended", "name", "original_name"))
        assertNull(findExactResult(results, "   ", "name", "original_name"))
    }

    @Test
    fun publicMetadataCalls_emptyInputShortCircuitsWithoutNetwork() {
        // Empty input is a no-network smoke check even if a developer has a local TMDB key.
        assertEquals(emptyList<MediaItem>(), TmdbApi.enrichEpisodes(emptyList()))
    }

    private fun normalize(value: String): String = invokePrivate(
        name = "normalize",
        parameterTypes = arrayOf(String::class.java),
        arguments = arrayOf(value)
    ) as String

    private fun findExactResult(results: JSONArray, query: String, vararg keys: String) = invokePrivate(
        name = "findExactResult",
        parameterTypes = arrayOf(JSONArray::class.java, String::class.java, Array<String>::class.java),
        arguments = arrayOf(results, query, keys)
    ) as? org.json.JSONObject

    private fun invokePrivate(name: String, parameterTypes: Array<Class<*>>, arguments: Array<Any?>): Any? {
        val method = TmdbApi::class.java.getDeclaredMethod(name, *parameterTypes).apply {
            isAccessible = true
        }
        return method.invoke(TmdbApi, *arguments)
    }
}
