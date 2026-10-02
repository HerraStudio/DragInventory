# 2.5.2 验证记录（小地图修复与楼层自动跟随批次）

- 日期：2026-10-01
- 环境：Minecraft 1.21.1 / NeoForge 21.1.251 / LDLib2 2.2.40 / Java 21（OpenJDK 21.0.12，Debian）
- 触发批次：用户对 2.5.1 的七项实测反馈（小地图消失 / GUI 调尺寸 / 弹窗位置 / 键位 / 手动层移除 / 错误材质 / 快捷键提示）。

## 根因分析（反编译实证）

- 反编译 NeoForge 21.1.251 `TextureManager.register`：同 id 重复注册会 `safeClose` 旧纹理（关闭 DynamicTexture 的 NativeImage 并释放 GL 纹理 id）。
- 事故链：小地图 HUD、大地图屏幕、设置预览三个 `FactoryMapTiles` 实例的瓦片序号各自从 0 递增，同位置瓦片生成同名 `draginventory:factory_map/tile_{floorY}_{x}_{z}_{seq}`——后注册者直接销毁先注册者的纹理。受害实例继续烘焙时 `getPixels()` 返回 null 抛 NPE（v2.5.1 的熔断标志是"本会话永久布尔"，跨世界不重置）→ 小地图消失且设置开关无效；未抛异常的路径 blit 到别人的纹理 → 错误材质/串图。

## 构建与测试

- `gradlew compileJava -PmapSmoke`（含 mapSmoke 源集）通过，仅既有 NeoForge 弃用 API 警告 4 条（与 2.5.0/2.5.1 基线相同）。
- `gradlew test --rerun` 强制重跑：15 测试类 / 138 用例 / 0 失败 / 0 错误 / 0 跳过。
- `gradlew jar` 产出 drag-inventory-1.21.1-neoforge-2.5.2.jar（388,063 B）。
- jar 解包验证：mods.toml version=2.5.2；zh_cn/en_us 各 218 键且键集合一致（删 floor_auto/floor_manual/floor_step/manual_y/auto_layer 七键，新增 floor_auto_follow/auto_floor_note，controls_full 更新为含 1/2/3 的主键盘键位表）。
- 字节码验证（javap）：FactoryMapTiles 含静态 AtomicInteger TILE_SEQ；FactoryMinimapHud 含 brokenUntil/failureCount/floorCacheWorld（可恢复熔断）；FactoryMapSession 无 manualFloor/cycleFloor/autoFloor 残留；FactoryMapLayerResolver 含 sharedCatalog/alignToSharedCatalog。

## 反馈项逐条自查

1. 小地图消失（反馈①）：纹理 id 跨实例全局唯一（TILE_SEQ 静态原子递增）消除 NPE 源头；熔断改为 10 秒冷却自动重试、连续 3 次才永久停用；换世界（ClientLevel 身份比较）重置熔断与楼层缓存——"旧游戏显示一会后消失、新游戏不显示、GUI 重开无效"三条症状全部覆盖。
2. GUI 调节小地图大小（反馈②）：行为页重排为 跟随(0)/小地图开关(16)/小地图缩放(34)/小地图尺寸(52)/最小缩放(70)/最大缩放(88)，末行 88+18=106 ≤ 内容区 108px，不再与底部按钮行重叠；尺寸滑条 64~256 即时生效（每帧读取 MINIMAP_SIZE）。
3. 弹窗从左侧伸出（反馈③）：popoverLayout 改为 x=8 左侧锚定（对标 2.4.0 左面板但 210×240 占位更小），开启时 180ms ease-out 滑入动画（popoverOpenedAt 时间戳驱动）；overPanel/clickChrome 命中几何同步。
4. 键位主键盘化（反馈④⑦）：地图内按键仅 M/ESC/H/C/1/2/3/空格/滚轮（GLFW_KEY_1/2/3 本就只响应主键盘数字）；PgUp/PgDn/Home 随手动层功能移除；KeyMappings（M/C/H）注册不变，均为主键盘键。
5. 去手动层 + 自动层完善（反馈⑤）：FactoryMapSession 删 manualFloor 全套（cycleFloor/setAutoFloor/applyManualFloor/syncFloorMode），MapConfig 删 auto_floor/manual_floor_y（旧键由 NeoForge 加载时自动清理），MapCommands 删 floor 子树，MapSettingsScreen 楼层页只留 span/radius；新增共享层目录（discover 写入静态缓存，小地图 alignToSharedCatalog 对齐同一层）。
6. 错误材质（反馈⑥）：同根因（纹理 id 唯一化）；另加 bakeStep 防御——纹理被外部关闭（pixels=null）时丢弃残壳重建，不再向已关闭的 NativeImage 写入。
7. 快捷键提示补全（反馈⑦）：controls_full 中文化为"M/ESC 关闭 · 左键 拖动 · 右键 标点 · 滚轮 缩放 · 空格 居中 · 1/2/3 标点类型 · %s 界面 · %s 清除标点"（H/C 键名动态跟随用户改键）。

## 实机建议

- 复测小地图：进入存档待小地图显示后，开一次大地图与设置 GUI（旧版触发纹理冲突的场景），回到游戏确认小地图仍显示且画面正确；退到主菜单再进另一存档，确认小地图立即出现（换世界熔断复位）。
- 行为页拖动"小地图尺寸"滑条确认边长实时变化；图例弹窗确认从左侧滑入；底部提示确认 1/2/3 已列出。
- 多层建筑上下移动（楼梯/电梯），确认换层提示只在真实换层时出现、大小地图显示同一层。

JAR SHA256: 7d738ba64df6ae9640374d4b189788b64d877e985dec1bb987266aa9455cd1cf
