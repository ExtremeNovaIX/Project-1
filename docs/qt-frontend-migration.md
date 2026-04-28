# Qt Frontend Migration Notes

## Goal

Replace the current Vue frontend with a Qt desktop client while keeping the Spring Boot backend and AI/memory pipeline unchanged.

This document focuses on:

- what the current frontend is responsible for
- what must be preserved in the Qt version
- what can be delayed
- how to split the Qt client into modules

## Current System Boundary

The project is already split into two major parts:

- `frontend/arklight-frontend`: Vue 3 + Vite chat client
- `backend`: Spring Boot API, AI orchestration, memory, persistence, vector search

For the Qt migration, the backend can remain the same. The main replacement target is the current web client.

## What The Current Frontend Does

The current frontend is not only a chat page. It handles six responsibilities:

1. Chat state and request flow
2. Local settings persistence
3. Theme selection and visual shell switching
4. Character catalog loading from the `chara` directory
5. Emotion parsing and avatar/expression switching
6. Basic backend connectivity inspection

The main implementation is in:

- `frontend/arklight-frontend/src/App.vue`
- `frontend/arklight-frontend/src/chara/catalog.ts`
- `frontend/arklight-frontend/src/chara/message-parser.ts`
- `frontend/arklight-frontend/src/setting/frontend-settings.ts`
- `frontend/arklight-frontend/src/theme/theme-registry.ts`

## Features To Preserve

These should be considered baseline requirements for the first usable Qt version:

### Required In MVP

- Send chat messages to the backend
- Render user/assistant message list
- Persist local settings
- Configure backend base URL
- Configure `sessionId`
- Choose active character
- Parse emotion prefix in assistant replies
- Switch current character image according to emotion
- Support delayed sentence-by-sentence display of assistant replies

### Can Be Delayed

- Boot animation
- Background particle effects
- Multiple complex visual themes
- In-app backend diagnostic preview page
- High-fidelity web-like animated transitions

### Can Be Simplified

- Theme system can start as one Qt style only
- Settings can begin as a single dialog/page instead of multiple styled panels
- Character installation/import workflow can be simplified into direct local loading

## Backend Contract The Qt Client Must Follow

The core API contract is simple and stable.

### Main Chat API

Endpoint:

- `POST /api/chat/send`

Request body:

```json
{
  "message": "你好",
  "sessionId": "demo-session",
  "characterName": "角色名",
  "shortMode": true
}
```

Server-side DTO:

- `backend/src/main/java/p1/model/ChatRequestDTO.java`

Response:

- JSON string array
- example:

```json
[
  "[happy]你好。",
  "[calm]今天想聊什么？"
]
```

Important behavior:

- when `shortMode=true`, the backend splits one LLM reply into multiple short sentences
- the frontend currently displays those sentences with artificial delays
- the Qt client should keep this interaction model to preserve product feel

### Error Handling Expectations

Current frontend behavior:

- if HTTP fails, show a local error message in chat
- if response body is invalid, show fallback text
- disable repeat send while request is in flight

Qt should keep the same behavior.

## Character And Asset Analysis

This is the biggest non-HTTP migration topic.

### Current Web Behavior

The Vue frontend loads character assets directly from the project-level `chara` directory through Vite aliasing:

- alias: `@project-chara`
- configured in `frontend/arklight-frontend/vite.config.ts`

The frontend expects per-character emotion assets and metadata. The intended shape appears to be:

```text
chara/
  <character-name>/
    prompt.txt
    emotion/
      emotions.json
      <image files>
```

Frontend uses:

- `emotions.json` to determine available emotion names
- matching image files to map emotion -> image URL

Backend uses:

- `prompt.txt` to load the role prompt through `CharacterPromptRegistry`

### Migration Risk

The current repository's `chara` directory is incomplete in this checkout, so the exact real asset set is not present. Before writing the Qt asset loader, we should first confirm the real production character directory structure.

### Recommended Qt Asset Strategy

Preferred order:

1. Keep `chara` as an external folder on disk
2. Let Qt scan that folder at runtime
3. Reuse the same naming rules as the current frontend/backend

Why this is best:

- no backend change required
- role prompt and client-side emotion assets stay in one place
- adding a new character remains a content operation instead of a rebuild

Avoid as first step:

- embedding all character assets into Qt resources (`.qrc`)

That would make content iteration heavier.

## Logic That Should Be Ported 1:1

These frontend rules are lightweight and should be copied into Qt nearly as-is:

### Emotion Prefix Parsing

Current parser behavior:

- detect prefixes like `[happy]你好`
- remove the prefix from displayed text
- expose the parsed emotion token separately

Source:

- `frontend/arklight-frontend/src/chara/message-parser.ts`

Qt equivalent:

- a small parser utility using `QRegularExpression`

### Settings Schema

Current settings fields:

- `themeId`
- `characterName`
- `sessionId`
- `workspaceName`
- `operatorName`
- `bootAnimationEnabled`
- `bootDurationMs`
- `responseDelayMs`
- `moteCount`
- `backendBaseUrl`

Source:

- `frontend/arklight-frontend/src/setting/types.ts`

Qt equivalent:

- `QSettings` backed struct/class

### Reply Scheduling

Current behavior:

- backend returns multiple strings
- frontend schedules them one by one with a delay based on sentence length

Qt equivalent:

- queue reply segments
- use `QTimer::singleShot` or a small scheduler object

## Recommended Qt Module Split

This is the suggested first-pass desktop architecture.

### 1. `ChatClient`

Responsibility:

- send HTTP requests to backend
- parse `POST /api/chat/send` response
- surface network and protocol errors

Suggested Qt pieces:

- `QNetworkAccessManager`
- request/response DTO wrappers

### 2. `SettingsStore`

Responsibility:

- load/save local settings
- provide defaults
- validate settings values

Suggested Qt pieces:

- `QSettings`
- `FrontendSettings` value type

### 3. `CharacterCatalog`

Responsibility:

- scan `chara` directory
- parse `emotions.json`
- discover image files
- build in-memory `CharacterProfile`

Suggested Qt pieces:

- `QDir`
- `QFile`
- `QJsonDocument`
- `QJsonObject`

### 4. `MessageParser`

Responsibility:

- parse emotion prefixes
- extract visible text

Suggested Qt pieces:

- pure utility class
- `QRegularExpression`

### 5. `ChatSessionController`

Responsibility:

- own message list
- mediate UI actions and backend calls
- prevent duplicate sends
- coordinate delayed assistant message playback

This is the main presentation-layer controller.

### 6. `MainWindow` or `ChatPage`

Responsibility:

- render chat UI
- render current character image
- render input area and settings entry

Choice:

- use `QML` if visual polish and animation matter
- use `QWidget` if you want faster basic desktop delivery

For this project, `QML` is the better fit if the current style is important.

## Suggested MVP Scope

To reduce migration risk, the first Qt version should aim for:

- one main chat window
- one settings dialog
- one visual theme
- local disk character loading
- emotion-driven portrait switching
- backend chat request support
- session-based conversation support

Do not make the first milestone responsible for reproducing every web animation.

## Suggested Development Order

1. Freeze the backend contract and verify it with sample requests
2. Confirm the real `chara` asset directory format
3. Define Qt data models for settings, message, character, reply segment
4. Implement `ChatClient`
5. Implement `CharacterCatalog`
6. Implement `SettingsStore`
7. Implement the main chat page
8. Add delayed assistant playback
9. Add expression switching
10. Add optional polish such as animation/theme variants

## Open Questions To Resolve Before Coding Deeply

- Will the Qt client be built with `QML` or `QWidget`?
- Will characters remain filesystem-based, or be packaged into the app?
- Do we want to keep multiple themes, or collapse to one desktop style first?
- Should the backend expose a character list API later, or should the client stay file-driven?

## Recommendation

For this project, the safest migration path is:

- keep the Spring Boot backend unchanged
- build a Qt QML client
- keep character resources on disk in the shared `chara` directory
- target a functional MVP first, not a pixel-perfect recreation of the web UI

This gives the shortest path to a working desktop client while preserving the most important product behavior.
