package dev.draginventory.client.compass;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 方位条绘制工具：全部基于原版 fill / LDLib2 平滑字体与姿态栈，
 * 不依赖额外纹理资源，保证在任何资源包下观感一致。
 */
final class CompassPaint {
    private CompassPaint() {}

    /** 居中缩放文本（平滑字体，子像素定位）。 */
    static void centeredScaled(Font font, GuiGraphics g, Component text, float centerX,
                               float baselineY, float scale, int rgb, float alpha, boolean shadow) {
        if (alpha <= 0.01f) return;
        g.pose().pushPose();
        try {
            g.pose().translate(centerX, baselineY, 0);
            g.pose().scale(scale, scale, 1);
            float w = font.width(text);
            CompassStyleContext.text(font, g, text, -w / 2f, 0, rgb, alpha, shadow);
        } finally {
            g.pose().popPose();
        }
    }

    /** 发光竖线：核心 + 两层光晕。 */
    static void glowVLine(GuiGraphics g, float x, float top, float height, float coreWidth,
                          int rgb, float alpha, boolean glow) {
        CompassStyleContext.vline(g, x, top, height, coreWidth, rgb, alpha);
        if (glow && alpha > 0.05f) {
            CompassStyleContext.vline(g, x, top - 1, height + 2, coreWidth + 2.5f, rgb, alpha * 0.22f);
            CompassStyleContext.vline(g, x, top - 2, height + 4, coreWidth + 5.5f, rgb, alpha * 0.10f);
        }
    }

    /** 旋转 45° 的实心菱形（标点用）。 */
    static void diamond(GuiGraphics g, float cx, float cy, float half, int rgb, float alpha) {
        int c = CompassStyleContext.rgba(rgb, alpha);
        int left = Math.round(cx - half);
        int right = Math.round(cx + half);
        int top = Math.round(cy - half);
        int bottom = Math.round(cy + half);
        // 逐行绘制菱形（每行宽度按到中心的距离收窄），避免姿态旋转的锯齿。
        int size = Math.max(1, Math.round(half * 2));
        for (int i = 0; i < size; i++) {
            float rowCenter = top + i + 0.5f;
            float dist = Math.abs(rowCenter - cy) / half;
            float rowHalf = Math.max(0, half * (1 - dist));
            if (rowHalf < 0.35f) continue;
            g.fill(Math.round(cx - rowHalf), Math.round(rowCenter - 0.5f),
                    Math.round(cx + rowHalf), Math.round(rowCenter + 0.5f), c);
        }
    }

    /** 发光菱形：光晕层 + 核心。 */
    static void glowDiamond(GuiGraphics g, float cx, float cy, float half, int rgb, float alpha, boolean glow) {
        if (glow && alpha > 0.05f) {
            diamond(g, cx, cy, half + 2.2f, rgb, alpha * 0.20f);
            diamond(g, cx, cy, half + 1, rgb, alpha * 0.35f);
        }
        diamond(g, cx, cy, half, rgb, alpha);
    }

    /** 方形描边（战术风格标点）。 */
    static void squareOutline(GuiGraphics g, float cx, float cy, float half, float thickness,
                              int rgb, float alpha) {
        int c = CompassStyleContext.rgba(rgb, alpha);
        int left = Math.round(cx - half), right = Math.round(cx + half);
        int top = Math.round(cy - half), bottom = Math.round(cy + half);
        int t = Math.max(1, Math.round(thickness));
        g.fill(left, top, right, top + t, c);
        g.fill(left, bottom - t, right, bottom, c);
        g.fill(left, top, left + t, bottom, c);
        g.fill(right - t, top, right, bottom, c);
    }

    /** 2 段阶梯圆角矩形（现代感的柔和边角，无需 SDF 纹理）。 */
    static void roundedRect(GuiGraphics g, float x, float y, float w, float h,
                            int rgb, float alpha, int radius) {
        int c = CompassStyleContext.rgba(rgb, alpha);
        int left = Math.round(x), right = Math.round(x + w);
        int top = Math.round(y), bottom = Math.round(y + h);
        if (radius <= 0) {
            g.fill(left, top, right, bottom, c);
            return;
        }
        int r = Math.min(radius, (int) (Math.min(w, h) / 2f));
        // 主体（内缩 r）
        g.fill(left + r, top, right - r, bottom, c);
        // 上下边条
        g.fill(left, top + r, left + r, bottom - r, c);
        g.fill(right - r, top + r, right, bottom - r, c);
        // 四角阶梯
        for (int i = 1; i <= r; i++) {
            int inset = (int) Math.round(r - Math.sqrt(r * r - (r - i + 0.5) * (r - i + 0.5)) + 0.5);
            inset = Mth.clamp(inset, 0, r);
            g.fill(left + inset, top + r - i, left + inset + 1, top + r - i + 1, c);
            g.fill(right - inset - 1, top + r - i, right - inset, top + r - i + 1, c);
            g.fill(left + inset, bottom - r + i - 1, left + inset + 1, bottom - r + i, c);
            g.fill(right - inset - 1, bottom - r + i - 1, right - inset, bottom - r + i, c);
        }
    }

    /** 下指小三角（中心指示器）。 */
    static void triangleDown(GuiGraphics g, float cx, float topY, float halfWidth, int rows,
                             int rgb, float alpha) {
        int c = CompassStyleContext.rgba(rgb, alpha);
        for (int i = 0; i < rows; i++) {
            float w = halfWidth * (1f - i / (float) rows);
            if (w < 0.4f) break;
            g.fill(Math.round(cx - w), Math.round(topY + i), Math.round(cx + w), Math.round(topY + i + 1), c);
        }
    }

    /** 水平渐变填充（从 x1 到 x2，alpha1 -> alpha2）。 */
    static void gradientH(GuiGraphics g, float x1, float x2, float y, float h,
                          int rgb, float a1, float a2, int segments) {
        float segW = (x2 - x1) / segments;
        for (int i = 0; i < segments; i++) {
            float t = (i + 0.5f) / segments;
            float a = Mth.lerp(t, a1, a2);
            if (a <= 0.004f) continue;
            g.fill(Math.round(x1 + i * segW), Math.round(y),
                    Math.round(x1 + (i + 1) * segW + 0.5f), Math.round(y + h),
                    CompassStyleContext.rgba(rgb, a));
        }
    }
}
