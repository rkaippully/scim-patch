# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Check for "value" or "path" key in add patch operations

## [0.2.2] — 2020-09-18

### Changed
- Removed attr path to from exception msg but added :path to exception #12 (contributed by @Quezion)
- Bugfix to prevent input vectors from being cast to lists in some nested add operations #11 (contributed by @smithtim)

## [0.2.1] — 2020-07-15
### Changed
- Fix issue with "add" when no path is provided #10 (Contributed by @smithtim)

## [0.2.0] — 2019-07-29
### Added
- Allow skipping patches on some schemas (Fixes #6)

## [0.1.0] — 2019-05-31
### Added
- Parse SCIM paths and use it for patching
- Support add, remove, and replace operations

[0.1.0]: https://github.com/rkaippully/scim-patch/compare/0.0.0...0.1.0
[0.2.0]: https://github.com/rkaippully/scim-patch/compare/0.1.0...0.2.0
[0.2.1]: https://github.com/rkaippully/scim-patch/compare/0.2.0...0.2.1
[0.2.2]: https://github.com/rkaippully/scim-patch/compare/0.2.1...0.2.2
[Unreleased]: https://github.com/rkaippully/scim-patch/compare/0.2.2...HEAD
