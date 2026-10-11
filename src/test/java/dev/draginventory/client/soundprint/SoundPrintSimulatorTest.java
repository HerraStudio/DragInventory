package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 模拟声源（v2.8.0 缺陷⑤测试功能）回归：发射节奏（脚步 420ms / 枪声 2200ms）、
 * 方位 -> 世界坐标数学（与 bearingFrom 互逆）、三种移动模式（固定 / 匀速旋转 /
 * 随机游走有界）、开关与参数约束。
 */
class SoundPrintSimulatorTest {

    private static final long TICK_MS = 50L;
    private static final double EYE_X = 100;
    private static final double EYE_Y = 64;
    private static final double EYE_Z = 200;

    private static SoundPrintSimulator enabledSim() {
        SoundPrintSimulator sim = new SoundPrintSimulator(new Random(42L));
        sim.setEnabled(true);
        return sim;
    }

    @Test
    void staticFootstepEmitsImmediatelyThenEvery420ms() {
        SoundPrintSimulator sim = enabledSim();
        sim.setBearing(90f); // 正东
        sim.setDistance(12f);

        long t = 10_000L;
        // 开启后第一 tick 立即发射。
        SoundSample first = one(sim.tick(t, EYE_X, EYE_Y, EYE_Z));
        assertEquals(SoundPrintType.FOOTSTEP, first.type());
        assertEquals(0.6f, first.volume(), 1e-4);
        assertEquals(SoundPrintSimulator.ID_FOOTSTEP, first.soundId());
        // 正东 12m：x = eyeX + sin(90°)*12，z = eyeZ - cos(90°)*12 = eyeZ。
        assertEquals(EYE_X + 12, first.x(), 1e-6);
        assertEquals(EYE_Z, first.z(), 1e-6);
        // 脚步在地面（眼下 1.62）。
        assertEquals(EYE_Y - 1.62, first.y(), 1e-6);

        // 间隔内不发射。
        for (int i = 1; i <= 7; i++) { // 50..350ms < 420
            assertTrue(sim.tick(t + i * TICK_MS, EYE_X, EYE_Y, EYE_Z).isEmpty(),
                    "no emit within footstep interval at " + i * TICK_MS + "ms");
        }
        // 420ms 到点再发一声（i=8 -> 400ms 还差 20ms；i=9 -> 450ms 触发）。
        assertTrue(sim.tick(t + 8 * TICK_MS, EYE_X, EYE_Y, EYE_Z).isEmpty());
        assertNotNull(one(sim.tick(t + 9 * TICK_MS, EYE_X, EYE_Y, EYE_Z)));
    }

    @Test
    void gunshotAtEyeLevelOnSlowerCadence() {
        SoundPrintSimulator sim = enabledSim();
        sim.setType(SoundPrintType.GUNSHOT);
        sim.setBearing(0f); // 正北
        sim.setDistance(8f);

        long t = 20_000L;
        SoundSample first = one(sim.tick(t, EYE_X, EYE_Y, EYE_Z));
        assertEquals(SoundPrintType.GUNSHOT, first.type());
        assertEquals(1f, first.volume(), 1e-4);
        assertEquals(SoundPrintSimulator.ID_GUNSHOT, first.soundId());
        // 正北 8m：z = eyeZ - 8。
        assertEquals(EYE_Z - 8, first.z(), 1e-6);
        assertEquals(EYE_X, first.x(), 1e-6);
        // 枪声在耳高（垂直偏移 0，无箭头）。
        assertEquals(EYE_Y, first.y(), 1e-6);

        // 2200ms 节奏：2s 内不再发。
        for (int i = 1; i <= 40; i++) {
            assertTrue(sim.tick(t + i * TICK_MS, EYE_X, EYE_Y, EYE_Z).isEmpty(),
                    "gunshot must not re-emit before 2200ms at " + i * TICK_MS + "ms");
        }
        // 2250ms 触发第二发。
        assertNotNull(one(sim.tick(t + 45 * TICK_MS, EYE_X, EYE_Y, EYE_Z)));
    }

    @Test
    void orbitAdvancesBearingAndCrossesNorth() {
        SoundPrintSimulator sim = enabledSim();
        sim.setBearing(350f);
        sim.setMovement(SoundPrintSimulator.Movement.ORBIT);
        sim.setDistance(10f);

        long t = 30_000L;
        sim.tick(t, EYE_X, EYE_Y, EYE_Z); // 立即发声 + 初始化时钟
        // 1 秒（20 tick）后方位累计推进约 18°（首 tick 默认 dt 0.05s 也计入）：
        // 350 + 18*1.05 ≈ 368.9 -> wrap ≈ 8.9°（跨北 wrap 回收）。
        for (int i = 1; i <= 20; i++) {
            sim.tick(t + i * TICK_MS, EYE_X, EYE_Y, EYE_Z);
        }
        assertTrue(sim.bearing() > 7f && sim.bearing() < 11f,
                "orbit should advance ~18.9° across north, got " + sim.bearing());
        // 方位有界 [0,360)。
        assertTrue(sim.bearing() >= 0f && sim.bearing() < 360f);
    }

    @Test
    void randomWalkStaysBoundedAndMoves() {
        SoundPrintSimulator sim = enabledSim();
        sim.setMovement(SoundPrintSimulator.Movement.RANDOM);
        sim.setBearing(180f);
        sim.setDistance(10f);

        long t = 40_000L;
        float minB = 360f, maxB = 0f, minD = 99f, maxD = 0f;
        float initialBearing = -1f;
        for (int i = 0; i <= 200; i++) { // 10 秒
            List<SoundSample> out = sim.tick(t + i * TICK_MS, EYE_X, EYE_Y, EYE_Z);
            if (i == 0) initialBearing = bearingOf(out, sim);
            minB = Math.min(minB, sim.bearing());
            maxB = Math.max(maxB, sim.bearing());
            minD = Math.min(minD, sim.distance());
            maxD = Math.max(maxD, sim.distance());
        }
        assertTrue(sim.bearing() >= 0f && sim.bearing() < 360f, "bearing bounded");
        assertTrue(sim.distance() >= 2f && sim.distance() <= 20f, "distance bounded: " + sim.distance());
        assertTrue(minD >= 2f - 1e-3 && maxD <= 20f + 1e-3,
                "distance history bounded: [" + minD + ", " + maxD + "]");
        // 10 秒随机游走后方位应已离开起点（确定性种子下）。
        assertTrue(Math.abs(SoundPrintSimulator.wrapDelta(sim.bearing() - initialBearing)) > 1f,
                "random walk should move away from start: " + initialBearing + " -> " + sim.bearing());
    }

    @Test
    void disabledAndResetSemantics() {
        SoundPrintSimulator sim = enabledSim();
        sim.setBearing(45f);
        sim.setDistance(15f);
        long t = 50_000L;
        assertNotNull(one(sim.tick(t, EYE_X, EYE_Y, EYE_Z)));

        // 关闭：立即停发。
        sim.setEnabled(false);
        for (int i = 1; i <= 20; i++) {
            assertTrue(sim.tick(t + i * TICK_MS, EYE_X, EYE_Y, EYE_Z).isEmpty());
        }
        // reset 清时钟但保留参数：重新开启立即发声。
        sim.setEnabled(true);
        sim.reset();
        assertNotNull(one(sim.tick(t + 2000L, EYE_X, EYE_Y, EYE_Z)));
        assertEquals(45f, sim.bearing(), 0.01f);
        assertEquals(15f, sim.distance(), 0.01f);
    }

    @Test
    void settersClampAndWrap() {
        SoundPrintSimulator sim = new SoundPrintSimulator();
        sim.setBearing(-30f);
        assertEquals(330f, sim.bearing(), 0.01f);
        sim.setBearing(400f);
        assertEquals(40f, sim.bearing(), 0.01f);
        sim.setDistance(1f);
        assertEquals(2f, sim.distance(), 0.01f);
        sim.setDistance(99f);
        assertEquals(20f, sim.distance(), 0.01f);
        // null 语义退回安全缺省。
        sim.setType(null);
        assertEquals(SoundPrintType.FOOTSTEP, sim.type());
        sim.setMovement(null);
        assertEquals(SoundPrintSimulator.Movement.STATIC, sim.movement());
    }

    /** 发射样本反推方位（与 bearingFrom 互逆自检）。 */
    private static float bearingOf(List<SoundSample> out, SoundPrintSimulator sim) {
        if (out.isEmpty()) return sim.bearing();
        SoundSample s = out.get(0);
        return SoundPrintTracker.bearingFrom(EYE_X, EYE_Z, s.x(), s.z());
    }

    private static SoundSample one(List<SoundSample> out) {
        assertEquals(1, out.size(), "expected exactly one sample, got " + out.size());
        return out.get(0);
    }
}
