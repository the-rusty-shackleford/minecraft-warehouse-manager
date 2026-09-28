# Decisions

Rationales are append-only. Supersede with a new decision.

| ID | Status | Decision |
|---|---|---|
| [D-0001](D-0001.md) | Accepted | Sky-bounded flood fill, moved contents, conjured signs, tagged containers |
| [D-0002](D-0002.md) | Accepted | Overflow containers and sibling groups stand next to each other |
| [D-0003](D-0003.md) | Accepted | Chest items dropped into the manager furnish an empty building |
| [D-0004](D-0004.md) | Accepted | Crafting tables in the building draw on its containers |
| [D-0005](D-0005.md) | Accepted | EMI counts the building's containers through a first-placed recipe handler |
| [D-0006](D-0006.md) | Accepted | An owner and a trusted roster bind crafting, the manager and its containers; one manager per building, claims persist |
| [D-0007](D-0007.md) | Accepted | The pooled fill unlocks a recipe the player has not unlocked (vanilla's placement refused it silently; Rusty's propeller), except under doLimitedCrafting |
| [D-0008](D-0008.md) | Accepted | A fill the player and the building cannot cover says what is short in chat, before vanilla's all-red ghost (Rusty's Engine without a boiler) |
| [D-0009](D-0009.md) | Accepted | The manager's screen is the warehouse's index: every kind under its heading with its total, a search, chest-slot taking, one Insert slot over the buffer |
| [D-0010](D-0010.md) | Accepted | Clicking a recipe again piles the grid up from the chests, one more craft per click, the most on a shift-click, capped by the stack (vanilla's own rule; Rusty's bullets) |
| [D-0011](D-0011.md) | Accepted | A shaped recipe with gaps in its pattern fills from the chests: vanilla answers air for an empty cell and the fill read it as an ingredient (Rusty's receiver, taken for a mixed-source failure) |
| [D-0012](D-0012.md) | Accepted | A part the building could make at the table counts as available, the full tree down, crafting recipes only, and the fill makes the parts first (Rusty's rifle from receivers from steel) |
| [D-0013](D-0013.md) | Accepted | Nothing being made is spent on its own ingredients, from the chests or by making it, at any depth; the planner is told the recipe's result (Rusty's ingot made from nuggets broken from ingots) |
| [D-0014](D-0014.md) | Accepted | One owner's warehouses are one network: automatic by ownership, balanced deposits and levelling draws, far warehouses loaded (full, not ticking) only while the network is in use, one roster per owner (Rusty's villages far apart) |
| [D-0015](D-0015.md) | Accepted | The pooled fill counts, takes and gives what the player carries through Carried, their bags included, as vanilla's placement now does |
