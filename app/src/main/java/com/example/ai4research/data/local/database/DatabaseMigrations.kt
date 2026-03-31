package com.example.ai4research.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `item_relations` (
                    `id` TEXT NOT NULL,
                    `owner_user_id` TEXT NOT NULL,
                    `from_item_id` TEXT NOT NULL,
                    `to_item_id` TEXT NOT NULL,
                    `relation_type` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `source` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_item_relations_owner_user_id_from_item_id` ON `item_relations` (`owner_user_id`, `from_item_id`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_item_relations_owner_user_id_to_item_id` ON `item_relations` (`owner_user_id`, `to_item_id`)"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_item_relations_owner_user_id_from_item_id_to_item_id_relation_type` ON `item_relations` (`owner_user_id`, `from_item_id`, `to_item_id`, `relation_type`)"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `project_context_documents` (
                    `id` TEXT NOT NULL,
                    `owner_user_id` TEXT NOT NULL,
                    `project_id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `markdown_path` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `keywords` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_project_context_documents_owner_user_id_project_id` ON `project_context_documents` (`owner_user_id`, `project_id`)"
            )
        }
    }

    /**
     * Add every new Room migration here.
     *
     * Rule:
     * 1. Do not use destructive migration for version upgrades.
     * 2. Every schema version bump must add a formal Migration.
     * 3. Prefer additive schema changes that preserve existing rows.
     */
    val ALL = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6
    )
}
