# Release verification — 0.2.0

2026-09-22. Rusty, on the first evening with 0.1.0: "Food 1/2" and "Food 2/2" ended up apart,
"we should keep things logically grouped together" (D-0002).

- `./gradlew test`: 39 JUnit tests. Five in `PoolingTest`: held items are not drawn, a shortfall
  comes from the containers, crafts shrink to what the building can supply, mixed ingredients
  are counted separately, edge cases (D-0004). Three new in `PlannerTest`: a group's overflow container
  is the free one next to its first, a sibling group's first container stands next to its
  sibling's, and a spare container joins the fullest group beside it, each on containers laid
  out in a line. Four new in `FurnishingTest`: an empty building wants its first container;
  another group is worth a container until every leaf is labelled; a crowded group wants a
  double beside it; a spare for an uncrowded group is not wanted (D-0003).
- `./gradlew runGameTestServer`: 10 real-server GameTests; a crafting table in the building
  draws two planks from a chest for a stick recipe, the craft consumes them and the chest is
  debited, and a table outside the walls draws nothing; stacked chests with a sign between
  them and one above keep their own signs (the sign between labels the lower chest) and get no
  extra signs conjured; the new one drops seven chest items
  into the manager of an empty house and finds seven chests stood on the floor, backed by
  walls, signed on the open face, seven groups labelled, no chest item left. The double-chest test's hint check
  now matches the hint's first word, since a hint may wrap across sign lines.
- `./gradlew runPhotoBooth` on the iconified Xephyr display, muted: COMPLETE, no loose items,
  every container labelled; [row](0.2.0/warehousemanager-00-labelled-row.png),
  [double chest](0.2.0/warehousemanager-02-double-chest-two-signs.png) and a
  [bare hut furnished with six chests from the buffer](0.2.0/warehousemanager-05-furnished-hut.png)
  inspected: chests along two walls, each signed on its open face. Two more:
  [the recipe book lighting sticks and a crafting table from the chests with an empty
  inventory](0.2.0/warehousemanager-06-recipe-book-pooled.png) and
  [the grid filled with two planks drawn from the chests after the click](0.2.0/warehousemanager-07-recipe-placed-from-chests.png).
  The booth's book had to be opened through the server's copy of the settings: a client-side
  open is reset by the next recipe award packet. And the book only lists unlocked recipes, so the
  booth awards the stick recipe first. The booth's
  world sync is now unconditional and configuration-cache safe.
- `./gradlew clean build -PskipBooth`: green; jar `warehousemanager-0.2.0.jar`
  sha1 `0914d4e7d04e03e54893c3b7ab705562636430e6`. Built and committed; release waits on
  Rusty's go.
