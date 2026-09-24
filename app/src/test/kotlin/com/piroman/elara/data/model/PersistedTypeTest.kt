package com.piroman.elara.data.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * [listTypeOf] exists because the obvious alternative — `object : TypeToken<List<T>>() {}` —
 * resolves `T` from an anonymous subclass's generic superclass *signature*, and R8 does not
 * preserve those. That failure is **release-only** (the 2026-09-21 build merged eleven such
 * subclasses into one class with a single `Signature` attribute, so every store read as
 * empty and the next save overwrote the user's data), which means **no test here can
 * reproduce it**.
 *
 * What these tests can do is pin the contract that makes the helper immune in the first
 * place: the type must be assembled from `Class` literals and carry its element type as a
 * real type argument. Rewrite the helper to go back through a signature and this fails on
 * the JVM, which is the earliest point anyone could notice.
 */
class PersistedTypeTest {

    @Test
    fun `a persisted list type is a parameterised List carrying its element type`() {
        val type = listTypeOf(Playlist::class.java)

        assertTrue("must be a real ParameterizedType, not a raw List", type is ParameterizedType)
        val parameterised = type as ParameterizedType
        assertEquals(List::class.java, parameterised.rawType)
        assertArrayEquals(
            "the element type must be the Class literal, not something read from a signature",
            arrayOf(Playlist::class.java),
            parameterised.actualTypeArguments
        )
    }

    /**
     * Every element type the app persists. `String` is included because the search history is
     * a `List<String>` — easy to forget, since it is not a `data.model` type.
     */
    @Test
    fun `every persisted element type resolves to itself`() {
        assertEquals(Track::class.java, elementOf(listTypeOf(Track::class.java)))
        assertEquals(Playlist::class.java, elementOf(listTypeOf(Playlist::class.java)))
        assertEquals(LyricLine::class.java, elementOf(listTypeOf(LyricLine::class.java)))
        assertEquals(String::class.java, elementOf(listTypeOf(String::class.java)))
    }

    private fun elementOf(type: Type): Type =
        (type as ParameterizedType).actualTypeArguments.single()
}
