package com.example.ai4research.di

import com.example.ai4research.core.network.NocoAuthInterceptor
import com.example.ai4research.core.util.Constants
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.api.SiliconFlowApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 网络层依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * 提供 Json 序列化器（容错配置）
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true  // 忽略未知字段
        coerceInputValues = true   // 强制输入值
        isLenient = true           // 宽松模式
        encodeDefaults = true      // 编码默认值
    }
    
    /**
     * 提供 NocoDB OkHttpClient（带拦截器）
     */
    @Provides
    @Singleton
    @Named("noco")
    fun provideNocoOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(NocoAuthInterceptor())  // NocoDB 认证
            .addInterceptor(loggingInterceptor)     // 日志
            .connectTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 提供 SiliconFlow OkHttpClient（更长超时）
     */
    @Provides
    @Singleton
    @Named("siliconflow")
    fun provideSiliconFlowOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)  // AI调用需要更长超时
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 提供 NocoDB Retrofit 实例
     */
    @Provides
    @Singleton
    @Named("noco")
    fun provideNocoRetrofit(
        @Named("noco") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        
        return Retrofit.Builder()
            .baseUrl(Constants.NOCO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
    
    /**
     * 提供 SiliconFlow Retrofit 实例
     */
    @Provides
    @Singleton
    @Named("siliconflow")
    fun provideSiliconFlowRetrofit(
        @Named("siliconflow") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        
        return Retrofit.Builder()
            .baseUrl(SiliconFlowApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
    
    /**
     * 提供 NocoApiService
     */
    @Provides
    @Singleton
    fun provideNocoApiService(@Named("noco") retrofit: Retrofit): NocoApiService {
        return retrofit.create(NocoApiService::class.java)
    }
    
    /**
     * 提供 SiliconFlowApiService
     */
    @Provides
    @Singleton
    fun provideSiliconFlowApiService(@Named("siliconflow") retrofit: Retrofit): SiliconFlowApiService {
        return retrofit.create(SiliconFlowApiService::class.java)
    }
}
