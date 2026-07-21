<div align="center">
  <h1 align="center">Youer 1.21.1</h1>
  <h3 align="center">Minecraft NeoForge Hybrid server implementing the Paper/Purpur API</h3>

  <p align="center">
    <a href="https://github.com/MohistMC/Youer/stargazers">
      <img alt="GitHub Stars" src="https://img.shields.io/github/stars/MohistMC/Youer?logo=github&color=181717&style=flat-square">
    </a>
    <a href="https://neoforged.net/">
      <img alt="NeoForge" src="https://img.shields.io/badge/NeoForge-21.1.241-FF8B00?style=flat-square">
    </a>
    <a href="https://www.azul.com/downloads/?version=java-21-lts#zulu">
      <img alt="JDK" src="https://img.shields.io/badge/JDK-21.0.8-007396?logo=java&logoColor=white&style=flat-square">
    </a>
    <a href="https://docs.gradle.org/9.2.0/release-notes.html">
      <img alt="Gradle" src="https://img.shields.io/badge/Gradle-9.2.0-02303A?logo=gradle&logoColor=white&style=flat-square">
    </a>
    <a href="https://discord.gg/mohistmc">
      <img alt="Discord" src="https://img.shields.io/discord/311256119005937665?color=5865F2&logo=discord&logoColor=white&style=flat-square">
    </a>
  </p>
</div>

## 🚀 Features

- Hybrid server combining NeoForge mod support with Paper API compatibility
- Seamless plugin support from Bukkit/Spigot/Paper ecosystem
- Optimized performance and stability for modded environments

## 📊 Progress Status

✅ **Core Integration**
- [x] NeoForge ([db942bc1e](https://github.com/neoforged/NeoForge/commit/db942bc1e))
- [x] Bukkit API ([69fa4695](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/bukkit/commits/69fa4695))
- [x] CraftBukkit ([19bf84656](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/commits/19bf84656))
- [x] Spigot ([a759b629](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/spigot/commits/a759b629))

✅ **In Progress**
- [x] Paper ([3cb8529bd](https://github.com/PaperMC/Paper-archive/commit/3cb8529bd))
  - [x] api (13)
  - [x] server (165)
    - [ ] chunk_system (AsyncYouer)
    - [ ] chunk_tick_iteration (AsyncYouer)
    - [ ] starlight (AsyncYouer)
- [x] PurPur ([803bf624](https://github.com/PurpurMC/Purpur/commit/803bf624))
  - [x] api
  - [x] server (273)

🔄 **Folia Region Threading (In Progress)**

This fork integrates Folia's regionized tick threading into Youer, enabling multi-region parallel ticking with NeoForge mod support.

- [x] Folia API patches (disable timings, deprecate BukkitScheduler, isGlobalTickThread API)
- [x] Folia aux patches (block update safety, TE access guards, broken APIs throw)
- [x] Moonrise interface implementations on 22+ MC classes
- [x] Server starts successfully with 119 mods loaded
- [x] Region shutdown thread (graceful server shutdown)
- [x] RegionShutdownThread uses SERVER thread group (AE2 `assertServerThread` compat)
- [x] DerivedLevelData handling for nether/end/mod dimensions
- [ ] Runtime mod compatibility testing (AE2, Mekanism, etc.)
- [ ] In-game region tick verification

### Known Issues

1. **AE2 `assertServerThread`**: AE2 checks `Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER`. Fixed by creating `RegionShutdownThread` with SERVER thread group.
2. **DerivedLevelData ClassCastException**: Nether/end/mod dimensions use `DerivedLevelData` which cannot be cast to `PrimaryLevelData` or `WorldData`. Fixed by skipping `saveDataTag` for non-primary dimensions (chunk data is saved separately via `saveRegionChunks`).
3. **NeoForge `ServerLifecycleHooks.handleServerStopped`**: Main thread `finally` block never executes in region threading mode. Moved to `stopPart2()` with try-catch for mod compatibility.

### Build

```bash
# Requires JDK 21
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat youerJar --no-daemon -x test
```

Output: `projects/youer/build/libs/`
- `youer-1.21.1-<commit>-server.jar` (binpatch jar)
- `youer-21.1.241-universal.jar` (universal jar)
- `merged-binpatches.lzma` (in `projects/youer/build/neodev/`)

### Deploy

Copy the three files to your server directory:
- `youer-1.21.1-<commit>-server.jar` → server root
- `youer-21.1.241-universal.jar` → `libraries/net/neoforged/neoforge/21.1.241/neoforge-21.1.241-universal.jar`
- `merged-binpatches.lzma` → `libraries/net/neoforged/neoforge/21.1.241/server.lzma`

Set `timeout-time: 0` in `server.properties` (disable watchdog - main thread sleeps in region threading mode).

## 📚 Documentation

- [English Documentation](https://mohistmc.com/youer/docs)
- [中文文档](https://www.mohistmc.cn/docs/youer)

## ⚙️ Technical Stack

| Component   | Source                                                              | Purpose                      | Status |
|-------------|---------------------------------------------------------------------|------------------------------|--------|
| NeoForge    | [GitHub](https://github.com/neoforged/NeoForge.git)                 | Mod support                  | ✅      |
| Bukkit      | [Spigot](https://hub.spigotmc.org/stash/scm/spigot/bukkit.git)      | Plugin support               | ✅      |
| CraftBukkit | [Spigot](https://hub.spigotmc.org/stash/scm/spigot/craftbukkit.git) | Plugin support               | ✅      |
| Spigot      | [Spigot](https://hub.spigotmc.org/stash/scm/spigot/spigot.git)      | Plugin support               | ✅      |
| Paper       | [GitHub](https://github.com/PaperMC/Paper.git)                      | Plugin support               | ✅      |
| PurPur      | [GitHub](https://github.com/PurpurMC/Purpur.git)                    | Plugin support               | ✅      |
| Arclight    | [GitHub](https://github.com/IzzelAliz/Arclight.git)                 | Plugin Remapping and Message | ✅      |
