package dev.draginventory.client.herra;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * HERRA 主界面：全部模块的统一设置入口（/herra 无参数打开）。
 *
 * <p>视觉语言与 CompassSettingsScreen / SoundPrintSettingsScreen 同源
 * （像素战术风 + 共享 MenuTheme 三主题，主题跟随 CompassConfig.MENU_THEME）。
 * 布局：左侧模块列表（id + 状态点），右侧详情（描述 / 状态 / 打开设置 / 开关），
 * 页脚关闭。外部 HERRA 模组经 {@code HerraApi.registerModule} 接入的模块
 * 与内置七模块并列展示；界面打开时对注册表取一次快照（中途注册的模块
 * 重开界面后出现——注册发生在初始化期，运行中动态注册属极端场景）。</p>
 */
public final class HerraHubScreen extends Screen {

    // ==================== 主题（同色值，与两个设置界面共享视觉） ====================

    private record MenuTheme(int accent, int accentBright, int panelBg, int panelInner, int panelBorder) {}

    private static final MenuTheme AMBER =
            new MenuTheme(0xFFD4A056, 0xFFF1C40F, 0xF61E2326, 0xFF2B3236, 0xFF4A555C);
    private static final MenuTheme TECH =
            new MenuTheme(0xFF2FB8A6, 0xFF5EEAD4, 0xF6161C24, 0xFF212A33, 0xFF41525E);
    private static final MenuTheme CRIMSON =
            new MenuTheme(0xFFCE5454, 0xFFFF7B72, 0xF6201719, 0xFF2B2124, 0xFF564249);

    private static MenuTheme theme() {
        return switch (dev.draginventory.client.compass.CompassConfig.MENU_THEME.get()) {
            case "tech" -> TECH;
            case "crimson" -> CRIMSON;
            default -> AMBER;
        };
    }

    private static final int TITLE_TEXT = 0xFFE0E6ED;
    private static final int LABEL_TEXT = 0xFFBDC3C7;
    private static final int VALUE_TEXT = 0xFF7F8C8D;
    private static final int CONTROL_BG = 0xFF262D33;
    private static final int CONTROL_BG_HOVER = 0xFF333B42;
    private static final int TRACK_EDGE = 0xFF16191D;

    private static final int PAD = 12;
    private static final int ROW_H = 16;
    private static final int LIST_W = 132;

    // ==================== 状态 ====================

    private final long openTime;
    private List<HerraModule> modules = List.of();
    private int selected;
    private int listScroll;
    private long closeStart;

    private int panelX, panelY, panelW, panelH;
    private int listX, listY, listH;
    private int detailX, detailW;
    private int footerY;

    private int mouseX, mouseY;

    public static HerraHubScreen create() {
        return new HerraHubScreen();
    }

    private HerraHubScreen() {
        super(Component.translatable("draginventory.herra.title"));
        this.openTime = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (closeStart == 0) {
            closeStart = System.currentTimeMillis();
        }
    }

    private void forceClose() {
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (closeStart == 0) {
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void init() {
        modules = HerraModuleRegistry.modules();
        selected = Mth.clamp(selected, 0, Math.max(0, modules.size() - 1));
        panelW = Math.min(430, this.width - 8);
        panelH = Math.min(222, this.height - 8);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listX = panelX + PAD;
        listY = panelY + 28;
        listH = panelY + panelH - 24 - listY;
        detailX = listX + LIST_W + 8;
        detailW = panelX + panelW - PAD - detailX;
        footerY = panelY + panelH - 18;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.currentTimeMillis();
        float closeT = closeProgress(now);

        g.pose().pushPose();
        try {
            if (closeT > 0f) {
                float s = 1f - 0.04f * closeT;
                g.pose().translate(panelX + panelW / 2f, panelY + panelH / 2f, 0);
                g.pose().scale(s, s, 1);
                g.pose().translate(-(panelX + panelW / 2f), -(panelY + panelH / 2f), 0);
            }
            super.render(g, mouseX, mouseY, partialTick);
            renderPanel(g, now, closeT);
        } finally {
            g.pose().popPose();
        }

        if (closeT >= 1f) {
            forceClose();
        }
    }

    private float closeProgress(long now) {
        if (closeStart == 0) return 0f;
        return Mth.clamp((now - closeStart) / 130f, 0f, 1f);
    }

    private float openProgress(long now) {
        return Mth.clamp((now - openTime) / 130f, 0f, 1f);
    }

    private void renderPanel(GuiGraphics g, long now, float closeT) {
        MenuTheme t = theme();
        float open = openProgress(now);
        int alpha = Math.round((closeT > 0f ? 1f - closeT : open) * 255);

        pixelRect(g, panelX, panelY, panelW, panelH, withAlpha(t.panelBg(), alpha),
                withAlpha(t.panelBorder(), alpha));
        g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2,
                withAlpha(t.panelInner(), alpha));

        String title = Component.translatable("draginventory.herra.title").getString();
        g.drawString(font, title, panelX + PAD, panelY + 6, withAlpha(TITLE_TEXT, alpha), false);
        String sub = "HERRA UNIFIED";
        int subW = font.width(sub);
        g.drawString(font, sub, panelX + panelW - PAD - subW, panelY + 8,
                withAlpha(t.accent(), alpha), false);
        g.fill(panelX + PAD, panelY + 22, panelX + panelW - PAD, panelY + 23,
                withAlpha(t.panelBorder(), alpha));

        renderList(g, alpha, t);
        renderDetail(g, alpha, t);
        renderFooter(g, alpha, t);
    }

    private void renderList(GuiGraphics g, int alpha, MenuTheme t) {
        int clipBottom = listY + listH;
        int visibleRows = Math.max(1, listH / ROW_H);
        int maxScroll = Math.max(0, modules.size() - visibleRows) * ROW_H;
        listScroll = Math.min(listScroll, maxScroll);

        // 列表底板
        g.fill(listX, listY, listX + LIST_W, clipBottom, withAlpha(0xF6101518, alpha));

        int y = listY + 2 - listScroll;
        for (int i = 0; i < modules.size(); i++, y += ROW_H) {
            if (y + ROW_H < listY || y > clipBottom) continue;
            HerraModule module = modules.get(i);
            boolean selectedRow = i == selected;
            boolean hot = mouseX >= listX && mouseX < listX + LIST_W
                    && mouseY >= y && mouseY < y + ROW_H - 1;
            if (selectedRow) {
                g.fill(listX + 2, y, listX + LIST_W - 2, y + ROW_H - 1,
                        withAlpha(t.panelBorder(), alpha));
                g.fill(listX + 3, y + 1, listX + LIST_W - 3, y + ROW_H - 2,
                        withAlpha(CONTROL_BG_HOVER, alpha));
            } else if (hot) {
                g.fill(listX + 2, y, listX + LIST_W - 2, y + ROW_H - 1, 0x14FFFFFF);
            }
            // 状态点：ON 实心主题色 / OFF 实心暗灰 / 不可开关 空心
            int dot = module.isEnabled() ? t.accent() : VALUE_TEXT;
            if (module.toggleable()) {
                g.fill(listX + 8, y + 5, listX + 12, y + 9, withAlpha(dot, alpha));
            } else {
                g.fill(listX + 8, y + 5, listX + 12, y + 9, withAlpha(TRACK_EDGE, alpha));
                g.fill(listX + 9, y + 6, listX + 11, y + 8, withAlpha(dot, alpha));
            }
            String label = module.id();
            g.drawString(font, label, listX + 18, y + 4,
                    selectedRow ? withAlpha(t.accentBright(), alpha)
                            : withAlpha(LABEL_TEXT, alpha), false);
        }
    }

    private void renderDetail(GuiGraphics g, int alpha, MenuTheme t) {
        int y = listY;
        if (modules.isEmpty()) {
            g.drawString(font, Component.translatable("draginventory.herra.cmd.empty").getString(),
                    detailX + 4, y + 6, withAlpha(VALUE_TEXT, alpha), false);
            return;
        }
        HerraModule module = modules.get(Mth.clamp(selected, 0, modules.size() - 1));

        // 标题（模块显示名）
        g.drawString(font, module.title().getString(), detailX + 4, y + 2,
                withAlpha(TITLE_TEXT, alpha), false);
        // 状态行
        String state = module.toggleable()
                ? (module.isEnabled() ? "STATE: ON" : "STATE: OFF")
                : "STATE: ALWAYS ON";
        g.drawString(font, state, detailX + 4, y + 14,
                withAlpha(module.isEnabled() ? t.accent() : VALUE_TEXT, alpha), false);
        // id
        String idLabel = "ID: " + module.id();
        int idW = font.width(idLabel);
        g.drawString(font, idLabel, detailX + detailW - idW - 4, y + 14,
                withAlpha(VALUE_TEXT, alpha), false);

        // 描述（按宽度截断成至多三行）
        List<String> lines = wrap(module.description().getString(), detailW - 8, 3);
        int dy = y + 30;
        for (String line : lines) {
            g.drawString(font, line, detailX + 4, dy, withAlpha(LABEL_TEXT, alpha), false);
            dy += 11;
        }

        // 操作按钮
        int by = footerY - 26;
        int bx = detailX + 4;
        if (module.hasSettings()) {
            boolean hot = hotBox(bx, by, 96, 14);
            g.fill(bx, by, bx + 96, by + 14,
                    withAlpha(hot ? CONTROL_BG_HOVER : CONTROL_BG, alpha));
            g.fill(bx, by, bx + 96, by + 1, withAlpha(TRACK_EDGE, alpha));
            String label = Component.translatable("draginventory.herra.open_settings").getString();
            g.drawString(font, label, bx + (96 - font.width(label)) / 2, by + 3,
                    withAlpha(LABEL_TEXT, alpha), false);
            settingsButtonX = bx;
            settingsButtonW = 96;
        } else {
            settingsButtonX = -1;
            settingsButtonW = 0;
            String hint = Component.translatable("draginventory.herra.no_settings").getString();
            g.drawString(font, hint, bx, by + 3, withAlpha(VALUE_TEXT, alpha), false);
        }

        if (module.toggleable()) {
            int tx = bx + 106;
            int tw = 72;
            boolean hot = hotBox(tx, by, tw, 14);
            g.fill(tx, by, tx + tw, by + 14, withAlpha(t.accent(), alpha));
            String label = Component.translatable(module.isEnabled()
                    ? "draginventory.herra.disable" : "draginventory.herra.enable").getString();
            g.drawString(font, label, tx + (tw - font.width(label)) / 2, by + 3,
                    0xFF14181D, false);
            toggleButtonX = tx;
            toggleButtonW = tw;
        } else {
            toggleButtonX = -1;
            toggleButtonW = 0;
        }
    }

    private int settingsButtonX = -1;
    private int settingsButtonW;
    private int toggleButtonX = -1;
    private int toggleButtonW;

    private boolean hotBox(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void renderFooter(GuiGraphics g, int alpha, MenuTheme t) {
        g.fill(panelX + PAD, footerY - 4, panelX + panelW - PAD, footerY - 3,
                withAlpha(t.panelBorder(), alpha));
        String done = Component.translatable("draginventory.soundprint.cfg.done").getString();
        int dw = font.width(done) + 14;
        int dx = panelX + panelW - PAD - dw;
        boolean dHot = hotBox(dx, footerY, dw, 13);
        g.fill(dx, footerY, dx + dw, footerY + 13, withAlpha(t.accent(), alpha));
        g.drawString(font, done, dx + 7, footerY + 3, 0xFF14181D, false);
        doneX = dx;
        doneW = dw;
    }

    private int doneX = -1;
    private int doneW;

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closeStart != 0) return true;
        int x = (int) mx;
        int y = (int) my;

        if (doneX >= 0 && hotBox(doneX, footerY, doneW, 13)) {
            onClose();
            return true;
        }
        if (modules.isEmpty()) return super.mouseClicked(mx, my, button);
        HerraModule module = modules.get(Mth.clamp(selected, 0, modules.size() - 1));
        if (settingsButtonX >= 0 && hotBox(settingsButtonX, footerY - 26, settingsButtonW, 14)) {
            if (module.hasSettings()) {
                module.openSettings();
                return true;
            }
        }
        if (toggleButtonX >= 0 && hotBox(toggleButtonX, footerY - 26, toggleButtonW, 14)) {
            if (module.toggleable()) {
                module.setEnabled(!module.isEnabled());
                return true;
            }
        }
        // 列表选择
        if (x >= listX && x < listX + LIST_W && y >= listY && y < listY + listH) {
            int row = (y - listY + listScroll - 2) / ROW_H;
            if (row >= 0 && row < modules.size()) {
                selected = row;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= listX && mx < listX + LIST_W && my >= listY && my < listY + listH) {
            int visibleRows = Math.max(1, listH / ROW_H);
            int maxScroll = Math.max(0, modules.size() - visibleRows) * ROW_H;
            listScroll = (int) Mth.clamp(listScroll - (float) scrollY * ROW_H, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ==================== 工具 ====================

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= maxWidth || line.isEmpty()) {
                line = new StringBuilder(candidate);
            } else {
                lines.add(line.toString());
                if (lines.size() >= maxLines) return lines;
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static void pixelRect(GuiGraphics g, int x, int y, int w, int h,
                                  int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static int withAlpha(int color, int alpha) {
        if (alpha >= 255) return color;
        if (alpha <= 0) return 0;
        int a = (color >>> 24) * alpha / 255;
        return (a << 24) | (color & 0xFFFFFF);
    }
}
