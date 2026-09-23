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

## 0.2.0 (was 0.1.1)

Rusty's first-evening report: "Food 1/2" and "Food 2/2" ended up apart. The planner now
knows where containers stand and clusters a group's containers and sibling groups
(D-0002). Three planner tests pin it. The same evening a bare dirt hut with no chests did
nothing, as designed; Rusty expected chests to appear. Chest items dropped into the manager
now furnish the building (D-0003): wall spots from the scan, a domain rule for whether one
more container earns its place, a double beside a crowded group. Rusty's stacked chests
then showed two signs each with half wrong: a sign now belongs to a container only when
attached to its faces or directly above it. Last, crafting tables in the building draw on
its containers through the recipe book (D-0004), the mod's first mixins; the version
became 0.2.0.

## Status

Rusty authorized release on 2026-09-22 together with Magical Map 0.2.0. The booth passed
and its five photos were inspected; see
[release verification](../devtools/verification/release-0.1.0.md). Published as 0.1.0 on
GitHub Releases and added to the pack.
