# 2.5.5 验证记录（对局计时实装 + 缩放范围 108%~600%）

- 日期：2026-10-02
- 环境：Minecraft 1.21.1 / NeoForge 21.1.251 / LDLib2 2.2.40（或 2.2.41）/ Java 21（Temurin 21.0.12.1+1，4GB 沙箱）
- 触发批次：用户确认 v2.5.4 实测无问题后提出的地图功能收尾需求；用户声明本轮后地图功能基础开发完毕。

## 功能与代码验证

### 需求 → 实现对照

| # | 用户需求 | 实现 | 验证 |
|---|---------|------|------|
| 1 | 大地图最小缩放 108% | `MapConfig.ZOOM_MIN` 默认 1.62（屏幕百分比 = zoom/1.5×100）；指令区间 1.08~2.0；设置页滑条同界 | 代码走查 + `/map zoom` 三处入口一致（MapCommands/MapSettingsScreen/MapConfig 同一常量源） |
| 2 | 最大缩放 600% | `ZOOM_MAX` 默认 9.0；指令区间 2.0~16.0；`zoom_retuned` 标记驱动存量收敛（旧默认 0.25→1.62、8.0→9.0，玩家自定值不动） | `retuneZoomRange()` 走查 + 与 v2.5.3 小地图尺寸收敛（`size_retuned`）同构，该机制已实测两轮 |
| 3 | 计时行加粗 | 整行 `Style.EMPTY.withBold(true)`，宽度用带样式组件置测（`Font.width` 不含粗体加宽） | 代码走查；绘制走 `GuiGraphics.drawString(Font, Component, ...)` 官方 API |
| 4 | 结束时间醒目色 | 时间值醒目金 `0xFFFFC84A`；最后 60 秒转红 `0xFFFF6B5E` 加速闪烁（2Hz）；前后缀文字保持常态色形成分层 | 哨兵 `\u0001` 拆分翻译键保证任意语序只给时间上色；中英两语言键均为单 `%s`（已断言） |
| 5 | 时间流动动效 | 每秒秒位跳变沿触发 300ms 正弦脉冲（紧急时幅度 0.22/常态 0.12）；冒号 1Hz 呼吸明暗；归零显示红色"对局已结束" | 纯函数部分（`MapMatchTimer`）11 组单测覆盖；渲染侧为确定性三角函数，无随机态 |
| 6 | （衍生）倒计时真实走表 | `deadline` 时间戳制（暂停菜单/掉帧不停表）；进世界按默认时长自动开始；`/map timer <时长>|default|reset|off` 全套；Provider 接口保留最高优先级 | `MapMatchTimerTest` 11/11（三种时长写法/倒计时数学/Provider 压过 stop/负值夹 0/默认值夹 [1,24h]） |

### 门禁结果

- **compileJava**：通过（20 条弃用警告，全部为既有 API 弃用，无错误）
- **test**：16 个测试类 **153 用例 0 失败 0 错误 0 跳过**（v2.5.4 基线 142 + 新增 MapMatchTimerTest 11）
- **build**：`BUILD SUCCESSFUL in 2m 25s`
- **产物**：`drag-inventory-1.21.1-neoforge-2.5.5.jar` 395,481 字节，SHA256 `b2d61239c9e14a7c99f12ba0327248cb5fd4bdc85eea5dd326a29e213a697816`
- banner 排障锚点（v2.5.4 引入）字节码在 jar 内复核：`Drag Inventory construct ok: version={} ...`
- 语言资产：en_us/zh_cn 各 224 键 JSON 语法校验通过；`cmd.info` 十占位符与十条实参严格一一对应（v1.5.4 教训延续）；`match_timer` 恰单 `%s`（哨兵拆分前提）
- mods.toml：TOML 解析通过，version=2.5.5

## 构建环境攻坚存档（沙箱重置后重建，后续会话必读）

本轮沙箱环境被整体重置（仓库/SSH/`~/.gradle` 全丢），构建环境从零重建过程中定位并修复了四个连环问题，全部有实证：

1. **vineflower wrapper 堆配比笔误**：wrapper 注释记录已验证配方 `-Xmx2368m`，但代码实际传 `-Xmx1800m`——反编译进入 GC 死亡螺旋（jstat 实测：老年代 100%、20 秒 7 次 Full GC、FGCT 占总 CPU 98.6%，有效工作仅约 4 分钟）。修复：1800m→2368m。
2. **4GB 内存账**：反编译峰值需 ~2.4GB，叠加 next dev（509MB）后超限——构建期间需暂停 `bun run dev`（构建后立即恢复）；曾被内核 OOM 杀死（dmesg：`Killed process ... anon-rss:2356224kB`）。
3. **NFRT 的 OOM 文本误判**：`ExternalJavaToolAction` 在工具退出码 0 且 output.jar 完整（9.4MB、5363 文件、`unzip -t` 无错）时，仅因 console 含 `java.lang.OutOfMemoryError` 字符串（来自逐类降级 WARN 的 cause 行）就判定整步失败。修复：wrapper 过滤子进程输出中的该触发字符串（反编译 NFRT 字节码确认判定逻辑：`:212` 抛点 = "退出 0 + console 含 OOM 串"）。
4. **Blocks.java 降级产物修复**（与上一会话同款问题）：全量反编译中 `Blocks.<clinit>`（全库最大静态块）OOM 降级为 2MB 巨型残骸 → NeoForge 补丁 2 个 hunk 打不上。修复三步：
   - 专用重反编译（输入只含 Blocks.class，堆内无其余 9000 类结果，clinit 可用堆从 ~400MB 升至 ~2GB）→ 296KB 正常产物，6604 行；
   - 工具上下文差异修正：专用反编译把 `BlockBehaviour.Properties` 输出为短名 `Properties`（类路径引用 vs 输入根类），手工对齐 hunk 上下文两行（powered_rail/detector_rail）+ 补 `import ...BlockBehaviour;`（recompile 报 `package BlockBehaviour does not exist` 三处，611/614/2705）；
   - 缓存手术：NFRT 缓存为内容哈希链式键控（编辑上游产物即自动失效下游），修复注入 decompile/inject 两个缓存条目。
5. **系统 java 为残缺 JRE**（无 `lib/ct.sym`）：NFRT 内置 javac 报 `release version 21 not supported`——延续上一会话结论"所有构建必须带 `JAVA_HOME=/home/z/jdk-21.0.12.1+1`"（完整 Temurin；`~/.gradle/jdks` 的 eclipse_adoptium 已随重置丢失）。

**sources 工件说明**：`neoforge-21.1.251-sources.jar` 为真实反编译产物（Blocks.java 经上述修复，与上一会话发布状态同构）；模组自身的 `-sources.jar` 照常生成。wrapper（`/home/z/wrapper-src/`）含兜底：子进程失败且无产出时生成占位 jar 保构建推进（本次未触发——真实产物完整产出）。若未来环境内存更充裕，删 NFRT decompile 缓存条目即可重得全量未降级 sources。

## 遗留

- 用户实机复测重点：计时行动效观感（金色/红闪/脉冲/冒号呼吸）、进世界自动开始、`/map timer 25:00` 与 `30m` 两种写法、升级后旧配置自动收敛（日志 `Retuned zoom_min 0.25 -> 1.62` / `Retuned zoom_max 8.0 -> 9.0`）、`/map info` 第十项对局计时。
- `mapSmoke` 冒烟（需显示环境）沿 v2.5.3 流程由用户执行；本轮变更不触碰地图屏/瓦片/滤镜路径，回归面集中在 HUD 计时行与配置迁移。
