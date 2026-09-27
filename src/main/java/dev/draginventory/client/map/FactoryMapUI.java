package dev.draginventory.client.map;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.draginventory.client.TacticalMarkerManager;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class FactoryMapUI {
    private FactoryMapUI() {}

    static View create(FactoryMapSession session) {
        Minecraft mc = Minecraft.getInstance();

        UIElement root = new UIElement();
        root.getLayout()
                .widthPercent(100)
                .heightPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH);

        UIElement header = new UIElement().addClass("panel_bg");
        header.getLayout()
                .widthPercent(100)
                .height(34)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(4);

        Label title = new Label();
        title.setText(Component.translatable("draginventory.map.title"));
        title.getLayout().flexGrow(1);

        Label floor = new Label();
        floor.getLayout().width(125);

        header.addChild(title);
        header.addChild(floor);
        header.addChild(button("draginventory.map.floor_down",
                () -> session.stepFloor(mc.level, mc.player, -1)));
        header.addChild(button("draginventory.map.auto",
                () -> session.toggleAutoFloor(mc.level, mc.player)));
        header.addChild(button("draginventory.map.floor_up",
                () -> session.stepFloor(mc.level, mc.player, 1)));
        header.addChild(button("draginventory.map.center", () -> {
            if (mc.player != null) session.centerOnPlayer(mc.player);
        }));
        header.addChild(button("draginventory.map.clear", TacticalMarkerManager::clearLocationMarkers));
        header.addChild(button("draginventory.map.close", () -> mc.setScreen(null)));

        UIElement mapSpacer = new UIElement();
        mapSpacer.getLayout().flexGrow(1).widthPercent(100);

        UIElement footer = new UIElement().addClass("panel_bg");
        footer.getLayout()
                .widthPercent(100)
                .height(26)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER);

        Label status = new Label();
        status.getLayout().flexGrow(1);
        footer.addChild(status);

        root.addChild(header);
        root.addChild(mapSpacer);
        root.addChild(footer);

        ModularUI mui = ModularUI.of(UI.of(root,
                StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MODERN)), mc.player);
        mui.shouldCloseOnEsc(true);
        return new View(mui, floor, status);
    }

    private static Button button(String translationKey, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(translationKey));
        button.setOnClick(event -> action.run());
        return button;
    }

    record View(ModularUI ui, Label floorLabel, Label statusLabel) {}
}
