package dev.draginventory.client.compass;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Slider;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 方位条图形化设置界面（LDLib2 MODERN 主题 + 自定义深色面板）：
 * <ul>
 *   <li>顶部实时预览：同一个 {@link CompassWidget} 渲染器，支持“自动摆动”与
 *       手动滑杆模拟转向，所有调整即时生效</li>
 *   <li>左侧导航 + 右侧滚动内容，覆盖全部配置项</li>
 *   <li>颜色覆盖行支持一键回到配色方案</li>
 *   <li>标点页内置测试标点按钮，可视化验证联动效果</li>
 * </ul>
 */
public final class CompassSettingsScreen {

    private static final int PANEL_BG = 0xF214181B;
    private static final int PANEL_BORDER = 0x33FFFFFF;
    private static final int SUBPANEL_BG = 0x59000000;

    private final CompassWidget preview = new CompassWidget(true);
    private final List<UIElement> sections = new ArrayList<>();
    private final List<Button> navButtons = new ArrayList<>();
    /** 各行控件的静默刷新器（Reset 后把 UI 控件同步回默认值，不触发写入）。 */
    private final List<Runnable> refreshers = new ArrayList<>();
    private int selected;

    private CompassSettingsScreen() {}

    public static ModularUIScreen create() {
        var screen = new CompassSettingsScreen();
        var ui = screen.build();
        return new ModularUIScreen(ui, Component.translatable("draginventory.compass.title"));
    }

    private ModularUI build() {
        var root = new UIElement().layout(l -> l.widthPercent(100).heightPercent(100)
                .alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER));

        var panel = new UIElement().layout(l -> l.width(496).maxWidthPercent(98).maxHeightPercent(96)
                .flexDirection(FlexDirection.COLUMN).paddingAll(12).gapAll(7));
        panel.style(s -> s.background(new SDFRectTexture()
                .setColor(PANEL_BG).setRadius(12f).setStroke(1f).setBorderColor(PANEL_BORDER)));

        panel.addChild(buildHeader());
        panel.addChild(buildPreviewBox());
        panel.addChild(buildBody());
        panel.addChild(buildFooter());
        root.addChild(panel);
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MODERN)));
    }

    // ==================== 组成部分 ====================

    private UIElement buildHeader() {
        var header = new UIElement().layout(l -> l.flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).height(22));
        var title = new Label().setText(Component.translatable("draginventory.compass.title"));
        title.layout(l -> l.flex(1));
        header.addChild(title);
        header.addChild(new Label().setText("HERRA"));
        return header;
    }

    private UIElement buildPreviewBox() {
        var box = new UIElement().layout(l -> l.flexDirection(FlexDirection.COLUMN)
                .height(84).paddingAll(8).gapAll(4));
        box.style(s -> s.background(new SDFRectTexture().setColor(SUBPANEL_BG).setRadius(8f)));

        // 实时预览控件（与 HUD 完全同一渲染路径）。
        var previewArea = new UIElement().layout(l -> l.widthPercent(100).height(56));
        preview.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .leftPercent(50).marginLeft(-170).top(0).width(340).height(58));
        previewArea.addChild(preview);
        box.addChild(previewArea);

        var controls = new UIElement().layout(l -> l.flexDirection(FlexDirection.ROW)
                .justifyContent(AlignContent.CENTER).gapAll(12).height(16));
        controls.addChild(hint("draginventory.compass.ui.sway"));
        Switch sway = new Switch().setOn(true).setOnSwitchChanged(on -> preview.setSway(on));
        sway.layout(l -> l.width(30));
        controls.addChild(sway);
        controls.addChild(hint("draginventory.compass.ui.simulate"));
        Slider heading = new Slider.Horizontal().setRange(0f, 360f).setValue(206f)
                .setOnValueChanged(v -> {
                    preview.setSway(false);
                    sway.setOn(false, false);
                    preview.setPreviewHeading(v);
                });
        // Reset 后预览滑杆同步回 206°（静默，不回调）。
        refreshers.add(() -> heading.setValue(206f, false));
        heading.layout(l -> l.width(140));
        controls.addChild(heading);
        box.addChild(controls);
        return box;
    }

    private UIElement buildBody() {
        var body = new UIElement().layout(l -> l.flexDirection(FlexDirection.ROW).flex(1).gapAll(10));

        var nav = new UIElement().layout(l -> l.flexDirection(FlexDirection.COLUMN).width(104).gapAll(4));
        String[] keys = {
                "draginventory.compass.ui.section.general",
                "draginventory.compass.ui.section.position",
                "draginventory.compass.ui.section.style",
                "draginventory.compass.ui.section.colors",
                "draginventory.compass.ui.section.animation",
                "draginventory.compass.ui.section.content",
                "draginventory.compass.ui.section.markers",
                "draginventory.compass.ui.section.about",
        };
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            Button button = new Button().setText(Component.translatable(keys[i]))
                    .setOnClick(e -> select(index));
            navButtons.add(button);
            nav.addChild(button);
        }

        // 注意：layout() 返回 UIElement 而非 ScrollerView，必须显式声明类型
        // 才能访问 addScrollViewChild（UIElement 上没有这个方法）。
        ScrollerView content = new ScrollerView();
        content.layout(l -> l.flex(1).paddingAll(4));
        content.addScrollViewChild(sectionGeneral());
        content.addScrollViewChild(sectionPosition());
        content.addScrollViewChild(sectionStyle());
        content.addScrollViewChild(sectionColors());
        content.addScrollViewChild(sectionAnimation());
        content.addScrollViewChild(sectionContent());
        content.addScrollViewChild(sectionMarkers());
        content.addScrollViewChild(sectionAbout());

        body.addChild(nav);
        body.addChild(content);
        select(0);
        return body;
    }

    private UIElement buildFooter() {
        var footer = new UIElement().layout(l -> l.flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).height(26));
        Button reset = new Button().setText(Component.translatable("draginventory.compass.ui.reset"))
                .setOnClick(e -> {
                    CompassConfig.resetToDefaults();
                    preview.setPreviewHeading(206f);
                    // 全部控件静默刷新回默认值，避免 UI 显示过期值、
                    // 用户再拖动时把旧值写回去。
                    for (Runnable refresher : refreshers) refresher.run();
                });
        reset.layout(l -> l.width(96));
        Button done = new Button().setText(Component.translatable("draginventory.compass.ui.done"))
                .setOnClick(e -> {
                    // 关闭设置界面时立即落盘，不等防抖窗口。
                    CompassConfig.flush();
                    Minecraft.getInstance().setScreen(null);
                });
        done.layout(l -> l.width(96));
        var spacer = new UIElement().layout(l -> l.flex(1));
        footer.addChild(reset);
        footer.addChild(spacer);
        footer.addChild(done);
        return footer;
    }

    // ==================== 分区 ====================

    private UIElement sectionGeneral() {
        var section = section("draginventory.compass.ui.section.general");
        section.addChild(boolRow("draginventory.compass.cfg.enabled", CompassConfig.ENABLED));
        section.addChild(boolRow("draginventory.compass.cfg.hide_debug", CompassConfig.HIDE_WITH_DEBUG));
        section.addChild(boolRow("draginventory.compass.cfg.entry_animation", CompassConfig.ENTRY_ANIMATION));
        return section;
    }

    private UIElement sectionPosition() {
        var section = section("draginventory.compass.ui.section.position");
        section.addChild(intSliderRow("draginventory.compass.cfg.offset_x", CompassConfig.OFFSET_X, -640, 640));
        // 与配置/指令范围保持一致：命令设置起范围外值时，滑条必须能如实回显。
        section.addChild(intSliderRow("draginventory.compass.cfg.offset_y", CompassConfig.OFFSET_Y, -640, 640));
        section.addChild(intSliderRow("draginventory.compass.cfg.width", CompassConfig.BAR_WIDTH, 120, 520));
        section.addChild(doubleSliderRow("draginventory.compass.cfg.scale", CompassConfig.SCALE, 0.5, 2.0));
        return section;
    }

    private UIElement sectionStyle() {
        var section = section("draginventory.compass.ui.section.style");
        List<String> styles = new ArrayList<>();
        for (CompassStyle style : CompassStyle.all()) styles.add(style.id());
        section.addChild(selectorRow("draginventory.compass.cfg.style", CompassConfig.STYLE, styles,
                id -> Component.translatable("draginventory.compass.style." + id)));
        List<String> palettes = new ArrayList<>();
        for (CompassPalette palette : CompassPalette.all()) palettes.add(palette.id());
        section.addChild(selectorRow("draginventory.compass.cfg.palette", CompassConfig.PALETTE, palettes,
                id -> Component.translatable("draginventory.compass.palette." + id)));
        section.addChild(doubleSliderRow("draginventory.compass.cfg.opacity", CompassConfig.OPACITY, 0.15, 1.0));
        section.addChild(boolRow("draginventory.compass.cfg.degree_symbol", CompassConfig.DEGREE_SYMBOL));
        return section;
    }

    private UIElement sectionColors() {
        var section = section("draginventory.compass.ui.section.colors");
        section.addChild(colorRow("draginventory.compass.cfg.color_accent", CompassConfig.COLOR_ACCENT,
                () -> CompassWidget.currentPalette().accent()));
        section.addChild(colorRow("draginventory.compass.cfg.color_text", CompassConfig.COLOR_TEXT,
                () -> CompassWidget.currentPalette().text()));
        section.addChild(colorRow("draginventory.compass.cfg.color_dim", CompassConfig.COLOR_DIM,
                () -> CompassWidget.currentPalette().dim()));
        section.addChild(colorRow("draginventory.compass.cfg.color_tick", CompassConfig.COLOR_TICK,
                () -> CompassWidget.currentPalette().tick()));
        section.addChild(colorRow("draginventory.compass.cfg.color_background", CompassConfig.COLOR_BACKGROUND,
                () -> CompassWidget.currentPalette().background()));
        return section;
    }

    private UIElement sectionAnimation() {
        var section = section("draginventory.compass.ui.section.animation");
        section.addChild(doubleSliderRow("draginventory.compass.cfg.smoothness", CompassConfig.SMOOTHNESS,
                CompassHeading.OMEGA_MIN, CompassHeading.OMEGA_MAX));
        section.addChild(boolRow("draginventory.compass.cfg.snap_assist", CompassConfig.SNAP_ASSIST));
        section.addChild(intSliderRow("draginventory.compass.cfg.snap_range", CompassConfig.SNAP_RANGE, 2, 20));
        section.addChild(boolRow("draginventory.compass.cfg.inertia_tilt", CompassConfig.INERTIA_TILT));
        section.addChild(doubleSliderRow("draginventory.compass.cfg.tilt_intensity", CompassConfig.TILT_INTENSITY, 0.0, 3.0));
        return section;
    }

    private UIElement sectionContent() {
        var section = section("draginventory.compass.ui.section.content");
        section.addChild(intSliderRow("draginventory.compass.cfg.range", CompassConfig.RANGE, 60, 360));
        // 候选覆盖配置允许的全部合法值（5~30 / 15~90）：
        // 外部编辑 TOML 写入的值也能在 UI 正确回显，不再出现“无选中项”。
        section.addChild(selectorRow("draginventory.compass.cfg.minor_step", CompassConfig.MINOR_STEP,
                List.of("5", "10", "15", "20", "25", "30"), s -> Component.literal(s + "\u00B0")));
        section.addChild(selectorRow("draginventory.compass.cfg.number_step", CompassConfig.NUMBER_STEP,
                List.of("15", "20", "30", "45", "60", "90"), s -> Component.literal(s + "\u00B0")));
        section.addChild(boolRow("draginventory.compass.cfg.show_cardinals", CompassConfig.SHOW_CARDINALS));
        section.addChild(boolRow("draginventory.compass.cfg.show_intercardinals", CompassConfig.SHOW_INTERCARDINALS));
        section.addChild(boolRow("draginventory.compass.cfg.show_numbers", CompassConfig.SHOW_NUMBERS));
        section.addChild(boolRow("draginventory.compass.cfg.cjk_labels", CompassConfig.CJK_LABELS));
        section.addChild(doubleSliderRow("draginventory.compass.cfg.cardinal_scale", CompassConfig.CARDINAL_SCALE, 0.8, 2.0));
        return section;
    }

    private UIElement sectionMarkers() {
        var section = section("draginventory.compass.ui.section.markers");
        section.addChild(boolRow("draginventory.compass.cfg.markers_enabled", CompassConfig.MARKERS_ENABLED));
        section.addChild(boolRow("draginventory.compass.cfg.markers_tactical", CompassConfig.MARKERS_TACTICAL));
        section.addChild(boolRow("draginventory.compass.cfg.markers_death", CompassConfig.MARKERS_DEATH));
        section.addChild(boolRow("draginventory.compass.cfg.markers_distance", CompassConfig.MARKERS_DISTANCE));
        section.addChild(boolRow("draginventory.compass.cfg.markers_labels", CompassConfig.MARKERS_LABELS));
        section.addChild(boolRow("draginventory.compass.cfg.markers_pulse", CompassConfig.MARKERS_PULSE));
        section.addChild(boolRow("draginventory.compass.cfg.markers_test", CompassConfig.MARKERS_TEST));

        var buttons = new UIElement().layout(l -> l.flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(6).paddingAll(6));
        buttons.addChild(testButton("draginventory.compass.ui.test_enemy", () ->
                CompassCommands.spawnTestMark(CompassMark.Kind.ENEMY, 15)));
        buttons.addChild(testButton("draginventory.compass.ui.test_location", () ->
                CompassCommands.spawnTestMark(CompassMark.Kind.LOCATION, 0)));
        buttons.addChild(testButton("draginventory.compass.ui.test_item", () ->
                CompassCommands.spawnTestMark(CompassMark.Kind.ITEM, -15)));
        var clear = new Button().setText(Component.translatable("draginventory.compass.ui.test_clear"))
                .setOnClick(e -> CompassHub.clearTestMarks());
        clear.layout(l -> l.flex(1));
        buttons.addChild(clear);
        section.addChild(buttons);
        section.addChild(hint("draginventory.compass.ui.markers_hint"));
        return section;
    }

    private UIElement sectionAbout() {
        var section = section("draginventory.compass.ui.section.about");
        section.addChild(hint("draginventory.compass.ui.about_1"));
        section.addChild(hint("draginventory.compass.ui.about_2"));
        section.addChild(hint("draginventory.compass.ui.about_3"));
        section.addChild(hint("draginventory.compass.ui.about_4"));
        return section;
    }

    // ==================== 行构建器 ====================

    private UIElement section(String titleKey) {
        var section = new UIElement().layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(7).paddingAll(4));
        var label = new Label().setText(Component.translatable(titleKey));
        label.layout(l -> l.paddingBottom(2));
        section.addChild(label);
        section.addChild(new UIElement().layout(l -> l.widthPercent(100).height(1))
                .style(s -> s.background(new SDFRectTexture().setColor(0x30FFFFFF).setRadius(0.5f))));
        sections.add(section);
        return section;
    }

    private UIElement boolRow(String key, net.neoforged.neoforge.common.ModConfigSpec.BooleanValue config) {
        var row = row();
        row.addChild(rowLabel(key));
        // 值变化守卫：开关初始化（程序化 setOn）也可能回调监听器，
        // 无值变化时直接跳过，避免每次打开设置界面就多一次无意义的落盘。
        var control = new Switch().setOn(config.get())
                .setOnSwitchChanged(on -> {
                    if (on != config.get()) CompassConfig.set(config, on);
                });
        control.layout(l -> l.width(34));
        refreshers.add(() -> control.setOn(config.get(), false));
        row.addChild(control);
        return row;
    }

    private UIElement intSliderRow(String key, net.neoforged.neoforge.common.ModConfigSpec.IntValue config,
                                   int min, int max) {
        var row = row();
        row.addChild(rowLabel(key));
        var slider = new Slider.Horizontal().setRange(min, max).setValue(config.get().floatValue())
                .setOnValueChanged(v -> CompassConfig.set(config, Math.round(v)));
        refreshers.add(() -> slider.setValue(Float.valueOf(config.get()), false));
        slider.layout(l -> l.flex(1));
        row.addChild(slider);
        return row;
    }

    private UIElement doubleSliderRow(String key, net.neoforged.neoforge.common.ModConfigSpec.DoubleValue config,
                                      double min, double max) {
        var row = row();
        row.addChild(rowLabel(key));
        var slider = new Slider.Horizontal().setRange((float) min, (float) max)
                .setValue(config.get().floatValue())
                .setOnValueChanged(v -> CompassConfig.set(config, (double) v));
        refreshers.add(() -> slider.setValue(Float.valueOf((float) config.get().doubleValue()), false));
        slider.layout(l -> l.flex(1));
        row.addChild(slider);
        return row;
    }

    private <T> UIElement selectorRow(String key, net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> config,
                                      List<T> candidates, java.util.function.Function<T, Component> namer) {
        var row = row();
        row.addChild(rowLabel(key));
        var selector = new Selector<T>().setCandidates(candidates)
                .setCandidateUIProvider(UIElementProvider.text(namer))
                .setSelected(config.get(), false)
                .setOnValueChanged(value -> CompassConfig.set(config, value));
        refreshers.add(() -> selector.setSelected(config.get(), false));
        selector.layout(l -> l.flex(1));
        row.addChild(selector);
        return row;
    }

    /** Selector 泛型擦除兜底：数字配置用字符串候选。 */
    private UIElement selectorRow(String key, net.neoforged.neoforge.common.ModConfigSpec.IntValue config,
                                  List<String> candidates, java.util.function.Function<String, Component> namer) {
        var row = row();
        row.addChild(rowLabel(key));
        var selector = new Selector<String>()
                .setCandidates(candidates)
                .setCandidateUIProvider(UIElementProvider.text(namer))
                .setSelected(String.valueOf(config.get()), false)
                .setOnValueChanged(value -> CompassConfig.set(config, Integer.parseInt(value)));
        refreshers.add(() -> selector.setSelected(String.valueOf(config.get()), false));
        selector.layout(l -> l.flex(1));
        row.addChild(selector);
        return row;
    }

    private UIElement colorRow(String key, net.neoforged.neoforge.common.ModConfigSpec.IntValue config,
                               Supplier<Integer> paletteColor) {
        var row = row();
        row.addChild(rowLabel(key));

        var selector = new ColorSelector().setOnColorChangeListener(color ->
                CompassConfig.set(config, color & 0xFFFFFF));
        selector.layout(l -> l.flex(1));
        Runnable syncColor = () -> selector.setColor(
                (config.get() >= 0 ? config.get() : paletteColor.get()) | 0xFF000000, false);
        syncColor.run();
        refreshers.add(syncColor);

        var reset = new Button().setText(Component.translatable("draginventory.compass.ui.follow_palette"))
                .setOnClick(e -> {
                    CompassConfig.set(config, -1);
                    selector.setColor(paletteColor.get() | 0xFF000000, false);
                });
        reset.layout(l -> l.width(70));
        row.addChild(reset);
        row.addChild(selector);
        return row;
    }

    private Button testButton(String key, Runnable action) {
        Button button = new Button().setText(Component.translatable(key)).setOnClick(e -> action.run());
        button.layout(l -> l.flex(1));
        return button;
    }

    private UIElement rowLabel(String key) {
        var label = new Label().setText(Component.translatable(key));
        label.layout(l -> l.width(128));
        return label;
    }

    private UIElement hint(String key) {
        return new Label().setText(Component.translatable(key));
    }

    private UIElement row() {
        return new UIElement().layout(l -> l.flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(10));
    }

    // ==================== 导航 ====================

    private void select(int index) {
        selected = index;
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).setVisible(i == index);
        }
        for (int i = 0; i < navButtons.size(); i++) {
            boolean active = i == index;
            navButtons.get(i).style(s -> s.background(new SDFRectTexture()
                    .setColor(active ? 0x40E8C3 : 0x00000000).setRadius(6f)));
        }
    }
}
