# Publishing Guide

This document describes how to set up publishing for the ShellTime JetBrains plugin.

## GitHub Repository Secrets

Add the following secrets to your GitHub repository at:
**Settings → Secrets and variables → Actions → New repository secret**

### Required for JetBrains Marketplace Publishing

| Secret Name | Description | How to Get |
|-------------|-------------|------------|
| `JETBRAINS_MARKETPLACE_TOKEN` | Plugin repository upload token | [JetBrains Marketplace](https://plugins.jetbrains.com/author/me/tokens) |
| `CERTIFICATE_CHAIN` | Plugin signing certificate chain | See [Plugin Signing](#plugin-signing) |
| `PRIVATE_KEY` | Plugin signing private key | See [Plugin Signing](#plugin-signing) |
| `PRIVATE_KEY_PASSWORD` | Password for private key | Your chosen password |

### Required for Code Coverage

| Secret Name | Description | How to Get |
|-------------|-------------|------------|
| `CODECOV_TOKEN` | Codecov upload token | [Codecov Dashboard](https://app.codecov.io/) |

### Required for Claude Code Integration

| Secret Name | Description | How to Get |
|-------------|-------------|------------|
| `ANTHROPIC_API_KEY` | Anthropic API key for Claude | [Anthropic Console](https://console.anthropic.com/) |

---

## JetBrains Marketplace Token

1. Go to [JetBrains Marketplace](https://plugins.jetbrains.com/)
2. Sign in with your JetBrains Account
3. Go to your profile → **My Tokens** or directly to https://plugins.jetbrains.com/author/me/tokens
4. Click **Generate Token**
5. Give it a name (e.g., "GitHub Actions")
6. Copy the token and add it as `JETBRAINS_MARKETPLACE_TOKEN` secret

---

## Plugin Signing

JetBrains requires plugins to be signed for distribution. You need to generate a certificate.

### Option 1: Generate Self-Signed Certificate (Development)

```bash
# Generate private key
openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096

# Generate certificate signing request
openssl req -new -key private.pem -out request.csr

# Generate self-signed certificate (valid for 1 year)
openssl x509 -req -days 365 -in request.csr -signkey private.pem -out chain.crt

# Encrypt private key with password
openssl pkcs8 -topk8 -inform PEM -outform PEM -in private.pem -out private_encrypted.pem
```

### Option 2: Use JetBrains Marketplace Certificate (Recommended for Production)

1. Go to [JetBrains Marketplace](https://plugins.jetbrains.com/)
2. Navigate to your plugin page
3. Request a certificate from JetBrains (for verified publishers)

### Setting Up Secrets

After generating certificates:

1. **CERTIFICATE_CHAIN**: Contents of `chain.crt`
   ```
   -----BEGIN CERTIFICATE-----
   MIIFazCCA1OgAwIBAgIUe...
   -----END CERTIFICATE-----
   ```

2. **PRIVATE_KEY**: Contents of `private_encrypted.pem`
   ```
   -----BEGIN ENCRYPTED PRIVATE KEY-----
   MIIJrTBXBgkqhkiG9w0BBQ...
   -----END ENCRYPTED PRIVATE KEY-----
   ```

3. **PRIVATE_KEY_PASSWORD**: The password you used to encrypt the private key

---

## Codecov Setup

1. Go to [Codecov](https://app.codecov.io/)
2. Sign in with GitHub
3. Add your repository
4. Copy the upload token from the repository settings
5. Add it as `CODECOV_TOKEN` secret

---

## Release Process

The CI/CD pipeline uses [Release Please](https://github.com/googleapis/release-please) for automated releases:

1. **Automatic Version Bumping**: Based on conventional commits
   - `feat:` → minor version bump
   - `fix:` → patch version bump
   - `feat!:` or `BREAKING CHANGE:` → major version bump

2. **Release Flow**:
   - Push to `main` branch triggers CI
   - Release Please creates/updates a release PR
   - Merging the release PR triggers:
     - Version bump in `gradle.properties`
     - CHANGELOG.md update
     - GitHub Release creation
     - Plugin published to JetBrains Marketplace

### Manual Release

To trigger a release manually:

```bash
# Build the plugin
./gradlew buildPlugin

# The ZIP file will be at:
# build/distributions/shelltime-jetbrains-{version}.zip

# Publish (requires PUBLISH_TOKEN environment variable)
PUBLISH_TOKEN=your_token ./gradlew publishPlugin
```

---

## First-Time Plugin Submission

Before automated publishing works, you need to submit the plugin manually:

1. Build the plugin: `./gradlew buildPlugin`
2. Go to [JetBrains Marketplace](https://plugins.jetbrains.com/)
3. Click **Upload plugin**
4. Upload `build/distributions/shelltime-jetbrains-0.0.1.zip`
5. Fill in plugin details:
   - **Name**: ShellTime
   - **Vendor**: ShellTime (or your organization)
   - **Category**: Productivity
   - **Tags**: time tracking, productivity, analytics
6. Submit for review

After approval, automated publishing via CI/CD will work.

---

## Verification

After setting up secrets, verify by:

1. Push a commit to `main`
2. Check GitHub Actions → CI workflow
3. Verify all jobs pass:
   - ✅ Build
   - ✅ Test
   - ✅ Coverage upload
   - ✅ Release Please (creates PR)

When you merge the release PR:
- ✅ Publish job runs
- ✅ Plugin uploaded to JetBrains Marketplace
