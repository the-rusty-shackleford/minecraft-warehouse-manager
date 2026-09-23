# Release verification — 0.1.1

2026-09-22. Rusty, on the first evening with 0.1.0: "Food 1/2" and "Food 2/2" ended up apart,
"we should keep things logically grouped together" (D-0002).

- `./gradlew test`: 34 JUnit tests. Three new in `PlannerTest`: a group's overflow container
  is the free one next to its first, a sibling group's first container stands next to its
  sibling's, and a spare container joins the fullest group beside it, each on containers laid
  out in a line. Four new in `FurnishingTest`: an empty building wants its first container;
  another group is worth a container until every leaf is labelled; a crowded group wants a
  double beside it; a spare for an uncrowded group is not wanted (D-0003).
- `./gradlew runGameTestServer`: 8 real-server GameTests; the new one drops seven chest items
  into the manager of an empty house and finds seven chests stood on the floor, backed by
  walls, signed on the open face, seven groups labelled, no chest item left. The double-chest test's hint check
  now matches the hint's first word, since a hint may wrap across sign lines.
- `./gradlew runPhotoBooth` on the iconified Xephyr display, muted: COMPLETE, no loose items,
  every container labelled; [row](0.1.1/warehousemanager-00-labelled-row.png),
  [double chest](0.1.1/warehousemanager-02-double-chest-two-signs.png) and a
  [bare hut furnished with six chests from the buffer](0.1.1/warehousemanager-05-furnished-hut.png)
  inspected: chests along two walls, each signed on its open face. The booth's
  world sync is now unconditional and configuration-cache safe.
- `./gradlew clean build -PskipBooth`: green; jar `warehousemanager-0.1.1.jar`
  sha1 `8d5d62c425add2b9089eac94413cad448a161b65`. Built and committed; release waits on
  Rusty's go.
