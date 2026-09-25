# Release verification — 0.4.1

2026-09-25, early. Two of Rusty's reports on pack 1.63.0, one of them misread until the real
recipe was put in the booth.

## The receiver that would not fill (D-0011)

Rusty: a Ranged Weapons Mod receiver, steel in hand and redstone only in the chests, "flashed
red all around" and did not fill; with everything in the inventory it worked. Read as a
mixed-source failure, and reproduced as such first, per Rusty's instruction:

- GameTest `aFillDrawsOnTheInventoryAndTheChestsTogether`: a torch with the coal (a tag
  ingredient, as steel is) in hand and the stick in the chest, then the stick in hand and the
  coal in the chest. **Passed on the shipped code.**
- Booth step through EMI: sticks in hand, redstone in the chests, a redstone torch; EMI counted
  them together, accepted the fill, the grid took one of each
  ([photo](0.4.1/08b-emi-fill-hand-and-chests.png)). **Passed on the shipped code.**
- Booth step, the same item split: five iron in hand, five in the chests, a block of iron;
  nine in the grid ([photo](0.4.1/08c-emi-fill-same-item-split.png)). **Passed.**
- Booth step with the pack's `rangedweaponsmod-2.8.0.jar` and `metalsandmaterials-1.0.3.jar` in
  `run/booth/mods`: the lower receiver, two steel in hand, eight steel and the redstone in the
  chests. EMI counted it craftable and sent the fill; **the grid stayed empty.** The server's
  new fill log line read `1 craft(s) wanted, drew {} (nothing moved)`, and with the inputs
  logged once, the chosen items were `[steel, steel, steel, minecraft:air, redstone,
  minecraft:air]`: vanilla's craftability check answers one item per cell of a shaped pattern,
  air for an empty cell, and `Pooling.pull` counted air as an ingredient needed twice that
  nobody holds, so zero crafts, nothing drawn, vanilla's ghost. The lower receiver is
  `TTT / R`; every recipe the tests had ever used has no gap.

The fix skips the empty cells. Verified:

- GameTest `aRecipeWithGapsInItsPatternFillsFromTheChests`: a bucket (`# # / #`) at a managed
  table fills three iron from the chest, and with one iron in hand draws the other two.
- Booth: the bucket through EMI with one iron in hand, three in the grid
  ([photo](0.4.1/08d-emi-fill-gapped-bucket.png)); the receiver, three steel over a redstone
  ([photo](0.4.1/08e-emi-fill-receiver.png)). The receiver steps run only when the two jars
  stand in `run/booth/mods` (the booth says so in its log when they do not); the bucket step
  always runs.

## Clicking again piles the grid up (D-0010)

Rusty: eight clicks on a recipe that makes eight bullets should leave sixty-four's worth in
the grid, as vanilla's book does from the inventory. `Pooling.wanted` is vanilla's own rule
(`ServerPlaceRecipe.getStackSize`): one craft, one more than the grid holds when it holds the
recipe, the most on a shift-click, capped by the smallest stack the chosen items make.

- JUnit: three new tests in `PoolingTest` (a first click, a held recipe, the stack cap, a
  shift-click, nothing craftable, bad counts). 64 in all.
- GameTest `clickingAgainPilesTheGridUpFromTheChests`: sticks with sixteen planks in the chest,
  three clicks leave two stacks of one, two and three planks with the chest debited each time and
  nothing loose in the inventory; a shift-click then piles the remaining sixteen on and empties
  the chest.
- Booth: a second EMI fill of the bucket draws three more from the chests, two iron on each of
  the three cells ([photo](0.4.1/08d2-emi-fill-bucket-twice.png)); the server logged `2 craft(s)
  wanted, drew {minecraft:iron_ingot=3}`.

## Also

- The Insert slot's sink sends the player a fresh index at once. The 0.4.0 booth's "the index
  has the cobblestone back" check had raced the one-second refresh and failed on the first run
  of this session with `[]`; D-0009 promised a fresh index after every click.
- One INFO line per fill on the server (`pooled fill of <recipe> for <player>: …`), named in the
  README's diagnosing section.

## Build

`./gradlew clean build` on Xephyr `:7`, llvmpipe, muted, with EMI and the two gun jars in
`run/booth/mods`: JUnit 64, GameTests 22 (19 in 0.4.0 plus the three above), booth COMPLETE, 57
checks. Jar `warehousemanager-0.4.1.jar` sha1 `959eeb41ef634a18220a27d47b539f4401623896`
(171271 bytes). Not released; waits on Rusty's go, meant for pack 1.64.0 with Backpacks+ 0.4.0
and Magical Map 0.3.1.
