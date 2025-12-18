package com.example.ai4research.core.util

/**
 * 全局常量配置
 */
object Constants {
    // NocoDB 后端配置
    const val NOCO_BASE_URL = "http://47.109.158.254:8080/api/v1/db/data/v1/p8bhzq1ltutm8zr/"
    const val NOCO_TOKEN = "lBVvkotCNwFCXz-j1-s3XcE5tXRCp7MzKECOfY2e"
    
    // NocoDB 表名
    const val TABLE_ITEMS = "items"
    const val TABLE_PROJECTS = "projects"
    
    // Item 类型枚举
    object ItemType {
        const val PAPER = "paper"
        const val COMPETITION = "competition"
        const val INSIGHT = "insight"
        const val VOICE = "voice"
    }
    
    // Item 状态枚举
    object ItemStatus {
        const val PROCESSING = "processing"
        const val DONE = "done"
        const val FAILED = "failed"
    }
    
    // 阅读状态枚举
    object ReadStatus {
        const val UNREAD = "unread"
        const val READING = "reading"
        const val READ = "read"
    }
    
    // WorkManager 标识
    const val WORK_SYNC_DATA = "sync_data_work"
    const val WORK_UPLOAD_FILE = "upload_file_work"
    
    // SharedPreferences 键名
    const val PREF_NAME = "ai4research_prefs"
    const val KEY_LAST_SYNC_TIME = "last_sync_time"
    
    // 权限相关
    const val PERMISSION_REQUEST_CODE = 1001
    
    // 网络超时
    const val NETWORK_TIMEOUT = 30L  // 秒
}

