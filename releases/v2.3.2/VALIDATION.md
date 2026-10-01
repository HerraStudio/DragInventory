# Drag Inventory 2.3.2 验证

2026-10-01。Minecraft1.21.1 / NeoForge21.1.251 / LDLib2 2.2.40 / Java21。

完整发布构建通过，131项单元测试、0失败/错误。输入测试包括首次即时动作、450/451ms边界、三击、菜单取消窗口；FIFO回归包括满5时双击换目标仅一次最终淘汰、刷新后保留旧标点及顺序、外部写入不被覆盖、失效标点不复活。

未安装GWO与安装实际GWO Fix-0.5两种独立客户端/集成服务器均通过完整标点检查：真实MouseHandler.onPress→MouseButton.Pre同一回调内标点已进入共享store，实测回调0–1ms，没有等待450ms；双击地面、墙、友善动物、掉落物及天空均为固定ENEMY，第二次瞄准决定落点，天空为128格方向终点。首个普通标点被正确升级，不额外占用FIFO。按下被标点消费、松开和菜单输入透传。

物品拾取自动清除、60秒过期、5点FIFO、非线性出现动画、FOV/GUI缩放、屏边投影和资源重载回归通过。原Wheel/GWO快速切枪代码未修改。

GWO共存测试没有使用受保护枪包；本次标点逻辑独立于枪械型号，原物品图标和GWO渲染代码保留。发布JAR没有测试类。

JAR SHA256: 4260db01a02067e64ac7595099bb1adcdf3b1c2903fc8102fc440077d4d8fefb

```text
no-gwo: [13:36:36] [Render thread/INFO] [TacticalMarkerSmoke/]: TACTICAL_MARKER_INPUT_SMOKE_PASS: immediate same-Pre-callback single clicks under 50ms; 450ms unrestricted enemy double clicks on ground/passive animal/item/wall/sky; current second ray and fixed 128m sky endpoint; no extra provisional ping; capacity and refresh rollback preserve one FIFO eviction; press consumed and release/menu pass through; fixed enemy location, nonlinear appearance, pickup, expiry, GUI/FOV/reload
gwo: [13:48:13] [Render thread/INFO] [TacticalMarkerSmoke/]: TACTICAL_MARKER_INPUT_SMOKE_PASS: immediate same-Pre-callback single clicks under 50ms; 450ms unrestricted enemy double clicks on ground/passive animal/item/wall/sky; current second ray and fixed 128m sky endpoint; no extra provisional ping; capacity and refresh rollback preserve one FIFO eviction; press consumed and release/menu pass through; fixed enemy location, nonlinear appearance, pickup, expiry, GUI/FOV/reload
```
