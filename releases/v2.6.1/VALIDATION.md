# v2.6.1 验证记录（方位条默认预设重调 + 倒计时归零字样停留后消失）

## 需求与实现

**用户需求**：①方位条默认预设太丑（用户实测反馈），按用户调优参数重调：apex 皮肤、顶部对齐/水平居中、屏宽占比 2/3 条宽（"27px▪67%"经推断为 427px·67% 的漏字，用户最终拍板"先按 427px·67% 试一下"）、1.0 缩放；②对局倒计时归零后的"对局已结束"字样停留 2~3 秒后消失。

**实现**：

1. **方位条出厂预设重调**（v1.5.5 口径）：
   - `CompassConfig`：默认 `style` delta→apex、`offset_y` 6→0、`width` 240→427（640 GUI 宽 × 2/3 的换算值）；新增 `general.defaults_retuned` 收敛标记（默认 false）。`offset_x=0` / `scale=1.0` 新旧默认相同，无需收敛。
   - `CompassConfigEvents.retuneDefaults()`（Loading 事件触发，仿 `minimap.size_retuned` / `behavior.zoom_retuned` 先例）：标记驱动一次性收敛——仅当项**仍是旧默认值**时换成新默认（`Retuned compass *` info 日志逐条可见），玩家自定值（≠旧默认）一律保留；置位标记 + flush 落盘后，玩家此后改回旧值的选择被永久尊重。全新安装的配置文件直接以新默认生成，三项检测均不命中，仅写标记。
   - 皮肤/对齐/条宽的渲染路径零改动——只动默认值与收敛逻辑，五套皮肤随时 `/compass style` 切换。
2. **倒计时归零字样停留后消失**：
   - `MapMatchTimer` 新增 `endedAtMillis`（epoch 毫秒，volatile）：结束沿（>0 → 0）触发时记录，恢复 >0（重开/加时）与 `stop()`（off）时复位为 0；由既有 `tickEndDetection` 状态机顺带维护，零额外 tick 开销。`System.currentTimeMillis` 与渲染侧 `Util.getMillis` 同源可直减。
   - `FactoryMinimapHud.drawMatchTimer()` 归零分支：`endedAt != 0 && now - endedAt > TIMER_OVER_LINGER_MS`（2500ms，常量区可调）时 return 整行消失；`endedAt == 0` 的保守回退（Provider 注册即返回 0 的场景）继续显示，不会出现"结束瞬间字样闪没"。

## 门禁记录

- 构建：`./gradlew build`（768m daemon；构建期停 dev server 腾内存）——BUILD SUCCESSFUL；compileJava 仅既有 EventBusSubscriber 弃用警告（非本次引入）。Task 20 完成代码时首轮门禁 21s 全绿（源码自此后未改动，本轮 build 复核 UP-TO-DATE 等价通过 + 版本号变更触发 jar 重打）。
- 测试：**181 用例 0 失败 0 错误 0 跳过**（180 + 1 新增 `endedAtMillisLifecycle`：未开始 0 → 计时态 0 → 结束沿记录 → 零位二次 tick 不重复记录（停留起点不变）→ 重新开始复位 → stop 复位）。
- jar：`drag-inventory-1.21.1-neoforge-2.6.1.jar` 415,341 B，SHA256 `9f984be0…2430`。
- jar 内字节码复核：`CompassConfigEvents.class` 含 `retuneDefaults` / `DEFAULTS_RETUNED` / 三条收敛日志串（delta→apex / 6→0 / 240→427）；`MapMatchTimer.class` 含 `endedAtMillis`；`FactoryMinimapHud.class` 含 `endedAt` 调用。
- 文档同步：README（主段/版本行/changelog 新条目/jar 名 3 处）、wiki/方位条HUD.md（版本行 v1.5.5/特性表/delta 与 apex 皮肤标题/配置示例三项默认值/收敛说明/版本历史表）、wiki/战术地图.md（版本行 v2.6.1/简介/特性表/视觉设计段/安装 jar 名/版本历史表）、docs/compass-hud.md（皮肤清单默认标注 2 处 + 历史段括注）、docs/factory-tactical-map.md（归零停留括注）；全量 `rg` 扫描无 `neoforge-2.6.0.jar` / 旧版本行残留。

## 决策记录

- **"27px▪67%" 的语义推断**：27 不在合法范围 120~960，推断为 427（640 GUI 宽 × 2/3，主用户流 1080p/缩放 3）抄漏"4"字；先按用户上一轮"条宽不动用原 240"准备回退，用户本轮改口"先按你的 427px·67% 试一下"——维持 427 发版实测，观感不佳可 v2.6.2 一分钟级回退（仅 CompassConfig 默认值 + 收敛目标值 + 注释三处数字 + 文档）。
- **收敛策略而非硬切**：老玩家文件里已是旧默认值，仅改代码默认无效——沿用项目既有的一次性收敛机制，且收敛后再改回旧值被永久尊重（玩家主权优先）。
- **`/map timer off` 复位停留计时**：隐藏不是结束；若不复位，下次开启直接跳过字样显示窗口属于反直觉行为。

## 实机复测要点

- 老配置升级：日志三条 `Retuned compass *` + 方位条实际变为 apex 皮肤 / 贴顶 / 427 宽；自定过某项（如手动调过 width=600）的玩家该项不被覆盖。
- 归零后"对局已结束"停留约 2.5 秒消失；`/map timer 5` 重开后数字正常显示、再次归零字样重现；`/map timer off` 后再 `/map timer 5m` 计时正常。
- `TIMER_OVER_LINGER_MS`（2500ms）与皮肤/对齐/条宽默认值均为常量区/配置项，实测观感可调。
