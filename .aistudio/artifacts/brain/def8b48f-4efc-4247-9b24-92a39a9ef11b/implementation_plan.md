# Clean ZIP Export for GitHub Upload

Prepare a clean, production-ready ZIP archive of the entire Android game project (excluding heavy build caches, `.gradle`, and temporary build outputs) so the user can easily upload it to GitHub.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Confirmed Choice 1**: Export clean Android project source code excluding build folders and caches.
> - **Confirmed Choice 2**: Exclude build directories (`.gradle`, `build`, `captures`) to keep repository size minimal and clean.

## 1. Overview & Core Concept

- **What It Does**: Packages all source code, resources (`res/`), Gradle configuration (`build.gradle.kts`, `settings.gradle.kts`), scripts, and Unity export assets into a pristine, lightweight ZIP archive.
- **Target Audience / Persona**: Developer looking to push the project to GitHub without gitignore or large file size issues.
- **Key Value**: Guaranteed clean build state, no corrupted files, and zero unnecessary cache bloat.

## 2. User Experience & Visual Design

- **ZIP Output**: A single clean `.zip` file available for download or export containing the complete game codebase.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1**: Exclude `.gradle`, `build`, and `.kotlin` caches from the ZIP.
  - *Chosen Approach*: Exclude transient build directories.
  - *Why*: Drastically reduces archive size, avoids file lock/permission conflicts, and ensures Gradle builds cleanly when cloned/unzipped on any machine.

## 4. Technical Architecture & Data Strategy

```
Game Repository Archive Structure:
├── app/
│   ├── src/main/java/com/example/ (Kotlin source code & Compose screens)
│   ├── src/main/res/ (Drawables, raw audio OGGs, layouts)
│   └── build.gradle.kts
├── unity_export/ (C# Unity scripts & architecture docs)
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── metadata.json
```
