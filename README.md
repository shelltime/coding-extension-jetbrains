# ShellTime for JetBrains

[![CI](https://github.com/shelltime/coding-extension-jetbrains/actions/workflows/main.yml/badge.svg)](https://github.com/shelltime/coding-extension-jetbrains/actions/workflows/main.yml)
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

Ensure your daemon config at `~/.shelltime/config.yaml` has code tracking enabled:

```yaml
codeTracking:
  enabled: true
```

Or if using `~/.shelltime/config.toml`:

```toml
[codeTracking]
enabled = true
```

### Verify Installation

Check that the daemon is running:

```bash
shelltime daemon status
```

## Installation

### From JetBrains Marketplace

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, PyCharm, etc.)
2. Go to **Settings/Preferences** → **Plugins** → **Marketplace**
3. Search for "ShellTime"
4. Click **Install** and restart your IDE

### Manual Installation

1. Download the latest release from [GitHub Releases](https://github.com/shelltime/coding-extension-jetbrains/releases)
2. Go to **Settings/Preferences** → **Plugins** → **⚙️** → **Install Plugin from Disk...**
3. Select the downloaded ZIP file
4. Restart your IDE

## Plugin Settings

Configure the plugin at **Settings/Preferences** → **Tools** → **ShellTime**:

* **Enable ShellTime tracking** - Enable/disable tracking (default: enabled)
* **Enable debug logging** - Log debug information to IDE logs (default: disabled)
* **Socket path** - Path to the ShellTime daemon socket (default: `/tmp/shelltime.sock`)
* **Heartbeat flush interval** - Interval in milliseconds between heartbeat flushes (default: `120000`)

## Commands

Access commands from **Tools** → **ShellTime**:

* **Show Status** - Display daemon connection status and version info
* **Flush Heartbeats** - Manually flush pending heartbeats to the daemon

## How It Works

The plugin monitors your IDE activity and sends heartbeats to a local daemon:

1. **Event Monitoring** - Tracks file opens, edits, saves, and cursor movements
2. **Debouncing** - Batches events to reduce overhead (max 1 heartbeat per file per 30 seconds)
3. **Periodic Flush** - Sends collected heartbeats to the daemon every 2 minutes
4. **Offline Support** - Queues heartbeats when daemon is unavailable

## Status Bar

The plugin shows its status in the IDE status bar:

- **ShellTime** - Connected and tracking
- **ShellTime (offline)** - Daemon not running (heartbeats queued)

Click the status bar item to view daemon status.

## Supported IDEs

This plugin supports all JetBrains IDEs based on IntelliJ Platform 2024.1+:

- IntelliJ IDEA (Community & Ultimate)
- WebStorm
- PyCharm (Community & Professional)
- GoLand
- PhpStorm
- RubyMine
- CLion
- Rider
- DataGrip
- Android Studio

## Privacy

The plugin communicates only with the local ShellTime daemon via Unix socket. The daemon syncs your coding activity to the ShellTime server for analytics and cross-device access.

## Development

### Building from Source

```bash
# Clone the repository
git clone https://github.com/shelltime/coding-extension-jetbrains.git
cd coding-extension-jetbrains

# Build the plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Run IDE with plugin for testing
./gradlew runIde
```

### Project Structure

```
src/main/kotlin/xyz/shelltime/jetbrains/
├── config/          # Configuration loading and settings
├── heartbeat/       # Heartbeat data models and collection
├── socket/          # Unix socket communication
├── listeners/       # IDE event listeners
├── services/        # Application and project services
├── actions/         # Menu actions
├── ui/              # Status bar widget
└── utils/           # Utility functions
```

## License

See [LICENSE](LICENSE) for details.
