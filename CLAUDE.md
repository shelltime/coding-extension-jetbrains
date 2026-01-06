# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commit Rules

Follow Conventional Commits with scope and module included:

```
fix(heartbeat): fix debounce timing issue
feat(config): add YAML config file support
refactor(socket): improve connection error handling
perf(collector): reduce memory allocation in event processing
docs(readme): update installation instructions
test(utils): add tests for SystemUtils
build(gradle): update IntelliJ Platform plugin version
ci(actions): add code coverage reporting
```

## Build Commands

```bash
./gradlew buildPlugin        # Build the plugin
./gradlew test               # Run all tests
./gradlew runIde             # Run IDE with plugin for testing
./gradlew verifyPlugin       # Verify plugin compatibility
./gradlew koverHtmlReport    # Generate coverage report (outputs to build/reports/kover/html)
```

Run a single test class:
```bash
./gradlew test --tests "xyz.shelltime.jetbrains.utils.SystemUtilsTest"
```

Run a single test method:
```bash
./gradlew test --tests "xyz.shelltime.jetbrains.utils.SystemUtilsTest.testGetMachineId"
```

## Architecture

This is a JetBrains IDE plugin (Kotlin, IntelliJ Platform 2024.1+) that tracks coding activity and sends heartbeats to the ShellTime daemon via Unix socket.

### Service Hierarchy

- **ShellTimeService** (App-level, `@Service(Service.Level.APP)`): Singleton managing global config and the SocketClient instance
- **ShellTimeProjectService** (Project-level, `@Service(Service.Level.PROJECT)`): Per-project instance managing heartbeat collection/sending

### Heartbeat Flow

```
IDE Events (file open/edit/save)
    → FileEditorListener / FileSaveListener (plugin.xml listeners)
    → ShellTimeProjectService
    → HeartbeatCollector (debounces events, max 1 per file per 30s)
    → HeartbeatSender (periodic flush every 2 min)
    → SocketClient (Unix socket to daemon)
```

### Configuration Sources

1. **File config**: `~/.shelltime/config.toml` or `~/.shelltime/config.yaml` (loaded by ConfigLoader)
2. **IDE settings**: Settings → Tools → ShellTime (ShellTimeConfigurable)

### Key Extension Points (plugin.xml)

- `applicationService`: ShellTimeService
- `projectService`: ShellTimeProjectService
- `statusBarWidgetFactory`: Status bar widget showing connection state
- `applicationConfigurable`: Settings panel under Tools
- `applicationListeners`: FileSaveListener
- `projectListeners`: FileEditorListener

### Testing

- JUnit 5 + MockK for mocking
- Kover for coverage
- IntelliJ test framework for integration tests
