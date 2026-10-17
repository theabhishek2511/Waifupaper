package fyi.ryujin.waifu

import org.junit.Test

import org.junit.Assert.*
import fyi.ryujin.waifu.api.WaifuApi
import fyi.ryujin.waifu.api.WaifuResponse
import com.google.gson.Gson

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun apiBaseUrlGetsTrailingSlash() {
        assertEquals("https://example.com/api/", WaifuApi.normalizeBaseUrl(" https://example.com/api "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun apiBaseUrlRejectsNonHttpSchemes() {
        WaifuApi.normalizeBaseUrl("ftp://example.com")
    }

    @Test
    fun documentedImageListingDeserializes() {
        val json = """{"items":[{"id":6758,"extension":".png","url":"https://cdn.example/6758.png","width":1600,"height":2088,"isNsfw":false,"artists":[{"name":"Artist"}],"tags":[{"name":"Waifu"}]}],"pageNumber":1,"totalPages":1,"totalCount":1}"""

        val response = Gson().fromJson(json, WaifuResponse::class.java)

        assertEquals(1, response.items.size)
        assertEquals("https://cdn.example/6758.png", response.items.single().url)
        assertEquals(6758L, response.items.single().id)
        assertEquals("Artist", response.items.single().artists?.single()?.name)
        assertEquals(1, response.totalCount)
    }
}
