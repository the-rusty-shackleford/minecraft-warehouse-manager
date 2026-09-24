# Release verification — 0.3.1

2026-09-24 evening. Rusty, at a warehouse table on pack 1.62.1: "I just tried crafting a
propeller which only needs iron, and I have iron in my chests but it did not even pre fill the
recipe." Cause pinned on the box (D-0007): the propeller (five vanilla iron ingots, Immersive
Aircraft) was not in their recipe book, that mod ships no unlock advancements at all, and
vanilla's recipe placement refuses a recipe the book lacks, silently; `Pooled.pull` carried the
same gate. Every test had awarded the stick recipe first. The pooled fill now unlocks the recipe
at the fill, as crafting it by hand would, except under `doLimitedCrafting`.

Full `./gradlew clean build` on the release tree, Xephyr `:7`, llvmpipe, muted:

- JUnit: 46 tests, unchanged.
- `runGameTestServer`: 15 real-server GameTests. New:
  `craftingTableFillsARecipeThePlayerHasNotUnlocked`: a fresh mock player with the stick
  recipe locked, at a managed table with planks in the chest, gets two planks placed from the
  chest (debited to 14) and holds the recipe afterwards; with `doLimitedCrafting` on, a second
  fresh player gets nothing placed, nothing drawn, and stays locked. On 0.3.0's code the first
  assertion is the live defect: the gate returned before anything was drawn.
- `runPhotoBooth` with EMI, Sodium, Iris and Complementary: COMPLETE, unchanged (the sticks
  it fills are awarded first; the locked path is the GameTest's).
- Jar `warehousemanager-0.3.1.jar` sha1 `ca3c0d59d8a5206b30f2704f7c8899a8b4a3fa9a`
  (135856 bytes).
- Not verified: Rusty's propeller itself on the live server (Immersive Aircraft is not in the
  booth); the fix is on the same path with the same gate.
