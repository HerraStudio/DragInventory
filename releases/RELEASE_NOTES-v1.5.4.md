# HERRA 方位条 HUD v1.5.4

`/compass info` 指令输出修复 + 全指令树审计。
本版本为修复版本，无配置格式变化，直接替换 jar 即可。

## 修复

### 1. `/compass info` 输出原始占位符（用户实测反馈）

- **现象**：执行 `/compass info` 输出 `朝向%s 方位%s 皮肤%s 配色%s 标点%s个`，
  占位符没有被实际数值替换。
- **根因（两层）**：
  1. 语言键 `draginventory.compass.cmd.info` 定义了 **5 个占位符**
     （朝向/方位/皮肤/配色/标点数），而代码只传了 **1 个**合并参数
     （`ON | delta/aurora | 35° | 北 | yaw 12`），未填充的占位符原样输出；
  2. 语言键里的 `%d` 是 Minecraft 翻译组件**不支持的格式说明符**——MC 语言
     系统只认 `%s`（及 `%1$s` 定位形式）与 `%%` 转义，`%d` 永远按字面显示。
- **修复**：
  - 指令改为按语言键顺序传 **5 个独立参数**：朝向度数、方位名、皮肤 id、
    配色 id、活动标点数；
  - 语言文件中英两份的 `%d` 全部改为 `%s`；
  - 标点数通过新增的 `CompassWidget.countLiveMarks()` 统计——与方位条实际
    显示同源（战术标点桥 + 测试标点 + 死亡标点 + 外部提供者），只读无副作用，
    不会顺手触发死亡标点的靠近自动清除；
  - 朝向显示对齐 `/compass copy` 的取模处理：359.7° 四舍五入到 360 时显示
    `0°` 而非 `360°`。
- 修复后输出示例：`朝向 206° · 方位 西南 · 皮肤 delta · 配色 aurora · 标点 2 个`。

### 2. 全指令树审计（18 个子命令逐一核对）

对 `/herracompass` / `/compass` / `/com` 全部 18 个子命令做了系统核对：

- **实参/占位符匹配**：全部 21 个 `cmd.*` 语言键的占位符数量与调用点实参数
  逐一比对——除 `info` 外全部正确（style/palette/offset/scale/opacity/width/
  range/smooth/copy/test_added 单参数，unknown_style/unknown_palette 双参数，
  toggle/on/off/markers/reset/test_cleared 无参数）。
- **参数范围与配置范围一致性**：offset ±640、scale 0.5~2.0、opacity 0.15~1.0、
  range 60~360、smooth 4~34、width 120~960——指令的 brigadier 参数范围与
  `CompassConfig` 的 defineInRange 完全一致，无越界写入或被静默钳制的窗口。
- **错误路径**：未进世界时 test/copy 正确报错不崩溃；未知 style/palette 报错
  并列出全部合法值；tab 补全（suggests）与合法值清单同源。
- **顺手小修**：`scale` 反馈的中文键 `缩放已更新:%s` 半角冒号统一为全角
  `缩放已更新：%s`（与其他 20 个键的排版一致）。

## 测试

- 方位条逻辑单测 74/74（环绕/跨零/弹簧/方位事件/撞色/密度自适应全量回归）。
- gradle test 73/73（强制重跑，含战术标点与方位桥接 24 项）。
- compileJava / build 全绿；javap 反汇编验证产物：`info` lambda 中
  `iconst_5` 五参数数组 + `CompassWidget.countLiveMarks()` 调用 + 
  `cmd.info` 键引用；jar 内语言键中英均已为 `%s` ×5。

## 升级说明

直接替换 jar。无新配置项，旧配置完全兼容。
