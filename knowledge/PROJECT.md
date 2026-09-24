# Warehouse Manager

Version 0.3.0, built 2026-09-24, unreleased. Minecraft 1.21.1, NeoForge 21.1.248, Java 21.

Rusty requested on 2026-09-22 a craftable **Warehouse Manager** block that, placed inside a
building, tracks every chest in that building across its floors, organises their contents by
a heuristic that groups things the way a crafter would look for them, labels each chest with
signs (reusing signs already nearby, two where that reads better), and behaves as a chest itself
whose contents are routed into the right chest. D-0001 records the design and Rusty's calls.

## Shape

- `src/domain` is JDK-only: `Taxonomy` (the group tree), `Classifier` (ordered rules over
  `ItemFacts`), `Planner` (groups to containers), `FloodFill` (incremental, budgeted, sky-aware),
  `SignText` (layout), `Pooling` (what a recipe click draws), `Access` (owner, trusted,
  stranger; which actions each tier gets). 46 JUnit tests with partitions at the top of each file.
- `src/main` adapts: `Facts` (stack to facts, cached per item), `ManagerBlockEntity` (scan,
  plan, sort, label, buffer, status, ownership and roster, the synchronous placement walk),
  `ManagerBlock` (placement refusal, placer owns, claim by sneak-click), `Signs`, `Transfer`,
  `Managers` (loaded managers, wake-ups, claiming through `Claims`), `Claims` (saved data:
  container to manager), `Guard` (the refusals through NeoForge events and the blast
  protection), `ManagerMenu` and `Roster` (the trust panel's menu and wire), `Config`
  (`operators_bypass`), `Pooled` (crafting from the chests), the EMI plugin, three mixins
  (recipe placement, the crafting table's position, container validity for trust withdrawal),
  and `client/ManagerScreen`.
- `src/gametest`: fourteen real-server GameTests on a two-floor house, plus the photo booth.

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

0.2.1 (2026-09-23): Rusty, on pack 1.56.0 at a warehouse table: "clicking the book icon and
nothing shows up". The server had sent the tally; EMI, which the pack ships, cancels the
vanilla book's toggle and counts craftables through the first handler registered for the
menu, its own. An EMI plugin now stands a warehouse handler first for the crafting table
(D-0005); the booth runs with EMI and asserts EMI counts the chests and fills from them.
The 0.2.0 booth ran without EMI, which is how this shipped broken.

## 0.3.0: ownership (D-0006, built 2026-09-24)

Rusty asked that pooled crafting, the manager and its claimed containers be bound to an owner
and a trusted roster toggled from the manager's own screen, that a second manager cannot be
placed into an owned building, and that claims persist. [D-0006](decisions/D-0006.md) holds
the design, Rusty's calls, the rejected alternatives, and the calls made on its three open
points during the build (explosion protection ships for every manager; hoppers stay a
documented limit; the refusal wording). The README's "Who may use it" is the reference.

What the build settled beyond the decision: the open refusal only stops the block from
opening (`setUseBlock(FALSE)`), so an item in hand is still used on the chest as vanilla
does; the trust-withdrawal close rides on `BaseContainerBlockEntity.stillValid`, which the
server checks every tick for every open menu, so nothing tracks who has what open; a manager
in an empty room now reports itself settled (it never did before, its sort pass being the
only place that set the flag); mock players in GameTests are creative, so a refused placement
is asserted on the block, not the stack count.

## Status

0.3.0 verified (46 JUnit, 14 GameTests, the booth with EMI; see
[release verification](../devtools/verification/release-0.3.0.md)) and **released 2026-09-24**
on Rusty's "Looks good, release it" after they vetted the photos: tag v0.3.0, GitHub release,
pack 1.60.0 assembled at 16:21 UTC and deployed at 16:34 UTC with nobody online (server repo
`knowledge/releases/pack-1.60.0.md`). On the box every manager placed before 0.3.0 is unowned
until its owner sneaks and right-clicks it; the restart warning said so. Not yet seen on a live
client: the trust panel against a real second player.

Rusty authorized release of 0.1.0 on 2026-09-22 together with Magical Map 0.2.0, of 0.2.0 on
2026-09-23 as pack 1.56.0 and of 0.2.1 the same night as pack 1.56.2. Booths passed and
their photos were inspected; see [devtools/verification](../devtools/verification/).
