# Qt Frontend Skeleton

This directory contains a first-pass Qt/QML frontend scaffold for Arklight.

## Current Scope

- Qt 6 QML application shell
- chat message list
- backend chat API client for `POST /api/chat/send`
- local settings persistence with `QSettings`
- character directory scanning from the project-level `chara` folder
- emotion prefix parsing and portrait switching

## Build

Typical local build flow:

```bash
cmake --preset qt-mingw-debug
cmake --build --preset qt-mingw-debug
```

Requirements:

- Qt 6.5+
- CMake 3.21+

Windows helper:

```bat
qt-frontend\build.bat
qt-frontend\deploy.bat
qt-frontend\run.bat
```

The helper script builds into `qt-frontend\build\qt-frontend` and looks for `qt-cmake.bat` in common `C:\Qt\...` and `E:\QT\...` locations, or uses `%QTCMAKE%` if you set it explicitly.

If Qt is installed in a custom location, you can point the script at it before building:

```bat
set QTCMAKE=C:\path\to\qt-cmake.bat
qt-frontend\build.bat
```

or:

```bat
set QTDIR=C:\Qt\6.7.3\msvc2022_64
qt-frontend\build.bat
```

## Notes

- The app searches upward from the current working directory and executable directory for a `chara` folder.
- The initial UI is intentionally simple. It is meant to establish structure, not to fully recreate the Vue frontend yet.
- The current first-run path works best when the app is launched from the repo root or from a build directory under the repo so the shared `chara` folder can be discovered.
- A minimal sample character is included at `chara/Demo/prompt.txt` so the backend and Qt frontend have at least one valid role to start with.
- In JetBrains Rider, open this folder as a CMake project and select the `qt-mingw-debug` preset. The build runs `windeployqt` after linking so the target can be started directly from the IDE.
- If Rider fails to open the project after a bad import, close Rider and remove the local `qt-frontend\.idea` folder, then reopen `qt-frontend` itself rather than `CMakeLists.txt` or a generated build folder.
