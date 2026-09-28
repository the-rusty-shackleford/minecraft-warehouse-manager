# Release verification — 0.6.0

2026-09-28. Rusty, after buying villages far apart: placing a manager in another village should
pool both inventories, so any manager he owns can pull from all of them; spreading items evenly
would be good redundancy. His call when asked: deposits are balanced (D-0014).

## The rule (JUnit)

`SpreadTest`, 9 tests, partitions at the top: the deposit order over no, one and several
warehouses, the least held first, ties by here, then distance, then id, closed warehouses left
out (the only one, here, a far one); draws under, equal to and over what is held, equal holdings,
the fullest pair, nothing held, the remainder one each by the tie order and not by what was held
before the levelling; 2,000 random cases against taking one at a time; bad inputs. 92 JUnit in
all, none failing.

## The network (GameTests)

The far warehouse is a stone room 2,048 blocks east of each test, built under a forced chunk,
scanned into the owner's network, then let go until `getChunkNow` answers null.

- `aFarWarehouseComesIntoReachWhenTheNetworkIsUsed`: unloaded, it counts nothing (no oak log in
  the index) and counting it loads nothing; `Network.touch` brings its chunk back, not ticking
  (`shouldTickBlocksAt` false); the index counts forty cobblestone as `[here 10, far 30 at its
  manager's x and z]` and the logs only the far chest holds; the near manager is linked with one;
  both broken, the owner's network is empty.
- `drawsAndDepositsLevelTheWarehouses`: a right-click take of twenty cobblestone comes all from
  the far thirty, leaving ten and ten; oak planks clicked at the near table draw the one log from
  the far chest; five sand (twenty near, none far) are deposited far; four cobblestone (ten each)
  stay near.
- `oneRosterCoversEveryWarehouse`: trusted at the far manager, a player may draw at the near one
  (the two answer with the same ownership object); withdrawn through the near panel's toggle, the
  far chests refuse them; the status reads "Linked with 1 other warehouse"; the far manager broken
  leaves one.
- `aRosterFromBeforeTheNetworkFoldsOnce`: a block with a 0.5.x `Trusted` list trusts its player
  through the owner's roster, by name; withdrawn and the old copy loaded again, still withdrawn;
  the block saves no roster.

Server log in the GameTest world: "loading the warehouse at … : 1 chunk(s)", then "… is in reach
… after 6 ms" and "after 80 ms". All 30 GameTests pass (26 before).

## The booth (a real client with EMI)

A second warehouse of the booth player's at 2052, 100, 4, forty cobblestone and nine copper in its
chest, let go until unloaded. The table opens with the room alone in reach (checked on the server
in the tick it opens; the first tally is "23 kinds from 1 warehouse(s)"); the far warehouse is in
reach 66 ms later in the clean build (801 ms in the first run, its data not cached), and the next
second's refresh sends "24 kinds from 2 warehouse(s)". The vanilla book lights the copper block
and EMI counts it (photo 15); EMI's fill puts nine copper in the grid and the far chest is empty
(16). The room's index tooltip over cobblestone reads "104 in your warehouses: 64 here, 40 at
2052, 4", the panel "Click a name to trust or untrust it at all 2 of your warehouses" (17). 96
checks, COMPLETE.

The first clean build failed one booth check, which read the client's tally three ticks after
the table opened and expected the room alone. The log showed the right order: first tally from
one warehouse, the far chunk in reach 48 ms later, the second tally before the client looked. The
check moved to the server, in the tick the table opens.

## Build

`./gradlew clean build` green: 92 JUnit, 30 GameTests, booth 96 checks. Jar
`warehousemanager-0.6.0.jar`, 225236 bytes, sha1 `d388cc1bde86dbc5529250869cba1a5dfa765a47`.
Wire version `"3"`: a 0.5.x client is refused at login until the pack carries 0.6.0.

Not seen live: Rusty's second village, and his friends' use of the one roster.
