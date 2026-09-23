# Release verification — 0.1.0

2026-09-22. Rusty requested the mod and, after the design preview and its four calls
(D-0001), authorized the release together with Magical Map 0.2.0.

- `./gradlew test`: 27 JUnit tests over the JDK-only domain (Taxonomy, Classifier,
  Planner, FloodFill, SignText).
- `./gradlew runGameTestServer`: 7 real-server GameTests on a two-floor stone house with
  seven containers: sorted and labelled across both floors with every item kept, an
  outside chest untouched, buffer deposits routed, an existing sign rewritten rather than
  duplicated, a double chest given title and hint signs, a chest half with no partner
  treated as a single chest, and the buffer spilled on break.
- `./gradlew runPhotoBooth` on Xephyr with llvmpipe: COMPLETE. Inspected
  [the labelled row](0.1.0/warehousemanager-00-labelled-row.png),
  [a sign close up](0.1.0/warehousemanager-01-sign-closeup.png) ("Gear / tools, armor,
  potions", "Food & Farming / food, seeds, crops", "Materials / ores, gems, drops"),
  [the double chest's two signs](0.1.0/warehousemanager-02-double-chest-two-signs.png)
  ("Building" / "blocks to build with"), [the block](0.1.0/warehousemanager-03-manager-block.png)
  and [its chest screen with the status line](0.1.0/warehousemanager-04-manager-screen.png).
  The booth's own log listed each container's contents after settling: the double chest held
  every block, food with food, ores, gems and drops together, the pickaxe under Gear, and no
  item entity anywhere in the room.
- Two earlier booth runs showed items on the floor; they were the previous run's sorted
  chests being cleared when the room was rebuilt on a stale booth save. The world sync is
  now unconditional. The mod never dropped anything.
- `./gradlew clean build -PskipBooth`: green; jar `warehousemanager-0.1.0.jar`
  sha1 `8f5f7685a1b620d114e788a3cc933e8bc13a3818`.
