# Release verification — 0.1.1

2026-09-22. Rusty, on the first evening with 0.1.0: "Food 1/2" and "Food 2/2" ended up apart,
"we should keep things logically grouped together" (D-0002).

- `./gradlew test`: 30 JUnit tests, three new in `PlannerTest`: a group's overflow container
  is the free one next to its first, a sibling group's first container stands next to its
  sibling's, and a spare container joins the fullest group beside it, each on containers laid
  out in a line.
- `./gradlew runGameTestServer`: 7 real-server GameTests. The double-chest test's hint check
  now matches the hint's first word, since a hint may wrap across sign lines.
- `./gradlew runPhotoBooth` on the iconified Xephyr display, muted: COMPLETE, no loose items,
  every container labelled; [row](0.1.1/warehousemanager-00-labelled-row.png) and
  [double chest](0.1.1/warehousemanager-02-double-chest-two-signs.png) inspected. The booth's
  world sync is now unconditional and configuration-cache safe.
- `./gradlew clean build -PskipBooth`: green; jar `warehousemanager-0.1.1.jar`
  sha1 `6bf57b6e665f057f651535e066b206d1a7606348`. Built and committed; release waits on
  Rusty's go.
