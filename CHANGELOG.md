# Changelog

All notable changes to this project will be documented in this file.

## [0.0.3](https://github.com/shelltime/coding-extension-jetbrains/compare/v0.0.2...v0.0.3) (2026-01-06)


### Bug Fixes

* **ci:** add GITHUB_TOKEN to checkout action for self-hosted runner ([669ea22](https://github.com/shelltime/coding-extension-jetbrains/commit/669ea22808959537b59e6ad5b00f549f90bd2749))
* **ci:** add GITHUB_TOKEN to checkout action for self-hosted runner ([aacb473](https://github.com/shelltime/coding-extension-jetbrains/commit/aacb4738b858dcc940f88bdb882f9c23f5722ee6))
* **ci:** add persist-credentials false to checkout action ([a0e7da7](https://github.com/shelltime/coding-extension-jetbrains/commit/a0e7da7b582cb18860fceab23cf3f564ee19161f))

## [0.0.2](https://github.com/shelltime/coding-extension-jetbrains/compare/v0.0.1...v0.0.2) (2026-01-06)


### Features

* **plugin:** initial implementation of ShellTime JetBrains plugin ([b7b2273](https://github.com/shelltime/coding-extension-jetbrains/commit/b7b2273406973292b8aa425ec6f334245bc79053))


### Bug Fixes

* **build:** use explicit IDE versions in pluginVerification ([082749a](https://github.com/shelltime/coding-extension-jetbrains/commit/082749a6cba36eaf96ab797589273e55880caab1))


### Documentation

* **claude:** improve CLAUDE.md with architecture and build details ([6a0b692](https://github.com/shelltime/coding-extension-jetbrains/commit/6a0b69235da9937f212e923b738fa1ea42897b09))
* **publishing:** add CI/CD secrets and publishing guide ([989d1ec](https://github.com/shelltime/coding-extension-jetbrains/commit/989d1ecd9d6e938981c18be0cb099786a7f0cd78))


### Continuous Integration

* **actions:** change runner to jp-arm-oracle self-hosted runner ([d07cf9a](https://github.com/shelltime/coding-extension-jetbrains/commit/d07cf9acb6f56d3412590b589185f78cdb354fb2))
* **actions:** restructure workflows to run release-please first ([d69d1d1](https://github.com/shelltime/coding-extension-jetbrains/commit/d69d1d1445c2aef9377cf8547bb23aabec1fbf32))
* **actions:** restructure workflows to run release-please first ([8a3bbe2](https://github.com/shelltime/coding-extension-jetbrains/commit/8a3bbe2ce1bce265e96d8bac664737d20636fcc9))
* **actions:** upgrade GitHub Actions to latest versions ([e81fcf4](https://github.com/shelltime/coding-extension-jetbrains/commit/e81fcf4b3ad29b3134a8a683db11e33fc987b0e2))

## [Unreleased]

### Added
- Initial release
- Automatic coding time tracking
- Language detection
- Project analytics
- Git integration (branch tracking)
- Debug session awareness
- Offline support (heartbeat queuing)
- Status bar widget
- Unix socket communication with ShellTime daemon
