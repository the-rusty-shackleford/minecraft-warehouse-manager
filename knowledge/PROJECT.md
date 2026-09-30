# Warehouse Manager

Version 0.8.0, built 2026-09-30. Minecraft 1.21.1, NeoForge 21.1.248, Java 21.

**0.8.0 (2026-09-30)**: your other warehouses load while you, or a player you trust, stand in one
of your buildings ([D-0016](decisions/D-0016.md)), so a screen or a table opened there counts
them at once. Rusty asked why the network does not keep a ledger of far contents and debit it
instead of fetching from the chests; D-0016 records the answer (it can be made to conserve
items only with a reconcile at chunk load and a sequence number in the chunk, it is not much
cheaper for the server, and a missed hook mints or destroys items silently) and this rule as
the cheap part of the win. `Network.warm`, once a second from the manager's tick; the arrival
log moved into `touch` too. New GameTest (a stranger inside and the owner outside load nothing,
the same player trusted does), checked against three mutations; the D-0014 tests stand the owner
outside while the far chunk unloads; the booth walks the player in from the roof and the table's
first tally counts the far copper. Also fixed: the build named `backpacksplus-0.6.0.jar`, gone
since that repo built 0.7.0, so the bag GameTest had silently lost its bag; it names 0.7.0 now
and the runs stop if the jar is missing. **Released 2026-09-30 and deployed in pack 1.70.0** with
Vanilla Wheels 1.10.1: 92 JUnit, 32 GameTests and the booth (109 checks) green in the release gate,
sha1 `25f1e685` on the server (the server repo's `knowledge/releases/pack-1.70.0.md`). Right
after, Rusty's first manager (-1570, 68, -355), which had not loaded since 0.6.0 and so was not in
his network, was force-loaded for a minute with nobody online: its 0.5.x roster folded (WAXER_01,
Bobandy_, OtatopMalloy) and it joined with 20 containers beside the new one's 3. Not yet seen in
play: the walk-in load on the box.

**0.7.0 (2026-09-28), released 2026-09-29 and deployed in pack 1.68.0** with 0.6.0 (never
released alone), Carried and Backpacks+ 0.6.0; 92 JUnit, 31 GameTests and the booth green in the
release gate, sha1 `1f0be280` on the server, not yet seen in play. 0.7.0: the pooled fill counts, takes and gives what the player carries, their bags included, as
vanilla's placement now does through the Carried protocol ([D-0015](decisions/D-0015.md)). 31
GameTests with Backpacks+ 0.6.0 on the gametest server, green; the new one failed on 0.6.0. The
release gate's booth run, with Backpacks+ on its client, found both mods' EMI table handlers
claiming the front of EMI's list; this handler now stays first and takes its input slots from
Backpacks+'s behind it (D-0015), and the booth ends with a worn bag's sticks counted by EMI and
filled into a stone pickaxe with the chests' cobblestone.

**0.6.0 (2026-09-28), released with 0.7.0 in pack 1.68.0**: one
owner's warehouses are one network ([D-0014](decisions/D-0014.md)). Rusty, after buying villages
far apart: a manager placed in another village should pool both inventories, and spreading items
evenly would be good redundancy; asked where deposits go, he chose balanced. Every owned manager
joins its owner's network (`Network`, overworld saved data: members with their containers and
routes, one roster per owner, the 0.5.x per-manager rosters folded once each). The index, the
book, EMI and the fill read every member in reach; a deposit goes to the warehouse holding the
least of its kind, a draw levels from the most (`domain.Spread`). Far warehouses load only while a
screen or table of the network is open, as full chunks that do not tick (a ticket renewed each
second, a minute's hold), and nothing loads synchronously: `hasChunk` answers true while a chunk
is still loading and a read would then block the server, so reach is `getChunkNow`. Wire "3".
Clean build green: 92 JUnit, 30 GameTests, booth 96 checks
([record](../devtools/verification/release-0.6.0.md)); jar sha1 `d388cc1b…`, 225236 bytes. Not
seen live: Rusty's second village. Ship as a minor pack bump (`--replaces
mods/warehousemanager-0.5.1.jar`).

0.5.1 (2026-09-27), released as pack 1.67.0 (below): Rusty on pack 1.66.0, "I am
creating iron ingots, which it is doing by using iron nuggets, which it is doing by breaking down
other iron ingots." D-0012's cycle guard never had the clicked recipe's result on its path and
only stopped *making*, not *taking from stock*, an item on it ([D-0013](decisions/D-0013.md)).
`Expansion.plan`/`most` now take the result; a cell never takes an item on the chain above its
rule, from stock or by making it, at any depth; a rule may still spend its own result from stock.
Reproduced first (a JUnit case, and a GameTest of the real click that failed on the old behaviour
with "nothing placed: nuggets 9"). Clean build green: 83 JUnit, 26 GameTests, booth 78 checks. Jar
`warehousemanager-0.5.1.jar`, sha1 `522c06ee…`, 197485 bytes. Not seen live: Rusty's click.

0.5.0 (2026-09-25): Rusty asked that a rifle whose receivers are themselves craftable from what
the building holds show as craftable, and that the fill make the sub-parts. Their three calls,
asked before the design: the full tree, crafting-table recipes only, the fill makes the parts
(D-0012). The pure `domain.Expansion` plans a recipe's cells over what is on hand and the rule
book (greedy, one kind per cell for all the crafts of a click as vanilla places them, a cycle
guard, rules by yield ascending, a rule backed out before the next, surplus kept), with `most`
by bisection and a count-free `reachable` pre-pass; 16 JUnit. `Pooled.pull` plans one craft,
then vanilla's count, then makes the steps (draw, take, lay out in the pattern, `matches`,
`assemble`, stat, event, trigger, award, give) before the 0.4.1 path; one log line per step and
a gray chat line naming what was made. The vanilla book gets it through `Craftables` and a mixin
on `RecipeCollection.canCraft`; EMI through `Expanded`, a subclass of its inventory that also
gathers candidates by the reachable items. Three GameTests (a torch from a log and a coal, a
shift-click for three, a locked planks recipe under doLimitedCrafting) and three booth steps
(the vanilla book, EMI's list and fill, the rifle from iron and coal with the gun jars), 78
checks; 80 JUnit, 25 GameTests. Carries 0.4.1, never released on its own
([record](../devtools/verification/release-0.5.0.md)). Released 2026-09-25 as tag v0.5.0 and
deployed as pack 1.64.0 at 06:14 UTC on Rusty's "go", with Backpacks+ 0.4.0, Magical Map 0.3.1
and Schnappviecher 0.1.2; not yet seen by Rusty in play (the rifle from the chests at their
warehouse is the thing to ask about).

0.4.1 (2026-09-25, early): Rusty's receiver ("steel in hand, redstone in the chests, flashed
red all around") was not a mixed-source failure: the two reproductions of that (a GameTest with
a tag ingredient on either side, an EMI booth step) passed, and the receiver itself failed in
the booth with the pack's jar. Vanilla's craftability check answers air for the empty cells of
a shaped pattern, and the fill counted air as an ingredient nobody holds (D-0011): no shaped
recipe with a gap ever drew from the chests since D-0004. Fixed by skipping them; pinned by a
bucket GameTest and a bucket booth step through EMI, plus the receiver in the booth when the
Ranged Weapons Mod and Metals and Materials jars stand in `run/booth/mods`. Also D-0010:
clicking a recipe again piles the grid up, vanilla's own `getStackSize` rule as the pure
`Pooling.wanted` (Rusty's bullets); the Insert slot's sink sends a fresh index at once (the
booth's index step had raced the one-second refresh); one INFO line per fill. 64 JUnit, 22
GameTests, the booth with EMI (57 checks); jar sha1 `959eeb41ef634a18220a27d47b539f4401623896`
(171271 bytes); [record](../devtools/verification/release-0.4.1.md). Shipped inside 0.5.0
(pack 1.64.0), never tagged on its own.

0.4.0 (2026-09-24, late): the manager's screen is the warehouse's index (D-0009): every kind
the building holds under its taxonomy heading with its total, a search box (name, group,
`@mod`), chest-slot taking (left a stack to the cursor, right half, shift to the inventory, a
loaded cursor inserts), and one Insert slot that sinks into the buffer. Rusty's three calls
are in D-0009; the trie they floated is rejected there with the reason. Domain `Index` and
`Take` (JUnit), server `Index` (payloads, tally, pick, refresh every second),
`ManagerBlockEntity.pull`, `ManagerMenu` rewritten over one slot, `ManagerScreen` redrawn.
Three GameTests and four booth photos. Released as tag v0.4.0 (0.3.2 inside it, never
tagged on its own) and deployed as pack 1.63.0 at 22:12 UTC on Rusty's "release it"; not yet
seen by Rusty in play.

0.3.2 (2026-09-24, late): Rusty's Engine prefilled all red and would not craft; the Engine
needs a boiler and there was none anywhere, so the game's ghost was right but said nothing.
A fill the inventory, grid and chests cannot cover now sends one chat line naming what is short
("Can't fill Engine from here: short of 1 × Boiler."), from the domain's `Pooling.shortfall`
(D-0008). JUnit and a GameTest (torch without coal); 16 GameTests.

0.3.1 (2026-09-24 evening): Rusty's propeller (five iron ingots, Immersive Aircraft) did not
fill from the chests. Vanilla's recipe placement silently refuses a recipe the player has not
unlocked, Immersive Aircraft ships no unlock advancements, and every test had awarded the stick
recipe first. The pooled fill now unlocks the recipe at the fill, as crafting it by hand would,
except under doLimitedCrafting (D-0007). GameTest added; 15 GameTests. Released as tag v0.3.1
and deployed as pack 1.62.2 at 20:51 UTC on Rusty's "Go, 2 min warning"; the propeller itself
not yet seen filling on a live client.

Rusty requested on 2026-09-22 a craftable **Warehouse Manager** block that, placed inside a
building, tracks every chest in that building across its floors, organises their contents by
a heuristic that groups things the way a crafter would look for them, labels each chest with
signs (reusing signs already nearby, two where that reads better), and behaves as a chest itself
whose contents are routed into the right chest. D-0001 records the design and Rusty's calls.

## Shape

- `src/domain` is JDK-only: `Taxonomy` (the group tree), `Classifier` (ordered rules over
  `ItemFacts`), `Planner` (groups to containers), `FloodFill` (incremental, budgeted, sky-aware),
  `SignText` (layout), `Pooling` (what a recipe click draws), `Expansion` (what the table can
  make in steps: rules, plans, the most, the reachable), `Access` (owner, trusted, stranger; which
  actions each tier gets), `Spread` (which warehouse of a network a deposit goes to, how a draw
  levels them). 92 JUnit tests with partitions at the top of each file.
- `src/main` adapts: `Facts` (stack to facts, cached per item), `ManagerBlockEntity` (scan,
  plan, sort, label, buffer, status, ownership and roster, the synchronous placement walk),
  `ManagerBlock` (placement refusal, placer owns, claim by sneak-click), `Signs`, `Transfer`,
  `Managers` (loaded managers, wake-ups, claiming through `Claims`), `Claims` (saved data:
  container to manager), `Guard` (the refusals through NeoForge events and the blast
  protection), `ManagerMenu` and `Roster` (the trust panel's menu and wire), `Config`
  (`operators_bypass`), `Network` (an owner's warehouses and roster in saved data, the sites in
  reach, the tickets that load them, counting and levelled taking across them), `Pooled`
  (crafting from the chests, the rule book, the steps, the tally re-sent as warehouses come into
  reach), the EMI
  plugin (the handler and `Expanded`), five mixins (recipe placement, the crafting table's
  position, container validity for trust withdrawal, the recipe book's tally, the collection's
  craftable set), `client/Craftables` and `client/ManagerScreen`.
- `src/gametest`: thirty-two real-server GameTests on a two-floor house (five with a second
  warehouse 2,048 blocks away, left to unload), plus the photo booth. A far test that lets the
  far chunk unload stands the owner outside the building, since standing inside keeps it loaded
  (D-0016).

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

## Published and deployed — 2026-09-28, pack 1.67.0

Version 0.5.1 is [published](https://github.com/the-rusty-shackleford/minecraft-warehouse-manager/releases/tag/v0.5.1)
(asset SHA-1 `522c06ee1adff90bd2dfa236a9da7055e9b9f163`, matching the clean-built jar) and deployed through Mod Hub in pack
**1.67.0**, replacing 0.5.0, on Rusty's "release with everything else after a 5 minute server
warning": restart 01:13:36 UTC at the end of the warning with nobody on, `Done` at 01:13:52,
"(0.5.0 -> 0.5.1)" in the log, 36 baseline errors, 20 TPS, parity clean. The server repo's
`knowledge/releases/pack-1.67.0.md` has the deployment. Not yet seen in play by Rusty.
