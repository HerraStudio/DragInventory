# v2.6.0 验证记录（玩家图标大小可调 + 倒计时只留数字）

## 需求与实现

**用户需求**：①游戏内指令触发的菜单（设置 GUI）添加自定义玩家位置图标大小的选项，可调最大 = 现在这么大，最小自定；②对局结束倒计时只留倒计时的数字，动效保留、颜色不变，倒计时数字在小地图下方居中；③不得重蹈图标不显示的覆辙，检查好没有 bug 再提交。

**实现**：

1. **图标大小**：
   - `MapConfig` 新增 `markers.player_marker_size`（DoubleValue，defineInRange 0.4~1.0，默认 1.0 = v2.5.7 以来的视觉尺寸 = 上限；下限 0.4 ≈ 5px 形状高，小地图上仍清晰可辨）。
   - `FactoryMapUI.player()` 唯一渲染入口在 pose 缩放层乘配置倍率（`k = 15.5/256 × scale × player_marker_size`）——**纹理管线零改动**（资源字节、加载路径、blit 调用全部不变），三处调用点（小地图/大地图/设置预览）与尾迹残影自动等比缩放。
   - `MapSettingsScreen` 标点页新增"玩家图标大小"滑条（SliderRow id 5，cy+70，70+18=88 ≤ 108 内容区不溢出；复用既有 SliderHelper 机制，值按百分比展示，预览区实时所见即所得）；滑条 id 与既有 0/1/2/3/4/6/7 无冲突。
   - `MapCommands` 新增 `/map player_size <0.4~1.0>`（DoubleArgumentType 限界，越界解析期报错；反馈按百分比 `80%` 展示，与滑条同口径）。
2. **倒计时只留数字**：`FactoryMinimapHud.drawMatchTimer()` 移除哨兵拆分的前后缀绘制（"对局还有……结束"整句文案不再渲染），仅绘制 m:ss 数字本体并保持小地图下方居中（`startX = left + (size - timeW×rowScale)/2`）；颜色（TIMER_GOLD/TIMER_RED）、秒位跳变脉冲（组中心锚缩放）、冒号 1Hz 呼吸、紧急段 2Hz 闪烁、归零"对局已结束"、超宽 0.8 缩排兜底全部保留。死键 `draginventory.map.match_timer` 从双语语言文件删除（`match_timer_over` 保留，归零态仍用）。

## 不显示回归的防线（用户红线）

- 纹理资源字节级校验：release jar 内 `player_marker.png`（SHA256 `6150db46…fbf5`）与 `player_marker.png.mcmeta`（`a9c0596d…858`）**与 v2.5.9 完全一致**（逐文件 sha256 对比通过）。
- `FactoryMapUI.class` 字节码复核：`player()` 方法仍走 `GuiGraphics.blit(ResourceLocation,IIIIII)V`（invokevirtual #183），新增仅为 `getstatic MapConfig.PLAYER_MARKER_SIZE`（#61）参与缩放计算——加载链路（SimpleTexture 自动加载 + mcmeta LINEAR）一行未动。
- 配置读取模式与同文件既有 `MINIMAP_SIZE.get()` 渲染期读取同构，无新生命周期假设。

## 门禁记录

- 构建：`./gradlew build`（JAVA_HOME=eclipse_adoptium-21，768m daemon；构建期停 dev server 腾内存）——BUILD SUCCESSFUL；compileJava 仅 4 条既有 EventBusSubscriber 弃用警告（非本次引入）。
- 测试：**180 用例 0 失败 0 错误 0 跳过**（18 测试类；时间戳核验为本轮新跑，非缓存；本次为纯渲染/GUI/指令层变更，未触碰 FX 纯逻辑类与资源守卫测试的被测对象，用例数与 v2.5.9 持平）。
- 提交前自查（用户"检查好没有 bug 再提交"）：
  - 全量 `git diff` 过目（8 文件 +65/−39），无意外文件混入；
  - 滑条几何推演：标点页 4 行（22+16+16+18=88px）压进 108px 内容区，最小面板（panelH=300 → 内容区 108px）下不与底部按钮行重叠；
  - 滑条 id 0..7 全局唯一性核对；
  - 语言键双语键集一致性：zh/en 各 225 键、`zh-only`/`en-only` 均空集；`match_timer` 残留引用全局 rg 仅剩 `match_timer_over`（在用）与 cmd.timer 系列（指令反馈，非 HUD）；
  - mapSmoke 源集交叉核对：仅引用 `MARKER_PING_TYPE`，不受本次变更影响；
  - JSON 双文件 `json.load` 校验通过。
- jar：`drag-inventory-1.21.1-neoforge-2.6.0.jar` 414,161 字节，SHA256 `71040ab14130a9a4f2c5cccd084a9893ff3108dcf50a824e35dade4ff0aa01f3`。
- jar 内复核：`MapConfig.class` 含 `player_marker_size`/`PLAYER_MARKER_SIZE` ✓；`MapCommands.class` 含 `player_size`/`draginventory.map.cmd.player_size` ✓；双语 lang 各含 `player_size` ×2、`match_timer` 仅剩 `match_timer_over` ×1 ✓。
- 打包：pack-release.py VER=2.6.0 / EXPECT_ENTRIES=183（181 + RELEASE_NOTES-v2.6.0.md + v2.6.0/VALIDATION.md），断言一次命中。
- 遗留实机复测项（沙箱无显示环境）：标点页滑条拖动预览缩放、最小档清晰度、倒计时数字居中/末 60 秒红闪/秒位脉冲、`/map player_size` 反馈百分比。

## 经验教训

- 长句子中文文案的引号字符（`"…"` ASCII 直引号 vs `“…”` 全角弯引号）在不同文件中混用——Edit 匹配失败时先 `sed -n` 看原文再重试，不要凭记忆拼 old_str。
- MultiEdit 非原子坑再次确认（wiki 批量编辑中一段失败，前三段已应用）：失败后先 `rg` 核对已应用状态，再逐段补齐，避免整体重放造成重复插入。
- README 中存在跨版本陈旧引用（"联机时需要本模组的 2.5.7 JAR"自 v2.5.7 起未随版本更新）——全量 `rg` 版本号扫描是发布门禁的必要步骤，本次顺手修正。
