package com.example.ai4research.data.remote.api

import com.example.ai4research.data.remote.dto.NocoItemDto
import com.example.ai4research.data.remote.dto.NocoListResponse
import com.example.ai4research.data.remote.dto.NocoProjectDto
import com.example.ai4research.data.remote.dto.NocoUserCreateDto
import com.example.ai4research.data.remote.dto.NocoUserDto
import com.example.ai4research.data.remote.dto.NocoUserListResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NocoApiService {
    @GET("mez4qicxcudfwnc")
    suspend fun getItems(
        @Query("where") where: String? = null,
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
        @Query("sort") sort: String? = "-CreatedAt"
    ): NocoListResponse<NocoItemDto>

    @GET("mez4qicxcudfwnc")
    suspend fun getItemsByType(
        @Query("where") where: String,
        @Query("limit") limit: Int? = 100
    ): NocoListResponse<NocoItemDto>

    @GET("mez4qicxcudfwnc/{id}")
    suspend fun getItemById(
        @Path("id") id: String
    ): NocoItemDto

    @POST("mez4qicxcudfwnc")
    suspend fun createItem(
        @Body item: NocoItemDto
    ): NocoItemDto

    @PATCH("mez4qicxcudfwnc/{id}")
    suspend fun updateItem(
        @Path("id") id: String,
        @Body item: NocoItemDto
    ): NocoItemDto

    @DELETE("mez4qicxcudfwnc/{id}")
    suspend fun deleteItem(
        @Path("id") id: String
    )

    @GET("m14rejhia8w9cf7")
    suspend fun getProjects(
        @Query("where") where: String? = null,
        @Query("limit") limit: Int? = 100
    ): NocoListResponse<NocoProjectDto>

    @GET("m14rejhia8w9cf7/{id}")
    suspend fun getProjectById(
        @Path("id") id: String
    ): NocoProjectDto

    @POST("m14rejhia8w9cf7")
    suspend fun createProject(
        @Body project: NocoProjectDto
    ): NocoProjectDto

    @DELETE("m14rejhia8w9cf7/{id}")
    suspend fun deleteProject(
        @Path("id") id: String
    )

    @POST("m1j18kc9fkjhcio")
    suspend fun registerUser(
        @Body user: NocoUserCreateDto
    ): NocoUserDto

    @GET("m1j18kc9fkjhcio")
    suspend fun loginUser(
        @Query("where") where: String
    ): NocoUserListResponse

    @GET("m1j18kc9fkjhcio")
    suspend fun checkUsernameExists(
        @Query("where") where: String
    ): NocoUserListResponse

    @GET("m1j18kc9fkjhcio/{id}")
    suspend fun getUserById(
        @Path("id") id: String
    ): NocoUserDto

    @PATCH("m1j18kc9fkjhcio/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body user: NocoUserDto
    ): NocoUserDto
}
