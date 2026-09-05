package com.relayhome.launcher

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException

/** Covers the dependency-free Nuvio model/validation and JSON response guards without HTTP. */
class NuvioApiModelTest {
    @Test
    fun sessionExpiration_isInclusiveAtExpiry_andUnknownExpiryStaysUsable() {
        assertFalse(NuvioSession("token", expiresAtEpochSeconds = null).isExpired(10))
        assertFalse(NuvioSession("token", expiresAtEpochSeconds = 101).isExpired(100))
        assertTrue(NuvioSession("token", expiresAtEpochSeconds = 100).isExpired(100))
    }

    @Test
    fun pullProfiles_rejectsBlankOrExpiredSession_withoutNetworkAccess() = runBlocking {
        val blank = NuvioApi.pullProfiles(NuvioSession(""))
        val expired = NuvioApi.pullProfiles(NuvioSession("token", expiresAtEpochSeconds = 1))

        assertTrue(blank.isFailure)
        assertTrue(blank.exceptionOrNull() is NuvioSessionExpiredException)
        assertTrue(expired.isFailure)
        assertTrue(expired.exceptionOrNull() is NuvioSessionExpiredException)
        assertEquals(
            "Nuvio session expired. Sign in again from Provider settings to reconnect your account. Your last successful data is still available.",
            blank.exceptionOrNull()?.message
        )
    }

    @Test
    fun jsonResponseParsing_acceptsObjectAndArrayResponses_withoutNetworkAccess() {
        assertEquals("abc", firstJsonObject("{\"code\":\"abc\"}").optString("code"))
        assertEquals("first", firstJsonObject("[{\"name\":\"first\"},{\"name\":\"second\"}]").optString("name"))

        val error = runCatching { firstJsonObject("true") }.exceptionOrNull()
        assertTrue(error is InvocationTargetException)
        assertEquals(
            "Nuvio returned an invalid QR login response.",
            (error as InvocationTargetException).targetException.message
        )
    }

    @Test
    fun jsonFieldParsing_prefersFirstUsefulValue_andRejectsNullOrNonPositiveInts() {
        val payload = JSONObject("""{"primary":" ","secondary":" Title ","nullValue":null,"zero":0,"valid":4}""")

        assertEquals("Title", firstString(payload, "primary", "secondary"))
        assertNull(firstString(payload, "missing", "nullValue"))
        assertEquals(4, firstInt(payload, "zero", "valid"))
        assertNull(firstInt(payload, "zero", "missing"))
        assertEquals("bad credentials", nuvioErrorDetail("{\"message\":\" bad credentials \"}"))
        assertNull(nuvioErrorDetail("{\"message\":\"null\"}"))
    }

    @Test
    fun visibleRelayText_removesProviderControlCharacters_withoutChangingContent() {
        assertEquals("Series title", "\u0000 Series\u000B title \u001F".visibleRelayText())
        assertEquals("episode title", " episode title ".visibleRelayText())
        assertEquals("", "\u0000\u000B".visibleRelayText())
    }

    private fun firstJsonObject(body: String): JSONObject = invokePrivate(
        name = "firstJsonObject",
        parameterTypes = arrayOf(String::class.java),
        arguments = arrayOf(body)
    ) as JSONObject

    private fun firstString(payload: JSONObject, vararg names: String): String? = invokePrivate(
        name = "firstString",
        parameterTypes = arrayOf(JSONObject::class.java, Array<String>::class.java),
        arguments = arrayOf(payload, names)
    ) as? String

    private fun firstInt(payload: JSONObject, vararg names: String): Int? = invokePrivate(
        name = "firstInt",
        parameterTypes = arrayOf(JSONObject::class.java, Array<String>::class.java),
        arguments = arrayOf(payload, names)
    ) as? Int

    private fun nuvioErrorDetail(body: String): String? = invokePrivate(
        name = "nuvioErrorDetail",
        parameterTypes = arrayOf(String::class.java),
        arguments = arrayOf(body)
    ) as? String

    private fun invokePrivate(name: String, parameterTypes: Array<Class<*>>, arguments: Array<Any?>): Any? {
        val method = NuvioApi::class.java.getDeclaredMethod(name, *parameterTypes).apply {
            isAccessible = true
        }
        return method.invoke(NuvioApi, *arguments)
    }
}
