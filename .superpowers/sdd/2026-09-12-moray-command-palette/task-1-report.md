# Task 1 report: Register commands and rank bounded results

## What was implemented

- Added the package-private `Command` record with validated IDs, required Swing action titles, immutable keyword metadata, and separate palette title/icon properties.
- Added the EDT-owned `CommandRegistry` with duplicate-ID rejection, identity-safe registrations, immutable indexed entries, safe copied listener notification, action property reindexing, idempotent subscriptions, and listener cleanup on close.
- Added bounded `CommandSearch` ranking with normalized Unicode-aware text, exact/prefix/word-prefix/substring/keyword/fuzzy tiers, worst-tier and summed-gap aggregation for multiword queries, recency tie-breaking, enabled-action filtering, and a five-result limit.

## TDD evidence

### RED

Ran:

```text
./gradlew :moray-app:test --tests '*CommandRegistryTest' --tests '*CommandSearchTest'
```

The build failed at test compilation because `CommandRegistry`, `CommandSearch`, and `Command` did not exist. The compiler reported six expected missing-type/symbol errors in the two newly added behavior tests. This established that the tests exercised the new API rather than existing behavior.

### GREEN

After implementing the three production types, the focused command passed. After extending the tests for the specified lifecycle, metadata, ranking, normalization, availability, and immutability behaviors, the focused suite passed 13/13. The full app suite then passed 302/302 with zero failures, errors, or skips.

Additional checks passed:

```text
git diff --check
source hygiene scan for moray-app/src/**/*.java
```

Both were clean.

## Files changed

- `moray-app/src/main/java/dev/moray/app/Command.java`
- `moray-app/src/main/java/dev/moray/app/CommandRegistry.java`
- `moray-app/src/main/java/dev/moray/app/CommandSearch.java`
- `moray-app/src/test/java/dev/moray/app/CommandRegistryTest.java`
- `moray-app/src/test/java/dev/moray/app/CommandSearchTest.java`

## Self-review

The implementation follows the exact source recipe in the task brief. Registry reads, writes, subscriptions, action-property indexing and close operations require the EDT. Returned registry entries, command keywords, and search results are immutable. Search work is bounded by the five-result limit and does not access terminal content or the filesystem. No correctness concerns remain.

