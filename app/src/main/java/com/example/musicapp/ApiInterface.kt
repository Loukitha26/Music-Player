package com.example.musicapp

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ApiInterface {

    @Headers("x-rapidapi-key: 44058fe06fmsh02c9940b1dc2a72p1bdefejsned14d566082a",
        "x-rapidapi-host: deezerdevs-deezer.p.rapidapi.com")
    @GET("search")
    fun getData(@Query("q") query: String) : Call<MyData>
}