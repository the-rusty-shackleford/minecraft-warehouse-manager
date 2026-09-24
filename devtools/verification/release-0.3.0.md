# Release verification — 0.3.0

2026-09-24. D-0006: the warehouse has an owner and a trusted roster; pooled crafting, the
manager and its claimed containers are theirs; one manager per building; claims persist;
claimed containers and the manager survive explosions. Built on Rusty's "build D-0006";
**not yet vetted by Rusty, not released.**

- `./gradlew test`: 46 JUnit tests (39 before, plus `AccessTest`: the tiers over every bound
  action, the operator bypass, claiming once, the roster's changes and the record's
  invariants).
- `./gradlew runGameTestServer`: 14 real-server GameTests, the 10 before unchanged (they
  place the manager with `setBlock`, so it is unowned, which pins that a pre-0.3.0 manager
  stays open to everyone) plus four:
  - the owner draws two planks from the chest through a recipe click, a stranger's click
    draws nothing, the stranger's right-click on the chest opens nothing and their
    `destroyBlock` on the chest and on the manager both return false with the blocks
    standing; the owner opens the chest; a trust toggle from the stranger is ignored and one
    from the owner (with the manager menu open, through `Roster.toggle`) trusts them; trusted,
    they draw and open; a toggle without the manager open is ignored; trust withdrawn, their
    open chest menu reports itself invalid, which the server's tick turns into a close;
  - a manager placed with the item in the room next door stands and its placer owns it; with
    a hole knocked through the shared wall the same placement is refused and the first
    manager claims the chest across the hole within its next rescan;
  - two TNT-strength explosions, one beside a claimed chest and one beside the manager, take
    the dirt witness blocks beside each and leave the chest with its diamonds and the manager
    standing;
  - a claim sits in the level's saved data; after the manager is dropped from the loaded set
    (what a chunk unload does) a second manager placed in the same building cannot take the
    chest while the first's block stands; breaking the first releases the claim and the
    second claims it.
- `./gradlew runPhotoBooth` with EMI, on an iconified Xephyr beside Rusty's own client,
  muted: COMPLETE, 26 checks. The nine earlier scenes pass unchanged. New: the booth player
  claims the room's manager, opens it, and the panel lists the two players the booth made the
  world remember (a player-data file each and a cached name), none trusted
  ([09](0.3.0/09-trust-panel.png)); a click at the first row's screen coordinates goes through
  the screen's own hit-test, the `Trust` packet and the server, and the fresh listing shows
  `[x] Jdrum12` ([10](0.3.0/10-trust-panel-one-trusted.png)); nfx owns the hut and the booth
  player's right-click on its manager and on one of its chests open nothing, with "nfx's
  warehouse. You're not on the trusted list." on the action bar
  ([11](0.3.0/11-refused-at-the-door.png)); the unclaimed room manager's panel reads
  "Unclaimed warehouse. Sneak and right-click the block to claim it."
  ([04](0.3.0/04-manager-screen-unclaimed.png)).
- Two things the photos caught that the tests could not: EMI's item panel painted over the
  trust panel (fixed with an EMI exclusion area for the screen, the D-0005 lesson paying
  off), and a three-line hint ran into the first row (the header now follows the hint's
  wrapped height). Both inspected fixed in the photos above.
- `./gradlew build -PskipBooth -PskipGameTests`: green; jar `warehousemanager-0.3.0.jar`
  sha1 `11abab9a79538b9fe7b4f32c127dbcc8f4b12dd3` (135632 bytes), no EMI class inside.
- Not verified: a real second client (the booth's "online" green mark is exercised by no
  scene, since a booth has one player); a real chunk unload (simulated by dropping the
  manager from the loaded set); Rusty's own client against the live server, where every
  existing manager is unowned until its owner sneaks and right-clicks it.
