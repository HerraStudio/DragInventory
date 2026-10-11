package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 声纹包络数学：钟形 / 垂直不对称 / 距离衰减 / 箭头阈值 / 能量合成 / 圆周加权。 */
class SoundPrintEnvelopeTest {

    @Test
    void bellPeaksAtCenterAndDecaysToEdges() {
        int n = 9;
        int center = 4;
        float centerValue = SoundPrintEnvelope.bell(center, n);
        assertEquals(1f, centerValue, 1e-4);
        // 两侧单调递减。
        for (int i = 1; i <= center; i++) {
            float inner = SoundPrintEnvelope.bell(center - i, n);
            float outer = SoundPrintEnvelope.bell(center + i, n);
            assertEquals(inner, outer, 1e-4); // 对称
            assertTrue(inner < SoundPrintEnvelope.bell(center - i + 1, n),
                    "bell should decay from center, i=" + i);
        }
        // 边缘条仍在可见阈值之上（快速退化簇不会全灭）。
        assertTrue(SoundPrintEnvelope.bell(0, n) > 0.05f);
        // 单条簇恒为中柱。
        assertEquals(1f, SoundPrintEnvelope.bell(0, 1), 1e-6);
    }

    @Test
    void asymmetryBoostsSourceSideAndSuppressesOtherSide() {
        // 水平声源：上下对称。
        float[] neutral = SoundPrintEnvelope.asymmetryFactors(0f);
        assertEquals(neutral[0], neutral[1], 1e-5);
        // 上方声源（正）：上排增强、下排压制。
        float[] up = SoundPrintEnvelope.asymmetryFactors(0.8f);
        assertTrue(up[0] > 1f);
        assertTrue(up[1] < 1f);
        assertTrue(up[1] >= 0.12f); // 压制有底：参考图 2 上排仍剩暗弱短条
        // 下方声源（负）：镜像。
        float[] down = SoundPrintEnvelope.asymmetryFactors(-0.8f);
        assertEquals(up[0], down[1], 1e-4);
        assertEquals(up[1], down[0], 1e-4);
        // 极值 clamp 不越界。
        float[] extreme = SoundPrintEnvelope.asymmetryFactors(5f);
        assertTrue(extreme[0] <= 1.75f + 1e-4);
        assertTrue(extreme[1] >= 0.12f - 1e-4);
    }

    @Test
    void distanceDecayFallsToFloorAtRadius() {
        assertEquals(1f, SoundPrintEnvelope.distanceDecay(0f, 20f, 0.2f, true), 1e-4);
        assertEquals(0.2f, SoundPrintEnvelope.distanceDecay(20f, 20f, 0.2f, true), 1e-4);
        assertEquals(0.2f, SoundPrintEnvelope.distanceDecay(99f, 20f, 0.2f, true), 1e-4);
        // 关闭衰减恒 1。
        assertEquals(1f, SoundPrintEnvelope.distanceDecay(19f, 20f, 0.2f, false), 1e-6);
        // 平方根曲线：四分之一半径处衰减小于线性。
        float quarter = SoundPrintEnvelope.distanceDecay(5f, 20f, 0f, true);
        assertEquals(1f - 0.5f, quarter, 1e-4); // sqrt(0.25) = 0.5
    }

    @Test
    void verticalOffsetScalesAndClamps() {
        // ±4 格满偏。
        assertEquals(1f, SoundPrintEnvelope.verticalOffset(4.5, 0.5, true), 1e-4);
        assertEquals(-1f, SoundPrintEnvelope.verticalOffset(-3.5, 0.5, true), 1e-4);
        assertEquals(0.25f, SoundPrintEnvelope.verticalOffset(1.5, 0.5, true), 1e-4);
        // 关闭联动恒 0（水平对称、无箭头）。
        assertEquals(0f, SoundPrintEnvelope.verticalOffset(9, 0, false), 1e-6);
        // 越界 clamp。
        assertEquals(1f, SoundPrintEnvelope.verticalOffset(20, 0, true), 1e-6);
    }

    @Test
    void arrowStrengthHasDeadZoneAndFullZone() {
        // 水平 / 轻微偏移：无箭头（参考图 1）。
        assertEquals(0f, SoundPrintEnvelope.arrowStrength(0f), 1e-6);
        assertEquals(0f, SoundPrintEnvelope.arrowStrength(0.14f), 1e-6);
        // 明确偏移：全显（参考图 2）。
        assertEquals(1f, SoundPrintEnvelope.arrowStrength(0.7f), 1e-6);
        assertEquals(1f, SoundPrintEnvelope.arrowStrength(-1f), 1e-6);
        // 中段单调淡入。
        float mid = SoundPrintEnvelope.arrowStrength(0.45f);
        assertTrue(mid > 0f && mid < 1f);
        assertTrue(SoundPrintEnvelope.arrowStrength(0.35f) < mid);
        // v2.9.5: 死区 0.22→0.15 —— 浅高差（0.2 ≈ 声源高/低约 0.8 格）也开始淡入箭头。
        float shallow = SoundPrintEnvelope.arrowStrength(0.2f);
        assertTrue(shallow > 0f && shallow < 0.5f, "shallow offset should start fading in the arrow");
        assertTrue(SoundPrintEnvelope.arrowStrength(0.16f) < shallow);
    }

    @Test
    void arrowRowSpreadPointsUpAndDownCorrectly() {
        // v2.9.5 回归：行序自顶向下（t=0 顶行 → t=1 底行）。
        // 上箭头 "^"：尖端在顶（t→0 最窄），臂在底部张开（t→1 最宽）。
        assertEquals(0f, SoundPrintEnvelope.arrowRowSpread(true, 0f), 1e-6);
        assertEquals(1f, SoundPrintEnvelope.arrowRowSpread(true, 1f), 1e-6);
        assertTrue(SoundPrintEnvelope.arrowRowSpread(true, 0.25f)
                < SoundPrintEnvelope.arrowRowSpread(true, 0.75f));
        // 下箭头 "v"：镜像 —— 臂在顶部张开（t→0 最宽），尖端在底（t→1 最窄）。
        assertEquals(1f, SoundPrintEnvelope.arrowRowSpread(false, 0f), 1e-6);
        assertEquals(0f, SoundPrintEnvelope.arrowRowSpread(false, 1f), 1e-6);
        assertTrue(SoundPrintEnvelope.arrowRowSpread(false, 0.25f)
                > SoundPrintEnvelope.arrowRowSpread(false, 0.75f));
        // 越界 t 钳制到 [0, 1]，不产生负张开或超张开。
        assertEquals(0f, SoundPrintEnvelope.arrowRowSpread(true, -5f), 1e-6);
        assertEquals(0f, SoundPrintEnvelope.arrowRowSpread(false, 5f), 1e-6);
    }

    @Test
    void mergeEnergyRmsStyleCappedAtOne() {
        assertEquals(0.5f, SoundPrintEnvelope.mergeEnergy(0.3f, 0.4f), 1e-4); // sqrt(.09+.16)=.5
        assertEquals(1f, SoundPrintEnvelope.mergeEnergy(0.9f, 0.9f), 1e-6);  // cap
        assertTrue(SoundPrintEnvelope.mergeEnergy(0.3f, 0.4f) > 0.4f);       // 比 max 明显
        assertTrue(SoundPrintEnvelope.mergeEnergy(0.2f, 0.2f) < 0.4f);       // 比求和温和
    }

    @Test
    void weightedBearingWrapsAcrossNorth() {
        // 359° 与 1° 的能量加权中心应为 0°（而非 180°）。
        assertEquals(0f, SoundPrintEnvelope.weightedBearing(
                new float[]{359f, 1f}, new float[]{1f, 1f}), 0.5f);
        // 能量加权（向量平均，非线性插值）：0° 与 30° 按 1:3 -> atan2(sin·1+sin30·3, cos·1+cos30·3)
        // = atan2(1.5, 3.598) ≈ 22.6°，偏向强源但受圆周几何约束。
        assertEquals(22.63f, SoundPrintEnvelope.weightedBearing(
                new float[]{0f, 30f}, new float[]{1f, 3f}), 0.2f);
        // 全零向量安全返回。
        assertEquals(0f, SoundPrintEnvelope.weightedBearing(
                new float[]{90f, 270f}, new float[]{0f, 0f}), 1e-6);
    }

    @Test
    void cappedBarHeightNeverExceedsCapForAnyMultiplierStack() {
        // v2.9.3 回归：满能量 + 枪声1.35x + 垂直不对称1.75x + 抖动1.26x 的历史叠加
        // 曾把 max_height=24 渲染到 ~71px（3 倍越顶）。顶棚后任何组合不越 cap。
        float cap = 24f * 1.35f; // 枪声单侧顶棚
        float amp = 0.26f;       // 满强度抖动幅度
        // 全参数扫描：rawBase 0..3x cap、wobble ±1，任何组合都不越顶。
        for (float raw = 0f; raw <= cap * 3f + 1f; raw += 1.37f) {
            for (int w = -100; w <= 100; w += 7) {
                float h = SoundPrintEnvelope.cappedBarHeight(raw, cap, amp, w / 100f);
                assertTrue(h >= 0f && h <= cap + 1e-4,
                        "height escaped cap: raw=" + raw + " w=" + (w / 100f) + " h=" + h);
            }
        }
        // 抖动关闭时精确等于 cap 钳制。
        assertEquals(cap, SoundPrintEnvelope.cappedBarHeight(cap * 99f, cap, 0f, 0.5f), 1e-4);
        assertEquals(cap, SoundPrintEnvelope.cappedBarHeight(cap, cap, 0f, 0f), 1e-4);
        // cap<=0 直接归零。
        assertEquals(0f, SoundPrintEnvelope.cappedBarHeight(50f, 0f, 0.26f, 1f), 1e-6);
        assertEquals(0f, SoundPrintEnvelope.cappedBarHeight(50f, -3f, 0.1f, -1f), 1e-6);
    }

    @Test
    void cappedBarHeightKeepsSmallBarsExactAndFullEnergyBarsDancing() {
        float cap = 24f;
        // 顶棚以下的条原样通过（抖动乘子保留）：wobble=+1 时 h = raw*(1+amp)。
        float amp = 0.10f;
        float small = 6f;
        assertEquals(small * (1f + amp), SoundPrintEnvelope.cappedBarHeight(small, cap, amp, 1f), 1e-4);
        assertEquals(small * (1f - amp), SoundPrintEnvelope.cappedBarHeight(small, cap, amp, -1f), 1e-4);
        // 满能量中心条（raw 远超顶棚）在顶棚内持续舞动而非钉死：
        // wobble=+1 触顶 cap；wobble=-1 收缩到 cap*(1-amp)/(1+amp)。
        assertEquals(cap, SoundPrintEnvelope.cappedBarHeight(cap * 2f, cap, amp, 1f), 1e-4);
        float dipped = SoundPrintEnvelope.cappedBarHeight(cap * 2f, cap, amp, -1f);
        assertEquals(cap * (1f - amp) / (1f + amp), dipped, 1e-4);
        assertTrue(dipped > cap * 0.5f, "full-energy bar must stay prominent while dancing");
        // 两个方向的舞动幅度差即节律可见性：>= amp 份额的摆动区间。
        float swing = cap - dipped;
        assertTrue(swing >= cap * amp * 0.5f, "visible swing expected, swing=" + swing);
    }
}
