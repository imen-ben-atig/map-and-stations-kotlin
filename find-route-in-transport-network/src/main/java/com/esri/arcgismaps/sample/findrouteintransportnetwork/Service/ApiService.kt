package com.esri.arcgismaps.sample.findrouteintransportnetwork.Service

import com.esri.arcgismaps.sample.findrouteintransportnetwork.Models.Location
import com.esri.arcgismaps.sample.findrouteintransportnetwork.Models.callResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("iterinary/A")
        fun getPost(@Query("fromLan") fromLan: Double, @Query("fromLat") fromLat: Double,@Query("toLan") toLan: Double, @Query("toLat") toLat: Double, @Query("type") type: String ): Call<List<Location>>
    @POST("A/stations")
    fun addLocation(@Body locationData: Location): Call<Location> // Replace ResponseType with your actual response class
    @POST("call")
    fun call():Call<callResponse>
    @GET("getStationTitle")
    suspend fun getStationTitle(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<String>
}