# 2.5.4 验证记录（启动自检日志 / 卡加载界面排障存档）

- 日期：2026-10-01
- 环境：Minecraft 1.21.1 / NeoForge 21.1.251 / LDLib2 2.2.41 / Java 21（OpenJDK 21.0.12，Debian）
- 触发批次：用户报告"装上模组后游戏卡在红色加载界面（不崩溃不报错）"，并要求顺带排查其他 bug。

## 用户日志根因分析（latest.log 1620 行全量）

致命错误链（时间戳为用户机器时间）：

1. `[05:00:25]` 第一次资源重载开始（红色加载界面阶段）。
2. `[05:01:11 → 05:04:36]` GWO 模组 `GwoOnlineKeyClient` 在**资源加载路径**（SpriteResourceLoader→SelectiveZipArchive.read）上同步请求 `https://api.playgwo.com` 解密素材，全部 `Connection timed out / Connection reset`——每个素材等满超时，Minecraft 后台线程池（Worker-Main 1~12）被占满约 4 分钟；同窗口内 Iris 更新检查同样超时（用户网络对境外服务整体不可达的旁证）。
3. `[05:04:36.955]` 第一次资源重载以异常完成：`Caught error loading resourcepacks, removing all selected resourcepacks`（`Cannot prepare protected asset gwo:gltf/animations/mg338_receiver_default.anim.glb`），触发第二次完整资源重载。
4. `[05:04:37.08x]` Common Setup 事件 dispatch 恢复执行：`playerrevive` / `creativecore` / `littletiles` / `enhancedvisuals` 四个 creative 系模组在 `FMLCommonSetupEvent` 中抛 `Key already registered <modid>`（注册代码重复执行）。
5. `[05:04:37.505]` `FATAL: Failed to wait for future Common setup, 4 errors found` → 模组状态 ERRORED → 之后所有事件 `Cowardly refusing to send event ... to a broken mod state` → 客户端永远无法完成启动——即用户看到的"卡红色加载界面不崩溃不报错"。

## 复现与对照实验（沙箱生产环境）

环境：NeoForge 21.1.251 官方 installer 安装的生产客户端 + 用户环境同版本模组（CreativeCore 2.13.49 / LittleTiles 1.6.0-pre230 / PlayerRevive 2.1.2 / LDLib2 2.2.41 / GWO-Beta1.0-Fix-0.5 / drag-inventory 2.5.3），`api.playgwo.com` 以 JVM 黑洞代理（不可达地址）模拟网络屏蔽：

| 实验 | GWO | 网络 | 本模组 | 结果 |
|------|-----|------|--------|------|
| 1 | 在场 | 黑洞 | 2.5.3 | **完整复现用户症状**：TransientFailure ×N → 第一次资源重载失败（`Cannot prepare protected asset gwo:gltf/equipment/armor.anim.glb`）→ 二次重载 → `Key already registered playerrevive/littletiles/creativecore`（Worker-Main-5，与用户日志同为 Worker-Main 线程）→ `Failed to wait for future Common setup, 3 errors found`（用户 4 errors，差异恰为未装 EnhancedVisuals）→ `Cowardly refusing` ×2 → 卡死 |
| 2 | 移除 | 黑洞（无关） | 2.5.3 | 完全正常：无任何 ERROR，资源重载成功、纹理图集烘焙完成 |
| 4 | 在场 | 畅通 | 2.5.3 | 完全正常：`Verified 79 protected assets in 7655 ms`，零错误 |

结论：**卡死 = "GWO 在场 + api.playgwo.com 不可达"的组合，与 drag-inventory 2.5.3 无关**（jar 层面对比亦佐证：2.5.2→2.5.3 仅 9 个客户端类字节码变化、mixins.json 与 MANIFEST 逐字节一致、mods.toml 仅版本号）。

给玩家的操作建议（按优先级）：
1. 换网络环境（手机热点/代理）或等 `api.playgwo.com` 服务恢复后重试——大概率直接解决；
2. 清理 mods 内同名双版本残留（本案例：`tactical-inventory-1.21.1-neoforge-1.0.14.jar` 与 `...-1.0.9.jar` 并存，删除旧版 1.0.9）；
3. 仍不行时用二分法定位（本模组可放最后一批验证，2.5.3/2.5.4 均不触碰 FML 生命周期）。

## 用户日志其他问题清单（"顺便看看有没有别的 bug"）

- `tactical-inventory` 1.0.14 + 1.0.9 双版本并存（见上）。
- `Invalid path : Invalid path ''` 大批 ERROR：GWO 附带资源包（gwo/assets/*.zip）内含空路径条目，PathPackResources 逐条报错——噪音级，GWO 侧问题。
- `Opening a config that was already loaded ... dummmmmmy-common.toml`：dummmmmmy 同一文件注册两类配置的常见警告，噪音级。
- `watut:textures/particles/inventory_*.png` 等缺失贴图 / littletiles `missing` 模型 / yuushya `pos_trans_item` 缺失贴图：第三方模组自带瑕疵，噪音级。
- Iris 更新检查 `ConnectException: Connection timed out`：网络问题旁证（与 api.playgwo.com 不可达同因）。
- authlib 401：PCL 离线会话的正常现象，噪音级。
- 本模组（draginventory）在用户日志中零 ERROR / 零 WARN / 资源正常挂载（mod/draginventory）——唯一缺陷是"零输出"导致无法自证推进阶段，即本版修复目标。

## 本版变更与验证

- 变更：仅 `DragInventory` 主类新增构造期 banner 日志（SLF4J，级别 INFO，logger 名 `DragInventory`）；无行为变更。
- `gradlew compileJava` 通过（仅 4 条既有弃用警告，与基线相同）。
- `gradlew test --rerun-tasks` 两轮全绿（15 测试类 / 142 用例，无新增用例——banner 为纯日志无断言价值）。
- `gradlew build` 产出 drag-inventory-1.21.1-neoforge-2.5.4.jar。
- jar 解包验证：mods.toml version=2.5.4；字节码含 `Drag Inventory construct ok: version={} (modid=draginventory, dist={}, java={})` 字符串与 FMLEnvironment 引用。

## 实机建议

- 更新后首次启动，latest.log 应出现一条 `[DragInventory/]: Drag Inventory construct ok: version=2.5.4 ...`。
- 该条与 MapConfigEvents 的 marker 日志（仅首启/迁移时出现）共同构成本模组的加载自检锚点。

JAR SHA256: （见 SHA256SUMS.txt）
