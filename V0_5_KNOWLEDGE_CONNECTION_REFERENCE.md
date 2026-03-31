# v0.5 Knowledge Connection Reference

## Purpose

This document is the implementation reference for `v0.5`.

The goal of `v0.5` is to move the product from isolated saved items toward connected research knowledge.

Primary outcomes:

- the same paper imported from multiple entry points should stop appearing as unrelated duplicates
- article and paper items should be explicitly linked
- insight items should be able to reference paper/article items and projects
- project pages should become actual research containers instead of only a filter dimension

This document is intentionally implementation-oriented and should be treated as the current source of truth for development.

---

## Scope

### In scope

1. Paper duplicate grouping
2. Article to paper relations
3. Insight to item relations
4. Project overview aggregation
5. Relation storage layer in Room

### Out of scope for the first implementation slice

1. vector search
2. semantic graph search
3. automatic weak-match merge confirmation UI
4. cross-project knowledge graph
5. cloud-synced relation editing

---

## Product rules

### 1. Duplicate handling

For `paper` items:

- strong match if `identifier` is equal
- strong match if `dedup_key` is equal
- weak match can be added later using normalized title + year + first author

v0.5 first slice only auto-links strong matches.

Do not physically delete duplicates in the first slice.

Instead:

- keep one canonical item
- keep other entries as source variants
- create a relation pointing duplicate -> canonical

### 2. Article and paper relations

For `article` items:

- use `paperCandidates`
- use `identifier`
- use explicit URLs in `paperCandidates.url`
- use optional title labels only as weaker backup

If a candidate can be mapped to an existing paper, create a relation from article to paper.

### 3. Insight relations

`insight` items can reference one or more paper/article items.

This should be stored as explicit relations, not only inside `meta_json`.

Project association for insight remains primarily through existing `projectId`.

### 4. Project overview

Project overview must show:

- recent items
- key papers
- insight summary list
- relation counts

The first version can be read-only and aggregation-focused.

---

## Relation model

The system needs a dedicated relation table.

Do not store graph edges only in `meta_json`.

### Entity

`ItemRelation`

Fields:

- `id`
- `ownerUserId`
- `fromItemId`
- `toItemId`
- `relationType`
- `confidence`
- `source`
- `createdAt`

### Recommended relation types

- `duplicate_of`
- `article_mentions_paper`
- `article_related_paper`
- `insight_references_item`

Notes:

- `duplicate_of` is directional: duplicate -> canonical
- article relations are directional: article -> paper
- insight reference is directional: insight -> target item

---

## Canonical item rule

For a duplicate paper group:

- canonical item is the oldest item by `createdAt`
- tie-breaker is lexicographically smaller `id`

This gives stable behavior and avoids random canonical selection.

---

## Storage strategy

### Room

Add a new Room table:

- `item_relations`

This becomes the SSOT for explicit links between items.

### meta_json

Continue storing parsing metadata inside `meta_json`, including:

- `identifier`
- `dedup_key`
- `paper_candidates`
- `keywords`
- `domain_tags`
- `topic_tags`

But do not use `meta_json` as the primary graph store.

---

## Auto relation generation

### Trigger points

Auto relation rebuild should happen after:

1. creating a full item
2. updating an item with metadata changes
3. syncing a local item to remote
4. refreshing items from remote if needed later

### Paper duplicate rebuild

For a paper item:

1. load peer papers for the same owner
2. compare by normalized identifier and dedup key
3. choose canonical
4. replace existing outgoing `duplicate_of` relation for this item

Only one outgoing `duplicate_of` relation is needed for a duplicate item.

### Article-paper rebuild

For an article item:

1. read `paper_candidates`
2. match against existing papers by:
   - origin URL
   - identifier extracted from candidate URL
   - candidate label equal to paper title as weaker fallback
3. create outgoing `article_mentions_paper` relations
4. replace previous auto-generated article-paper links for this article

### Insight links

First slice:

- support manual replacement API
- no automatic insight suggestion yet

---

## Manual relation editing

The repository layer should support replacing manual insight links.

API behavior:

- caller sends `insightId + targetItemIds`
- repository deletes previous `insight_references_item` links created from that insight
- repository inserts the new set

This keeps the later UI simple.

---

## Read model for UI

The UI should not consume raw relation rows directly.

Preferred read model:

`ItemConnection`

Fields:

- `relation`
- `item`

This allows a detail page to render:

- related item title
- relation type badge
- source metadata

without doing extra joins in UI code.

---

## Project overview model

`ProjectOverview`

Fields:

- `project`
- `recentItems`
- `keyPapers`
- `recentInsights`
- `stats`

`ProjectOverviewStats`

Fields:

- `totalItems`
- `paperCount`
- `articleCount`
- `insightCount`
- `duplicateRelationCount`
- `articlePaperRelationCount`

### Selection rules

#### recentItems

- latest 10 items in the project

#### keyPapers

Prefer papers that satisfy more of these:

- starred
- has identifier
- has reading card
- has summary

Return top 5.

#### recentInsights

- latest 10 insight items in the project

---

## UI plan

### Detail page

Later detail page should show:

- duplicate source info for papers
- related papers for articles
- linked items for insights

### Project page

Later project page should show:

- recent additions
- key papers
- insight digest
- relation metrics

This document only defines the data contract and behavior for now.

---

## Repository plan

### New repository

Create a dedicated repository:

- `KnowledgeConnectionRepository`

Responsibilities:

- read item connections
- rebuild auto relations
- replace manual insight links

### Existing repository extensions

`ProjectRepository` should gain:

- `getProjectOverview(projectId)`

This is enough for the first data-backed project page.

---

## Testing plan

First slice needs unit coverage for:

1. paper duplicate canonical selection
2. article-paper candidate matching
3. relation replacement semantics
4. project overview aggregation

Pure matching logic should be extracted into deterministic helper code for easier tests.

---

## Implementation order

### Phase 1

1. add relation domain model
2. add Room relation entity and DAO
3. add relation repository
4. add project overview read model

### Phase 2

1. rebuild duplicate relations for paper
2. rebuild article-paper relations
3. add manual insight relation API

### Phase 3

1. project overview data query
2. detail page relation data wiring
3. future UI layer

---

## Current development decision

For the active implementation pass:

- build the data foundation first
- keep the first relation types small and explicit
- avoid overloading `meta_json`
- prefer deterministic auto-linking over fuzzy heuristics

This is the reference document to follow unless later product decisions explicitly replace it.
