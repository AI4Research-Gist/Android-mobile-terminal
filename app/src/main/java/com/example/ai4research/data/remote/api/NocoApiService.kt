package com.example.ai4research.data.remote.api

import com.example.ai4research.data.remote.dto.NocoItemDto
import com.example.ai4research.data.remote.dto.NocoListResponse
import com.example.ai4research.data.remote.dto.NocoProjectDto
import com.example.ai4research.data.remote.dto.NocoUserCreateDto
import com.example.ai4research.data.remote.dto.NocoUserDto
import com.example.ai4research.data.remote.dto.NocoUserListResponse
import retrofit2.http.*

/**
 * NocoDB API 接口定义
 * Base URL: http://47.109.158.254:8080/api/v1/db/data/v1/p8bhzq1ltutm8zr/
 */
interface NocoApiService {
    /**
     * 获取所有 Items
     * GET /mez4qicxcudfwnc
     */
    @GET("mez4qicxcudfwnc")
    suspend fun getItems(
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
        @Query("sort") sort: String? = "-CreatedAt"  // 按创建时间倒序
    ): NocoListResponse<NocoItemDto>
    
    /**
     * 根据类型获取 Items
     * GET /mez4qicxcudfwnc?where=(type,eq,paper)
     */
    @GET("mez4qicxcudfwnc")
    suspend fun getItemsByType(
        @Query("where") where: String,  // 格式：(type,eq,paper)
        @Query("limit") limit: Int? = 100
    ): NocoListResponse<NocoItemDto>
    
    /**
     * 获取单个 Item
     * GET /mez4qicxcudfwnc/{id}
     */
    @GET("mez4qicxcudfwnc/{id}")
    suspend fun getItemById(
        @Path("id") id: String
    ): NocoItemDto
    
    /**
     * 创建新 Item
     * POST /mez4qicxcudfwnc
     */
    @POST("mez4qicxcudfwnc")
    suspend fun createItem(
        @Body item: NocoItemDto
    ): NocoItemDto
    
    /**
     * 更新 Item
     * PATCH /mez4qicxcudfwnc/{id}
     */
    @PATCH("mez4qicxcudfwnc/{id}")
    suspend fun updateItem(
        @Path("id") id: String,
        @Body item: NocoItemDto
    ): NocoItemDto
    
    /**
     * 删除 Item
     * DELETE /mez4qicxcudfwnc/{id}
     */
    @DELETE("mez4qicxcudfwnc/{id}")
    suspend fun deleteItem(
        @Path("id") id: String
    ): Unit
    
    // ==================== Projects API ====================
    
    /**
     * 获取所有项目
     * GET /m14rejhia8w9cf7
     */
    @GET("m14rejhia8w9cf7")
    suspend fun getProjects(
        @Query("limit") limit: Int? = 100
    ): NocoListResponse<NocoProjectDto>
    
    /**
     * 获取单个项目
     * GET /m14rejhia8w9cf7/{id}
     */
    @GET("m14rejhia8w9cf7/{id}")
    suspend fun getProjectById(
        @Path("id") id: String
    ): NocoProjectDto
    
    /**
     * 创建项目
     * POST /m14rejhia8w9cf7
     */
    @POST("m14rejhia8w9cf7")
    suspend fun createProject(
        @Body project: NocoProjectDto
    ): NocoProjectDto
    
    /**
     * 删除项目
     * DELETE /m14rejhia8w9cf7/{id}
     */
    @DELETE("m14rejhia8w9cf7/{id}")
    suspend fun deleteProject(
        @Path("id") id: String
    ): Unit

    
    // ==================== Authentication API ====================
    
    /**
     * 用户注册
     * POST /m1j18kc9fkjhcio
     */
    @POST("m1j18kc9fkjhcio")
    suspend fun registerUser(
        @Body user: NocoUserCreateDto
    ): NocoUserDto
    
    /**
     * 用户登录（通过邮箱/用户名/手机号查询）
     * GET /m1j18kc9fkjhcio?where=...
     */
    @GET("m1j18kc9fkjhcio")
    suspend fun loginUser(
        @Query("where") where: String  // 支持多种查询条件
    ): NocoUserListResponse
    
    /**
     * 检查用户名是否已存在
     * GET /m1j18kc9fkjhcio?where=(username,eq,{username})
     */
    @GET("m1j18kc9fkjhcio")
    suspend fun checkUsernameExists(
        @Query("where") where: String
    ): NocoUserListResponse
    
    /**
     * 获取用户信息
     * GET /m1j18kc9fkjhcio/{id}
     */
    @GET("m1j18kc9fkjhcio/{id}")
    suspend fun getUserById(
        @Path("id") id: String
    ): NocoUserDto
    
    /**
     * 更新用户信息
     * PATCH /m1j18kc9fkjhcio/{id}
     */
    @PATCH("m1j18kc9fkjhcio/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body user: NocoUserDto
    ): NocoUserDto
}

