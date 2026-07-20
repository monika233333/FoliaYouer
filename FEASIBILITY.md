# FoliaYouer 可行性报告

## 目标
在 1.21.1 上实现 **Folia 区块线程化 + NeoForge mod 支持** 的混合服务端，融合 Kitin 网络优化 + Lophine 生电功能。

## 项目继承链

```
PaperMC
  ├─ Folia (Paper fork, 区块线程化)
  │   ├─ Kitin (Folia fork, 网络优化+生电修复, 1.21.11)
  │   └─ Luminol (Folia fork)
  │       └─ Lophine (Luminol fork, 生电功能+Leaves协议, 26.1.2)
  └─ Youer (NeoForge 1.21.1 混合端, Paper API + Bukkit 插件兼容)
```

## 路线 A：以 Youer 1.21.1 为底，移植 Folia 补丁

### 已验证基线

| 检查项 | 结果 |
|--------|------|
| Youer 1.21.1 构建体系 | ✅ 独立可编译，`youerJar` BUILD SUCCESSFUL |
| Folia-dev-1.21.1 applyPatches | ✅ 5 API补丁 + 19 Server补丁全部干净应用 |
| FoliaYouer 骨架 | ✅ git init + initial commit，基线 jar 产出 (124M) |
| FoliaYouer youerJar | ✅ BUILD SUCCESSFUL in 1m 9s |

### 构建坑（已解决）

1. **Youer 要求项目必须是 git 仓库** — gradleutils.gitInfo 需要读取 git 信息，干净副本必须 `git init && git commit`
2. **Windows 长路径限制** — CraftBukkit 有超长路径文件，需 `git config --global core.longpaths true`
3. **网络受限** — paperweight 需要从 GitHub/SpigotMC clone 仓库，需代理 `HTTP_PROXY=http://127.0.0.1:10809`
4. **patchCraftBukkit.repo 目录残留** — 失败后留下空目录，需手动清理
5. **createInstallerProfile 缺 icon** — 跳过此任务，直接用 `youerJar` target 产出 server jar

### Folia 补丁清单（24个，28268行）

#### API 补丁（5个）
| # | 补丁 | 行数 | 功能 |
|---|------|------|------|
| 1 | Force-disable-timings | 53 | 关闭 timings |
| 2 | Region-scheduler-API | ~70 | 区域调度器 API |
| 3 | Require-plugins-Folia-supp | 80 | 插件必须标记 Folia 支持 |
| 4 | Add-RegionizedServerInitEvent | ~40 | 区域初始化事件 |
| 5 | Add-ownership-check-API | 46 | 区域位置所有权检查 |

#### Server 补丁（19个）
| # | 补丁 | 行数 | 功能 | 优先级 |
|---|------|------|------|--------|
| **0003** | **Threaded-Regions** | **20546** | **核心区域线程化（211文件）** | **P0 核心** |
| 0006 | CraftEntity-getHandle线程检查 | 3288 | 实体跨线程安全 | P0 核心 |
| 0017 | Region-profiler | 1974 | 区域性能分析器 | P2 |
| 0001 | Build-changes | 1157 | 构建配置 | P0 |
| 0019 | Add-watchdog-thread | 199 | 看门狗线程 | P1 |
| 0014 | Synchronize-PaperPermissionManager | 190 | 权限管理同步 | P1 |
| 0010 | Prevent-block-updates-non-owned-chunks | 113 | 防止跨区域方块更新 | P1 |
| 0005 | Add-chunk-throughput-counters | 86 | TPS 吞吐量计数 | P2 |
| 0009 | Require-plugins-Folia-supp | 82 | 插件 Folia 标记 | P1 |
| 0007 | Throw-UnsupportedOp-broken-APIs | 74 | 不兼容 API 抛异常 | P1 |
| 0002 | MC-Dev-fixes | 49 | MC 开发修复 | P2 |
| 0004 | Max-pending-logins | ~40 | 限制待处理登录 | P2 |
| 0011 | Block-reading-TE-on-worldgen | ~30 | 世界生成线程安全 | P1 |
| 0012 | Skip-worldstate-waking-players | ~35 | 唤醒玩家线程安全 | P1 |
| 0013 | No-POI-for-lodestone-compass | ~30 | 磁盘指南针线程安全 | P2 |
| 0015 | Fix-off-region-raid-heroes | ~40 | 袭击英雄跨区域修复 | P2 |
| 0016 | Sync-vehicle-position | ~30 | 载具位置同步 | P2 |
| 0018 | Disable-spark-profiler | ~20 | 禁用 spark | P2 |
| 0008 | Fix-tests-by-removing | ~20 | 移除测试 | P2 |

### Kitin 补丁清单（38个 server, 4939行）

**分类：**
- **网络优化（核心价值）**: 0003 ChunkLazyLoading, 0004 PlayerThrottling, 0005 Reduce-Entity-packets, 0015 globalMaxChunkSendRate, 0018 Particle-throttling, 0020 Multi-Port-Listening, 0024 Reimplement-ACK-Chunk-Sending, 0027 Staggered-Metadata-Sync
- **Folia修复**: 0001 WaypointManager, 0006 Ender-Pearl-Chunk-Loading, 0007 Fix-time-fetching, 0008 SafeSandDuper, 0011 SpreadPlayersCommand, 0012 ScheduleCommand, 0013 ScoreboardCommand, 0025 ArrayIndexOutOfBounds, 0029 FishingHook
- **性能**: 0002 Entity-push, 0014 dropper-container-transfer, 0017 VillagerSmartHibernation, 0023 Replace-AI-attributes
- **安全**: 0010 LazyChunkBarrier, 0021 High-altitude-void, 0022 Secure-seed
- **调度器**: 0004 Galaxy-Scheduler (paper-patch), 0028 Galaxy-Scheduler
- **其他**: 0009 TNT-fix, 0016 Add-Config, 0019 Login-NPE, 0026 ItemEntity-movement, 0030 teleportation-event-APIs

### Lophine 补丁清单（68个 server, 12794行）

**分类：**
- **生电功能（核心价值）**: Carpet-features (1238行), Leaves-Old-Block-remove (843行), Leaves-Fakeplayer (782行), Leaves-Replay-Mod (560行), Leaves-Item-overstack (466行), update-suppression系列, Instant-Block-Updater, Old-zombie-reinforcement, Old-leader-zombie, Spawn-invulnerable-time, Old-raid-behavior, Old-Explosion-Damage
- **协议兼容**: Leaves-Base-Protocol, Servux, Syncmatica, BBOR, Xaero-Map, REI, Jade, PCA-sync, Alternative-block-placement
- **配置**: tick-rate-support, enable-command系列 (function/scoreboard/trigger/save-all), raytracing-tracker, cross-region-damage-trace
- **修复**: Camellia-teleport-fix, Camellia-packet-processing-queue, datapack-command-fix, Restore-vanilla-ender-pearl

## 工作量评估

### 阶段 1：Folia 核心移植（P0，预计 2-3 周）
- 移植 `0003-Threaded-Regions`（20546行/211文件）到 Youer 的 Paper 层
- 移植 `0006-CraftEntity-getHandle`（3288行）
- 移植 API 层 5 个补丁
- 解决 Folia 的 `io.papermc.paper.threadedregions` 包与 Youer 现有占位类的冲突
- **风险**：Folia 补丁基于 Paper 的 moonrise 区块系统，Youer 的 Paper 层可能已修改相关文件，需手工解决冲突

### 阶段 2：Folia 辅助补丁（P1，预计 1 周）
- 移植 0001/0002/0004-0016 中小补丁
- 确保 NeoForge mod 加载器能在区域线程模型下启动

### 阶段 3：Kitin 网络优化（P1，预计 1 周）
- 选择性移植网络优化补丁（0003/0004/0005/0015/0018/0020/0024/0027）
- 这些是独立功能补丁，冲突风险低

### 阶段 4：Lophine 生电功能（P2，预计 1-2 周）
- 选择性移植生电补丁（Carpet/Leaves/update-suppression）
- 协议补丁按需移植

### 总计：5-7 周（单人全职）

## 关键技术风险

1. **Folia 与 Youer 的 Paper 层冲突**：Youer 已移植 176 个 Paper server 补丁 + 272 个 Purpur 补丁，Folia 补丁基于原始 Paper，可能在相同文件上冲突
2. **NeoForge mod 加载与区域线程**：NeoForge 的 `FancyModLoader` 在主线程加载 mod，Folia 的区域线程模型可能破坏 mod 初始化
3. **Bukkit 插件兼容性**：Folia 要求插件显式标记支持，大量旧插件可能不兼容
4. **Moonrise 区块系统版本差异**：Folia 补丁引用 `ca.spottedleaf.moonrise` 包，Youer 的 Paper 层可能使用不同版本的 moonrise

## 下一步

1. 深入分析 `0003-Threaded-Regions.patch` 的 211 个文件，标记哪些与 Youer paper-patches 冲突
2. 尝试将 Folia API 补丁（5个，小文件）先移植到 FoliaYouer，验证补丁应用
3. 建立 `folia-patches` 目录结构，开始逐个移植 server 补丁
