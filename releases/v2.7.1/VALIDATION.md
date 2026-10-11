# v2.7.1 验证记录（声纹实机崩溃热修）

## 事故与根因

**用户报告**：v2.7.0 实机首测，进存档走第一步即崩（客户端整合包约百模：Sinytra Connector + Sound Physics Remastered + GWO 枪械包 + lithium + sodium 等）。错误报告 zip 含 crash report / latest.log / debug.log / PCL 启动器日志。

**崩溃堆栈**（crash-2026-10-03_03.37.24-client.txt，Description: Ticking entity）：

```
java.lang.NullPointerException: Cannot invoke "Sound.getVolume()" because "this.sound" is null
  at net.minecraft.client.resources.sounds.AbstractSoundInstance.getVolume(AbstractSoundInstance.java:78)
  at dev.draginventory.client.soundprint.SoundPrintEvents.onPlaySound(SoundPrintEvents.java:97)
  at net.neoforged.bus.EventBus.post(...)
  at net.neoforged.neoforge.client.ClientHooks.playSound(ClientHooks.java:405)
  at net.minecraft.client.sounds.SoundEngine.play(...)
  ...
  at net.minecraft.client.player.LocalPlayer.aiStep(LocalPlayer.java:834)   ← 玩家自己走第一步
  at ... LivingEntity.tick → ClientLevel.tickEntities → Minecraft.tick
```

**根因分析**（对照本地 NFRT 反编译源码逐行验证，非推测）：

1. **修复①主根因**：`SoundEngine.play`（NeoForge 补丁版）顺序为
   `p = ClientHooks.playSound(this, p)`（发 PlaySoundEvent）→ `p.resolve(this.soundManager)` → `p.getVolume()`。`AbstractSoundInstance` 的 `sound` 字段**只有 resolve() 赋值**，且 `getVolume()` 实现为 `this.volume * this.sound.getVolume().sample(...)` 无空判——**事件回调期 sound 字段必然为 null，直接调 getVolume() 必 NPE**。v2.7.0 的 SoundPrintEvents:97 在事件回调里直接 `sound.getVolume()`，任何命中关键字的世界内声音都会炸穿 tick。单测漏网原因：SoundSample 在测试里直接构造，采集路径从未用真实 SoundInstance 语义走过（test 源集零 MC 类是项目纪律，但当时未把采集决策抽到纯 JVM 层）。
2. **修复②触发器**：堆栈显示触发者是 `Player.playStepSound`——玩家自己的脚步。v2.7.0 自声过滤以眼睛位置为基准（`ey = player.getEyeY()`）做 3D 距离、阈值 0.9m，而脚步声在脚部播放，眼脚垂直差 1.62m > 0.9m，**自声过滤从未拦住过任何一次自己的脚步**，于是自己的第一步声源走进了响度读取并触发①。两缺陷独立存在、叠加爆发。
3. **顺带发现**：崩溃报告头部 `TRANSFORMER/draginventory@2.5.7` 与 mod 列表 `Drag Inventory 2.5.7`——mods.toml 硬编码 `version="2.5.7"`，2.6.0/2.6.1/2.7.0 三版从未更新（v2.7.0 VALIDATION 所记"mods.toml 未动（项目惯例不随版更新）"实为沿袭的错误惯例，本版纠正）。

## 修复实现

1. **决策核心抽纯 JVM**（新文件 `SoundPrintCaptureRules`，零 MC 依赖，与分类器 / 包络 / 追踪器同套"纯逻辑 + 薄适配"纪律）：
   - `safeLoudness(SoundView)`：未解析 → 先幂等 `resolveOnce()` 再读响度（**时序即崩溃根因的回归点**）；解析后为占位声效 → 返回 NaN（调用方按"听得到才采集"跳过）；resolve / 取声效 / 读响度任一步抛异常 → 退回满响度 1.0，任何路径不外溢。
   - `boxDistanceSqr` / `isOwnSound`：声源到玩家碰撞箱（中心脚底坐标、宽 0.6、高 1.8）最近点距离，数学与原版 `AABB.distanceToSqr(Vec3)` 逐行一致（每轴 `max(max(min-p, p-max), 0)` 平方和）；阈值 `OWN_SOUND_METERS = 0.3`（箱内自声距离恒为 0；贴脸敌人碰撞箱间隙 ≥ ~0.6m 不受影响；代价：间隙 <0.3m 的贴身声会被误伤——该距离威胁肉眼可见，声纹不承担信息量）。
2. **MC 薄适配**（`SoundPrintEvents.loudness`）：`SoundView` 四方法包装 `sound.getSound()` / `sound.resolve(manager)` / `EMPTY_SOUND|INTENTIONALLY_EMPTY_SOUND` 引用恒等 / `sound.getVolume()`，决策全部在上层。
3. **采集全径兜底**：`onPlaySound` 整体 try-catch（AtomicBoolean 同类只报一次日志）——第三方非常规 SoundInstance 实现（本环境为 Connector 转换的 Fabric 声）的任何异常都不允许杀死客户端 tick。
4. **版本元数据单源**：mods.toml `version="${modVersion}"`，build.gradle `processResources` 按 `project.version` 注入；构建产物内已验证 `version="2.7.1"`。

## 门禁记录

- 构建：`./gradlew compileJava` / `test` / `build` 三连 **BUILD SUCCESSFUL**（JAVA_HOME=Adoptium 21.0.12.1，768m daemon，4GB 沙箱纪律全程沿用）。首轮尝试 MDG `unitTest.enable()` 给 test 挂 MC 类路径——只挂了 neoforge maven 依赖不含本地 MC 工件（compileTestJava 仍报 package does not exist），且违背项目"测试零 MC 类"纪律，**已回退**，改走纯 JVM 决策抽取路线（见上）。
- 测试：**224 用例 0 失败 0 错误**（212 原有 + 12 新增 SoundPrintCaptureSafetyTest：崩溃时序锁定 `unresolvedInstanceIsResolvedBeforeVolumeRead`（桩在未解析时 getVolume 抛 NPE 精确镜像原版语义 + 断言 resolveCalled）、占位声效跳过、resolve 失败 / 不可解析实现 / resolvedSound 访问失败 / volume 读取失败四类异常退满响度、预解析实例不重复 resolve、自声脚部 / 头部箱内判定、贴脸敌人 1.0m 与 0.7m 不误伤、楼上楼下不误伤、boxDistanceSqr 与原版 AABB 数学逐点对拍 3 组）。
- jar：`drag-inventory-1.21.1-neoforge-2.7.1.jar` 504,904 B，SHA256 `0fef7305046e555913e09a9b3ca205133b3386746e84f187828f865a09ecf0fb`；内置 mods.toml 版本验证 `version="2.7.1"`；sources jar 329,704 B。
- 语言键：无变化（本版零新增文案，en/zh 291 对称不变）。

## 决策记录

- **resolve 补调的线程面**：PlaySoundEvent 绝大多数在主线程触发（本次崩溃堆栈即 Minecraft.tick 主链）；`resolve()` 与引擎稍后的自调幂等（重查表重赋值），额外一次读表与引擎自身路径同风险面，不引入新竞态。绝大多数脚步 / 枪声的音量为常量分布（ConstantFloat.sample 不消耗随机态）。
- **占位声效跳过而非采集**：EMPTY_SOUND / INTENTIONALLY_EMPTY 在 SoundEngine 只 warn 不排程（"Unable to play unknown soundEvent"）——玩家听不到的声音不产生声纹，与"采集天然防作弊"原则一致。
- **满响度兜底而非丢弃**：解析不出真实响度的非常规实现（Connector 转换声）按 1.0 采集——声纹强度本就以距离衰减为主导（归一化基准音量 × 距离曲线），拿不到原音量退满值视觉差异有限，好过整条静默丢失。
- **测试挂 MC 类路径方案否决**：MDG unitTest 只解决一半问题且破坏"测试零 MC 类"纪律；纯 JVM 决策抽取把崩溃时序变成可断言的桩语义，反而比挂真实类路径回归得更准（桩可直接编排"未解析即 NPE"）。
- **版本元数据惯例纠正**：v2.7.0 记录的"mods.toml 不随版更新"实为 2.6.0 起的漏更（jar 名 / README / mods.toml 三处两处更新一处不动），本版起单源注入。

## 实机复测要点

- **原崩溃场景**：进存档落地走前几步——不再崩溃（v2.7.0 必崩点：自己的第一步）。
- 自声：自己走路 / 跳落地 / 受击——不产生声纹（碰撞箱内判定，脚部声稳定过滤，v2.7.0 因 1.62m 垂直差从未生效）。
- 他人声：0.9m 外动物 / 敌人 / 玩家脚步——白色声纹浮现、约 2.5 秒绵柔退场；贴脸（<1m）敌人脚步仍显示（碰撞箱间隙 >0.3m）。
- GWO 开枪——红色高声纹瞬间冲起快速消散；楼上 / 楼下声源——上下两排不对称 + V 形箭头。
- mod 列表（或 `/herra list`）内 Drag Inventory 版本显示 **2.7.1**（此前三版均错显 2.5.7）。
- 整合包特异性：Connector + Sound Physics Remastered 环境下任意走动 2 分钟无崩溃、无 "声纹采集异常" 日志（兜底 try-catch 从未触发为佳）。
