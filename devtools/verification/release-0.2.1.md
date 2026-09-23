# Release verification — 0.2.1

2026-09-23. Rusty, at a warehouse crafting table on pack 1.56.0: "clicking the book icon and
nothing shows up; it is clearly not pulling from my pooled chest inventories". The server log
showed the tally going out ("Jdrum12 opened a table in the building at -1570, 68, -355:
sending 66 kinds"); the pack's EMI takes the recipe book button for its own craftables view,
which counts only what its first handler for the menu reports (D-0005).

- `./gradlew test`: 39 JUnit tests, unchanged (the fix is client glue over D-0004's server
  path, which the existing tests cover).
- `./gradlew runGameTestServer`: 10 real-server GameTests, unchanged, now with EMI on the
  server's classpath as the pack has it.
- `./gradlew runPhotoBooth` with EMI loaded, on an iconified Xephyr, muted: COMPLETE. The
  existing recipe-book scene still passes (sticks craftable from the building's planks with an
  empty inventory; the placement draws two planks from the chests). A new scene reopens the
  table with an empty inventory and asserts, through EMI's own registry and API, that the
  warehouse handler stands first for the crafting table, that `EmiPlayerInventory.of(player)`
  can craft sticks, that `getFirstValidHandler` picks the warehouse handler, that
  `performFill` is accepted, and that the grid then holds two planks drawn from the chests.
  Inspected: [EMI fill from the chests](0.2.1/08-emi-fill-from-chests.png): inventory empty,
  two planks in the grid, four sticks offered, EMI's craftables column listing what the
  building's planks make.
- `./gradlew build -PskipBooth -PskipGameTests` after the runs above: green; jar
  `warehousemanager-0.2.1.jar` sha1 `df9224974f4ccc010c90ab7eb0cd7e965dbbe0fb` (101508 bytes).
  It ships the plugin's two classes and no EMI class; EMI is `compileOnly`.
- Not verified: Rusty's own client against the live server, which is the only place the
  original report exists. The booth reproduces the mechanism (EMI first, chests invisible)
  and the fix, with the same EMI build the pack ships.
