# Changelog

This project adheres to [Semantic
Versioning](https://semver.org/spec/v2.0.0.html).

## Release 1.1 (2026-08-22)
### Added
- Improved unit test coverage from 69 → 91%. #5
- Added tests for concurrent encoding. #8
- Added tests for automatic mask selection, and a `mutation` Maven profile for running PIT mutation analysis. #9
- Added tests for the Memoizer cache's soft reference and interrupt recovery paths. #10

### Changed
- Replaced the non-functioning FindBugs plugin with SpotBugs, and switched the PMD and Checkstyle ruleset downloads to HTTPS, restoring static analysis reporting. #6

### Fixed
- Fixed ArrayIndexOutOfBoundsException in makeSegmentsOptimally(). #4
- QrCode's public constructor now reports the expected and actual codeword counts when given a wrongly sized array, instead of throwing a bare IllegalArgumentException. #5
- QrSegment's public constructor now copies the supplied data array, so a segment can no longer be altered after it is constructed. #7
- Memoizer.get() now restores the calling thread's interrupt status before rethrowing, so an interrupt is no longer invisible to callers upstream. #11

## Release 1.0 (2023-04-16)

Initial public release.
