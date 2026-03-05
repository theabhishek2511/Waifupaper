package fyi.ryujin.waifu.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class WaifuResponse(val items: List<WaifuImage>)
data class WaifuImage(val url: String)

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
