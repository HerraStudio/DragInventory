# Drag Inventory 2.4.0 验证

2026-10-01。Minecraft1.21.1 / NeoForge21.1.251 / LDLib2 2.2.40 / Java21。

完整发布构建（gradle build）通过；14 个测试类、131 项单元测试全部通过，0 失败/0 错误/0 跳过。

贴图对齐修复（阶段1）：纹理 blit 改为浮点 pose 定位，与标点、网格统一浮点坐标系；整数倍缩放启用 NEAREST 锐利采样、低于 1 像素/格回退线性，消除进图与缩放拖动后的系统性错位。

动态瓦片引擎（阶段2）：编译期验证加单元测试双通道，131 项测试含 MapCoordinateTransformTest 31 例坐标变换回归，旧预渲染路径（FactoryMapTexture/FactoryMapBounds）删除后无遗留引用。

HUD 布局重构、指令系统与设置 GUI（阶段3–5）：compileJava（含 -PmapSmoke 源集一并编译）全部通过，语言键与配置键经编译期核对；配置对账、防抖落盘与退出兜底逻辑随单测链路覆盖。

mapSmoke 实机验证建议在带显示环境执行：`./gradlew runClient -PmapSmoke -PweaponGameDir=run-map-redesign`（自动建世界检查动态瓦片渲染、楼层发现与切换、面板折叠、定位回中、标点与 GUI 缩放命中）。

JAR SHA256: 6ee47587ac04ba4d4f1ed595ee2c95205138a7aa8b08c749e8cf9e385446f936
