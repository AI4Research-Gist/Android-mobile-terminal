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
     * GET /items
     */
    @GET("items")
    suspend fun getItems(
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
        @Query("sort") sort: String? = "-CreatedAt"  // 按创建时间倒序
    ): NocoListResponse<NocoItemDto>
    
    /**
     * 根据类型获取 Items
     * GET /items?where=(type,eq,paper)
     */
    @GET("items")
    suspend fun getItemsByType(
        @Query("where") where: String,  // 格式：(type,eq,paper)
        @Query("limit") limit: Int? = 100
    ): NocoListResponse<NocoItemDto>
    
    /**
     * 获取单个 Item
     * GET /items/{id}
     */
    @GET("items/{id}")
    suspend fun getItemById(
        @Path("id") id: String
    ): NocoItemDto
    
    /**
     * 创建新 Item
     * POST /items
     */
    @POST("items")
    suspend fun createItem(
        @Body item: NocoItemDto
    ): NocoItemDto
    
    /**
     * 更新 Item
     * PATCH /items/{id}
     */
    @PATCH("items/{id}")
    suspend fun updateItem(
        @Path("id") id: String,
        @Body item: NocoItemDto
    ): NocoItemDto
    
    /**
     * 删除 Item
     * DELETE /items/{id}
     */
    @DELETE("items/{id}")
    suspend fun deleteItem(
        @Path("id") id: String
    ): Unit
    
    // ==================== Projects API ====================
    
    /**
     * 获取所有项目
     * GET /projects
     */
    @GET("projects")
    suspend fun getProjects(
        @Query("limit") limit: Int? = 100
    ): NocoListResponse<NocoProjectDto>
    
    /**
     * 获取单个项目
     * GET /projects/{id}
     */
    @GET("projects/{id}")
    suspend fun getProjectById(
        @Path("id") id: String
    ): NocoProjectDto
    
    /**
     * 创建项目
     * POST /projects
     */
    @POST("projects")
    suspend fun createProject(
        @Body project: NocoProjectDto
    ): NocoProjectDto
    
    // ==================== Authentication API ====================
    
    /**
     * 用户注册
     * POST /users
     */
    @POST("users")
    suspend fun registerUser(
        @Body user: NocoUserCreateDto
    ): NocoUserDto
    
    /**
     * 用户登录（通过邮箱查询）
     * GET /users?where=(email,eq,{email})
     */
    @GET("users")
    suspend fun loginUser(
        @Query("where") where: String  // 格式：(email,eq,user@example.com)
    ): NocoUserListResponse
    
    /**
     * 获取用户信息
     * GET /users/{id}
     */
    @GET("users/{id}")
    suspend fun getUserById(
        @Path("id") id: String
    ): NocoUserDto
    
    /**
     * 更新用户信息
     * PATCH /users/{id}
     */
    @PATCH("users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body user: NocoUserDto
    ): NocoUserDto
}

