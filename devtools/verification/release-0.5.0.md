# Release verification — 0.5.0

2026-09-25. Rusty's ask on pack 1.63.0: a rifle whose receivers are themselves craftable from
what the building holds should show as craftable, and the fill should be able to make the
sub-parts. Their calls before the design: the full tree, crafting-table recipes only, the fill
makes the parts (D-0012). Carries 0.4.1 (D-0010, D-0011), never released on its own.

## The rule (JUnit)

`ExpansionTest`, 16 tests, partitions at the top: covered with nothing to make; zero crafts; one
level and depth 0 refusing it; a made item's surplus serving a later cell in either order; several
crafts scaling the need with times rounded up; the rifle at depth 1 (receivers named short) and
depth 2 (six steps, steel before the receiver that needs it, the shared iron counted once, eleven
iron leaving the barrel short); a later alternative made when the earlier cannot be; a rule backed
out cleanly before the next; yield-ascending order (iron and coal before a block is broken); the
ingot-nugget-block cycle terminating; a shared material never counted twice; one kind per cell;
shortages merged in order; `most`; `reachable`; bad input; immutability. 80 JUnit in all.

## The fill (GameTests)

- `aFillMakesTheSubPartsFromTheChests`: a torch at a managed table with one oak log and one coal
  in the chest and no stick anywhere. The click made four planks from the log and four sticks from
  two of them, the grid holds the coal and a stick, the player keeps two planks and three sticks,
  the chest is empty, the stick and planks recipes are unlocked, `ITEM_CRAFTED` counts four of
  each, and the chat line reads "Made 4 × Oak Planks, 4 × Stick for Torch." Server log:
  `2 step(s) planned in 1 ms`, one `pooled step` line per part, then `drew {minecraft:coal=1}`.
- `aShiftClickMakesPartsForEveryCraft`: three coal and two logs; the shift-click placed three
  crafts (three coal, three sticks) from one plank craft and one stick craft, one log left.
- `lockedSubRecipesAreNotMadeUnderLimitedCrafting`: sticks unlocked, planks not: nothing made,
  nothing drawn, the chest untouched (`2 rules` in the log, the book reduced to the unlocked
  ones); planks unlocked: made and filled.
- The 22 earlier GameTests unchanged and green: 25 in all. The rule book on the test server: 889
  rules from 903 crafting recipes in 19 ms, built once.

## The books (booth, with EMI)

With every plank and stick removed from the room and four coal put in, so a torch is two
sub-crafts away:

- The vanilla book lights the torch (and the planks, straight from the logs)
  ([photo](0.5.0/12-book-recursive.png): the filter on, the torch and the stick among the
  craftables with an empty inventory).
- EMI's inventory over the managed table is `Expanded`; `canCraft(torch)` true in 0.04 ms after
  the reachable set (204 items) in 0.6 ms; `getCraftables()` (122 entries, 11 ms) lists the torch,
  whose inputs are nowhere in the room; no input shows missing; the fill is accepted; the grid holds
  the coal and a made stick, the player three sticks and two planks, the chat line
  "Made 4 × Oak Planks, 4 × Stick for Torch." ([photo](0.5.0/13-emi-fill-recursive.png)).
- With `rangedweaponsmod-2.8.0.jar` and `metalsandmaterials-1.0.3.jar` in `run/booth/mods` and
  the steel the receiver steps left taken out of the room: iron, coal, redstone, three planks and
  a stick go in; EMI counts the rifle craftable (0.5 ms; 163 craftables in 9 ms) and lists it; the
  fill is accepted; the server planned six steps in 1 ms and made them: steel ×2 crafts (six),
  the upper receiver, the stock, steel ×1 (three), the lower receiver, the barrel; the grid holds
  the four parts, the result slot the rifle, two steel stay with the player, the chat line
  "Made 9 × Steel Ingot, 1 × Upper Receiver, 1 × Gun Stock, 1 × Lower Receiver, 1 × Gun Barrel
  for Rifle." ([photo](0.5.0/14-emi-fill-rifle.png); the vanilla book behind it now lists the
  rifle too).
- The rule book on the client: 914 rules from 928 crafting recipes (the gun jars in) in 7–10 ms.
- 78 checks, COMPLETE. The first run failed on a check of mine (the planks collection had not been
  unlocked for the booth player; the vanilla book lists unlocked recipes only), not on the code.

## Build

`./gradlew clean build` on Xephyr `:7`, llvmpipe, muted, with EMI and the two gun jars in
`run/booth/mods`: JUnit 80 (64 in 0.4.1 plus the 16 above), GameTests 25 (22 plus the three
above), booth COMPLETE, 78 checks. Jar `warehousemanager-0.5.0.jar` sha1
`13321a5dbf8c0440b3e4d36af00fbd7c7ffe7b02` (197176 bytes).

## Live deployment

Published as v0.5.0 at commit e4c177d and deployed in pack 1.64.0 on 2026-09-25 with Backpacks+
0.4.0, Magical Map 0.3.1 and Schnappviecher 0.1.2, on Rusty's "go". The downloaded release asset
and the installed server jar match the SHA-1 above; 0.4.0 is gone from `/data/mods`. Restart
06:14:11 UTC with nobody on, `Done (2.808s)!` at 06:14:26, "warehousemanager (version 0.4.0 ->
0.5.0)", 20.000 TPS, Mod Hub parity clean. Record: the server repo's
`knowledge/releases/pack-1.64.0.md`.
