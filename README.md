# Warehouse Manager

A block for **Minecraft 1.21.1 / NeoForge 21.1.248** that turns a building full of chests into a
labelled warehouse. Place it inside a building and it finds every chest, trapped chest and barrel
under that roof, across every floor, decides which container holds which kind of thing, moves
the contents until each container holds one group, and writes a sign on each one. Open the
manager like a chest, drop anything in, and it goes to the right container on its own.

## Crafting

```
paper       book   paper
iron ingot  chest  iron ingot
paper       redstone paper
```

## What it does when placed

1. **Maps the building.** From the block it walks through every cell you could walk or see
   through (air, stairs, slabs, doors, ladders, the chests themselves) and stops at full solid
   blocks. A cell with nothing between it and the sky counts as a wall, so an open door stops at
   the doorstep rather than spilling into the yard. Stairs, ladders and open trapdoors connect
   floors. The walk is capped at 20,000 cells and spread over ticks, so a hall never spikes.
2. **Finds the containers.** Every block in the tag `warehousemanager:managed` (chest, trapped
   chest, barrel) that the walk reaches is managed. A double chest is one container. A container
   another manager already holds is left to it.
3. **Plans the groups.** Items belong to one of nineteen groups in a small tree (Building: Stone,
   Wood, Earth, Glass & Light, Workstations, Other Blocks; Materials: Ores & Metals, Gems,
   Redstone, Mob Drops, Dyes & Wool; Food & Farming: Food, Crops & Seeds; Gear: Tools & Weapons,
   Armor, Magic; Nature: Plants, Decoration; Misc). With few containers it labels the six top
   groups and folds the smallest into Misc; with spare containers it splits the fullest group into
   its children; a group that outgrows a container takes several ("Stone 1/3"). A container keeps
   its group across rescans while that group still needs it, and a group prefers the container
   that already holds most of it, so little moves. Containers cluster: a group's second
   container is the free one nearest its first, and a group's first stands near its siblings'
   (Stone by Wood by Earth), so "Food 1/2" and "Food 2/2" are neighbours.
4. **Sorts.** At most four stacks move per tick, and a container a player has open is skipped.
   Once a full pass finds nothing to move the manager goes idle and costs nothing per tick.
5. **Labels.** A sign that belongs to a container is rewritten: one attached to its faces, or one
   standing directly above it. A sign beside, below or diagonal to it is somebody else's, so with
   chests stacked in a column the sign between two chests labels the lower one. A container with
   no such sign gets an oak wall sign conjured on its front face, or any free side. A double chest gets two signs: the
   group's name on one, its typical contents on the other. Signs are not consumed from anywhere.

It rescans every ten seconds, and at once when a container or sign is placed or broken inside the
building. Anything you put in the manager's own six-row inventory is routed the same way; what
has no room stays visible there, and right-clicking the block shows why on the action bar.

## Furnishing an empty room

A building with no containers gets none until you provide them: drop chest items (or
trapped chests, or barrels) into the manager and it stands them along the walls, one at a
time, on floor cells backed by a full block with the open side clear and no door beside
them, nearest the manager first, facing into the room. It places a container only while
doing so gains a labelled group or relieves a group over 90 percent full, in which case
two chest items become a double chest beside that group's containers. Each placement is
followed by a rescan, so the new container is labelled and filled like any other. Chest
items it has no use for are stored like any other item. Right-click reports "no free wall
space" when the walls are full.

## Crafting from the building

Open a crafting table that stands inside a managed building and the recipe book counts the
building's chests as yours: with the "craftable" filter on, recipes you could make from the
warehouse light up alongside those you could make from your pockets. Click one and the
manager draws the shortfall out of the chests into your inventory, exactly what that click
needs (one craft, or as many as the building and you together allow for a shift-click),
before the grid fills as usual; crafting then consumes it, so the chests are debited by
what you made. Close the table without crafting and the drawn ingredients stay with you.
Tables outside the building's walls, and the inventory's own 2x2 grid, are unaffected. As
always, the book shows only recipes you have unlocked; the warehouse does not unlock them.

With **EMI** installed, EMI takes over the recipe book button (its default setting turns it
into EMI's own "craftables" toggle). Warehouse Manager registers an EMI handler for the
crafting table, so EMI's craftables view and its fill buttons count the building's chests
too, and a fill draws from them exactly as the vanilla book does. If EMI shows nothing
craftable at a table in a managed building, check the server log for the line
"<player> opened a table in the building at <manager>: sending N kinds": absent, the table
is outside the building (see "What counts as the building"); present, the client is on an
older pack without this handler.

**Existing contents move.** That is the point of placing the block, and it will surprise anyone
sharing the building who did not expect their sorting to change.

## Tuning the groups

Every group has an item tag `warehousemanager:category/<group>` (for example
`warehousemanager:category/building/stone`). A datapack that adds an item to one of these pins
the item to that group ahead of every built-in rule. The container list is the block tag
`warehousemanager:managed`.

## How the classification works

The rules live in `Classifier.java` in the domain layer and run in order: a datapack override tag,
then specific groups before broad ones (redstone before wood so buttons and pressure plates do not
land with planks; mob drops before food so rotten flesh is not a meal; gems before metals so a
diamond block is not an ore), matching on vanilla and `c:` tags, exact ids, and id suffixes such
as `_pickaxe`, `_planks`, `_slab`. An item nothing names falls back on what the game says about
it: edible, armour, tool, potion, block, else Misc. Modded items therefore land sensibly whenever
their mod uses the common tags or the usual naming.

## Diagnosing it

- Right-click the block: "mapping the building", "N containers managed, everything in its
  place", "sorting", "no containers found", or "N stacks waiting, no room in Stone".
- A container is not managed when the walk cannot reach it: a wall in between, a container
  under open sky, or a building past the 20,000-cell cap. Put a roof over it or move the manager
  nearer.
- A container is not labelled when every face is blocked and no sign stands within a block.
- Breaking the manager spills whatever was waiting in it. Signs stay.

## Building

Java 21. `./gradlew test` runs the JDK-only domain tests (classifier, planner, flood fill, sign
layout, taxonomy). `./gradlew runGameTestServer` runs the real-server GameTests (a two-floor house
with seven containers gets sorted and labelled, an outside chest is untouched, the buffer routes,
an existing sign is rewritten, a double chest gets two signs, breaking spills). `./gradlew
runPhotoBooth` opens a client on the booth world for a visual check of the signs. `./gradlew
build` produces `build/libs/warehousemanager-<version>.jar`.

## Status

**0.2.1**: EMI's craftables view and fill buttons count the building's chests (0.2.0 fed only
the vanilla recipe book, which EMI hides behind its own; every player with EMI saw nothing).
**0.2.0**: crafting tables in the building draw on its chests; overflow containers and
sibling groups are placed next to each other (0.1.0 chose them by distance from the manager,
which could put "Food 1/2" and "Food 2/2" at opposite ends of a building); an empty building
is furnished from chest items dropped into the manager; each container keeps only its own
signs (stacked chests had shared one). Download from
[GitHub Releases](https://github.com/the-rusty-shackleford/minecraft-warehouse-manager/releases).
Verified: 39 JUnit tests, 10 real-server GameTests and the photo booth; see
[release verification](devtools/verification/).
