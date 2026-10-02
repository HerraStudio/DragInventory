# v2.5.6 验证记录（玩家图标现代化与移动动效）

## 变更清单

| 文件 | 变更 |
| --- | --- |
| `src/main/java/dev/draginventory/client/map/PlayerMarkerFX.java` | 新增：动效状态机（平滑朝向 / 速度指数平滑 / 尾迹环形缓冲），纯 JVM 可测（时钟 LongSupplier 注入，零 Minecraft import） |
| `src/main/java/dev/draginventory/client/map/FactoryMapUI.java` | `player()` 从 9 行像素阶梯 fill 重做为平滑三角几何（Tesselator TRIANGLES + position_color shader，项目内 weapon wheel 先例管线）；新增 `tri()`/`withAlpha()` 辅助；左右翼折纸明暗 + 中脊折痕 + 机腹软影 |
| `src/main/java/dev/draginventory/client/map/FactoryMinimapHud.java` | 接入 FX（尾迹残影先画垫底、主图标平滑转向+弹性+呼吸）；换世界重置 FX |
| `src/main/java/dev/draginventory/client/map/FactoryMapScreen.java` | 移除玩家图标周围 21×21 半透明方框（用户反馈）；接入 FX（尾迹含 `inMapBody` 边界过滤） |
| `src/main/java/dev/draginventory/client/map/MapSettingsScreen.java` | 设置预览接同一 FX 路径（所见即所得） |
| `src/test/java/dev/draginventory/client/map/PlayerMarkerFXTest.java` | 新增 15 组断言 |
| `build.gradle` / `src/main/resources/META-INF/neoforge.mods.toml` | 版本 2.5.5 → 2.5.6 |
| `README.md` / `wiki/战术地图.md` / `docs/factory-tactical-map.md` | 同步更新（主段、changelog、jar 名、wiki 新增"玩家纸飞机"章节与目录） |

## 门禁

- 环境：沙箱 4GB / 2 核，JAVA_HOME=Temurin jdk-21.0.12.1+1，gradle daemon `-Xmx768m`（构建期暂停 next dev，腾 509MB）。
- 第一轮 `build`：`PlayerMarkerFXTest.sameMillisecondUpdateIsIgnored` 断言错误（把指数平滑的中间值当成终值 5.0，实际 3.80）——测试侧修正断言（被测逻辑本身正确，去重生效）。
- 第二轮 `build`：**BUILD SUCCESSFUL，168 用例（153 + 15）0 失败 0 错误 0 跳过**。
- 产物：`drag-inventory-1.21.1-neoforge-2.5.6.jar` 402,495 字节，SHA256 `f1ae32e8adead4ec4f7e5eb0e52654bea49570cf8f17399f1ed93a0d83ee0968`。
- jar 内容复核：`PlayerMarkerFX.class`、`PlayerMarkerFX$TrailSample.class`、重做的 `FactoryMapUI.class` 均在包内。

## 推送前审查发现并修复的问题

1. **尾迹断流跳变（边界 bug，本轮修复）**：切后台 / 长 GC 恢复后，环形缓冲里的远古样本（年龄可达数秒）仍会被当作残影采样，导致残影瞬移到几秒前的位置。修复：样本年龄 > 800ms 即断流（`TRAIL_MAX_AGE_MS`），不采纳；配套测试 `trailBreaksAfterLongGap`（高速恢复场景推演：断流瞬间尾迹清空，500ms 后仅由新轨迹重建）。
2. **测试断言错误（第一轮 build 暴露）**：同帧去重用例期望速度一帧到 5.0，实际指数平滑中间值 3.80——修正断言并注明推演（去重失效时该值会归 0，断言区间足以区分两种行为）。
3. **渲染状态纪律**：`tri()` 进入时 `disableDepthTest/enableBlend`、退出恢复 `enableDepthTest/disableBlend`，与 GUI 常态一致（比 weapon wheel 先例更保守——先例 disable 后不恢复）；`g.flush()` 先于自定义几何提交。scissor 对 shader 绘制同样生效（小地图内裁剪验证过路径）。
4. 顺带排查：全仓库 `player(` 调用共 3 处全部接入 FX（旧签名删除，编译器保证无遗漏）；语言文件无需新增键（图标无文字）；`RenderSystem.setShaderColor` 每次显式复位，防止外部残留染色。

## 遗留

- 实机动效观感（拉伸幅度 / 尾迹透明度 / 呼吸幅度）属主观项，参数集中在 `PlayerMarkerFX` 常量区与 `FactoryMapUI.PLAYER_STRETCH/SQUEEZE`，可按反馈一键微调。
- mapSmoke 类显示环境验证仍需用户实机执行（沙箱无显示）。
