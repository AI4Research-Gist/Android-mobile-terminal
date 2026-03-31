# Database Migration Policy

## Goal

Preserve existing local data across app upgrades.

This project should not rely on destructive database recreation when the Room schema changes.

## Rules

1. Every Room version bump must add a formal migration.
2. Prefer additive schema evolution:
   - create new tables
   - add nullable or safe default columns
   - add indexes
3. Do not re-enable `fallbackToDestructiveMigration()`.
4. Register every new migration in:
   - [DatabaseMigrations.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/local/database/DatabaseMigrations.kt)
5. Wire the full migration list through:
   - [DatabaseModule.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/di/DatabaseModule.kt)

## Current migration path

- `4 -> 5`
  - add `item_relations`
- `5 -> 6`
  - add `project_context_documents`

## Upgrade checklist

When adding a new schema version:

1. Increase the Room version in `AI4ResearchDatabase`
2. Add `Migration(oldVersion, newVersion)`
3. Register it in `DatabaseMigrations.ALL`
4. Compile-check the app
5. If the migration changes existing columns or semantics, document it in the changelog

## What not to do

Do not:

- bump the database version without adding a migration
- delete old tables unless a deliberate data migration has already copied the content
- use destructive migration as a shortcut during normal development

## Rationale

This app stores research items, projects, relations, and project context locally. Losing local data on upgrade is too risky for the product stage the app is now in.
