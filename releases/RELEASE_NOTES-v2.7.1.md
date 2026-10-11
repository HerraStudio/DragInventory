# Drag Inventory 2.7.1 —— 声纹实机崩溃热修

本版为 v2.7.0 的热修复。用户实机首测（百模整合包：Connector + Sound Physics Remastered + GWO 枪械包等）**进存档走第一步即崩**，崩溃报告定位到声纹采集路径两处叠加缺陷，本版全部修复：

**修复①（崩溃根因）采集 NPE**：

- 原版 `SoundEngine.play` 的执行顺序是"先发 PlaySoundEvent、事件返回后才 `resolve()` 音效实例"；`AbstractSoundInstance` 内部的声效字段只有 resolve 才赋值，而 `getVolume()` 直接解引用它——**事件回调里直接读响度必然 NPE**，异常顺着事件总线炸穿客户端 tick 循环（崩溃堆栈：LocalPlayer.aiStep → playStepSound → PlaySoundEvent → SoundPrintEvents:97）。
- 修复：采集时先借 SoundManager **幂等补一次 resolve** 再读响度（引擎稍后自己还会再 resolve，无副作用）；未注册 / 刻意留空的声效（引擎最终只会 warn 不会播放）按"听得到才采集"原则直接跳过；resolve 或读值抛异常（Connector 转换的 Fabric 声等非常规实现）一律退回满响度，**任何路径都不外溢**。
- 另加采集全径兜底 try-catch（同类异常只报一次日志）：声纹是纯展示功能，宁可丢一条声纹，绝不崩游戏。

**修复② 自声过滤从未生效**：

- v2.7.0 以"眼睛位置"为基准做 3D 距离过滤自声，但脚步声在**脚部**播放——垂直差 1.62m 直接击穿 0.9m 阈值，自己的脚步从来没被过滤过（**正是它触发了修复①的 NPE**，所以崩溃发生在"走第一步"这一刻）。
- 修复：改为**声源到玩家碰撞箱（0.6 宽 × 1.8 高）的最近距离**判定——自声（脚步 / 落地 / 受击）全部发自体内（距离 0m，稳定过滤），贴脸敌人的碰撞箱间隙仍有约 0.6m（照常产生声纹），语义边界清晰。

**同版修复：mod 版本元数据错乱**

- mods.toml 版本号自 2.5.7 后未随发布更新——2.6.0 / 2.6.1 / 2.7.0 三个版本在 mod 列表、崩溃报告、依赖解析里一直显示 `draginventory@2.5.7`（本次崩溃报告实锤）。改为 `${modVersion}` 模板占位、构建时随 `project.version` 单源注入，此后不可能再漏更。

**质量门禁**：

- 新增 12 例纯 JVM 回归（`SoundPrintCaptureSafetyTest`）：崩溃时序锁定（桩精确镜像"未解析即读响度必 NPE"的原版语义，先 resolve 再读的顺序被断言锁定）、占位声效跳过、三类异常退满响度、碰撞箱自声判定（脚部 / 头部自声、贴脸敌人、楼上楼下）、与原版 `AABB.distanceToSqr` 数学逐点对拍。
- 决策核心抽为纯 JVM 类 `SoundPrintCaptureRules`（零 Minecraft 依赖，MC 侧仅剩薄适配），与项目"纯逻辑 + 薄适配、测试零 MC 类"的既有纪律一致。
- 全套 **224 用例全绿**（212 原有 + 12 新增）；`compileJava` / `test` / `build` 门禁三连通过。
- jar：`drag-inventory-1.21.1-neoforge-2.7.1.jar` 504,904 B（SHA256 见 SHA256SUMS.txt），内置 mods.toml 已验证为 `version="2.7.1"`。

**升级与复测**：

- 覆盖安装：删除旧 jar，放入本 jar 即可（配置无变化，`draginventory-soundprint-client.toml` 沿用）。
- 复测要点：进存档四处走动——自己的脚步**不再产生声纹**；身边动物 / 敌人（0.9m 外）脚步产生白色声纹、约 2.5 秒退场；开一枪——红色高声纹瞬间冲起；楼上 / 楼下走动——上下不对称 + V 形箭头。mod 列表内版本应显示 2.7.1。
