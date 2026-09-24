# Release verification — 0.3.2

2026-09-24, late. Rusty: "When I try to craft the Engine from resources in my inventory, it
prefills the recipe with red slot backgrounds and does not let me craft it." Pinned on the box:
the Engine needs an Immersive Aircraft boiler and there was none in their inventory (queried
live) or in any of the twenty warehouse chests or double-chest halves (cobblestone, two pistons
and one blast furnace were there); the pull had reached the recipe (it was newly in their book,
0.3.1's unlock). Vanilla's ghost recipe was the right answer and tints every slot red whichever
part is missing. 0.3.2 says what is short (D-0008).

Full `./gradlew clean build` on the release tree, Xephyr `:7`, llvmpipe, muted:

- JUnit: 51 tests (5 new over `Pooling.shortfall`: covered and empty; the Engine short of one
  boiler, and short of cobblestone, a piston and the boiler in the recipe's order with like
  ingredients counted together; alternatives covered across coal and charcoal and short across
  them, one of each covering two; a shared item going to the earlier ingredient; bad input).
- `runGameTestServer`: 16 real-server GameTests. New: `anUncoverableFillSaysWhatIsShort`: a
  torch at a managed table with four sticks in the chest and nothing else is short of one
  coal-or-charcoal; the message reads "Torch" and "1 × Coal"; the placement places nothing and
  draws nothing; a coal in the inventory covers it.
- `runPhotoBooth` with EMI, Sodium, Iris and Complementary: COMPLETE, unchanged.
- Jar `warehousemanager-0.3.2.jar` sha1 `f37f68a61a21479a9c4d4d24a4df2e04f8fcae7c`
  (139203 bytes).
- Not verified: the chat line on a live client (the GameTest asserts the text the server
  sends, not its arrival); the Engine itself once Rusty has a boiler.
