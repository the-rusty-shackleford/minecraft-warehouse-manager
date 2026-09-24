# Release verification — 0.4.0

2026-09-24, late. The manager's screen is the warehouse's index (D-0009): every kind the building
holds under its heading with its total, a search box, chest-slot taking, one Insert slot in place
of the six-row chest. Rusty's three calls (group order, chest-slot taking, EMI-style search) are
in D-0009, as is the trie they floated and why not.

Full `./gradlew clean build` on the release tree, Xephyr `:7`, llvmpipe, muted:

- JUnit: 61 tests. New: `IndexTest` (order by group then name, deterministic; words in name or
  group; `@mod`; rows with headings and wrapping; invariants) and `TakeTest` (an empty cursor
  lifts a stack or half, shift to the inventory, nothing there; a loaded cursor inserts all or
  one with any button; bad input).
- `runGameTestServer`: 19 real-server GameTests. New:
  - `theIndexListsEveryKindWithItsTotal`: the house's 24 kinds once each; stone counts the
    chest and the buffer under "Building / Stone"; iron 5 under "Materials / Ores & Metals" as a
    stack of one; the headings start at "Building / Stone" and end at "Misc".
  - `takingFromTheIndexBehavesLikeAChestSlot`: left lifts 64 cobblestone and the chests give
    all 64; a loaded cursor puts it all back; right lifts 3 of 5 iron; right again inserts one;
    shift with a loaded cursor inserts it; shift with an empty cursor sends 40 dirt to the
    inventory; a kind the building lacks does nothing; after the owner claims, a stranger takes
    nothing and, trusted, lifts the gravel.
  - `theInsertSlotSinksIntoTheBuffer`: 10 sand set in the slot is in the buffer at once and the
    slot empty; shift-click from the hotbar sinks 5 gravel; a hundred ticks later the buffer
    is empty and the chests hold 40 sand and 25 gravel; with the buffer full of bedrock the
    slot keeps its 7 sand and closing the menu hands them back.
- `runPhotoBooth` with EMI, Sodium, Iris and Complementary: COMPLETE, 36 checks. New steps at
  the room's manager: the listing has the room's 23 kinds and the grid opens on "Building /
  Stone" ([index](0.4.0/04-manager-index.png)); "iron" typed into the search shows the ingot and
  the pickaxe only ([search](0.4.0/04b-manager-search.png)); a left click on the cobblestone
  cell puts 64 on the cursor and the kind leaves the index ([cursor](0.4.0/04c-take-on-cursor.png));
  a click on the Insert slot empties the cursor, the server's slot is empty (sunk into the
  manager) and the index counts the 64 cobblestone again ([inserted](0.4.0/04d-inserted.png)).
  The trust panel photos are as before ([panel](0.4.0/09-trust-panel.png)).
- Judged by eye at 2x and 3x: headings and cells in their groups, the counts at half size in
  the corners, the scrollbar, the search field clear of the title (the first build's field
  overlapped "Manager"; moved 22 px right), the Insert slot and label, the inventory below, the
  trust panel beside. Not judged: the tooltip (the booth's mouse never rests on a cell).
- Jar `warehousemanager-0.4.0.jar` sha1 `dd762b88458cfd528ef667acbd51e221119cfaef`
  (170429 bytes).
- Not verified: the screen at GUI scale 5 on Rusty's ultrawide (224 rows fit the 240 minimum,
  318 columns with the panel fit 320); a warehouse of hundreds of kinds (the listing is one
  packet; a few hundred rows is a few kilobytes); a real second player taking while the owner
  watches (the one-second refresh is the mechanism).
