# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an **IntelliJ Platform Plugin** for IntelliJ IDEA 2025.2+, written in Kotlin, built with Gradle. It targets build 252+ and uses the [IntelliJ Platform Gradle Plugin](https://github.com/JetBrains/gradle-intellij-plugin).

## Common Commands

```bash
# Build the plugin artifact
./gradlew buildPlugin

# Run the plugin in a sandboxed IntelliJ IDE instance
./gradlew runIde

# Run all tests
./gradlew test

# Run a single test
./gradlew test --tests "com.github.fuqiangrepository.unionapiintellijplatformplugin.MyPluginTest.testProjectService"

# Run all checks (tests + Qodana + plugin verifier)
./gradlew check

# Verify plugin compatibility with IntelliJ Plugin Verifier
./gradlew verifyPlugin
```

## Architecture

### Plugin Entry Points (plugin.xml)

- **Plugin ID**: `com.github.fuqiangrepository.unionapiintellijplatformplugin`
- **Tool Window**: `MyToolWindowFactory` — registers a sidebar tool window (`id="MyToolWindow"`)
- **Post-Startup Activity**: `MyProjectActivity` — runs after project opens

### Source Layout

```
src/main/kotlin/.../
├── MyBundle.kt                  # i18n helper wrapping messages/MyBundle.properties
├── services/MyProjectService.kt # Project-scoped service registered in plugin.xml
├── startup/MyProjectActivity.kt # Lifecycle hook (runs on project open)
└── toolWindow/MyToolWindowFactory.kt  # Tool window UI
```

### Services

Project-level services are registered under `<extensions defaultExtensionNs="com.intellij">` in `plugin.xml` with `<projectService>`. Inject them via `project.service<MyProjectService>()`.

### Localization

All UI strings go in `src/main/resources/messages/MyBundle.properties` and are accessed via `MyBundle.message("key")`.

## Key Configuration

- **gradle.properties** — plugin version, group, IntelliJ platform version (`platformVersion`)
- **plugin.xml** — plugin manifest: ID, name, vendor, dependencies, extension registrations
- **CHANGELOG.md** — must be updated with each release (used for marketplace release notes)

## Publishing & Signing

Publishing to JetBrains Marketplace requires these secrets set as environment variables or CI secrets:
- `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` — for plugin signing
- `PUBLISH_TOKEN` — for marketplace deployment
- `CODECOV_TOKEN` — for coverage reporting
