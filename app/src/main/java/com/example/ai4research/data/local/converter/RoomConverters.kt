package com.example.ai4research.data.local.converter

import androidx.room.TypeConverter
import com.example.ai4research.domain.model.ItemMetaData
import com.example.ai4research.domain.model.TimelineEvent
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Room TypeConverters - 处理复杂类型的转换
 * 用于将 JSON 字符串转为对象，或将 Date 转为 Long
 */
class RoomConverters {
    private val gson = Gson()
    
    // ==================== Date 转换 ====================
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    // ==================== List<String> 转换 ====================
    
    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }
    
    @TypeConverter
    fun stringListToJson(list: List<String>?): String? {
        return gson.toJson(list)
    }
    
    // ==================== MetaData 转换 ====================
    // 注意：这里只是示例，实际使用时需要根据 type 动态解析
    
    @TypeConverter
    fun fromMetaJson(value: String?): ItemMetaData? {
        if (value.isNullOrEmpty()) return null
        return try {
            // 暂时返回 null，具体解析在 Repository 层处理
            null
        } catch (e: JsonSyntaxException) {
            null
        }
    }
    
    @TypeConverter
    fun metaDataToJson(metaData: ItemMetaData?): String? {
        return metaData?.let { gson.toJson(it) }
    }
    
    // ==================== Timeline 转换（比赛专用）====================
    
    @TypeConverter
    fun fromTimelineJson(value: String?): List<TimelineEvent>? {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            val listType = object : TypeToken<List<TimelineEvent>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }
    
    @TypeConverter
    fun timelineToJson(timeline: List<TimelineEvent>?): String? {
        return gson.toJson(timeline)
    }
}

