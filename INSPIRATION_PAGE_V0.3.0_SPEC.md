# Inspiration Page v0.3.0 Addendum

## 1. Scope
This addendum documents the new inspiration-page-related contracts introduced in `v0.3.0`.

It supplements existing project docs such as:

- [README.md](D:/Android-mobile-terminal/README.md)
- [Gist.md](D:/Android-mobile-terminal/Gist.md)
- [CLOUD_WEB_API.md](D:/Android-mobile-terminal/CLOUD_WEB_API.md)

## 2. Product Behavior

The old `home` tab is now rendered as the inspiration page.

Current page behavior:

- Bottom nav label: `灵感`
- Left top action: `显示已读 / 隐藏已读`
- Right top action: search toggle
- Page-level create entry below search action
- Inspiration cards support:
  - open detail
  - quick edit
  - toggle read/unread

## 3. Inspiration Item Model

An inspiration item is stored using existing `INSIGHT` type and can contain four modules:

- `title`
- `body`
- `image`
- `audio`

Rules:

- `title` is required
- `body` is optional
- `image` is optional
- `audio` is optional

## 4. Persistence Mapping

Current persistence mapping:

- `type = insight`
- `title`
  - inspiration title
- `summary`
  - preview text
- `content_md`
  - inspiration body
- `origin_url`
  - image attachment URI for inspiration items
- `audio_url`
  - raw audio attachment URI for inspiration items
- `read_status`
  - `unread` or `read`
- `meta_json`
  - structured inspiration payload

## 5. `meta_json` Contract for `INSIGHT`

```json
{
  "source": "灵感",
  "body": "用户手动填写的正文",
  "tags": ["idea", "product"],
  "image_uri": "content://...",
  "audio_uri": "content://...",
  "audio_duration": 37,
  "has_image": true,
  "has_audio": true
}
```

Field notes:

- `body`
  - mirrored body field for WebView parsing
- `tags`
  - custom tags array
- `image_uri`
  - mirrored image URI
- `audio_uri`
  - mirrored audio URI
- `audio_duration`
  - raw audio duration in seconds
- `has_image`
  - convenience flag for UI
- `has_audio`
  - convenience flag for UI

## 6. Main WebView Bridge

### 6.1 Web -> Android

Methods exposed by `MainAppInterface`:

- `pickInsightImage()`
  - opens the system image picker for inspiration draft
- `recordInsightAudio()`
  - opens the system sound recorder for inspiration draft
- `saveInsight(payloadJson)`
  - creates or updates an inspiration item
- `updateInsightReadStatus(itemId, readStatus)`
  - toggles `read/unread`
- `navigateToDetail(itemId)`
  - opens Compose detail page

### 6.2 `saveInsight(payloadJson)` payload

```json
{
  "id": "string|null",
  "title": "string",
  "body": "string",
  "imageUri": "string|null",
  "audioUri": "string|null",
  "audioDurationSeconds": 37,
  "tags": ["string"],
  "readStatus": "unread|read"
}
```

### 6.3 Android -> Web events

Events dispatched back to `main_ui.html`:

- `insight-image-picked`
  - `detail.uri`
  - `detail.name`
- `insight-audio-recorded`
  - `detail.uri`
  - `detail.name`
  - `detail.durationSeconds`
- `insight-save-result`
  - `detail.success`
  - `detail.itemId`
  - `detail.message`
- `insight-read-status-updated`
  - `detail.success`
  - `detail.itemId`
  - `detail.readStatus`
  - `detail.message`

## 7. Detail Page Behavior

Clicking an inspiration card now opens Compose `DetailScreen`.

Current `INSIGHT` detail capabilities:

- display title
- display created time
- display tags
- display body
- display image preview
- tap image to enter fullscreen preview
- display raw audio player
- play / pause raw audio
- show playback progress and duration

## 8. File Ownership

Primary files involved in this change:

- [main_ui.html](D:/Android-mobile-terminal/app/src/main/assets/main_ui.html)
- [MainScreen.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/main/MainScreen.kt)
- [MainViewModel.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/main/MainViewModel.kt)
- [ItemRepository.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/domain/repository/ItemRepository.kt)
- [ItemRepositoryImpl.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/repository/ItemRepositoryImpl.kt)
- [DetailScreen.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/detail/DetailScreen.kt)

## 9. Current Limits

- image detail preview does not yet support pinch zoom
- raw audio player does not yet support seek dragging
- inspiration audio is stored as raw audio only
- one inspiration item currently supports one image URI and one audio URI
