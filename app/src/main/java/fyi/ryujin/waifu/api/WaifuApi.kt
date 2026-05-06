package fyi.ryujin.waifu.api

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class WaifuResponse(
    val items: List<WaifuImage>,
    val pageNumber: Int? = null,
    val totalPages: Int? = null,
    val totalCount: Int? = null
)

data class WaifuImage(
    val id: Long? = null,
    val url: String,
    val extension: String? = null,
    val dominantColor: String? = null,
    val source: String? = null,
    val artists: List<WaifuArtist>? = emptyList(),
    val tags: List<WaifuTag>? = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long? = null,
    val isNsfw: Boolean? = null
)

data class WaifuArtist(
    val name: String? = null,
    val pixiv: String? = null,
    val twitter: String? = null,
    val deviantArt: String? = null
)

data class WaifuTag(
    val name: String? = null,
    val description: String? = null,
    @SerializedName("isNsfw")
    val nsfw: Boolean? = null
)

interface WaifuApiService {
    @GET("images")
    suspend fun getImages(
        @Query("IsNsfw") isNsfw: String,
        @Query("OrderBy") orderBy: String,
        @Query("Orientation") orientation: String,
        @Query("PageSize") pageSize: Int
    ): WaifuResponse
}

object WaifuApi {
    fun create(): WaifuApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.waifu.im/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WaifuApiService::class.java)
    }
}
