# 2.5.3 验证记录（键位标签 / 小地图尺寸 / 快捷键说明 / 滤镜重做批次）

- 日期：2026-10-01
- 环境：Minecraft 1.21.1 / NeoForge 21.1.251 / LDLib2 2.2.40 / Java 21（OpenJDK 21.0.12，Debian）
- 触发批次：用户对 2.5.2 的四项实测反馈（按键绑定找不到开/关大地图 / 小地图默认过大 / 右下角快捷键说明不全 / 滤镜不像原游戏）。

## 根因分析（反编译实证）

- 反编译 NeoForge 21.1.251 `KeyMapping`：原版三参/四参构造的默认冲突上下文是 `KeyConflictContext.UNIVERSAL`（字节码 `getstatic KeyConflictContext.UNIVERSAL` 在构造器内直接 putfield）——与原版游戏内按键（IN_GAME）的冲突检测口径不一致；`matches(int,int)` 实现不检查冲突上下文（仅键类型 + 键值比较），因此地图屏幕内 `OPEN_MAP.matches()` 关图行为在 IN_GAME 下不变。
- 反编译 `KeyBindsList`：按键绑定列表按注册数组 + 类别排序展示，Drag Inventory 分类下 5 个绑定（compass/wheel/map/map_clear_markers/map_hud）全部可见——用户"找不到开/关大地图键位"的实际卡点是标签语义（"打开战术地图"不含"大地图"字样），而非条目缺失。
- 反编译 `KeyboardHandler.keyPress`：按键映射的 `KeyMapping.click` 仅在无屏幕打开时累计（`if (screen == null)` 门控），聊天等界面打开时不会误触发 M/C。

## 构建与测试

- `gradlew compileJava -PmapSmoke`（含 mapSmoke 源集）通过，仅既有 NeoForge 弃用 API 警告 4 条（与 2.5.0~2.5.2 基线相同）。
- `gradlew test --rerun` 强制重跑两轮：15 测试类 / 142 用例 / 0 失败 / 0 错误 / 0 跳过（138 旧用例 + 4 新增滤镜断言）。
- `gradlew build` 产出 drag-inventory-1.21.1-neoforge-2.5.3.jar（389,046 B）。
- jar 解包验证：mods.toml version=2.5.3；zh_cn/en_us 各 217 键且键集合一致（删 map.hud_hint 一键，改 map/map_clear_markers/map_hud 三键标签与 controls_full）。
- 字节码验证（javap）：FactoryMapKeyBindings 含 3 处 KeyConflictContext.IN_GAME 构造；MapConfig 含 MINIMAP_SIZE_RETUNED；MapConfigEvents 含 retuneMinimapSize；FactoryMapPalette$Style 含 sCurve/wallTarget/wallWhiten/floorTarget/floorAmount 十参数。

## 反馈项逐条自查

1. 按键绑定找不到开/关大地图（反馈①）：三个地图键位标签改名为"开/关大地图 / 开/关大地图界面显示 / 清除地图标点"（Toggle Big Map / Toggle Big Map HUD / Clear Map Markers），与玩家检索词对齐；OPEN_MAP/CLEAR_MARKERS 补 IN_GAME 冲突上下文（TOGGLE_MAP_HUD 原本已是）。绑定位置与数量不变（5 个绑定均在 Drag Inventory 分类）。
2. 小地图默认过大（反馈②）：MINIMAP_SIZE 默认 128→96；存量配置一次性收敛——MapConfigEvents.retuneMinimapSize 首次加载检测 size==128（旧默认落盘值）时改写 96 并落盘，minimap.size_retuned 标记置位后玩家自改尺寸（含改回 128）被永久尊重；全新安装走新默认 96 不触发改写。
3. 右下角快捷键说明不全（反馈③）：drawControlsHint 统一清屏与完整界面两处提示（清屏此前仅"按 H 显示界面"），字号 0.7→0.6；controls_full 双语补全全部操作——M/ESC 关闭、左键拖动、右/中键标点（中键放点此前未列出）、滚轮缩放、空格居中、1/2/3 标点类型、H 界面开关、C 清除标点；H/C 键名动态跟随改键。map.hud_hint 键随之删除（双语键集合一致）。
4. 滤镜不像原游戏（反馈④）：FactoryMapPalette 全面重做——Style 扩展为十参数（新增 sCurve/wallTarget/floorTarget/floorAmount）；rebuildLut 加入 smoothstep S 曲线（单调性保持）；FLOOR/BLOCKED 向地面目标色收敛、WALL 向墙体目标色收敛；DOOR/STAIR 强调移到滤镜后（强度 0.75/0.40），不再被去饱和冲淡。六预设重调：DELTA 暗蓝黑地面+冷白建筑（#171C21/#CED2D5 实测）、TARKOV 米黄纸面+暖米白（#A19987/#D5CEC4）、DARKZONE 墨绿地面+近中性灰白（#1D2B25/#A7ADA9）、APEX 提亮增饱和、PUBG 自然暖褐、NONE 直通不变。
   - 视觉走查（VLM 两轮迭代）：第一轮发现门在单色滤镜下不明显（0.55→0.65）与塔科夫细节丢失（tint 0.30→0.24、对比 1.16→1.20）；第二轮发现暗区墙体偏绿与门色相接近（wallTarget #DCE6DA→#E0E4E0 中性化、门强调 0.65→0.75）；第三轮六格全部"清晰可辨"。单元测试同步钉住：DELTA 门色去饱和后存活（G≥100 且主导）、TARKOV 地面纸面化（变亮且 R>B）、全样式灰阶单调（S 曲线无反转）、DELTA 墙体-地面亮度差 ≥60/255。
5. 附带修复：mapSmoke 陈旧断言（v2.5.1 改默认后"opens in clean mode"必然失败）——流程改为开图即完整界面断言，H 双向切换各断言一次，截图标签 hud/clean 互换对应。

## 实机建议

- 按键绑定页确认三个新标签可检索；改 M 键后游戏内新键开图、地图内同键关图、底部说明键名跟随。
- 从 2.5.2 升级首次进存档：小地图应为 96 像素（日志可见 "Retuned minimap size 128 -> 96"）；行为页拖尺寸滑条实时生效。
- 清屏模式（若开启 clean_on_open）与完整界面均显示完整快捷键说明。
- 设置视觉页逐个切换六滤镜观察预设观感，重点确认：三角洲下房门为亮青绿方块、塔科夫纸面色调、暗区夜视绿。

JAR SHA256: 2e84d915de674253bd7ab1a4c8e643d6cbbc235046fb165d540162562f642919
