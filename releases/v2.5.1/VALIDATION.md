# 2.5.1 验证记录（错误修正批次）

- 日期：2026-10-01
- 环境：Minecraft 1.21.1 / NeoForge 21.1.251 / LDLib2 2.2.40 / Java 21（Eclipse Adoptium OpenJDK 21）
- 用户实机日志（latest.log，Windows 11 + RTX 4060 + Sodium/Iris/Connector 重型整合包）佐证：模组加载链路正常、无任何异常堆栈，/map 指令可正常打开地图与设置 GUI——问题定性为行为逻辑而非崩溃。

## 构建与测试

- `gradlew compileJava`（含 -PmapSmoke 源集）通过，仅既有 NeoForge 弃用 API 警告（与 2.5.0 相同的 4 条）。
- `gradlew test --rerun` 强制重跑：15 测试类 / 138 用例 / 0 失败 / 0 错误 / 0 跳过。
- `gradlew build` 通过，产出 drag-inventory-1.21.1-neoforge-2.5.1.jar（390,104 B）。
- jar 解包验证：mods.toml version=2.5.1；zh_cn/en_us 各 223 键且键集合一致（新增 ping_notice / no_markers_notice / cleared_notice / clean_on_open 四键）；MapConfigEvents/FactoryMapScreen/FactoryMinimapHud 类均在包内。

## 修正项逐条自查

1. 配置迁移（标记制）：migrateLegacyConfig 改为 general.migrated 驱动——首次加载无论文件结构（v2.4.0 直升 / 运行过 2.5.0 后的"新结构 + enabled=false 残留"）一律复位 enabled 并置位落盘；已迁移（migrated=true）后不再干预，尊重主动 /map off。删除了按 contrast 键/有无 [minimap] 段判断的旧启发式。
2. 清屏默认：FactoryMapScreen.cleanMode 初始化改为读取 general.clean_on_open（默认 false）；设置 GUI 视觉页新增开关（内容区末行 cy+92，几何与命中对齐）；H 键与眼睛按钮行为不变。
3. 按键反馈：地图内 1/2/3 → selectPingTypeWithNotice（写配置 + 底部居中通知 1.4s 渐隐）；C → clearMarkersWithNotice（无标点提示"当前没有标点"，有则清除并报数量）。
4. 闪烁治理：Layer.id 改为 floorY（discover 与 resolve 空目录路径同步，MinimapView 合成层一致）；resolveAutoFloor 增加迟滞（候选不严格更近则不切）；静止玩家（水平位移 <4 格）目录重建间隔 40→200 tick；小地图楼层缓存 ±2 死区。

## 实机建议

- 升级路径验证：用旧配置（enabled=false，无论 v2.4.0 还是 2.5.0 结构）启动一次，确认日志出现迁移行、M 键与小地图恢复。
- 地图内按 1/2/3/C 确认底部反馈；H 切清屏/完整界面；设置视觉页开关"打开时进入清屏模式"后重开地图确认开局即清屏。
- 原地图闪烁场景（多层工厂、楼梯口停留）复测：换层提示只在真实换层时出现，地图不再反复重烤。

JAR SHA256: 5166d0af36927818b7a66dd0e682a9e45497ea84023b9186f4fbc660f5e50435
