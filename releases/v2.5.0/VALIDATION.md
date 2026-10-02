# Drag Inventory 2.5.0 验证

2026-10-01。Minecraft1.21.1 / NeoForge21.1.251 / LDLib2 2.2.40 / Java21。

完整发布构建（gradle build）通过；15 个测试类、138 项单元测试全部通过，0 失败/0 错误/0 跳过（含新增 FactoryMapPaletteTest 7 例：VOID 全透明、语义强调恒定、六预设滤镜 LUT、样式切换置脏与解析回退）。

引擎层（阶段1）：方块颜色经 state.getMapColor 采样并完成 ABGR→ARGB 转换写入瓦片，六种滤镜预设驱动色板 LUT 管线（房门/楼梯/边界语义强调恒定不受预设影响），未加载区域瓦片 VOID 全透明化；手动楼层与指令写入同一配置源，自动楼层带层高跨度死区防抖。

屏幕层（阶段2）：清屏模式与完整 HUD 在 640×360 / 800×450 / 640×512 / 1920×1080 四档画布几何自查通过——图例+标点管理弹窗不溢出、18px 按钮命中域、工具栏与缩放控件无重叠；清屏分支结构审计确认标点不随界面隐藏。

小地图层（阶段3）：registerAboveAll 独立 GUI 层注册，七项显隐守卫（世界/玩家/旁观/F1/任意界面/过场/总开关）；合成活动层零 discover，静态 LRU 瓦片池与主地图互不干扰；对局计时默认 600 秒占位，MapMatchTimer.Provider 注册即接真实剩余时间。

十项用户反馈逐项代码审查 D1-D9 全过（透明度等设置接线、/map off 门禁、彩色采样与预设、自动楼层、图例弹窗与 C 键、18px 按钮、清屏默认与 H 键、右下角说明、小地图与计时，证据表结论见开发工作日志 Task 5）。

语言文件中英双语各 219 键且键集合一致，占位符断言通过（cmd.info 9=9、hud_hint 1、controls_full 2、match_timer 1）。

错误修正（重发批次，问诊过程全链路复核）：问诊阶段在 xvfb + llvmpipe 软渲染环境下实测 mod 加载链路正常（Drag Inventory 2.5.0 被正常发现、资源重载成功、无任何加载错误），定位根因为 v2.4.0 配置残留——旧版 /map off 写入的 enabled=false 在 v2.5.0 接线后生效导致 M 键与小地图静默失效。修正四项：① MapConfigEvents 配置 Loading 事件新增 v2.4.0→v2.5.0 迁移（检测旧结构 contrast 键+无 [minimap] 段 → enabled 复位 true 并落盘，新结构配置不迁移尊重用户主动关闭）；② M 键在地图关闭时改为动作栏提示（新键 cmd.map_disabled_hint 双语）；③ cmd.enabled/disabled 文案补全影响范围与恢复方式；④ 小地图 HUD 层渲染异常保护（NeoForge LayeredDraw 无异常保护实证核查，首次异常停用小地图本会话并记录日志，不再波及整条 HUD 链）。双语 218→219 键、compileJava+test 138/0 复跑全绿、mapSmoke 源集编译通过、jar 解包验证（mods.toml/迁移字节码/新语言键齐全）。

mapSmoke 实机验证建议在带显示环境执行：`./gradlew runClient -PmapSmoke -PweaponGameDir=run-map-redesign`（六张新截图流程：清屏默认 → H 完整界面 → 图例弹窗 → 数字键 1/2/3 放点 → 楼层切换 → guiScale 4 命中）。

JAR SHA256: 0f5499c46921edd3eac0d1ea73c3f9b66dc498e2f67114d626007b8fbe641dab（重发批次，含错误修正；首版 c0dfcbc15a5751c8255ad6b25b0131d51418b455f0f189d2caa1d235e1734a6e 已作废）
