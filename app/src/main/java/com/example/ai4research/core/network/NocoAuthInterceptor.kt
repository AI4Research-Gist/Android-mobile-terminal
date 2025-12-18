package com.example.ai4research.core.network

import com.example.ai4research.core.util.Constants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * NocoDB 认证拦截器
 * 为每个请求自动添加 xc-token Header
 */
class NocoAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // 添加 NocoDB Token
        val authenticatedRequest = originalRequest.newBuilder()
            .header("xc-token", Constants.NOCO_TOKEN)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        
        return chain.proceed(authenticatedRequest)
    }
}

