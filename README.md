# Weird Gun Game

A Roblox-style **Weird Gun Game** for **Minecraft 26.2**, as a Paper plugin.

Open a chest menu, bolt together a gun from six sections — **Barrel, Core, Grip, Magazine,
Sight, Stock** — with **10 parts each**, and go and be strange. Then start a **Superbox
tournament** and fight a boss the size of a house with whatever nonsense you just built.

- **60 gun parts**, 10 per section, all combinable → **1,000,000 possible guns**
- **12 ready-made guns** including the RPG, AK, sniper, shotgun, Maxigun and explosive-bullet rifle
- **11 melee weapons** including a **katana that deflects bullets back at whoever fired them**
- **A real sniper scope** with a laser dot, range readout and a steadied-shot stance
- **Superbox tournament** — a multi-round boss fight with three phases and Minibox minions
- A **balance pass** that keeps wild builds fun instead of round-ending (see below)

---

## Building

Requires **JDK 25** and Maven. Minecraft 26.2 runs on Java 25, so an older JDK will not work.

```bash
mvn package
```

The plugin lands at `target/WeirdGunGame-1.0.0.jar`. Drop it in your server's `plugins/`
folder and restart. Built against the **Paper 26.2** API (`26.2.build.87-stable`).

Every push and pull request is built and tested by GitHub Actions
(`.github/workflows/build.yml`), which also uploads the built jar as an artifact.

### Why Paper and not Fabric

Fabric was the original target for 26.2, but it is not currently buildable. Modding
Fabric requires deobfuscation mappings for the game, and as of Minecraft 26.x there are
none published: Mojang's version manifest for 26.1 and 26.2 ships only `client` and
`server` downloads, having dropped the `client_mappings` / `server_mappings` entries that
1.21.11 and earlier included, and Fabric's Yarn has no 26.x mappings either. Fabric Loom
fails at configuration time with `Failed to find official mojang mappings for 26.2`.

Paper does not need mappings — it exposes a stable, named API — so it is the only route
to Minecraft 26.2 today. If mappings appear later, the whole `gun` package (parts, stats,
blueprints, the balance pass) is plain Java with no server dependency and ports across
unchanged.

---

## Playing

| Command | What it does |
| --- | --- |
| `/wgg bench` | Open the gun bench — this is the main screen |
| `/wgg gun <preset>` | Grab a ready-made gun (`rpg`, `ak`, `sniper`, `shotgun`, `maxigun`, …) |
| `/wgg gun <part ids...>` | Build a gun straight from part ids |
| `/wgg knife <id>` | Grab a knife |
| `/wgg random` | Roll a completely random gun |
| `/wgg parts` | List every part id |
| `/wgg tournament start [rounds]` | Start a Superbox tournament |
| `/wgg tournament join\|leave\|stop\|status` | Manage the running tournament |
| `/wgg reload` | Reload `config.yml` (admin) |

### Controls

| Input | Action |
| --- | --- |
| **Right-click** | Fire. Hold it down for automatics and the Maxigun |
| **Left-click** | Aim down sights — tightens spread, zooms if the sight supports it |
| **F** (swap hands) | Reload |
| **Right-click** (katana) | Parry — deflects incoming bullets |

Charge weapons start winding up on right-click and fire themselves when they are ready.
Spin-up weapons need a moment of held trigger before they reach full rate.

### Scoping

Aiming a sight with real magnification (Sniper Scope, Thermal, Cracked Monocle) draws a
marker at the exact point your round will land, sent only to you, along with the range and
what you are looking at. Crouch and hold still for a second and the scope **steadies**:
spread drops to zero, so the shot goes precisely where the dot is. Steadying removes
spread rather than adding damage, which keeps snipers precise without letting them punch
through the balance caps.

### The katana

Right-click to parry for just over a second, on a five second cooldown. Rounds arriving
from the front are knocked straight back down their own flight path at whoever fired them,
carrying the original gun's stats — deflect a rocket and the rocket explodes on the person
who launched it. Only the front arc is covered, so getting shot in the back still hurts,
and returned rounds cannot be deflected again.

---

## The gun bench

`/wgg bench` opens a chest menu:

- **Six section slots** down the left — click one to see all 10 parts for that section.
  Each part shows exactly what fitting it would do to your gun, in green and red.
- **A live preview** in the middle showing the finished gun's real stats, traits, power
  and weirdness score.
- **Armoury** — 12 pre-built guns. Left-click takes one, right-click loads it into the
  bench so you can tinker with it.
- **Knife Rack** — 11 melee sidearms, including the katana.
- **Randomise** — roll all six sections at once.
- **Assemble** — build it and put it in your inventory.

---

## The parts

Every part is deliberately lopsided. Anything that gives you a lot takes something away.

### Barrel — damage, range, and what you actually shoot
Snubnose Stub · Long Rifled Barrel · Boomstick Bore · **RPG Launch Tube** · Gatling Cluster ·
Wet Noodle Barrel · Tesla Rod · Frostbite Pipe · Cursed Bone Barrel · Singularity Muzzle

### Core — fire mode and the elemental nonsense
Iron Core · Redstone Reactor · Blaze Heart · Ender Core · **TNT Core (explosive bullets)** ·
Amethyst Resonator · Slime Core · Nether Star Core · Goat Horn Core · Chicken Core

### Grip — spread, recoil and handling
Duct Tape · Ergonomic Rubber · Bipod · Angled Foregrip · Vertical Foregrip · Honey ·
**Rocket Grip (rocket jumping)** · Squid · Golden · Skeleton Hand

### Magazine — ammo count and reload
Stick Mag · Extended Mag · Drum Mag · **Ammo Belt (156 rounds)** · Rocket Rack · Shell Box ·
Bottomless Satchel · **Nuke Mag** · Hopper Feed · Soul Jar Mag

### Sight — accuracy, zoom and targeting
Iron Sights · Red Dot · Holographic · **Sniper Scope** · Thermal Scope · Laser Pointer ·
Googly Eyes · Eye of the Seeker (homing) · Cracked Monocle · Target Computer (auto-lock)

### Stock — stability and utility
Wooden · Tactical · Heavy Anvil · Skeleton · Slime Pad · Rocket Booster · Cactus ·
**Cushion (immune to your own explosions)** · Jukebox · Void

### Knives

Butter Knife · Combat Knife · Karambit · Butcher's Cleaver · Ender Shiv · Frost Fang ·
Vampire Fang · Baguette · Diamond Shank · **Boxcutter (anti-Superbox)** · **Katana (parry)**

### Traits

Parts stack traits, which is where most of the weirdness comes from. An explosive homing
lightning bullet that also steals health is a completely legal build. Traits include
Explosive, Incendiary, Homing, Ricochet, Shocking, Cryo, Cursed, Vampiric, Piercing,
Singularity, Rocket Recoil, Poultry, Thermal, Auto-Lock, Padded, Soul-Fed, Trickshot,
Blink, Shockwave, Auto-Feed and Rally.

---

## The Superbox tournament

```
/wgg tournament start 5
```

Everyone within 40 blocks is entered automatically; anyone else can `/wgg tournament join`
during the countdown. Then it is round after round against the **Superbox** — a magma cube
the size of a small building, because the boss is supposed to be a box and magma cubes are
the only genuinely cubic mob in the game.

Each round the Superbox gets tougher. It scales with the round number *and* the number of
fighters, and it fights in three phases:

1. **Phase 1** — slams that flatten everything nearby, and summons **Minibox** minions.
2. **Phase 2** — *the box has guns now.* Volleys of explosive shells and sweeping lasers.
3. **Phase 3** — enraged. Faster, plus barrages that rain explosions on every player at once.

Die and you sit the round out as a spectator, then come back at the next intermission.
Between rounds you get a breather to rebuild your gun at `/wgg bench` — that is the point of
the game, and each round hands you a fresh loadout from whatever is on your bench plus a
random knife. Clear the last round and everyone still standing takes home a Champion's gun.

---

## Balance: crazy, not broken

Guns are supposed to be ridiculous. They are not supposed to end the round on their own.

Individual parts are free to be greedy, because the ceiling is enforced centrally in
`GunStats.balance()` after all six parts have applied:

- Hard caps on per-pellet damage, burst damage, explosion power, range, magazine size,
  pierce, lifesteal and crit chance.
- A **sustained-damage budget**: 34 effective DPS, with explosion power counted toward it so
  splash builds cannot sneak past on blast alone.
- If a build exceeds the budget, damage is solved back down to land exactly on the cap.
  Explosions are only allowed to shrink so far — a rocket that does not go bang is not a
  rocket — so bullet damage absorbs the rest.
- If a build is so pellet-heavy that even the minimum damage per pellet would breach the
  budget, the gun **cycles slower** instead. Greedy builds get heavy.

The bench tells you when the balancer has trimmed your build, and by how much.

This is verified exhaustively rather than by sampling: `GunBalanceTest` assembles **all
1,000,000 possible guns** and asserts every one of them respects every cap.

```bash
mvn test
```

---

## Known limitations

- **No custom creative-inventory tab.** A server-side plugin cannot add tabs to the vanilla
  creative menu; that needs a client mod or resource pack. Use `/wgg bench` instead.
- **Held-trigger automatics** run on a rolling window that each right-click refreshes,
  because Bukkit only reports a right-click on air once per click. Holding the button on a
  block repeats naturally.
- **No ammo bar on the item.** Guns are unbreakable so they can never be destroyed
  mid-fight, and the client only draws a durability bar for damageable items. Ammo is shown
  in the lore and on the action bar instead.

## Configuration

`config.yml` covers friendly fire, whether explosions break blocks (off by default, so the
arena survives), bullet trails, a global damage dial, and the tournament's round count,
boss health scaling, arena radius and timings.

---

## Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `wgg.use` | everyone | The gun bench, guns and knives |
| `wgg.tournament.start` | op | Starting and stopping tournaments |
| `wgg.admin` | op | `/wgg reload` |
