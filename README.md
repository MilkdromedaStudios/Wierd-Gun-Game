# Ledger

A Fabric mod for **Minecraft 26.2**.

Every block you mine is recorded. Every block you place, every item you use, every step
you take. For a long time nothing comes of it. Then a book turns up in your hotbar that
you did not write, signed by someone calling themselves **Earth**, and it knows exactly
how much stone you have taken.

Ledger also contains the gun bench it grew out of: **60 parts** across six sections,
combinable into **1,000,000 guns**, held inside a sustained-damage budget so wild builds
stay fun instead of round-ending.

---

## Building

Requires **JDK 25** (Minecraft 26.2 runs on Java 25).

```bash
./gradlew build      # or: gradle build
```

The mod lands at `build/libs/ledger-1.0.0.jar`. Drop it in your server's `mods/` folder
alongside Fabric API. Ledger is entirely server-side logic — no client install needed.

CI builds and tests on every push (`.github/workflows/build.yml`).

### Fabric on 26.2

Loom resolves Minecraft's mappings by itself now, so `build.gradle` deliberately has **no
`mappings` line** — declaring one (for example `loom.officialMojangMappings()`) fails on
26.x with `Failed to find official mojang mappings for 26.2`. The versions that work
together are pinned in `gradle.properties`:

```
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version=0.156.0+26.2
```

Two 26.x API changes worth knowing if you extend this: `GameProfile` is a record now, so
it is `profile.name()` rather than `getName()`, and `Material.CHAIN` became `IRON_CHAIN`
once copper chains arrived.

---

## What works today

### The record

Every player has a `WatchRecord`. It counts, per registry id:

- blocks mined
- blocks placed
- items used
- blocks walked (sampled once a second — movement fires far too often to hook directly)
- creatures killed, deaths

Records persist to `<world>/ledger-records.json`, flushed every two minutes and on
shutdown. A surveillance state with amnesia is not frightening.

`totalInteractions()` — mined + placed + used + walked — is the single number the whole
escalation runs on.

### The gun bench

60 parts across Barrel, Core, Grip, Magazine, Sight and Stock. Individual parts are
deliberately lopsided; the ceiling is enforced centrally after assembly by hard caps plus
a 34 sustained-DPS budget that counts explosion power, solved directly so results land
exactly on the cap. Builds so pellet-heavy that even minimum damage would breach the
budget are slowed down instead.

`GunBalanceTest` verifies this exhaustively rather than by sampling: it assembles **all
1,000,000 possible guns** and asserts every one respects every cap.

---

## Not built yet

Named honestly, because the design is settled but the code is not written:

- **Cameras** — small watchers on trees and in caves that turn to face you.
  `models/camera.bbmodel` is the authored model.
- **The Earth book** — a written book of your real statistics, author "Earth", slipped
  into your hotbar after a few hundred records, hinting that something is watching.
- **THE WITNESS** — at 10,000 interactions the record assembles itself into a staged boss
  built from the blocks you mined, which names your inventory back to you.
  `models/witness.bbmodel` is the silhouette reference.
- **The cow.** No further comment.

## Models

`models/` holds hand-authored Blockbench files (`.bbmodel`, openable directly in
Blockbench). The Witness is intended to be assembled at runtime from block-display
entities using each player's own mined blocks, so its file is a silhouette reference
rather than a rig.
