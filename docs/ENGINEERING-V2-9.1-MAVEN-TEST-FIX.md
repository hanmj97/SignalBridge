# Engineering V2-9.1: Maven test hotfix

## What failed

The two JUnit tests `CatalogRepositoryV29Test.fixesV28WhitespaceAndSortsByPublicationDate` and `CatalogRepositoryNewestChangeTest.recordsUseMostRecentVersionNotLastPollOrInitialDiscovery` assumed that `r.source_id` is immediately followed by `LEFT JOIN LATERAL` for both search strategies. The `UNION` strategy validly includes a `JOIN (...) matched ON matched.id = r.id` between those clauses. Both tests consequently produced false negatives.

## What changes

Only the two JUnit tests change. They now check the proper join layout for `OR` and `UNION` separately; they still check for LATERAL, published-at based ordering, numeric versions, and absence of missing whitespace or last-seen polling time sorting. No production SQL, back-end runtime code, front-end code, migrations, or user data changes.

## Apply

After backing up the two original test files, unpack this ZIP into the root of the SignalBridge project. Then rerun the normal `docker compose ... up -d --no-deps --build api` command. Do **not** use Maven `-DskipTests` as a workaround. If any tests still fail, review new test output before proceeding.

This patch fixes the identified false-negative JUnit tests only. Database/API runtime correctness still needs to be checked after the build succeeds.
