# Warehouse Manager

Version 0.1.0, unreleased. Minecraft 1.21.1, NeoForge 21.1.248, Java 21.

Rusty requested on 2026-09-22 a craftable **Warehouse Manager** block that, placed inside a
building, tracks every chest in that building across its floors, organises their contents by
a heuristic that groups things the way a crafter would look for them, labels each chest with
signs (reusing signs already nearby, two where that reads better), and behaves as a chest itself
whose contents are routed into the right chest. D-0001 records the design and Rusty's calls.

## Shape

- `src/domain` is JDK-only: `Taxonomy` (the group tree), `Classifier` (ordered rules over
  `ItemFacts`), `Planner` (groups to containers), `FloodFill` (incremental, budgeted, sky-aware),
  `SignText` (layout). 27 JUnit tests with partitions at the top of each file.
- `src/main` adapts: `Facts` (stack to facts, cached per item), `ManagerBlockEntity` (scan,
  plan, sort, label, buffer, status), `Signs`, `Transfer`, `Managers` (claims and rescan wake-ups).
- `src/gametest`: six real-server GameTests on a two-floor house, plus the photo booth.

## Open

- The booth walkthrough under Rusty's shaders (sign readability) is the remaining visual check.
- No pack deployment; release waits on Rusty's go.
