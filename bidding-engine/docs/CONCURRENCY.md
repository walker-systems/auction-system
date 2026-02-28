# Concurrency & Race Conditions

## The Problem
Two users (Alice and Bob) bid \$100 on an item currently at \$90 at the exact same millisecond.

## The Solution: Optimistic Locking
We do not use Java `synchronized` (which only works on one machine). We use Redis `WATCH` / `MULTI` / `EXEC` or versioned CAS.

## Logic
1.  GET auction (Version 1)
2.  Application logic: New Price = $100
3.  Redis: SET auction (Version 2) WHERE Version == 1
4.  If Redis returns "False" (CAS failed), we throw `OptimisticLockingFailureException` and ask the user to retry.
