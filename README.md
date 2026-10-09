<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="ShellTime logo" width="96" height="96">
</p>

# ShellTime for JetBrains

[![CI](https://github.com/shelltime/coding-extension-jetbrains/actions/workflows/release.yml/badge.svg)](https://github.com/shelltime/coding-extension-jetbrains/actions/workflows/release.yml)
[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Install%20Plugin-blue?logo=jetbrains)](https://plugins.jetbrains.com/plugin/29657-shelltime)
[![codecov](https://codecov.io/gh/shelltime/coding-extension-jetbrains/graph/badge.svg?token=d7WgY0yRtw)](https://codecov.io/gh/shelltime/coding-extension-jetbrains)

<iframe width="245px" height="48px" src="https://plugins.jetbrains.com/embeddable/install/29657"></iframe>

Track your coding time and productivity across projects with ShellTime. Automatic language detection, project analytics, and detailed activity insights.

<!-- Plugin description -->
## Features

- **Automatic Time Tracking** - Tracks your coding activity in the background without interrupting your workflow
- **Language Detection** - Automatically detects and categorizes time by programming language
- **Project Analytics** - View time spent per project and workspace
<!-- Plugin description end -->

## Prerequisites

This plugin requires the ShellTime CLI and daemon to be running. Follow the steps below to set up.

### Step 1: Install the ShellTime CLI

Run this command in your terminal:

```bash
curl -sSL https://shelltime.xyz/i | bash
```

After installation, reload your shell configuration:

```bash
# For zsh
source ~/.zshrc

# For fish
source ~/.config/fish/config.fish

# For bash
source ~/.bashrc
```

### Step 2: Initialize and Authenticate

Run the initialization command:

```bash
shelltime init
```

This command will:
- Open your browser for authentication
- Install shell hooks for your shell (zsh/fish/bash)
- Start the background daemon service

### Step 3: Enable Code Tracking

The daemon ignores editor heartbeats unless code tracking is enabled. Ensure your daemon config at `~/.shelltime/config.yaml` has:

```yaml
codeTracking:
  enabled: true
```

Or if using `~/.shelltime/config.toml`:

```toml
[codeTracking]
enabled = true
```

The plugin reads the same file when the IDE starts: `codeTracking.enabled` decides whether it tracks, and `socketPath` (if set) tells it where to find the daemon.

### Verify Installation

Check that the daemon is running:

```bash
shelltime daemon status
```

It should report `Code Tracking: enabled` and `Status: Running`. If the daemon is stopped, run `shelltime daemon install`.

## Installation

### From JetBrains Marketplace

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, PyCharm, etc.)
2. Go to **Settings/Preferences** → **Plugins** → **Marketplace**
3. Search for "ShellTime"
4. Click **Install** and restart your IDE

### Manual Installation

1. Download the plugin ZIP from the [Marketplace versions page](https://plugins.jetbrains.com/plugin/29657-shelltime/versions), or build it from source (see [Development](#development)). GitHub Releases carry release notes only, not the ZIP.
2. Go to **Settings/Preferences** → **Plugins** → **⚙️** → **Install Plugin from Disk...**
3. Select the ZIP file
4. Restart your IDE

## Plugin Settings

Configure the plugin at **Settings/Preferences** → **Tools** → **ShellTime**:

* **Enable ShellTime tracking** - Enable/disable tracking (default: `codeTracking.enabled` from the ShellTime config, otherwise enabled)
* **Enable debug logging** - Log debug information to the IDE log (default: disabled)
* **Socket path** - Path to the ShellTime daemon socket (default: `socketPath` from the ShellTime config, otherwise `/tmp/shelltime.sock`)
* **Heartbeat flush interval (ms)** - Time between heartbeat flushes (default: `120000`, i.e. 2 minutes)

Changes apply right away to all open projects; turning tracking off still sends the heartbeats already collected. The settings are kept in memory only, so they return to the defaults above when the IDE restarts.

## Commands

Access commands from **Tools** → **ShellTime**:

* **Show Status** - Display the daemon connection status, version, uptime and platform
* **Flush Heartbeats** - Send pending heartbeats to the daemon now

## How It Works

The plugin monitors your IDE activity and sends heartbeats to a local daemon:

1. **Event Monitoring** - Tracks file opens and tab switches, edits, cursor movements and saves, including in tabs restored when a project opens. Each event is attributed to the project the file is open in.
2. **Debouncing** - Batches events to reduce overhead (max 1 heartbeat per file per 30 seconds). Saves always count; repeated cursor or tab events at the same position are skipped.
3. **Periodic Flush** - Sends collected heartbeats to the daemon every 2 minutes (configurable)
4. **Offline Support** - Keeps heartbeats in memory when the daemon is unavailable and retries on the next flush. Closing a project or the IDE flushes whatever is still pending (waiting at most 2 seconds).

Each heartbeat records the file, project, Git branch (through the bundled Git plugin), language, line count and cursor position, IDE and plugin versions, OS, and machine hostname. The hostname is the same one the ShellTime CLI reports, so IDE, terminal and AI activity on one computer are grouped together. Heartbeats sent while a debugger session is running are categorized as `debugging` instead of `coding`.

Files outside the local file system are not tracked, and neither are paths inside the project under `.git`, `.idea`, `build`, `out`, `target`, `node_modules`, `.gradle`, `vendor` or `__pycache__`. These are matched relative to the project root, so a project that itself lives under e.g. `~/build/` is still tracked.

## CLI Update Check

When a project opens, the plugin asks the daemon for its version and checks it against the ShellTime API. If a newer CLI is available, a notification shows the update command (`curl -sSL <webEndpoint>/i | bash`) with a **Copy Update Command** button, at most once per project per session. The check runs only when the ShellTime config file sets both `apiEndpoint` and `webEndpoint`.

## Status Bar

The plugin shows its status in the IDE status bar:

- **ShellTime** - Connected and tracking
- **ShellTime (offline)** - Daemon not reachable (heartbeats queued)

The state updates on each flush, so it shows offline until the first heartbeats have been sent. Hover over it for the number of pending heartbeats, or click it to view daemon status.

## Supported IDEs

This plugin supports JetBrains IDEs based on IntelliJ Platform 2024.1 through 2025.3 (builds `241`–`253.*`, set by `pluginSinceBuild`/`pluginUntilBuild` in `gradle.properties`):

- IntelliJ IDEA (Community & Ultimate)
- WebStorm
- PyCharm (Community & Professional)
- GoLand
- PhpStorm
- RubyMine
- CLion
- Rider
- RustRover
- DataGrip
- DataSpell
- Android Studio

## Privacy

Heartbeats go only to the local ShellTime daemon via Unix socket. The daemon syncs your coding activity to the ShellTime server for analytics and cross-device access. The only network request the plugin makes itself is the CLI update check, which sends the daemon's version to your configured `apiEndpoint`.

## Development

### Building from Source

Requires JDK 17 or newer (CI uses Java 21). The Gradle wrapper is included.

```bash
# Clone the repository
git clone https://github.com/shelltime/coding-extension-jetbrains.git
cd coding-extension-jetbrains

# Build the plugin (ZIP in build/distributions/)
./gradlew buildPlugin

# Run tests
./gradlew test

# Generate a coverage report (build/reports/kover/html)
./gradlew koverHtmlReport

# Check compatibility against IntelliJ IDEA Community 2024.1, 2024.2 and 2024.3
./gradlew verifyPlugin

# Run IDE with plugin for testing
./gradlew runIde
```

### Continuous Integration

- **Testing** (`.github/workflows/testing.yml`) - On pushes and on pull requests to `main`: builds the plugin, runs the tests and uploads Kover coverage to Codecov.
- **Release** (`.github/workflows/release.yml`) - On pushes to `main`, [Release Please](https://github.com/googleapis/release-please) maintains a release PR. Merging it creates the GitHub release, and the workflow then builds, verifies and publishes the plugin to the JetBrains Marketplace.
- **Claude Code** (`claude.yml`, `claude-code-review.yml`) - Reviews pull requests and responds to `@claude` mentions.

All jobs run on GitHub-hosted `ubuntu-latest` runners. Required secrets and the release process are described in [docs/PUBLISHING.md](docs/PUBLISHING.md).

### Project Structure

```
src/main/kotlin/xyz/shelltime/jetbrains/
├── config/          # Configuration loading and settings
├── heartbeat/       # Heartbeat data models, collection and sending
├── socket/          # Unix socket communication
├── listeners/       # IDE event listeners
├── services/        # Application and project services
├── actions/         # Menu actions
├── ui/              # Status bar widget
├── utils/           # Utility functions
└── version/         # CLI update check
```

## License

See [LICENSE](LICENSE) for details.
