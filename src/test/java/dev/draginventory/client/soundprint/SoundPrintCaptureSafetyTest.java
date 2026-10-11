package dev.draginventory.client.soundprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * v2.7.1 实机崩溃回归（用户实测：进存档走第一步即崩，堆栈
 * LocalPlayer.aiStep → playStepSound → PlaySoundEvent → onPlaySound:97 NPE）。
 *
 * <p>两个被拦截的缺陷：①PlaySoundEvent 早于 SoundEngine 的 resolve()，直接
 * getVolume() 必 NPE（采集路径必须先幂等补 resolve 再读响度，未注册声效跳过，
 * 任何实现异常退满响度）；②自声过滤原以眼睛位置做 3D 距离，脚步声在脚部播放
 * （垂直差 1.62m 击穿 0.9m 阈值）从未生效——改为声源到玩家碰撞箱的最近距离。</p>
 *
 * <p>测试通过 {@link SoundPrintCaptureRules} 纯 JVM 层进行（生产侧 MC 薄适配
 * 见 SoundPrintEvents#loudness，与决策层的契约由本类的桩语义锁定）。</p>
 */
class SoundPrintCaptureSafetyTest {

    /** 模拟“引擎最终不会播放”的占位声效（生产侧为 EMPTY_SOUND / 刻意留空）。 */
    private static final Object UNPLAYABLE = new Object();

    /** 普通已解析声效标记。 */
    private static final Object PLAYABLE = new Object();

    /**
     * 可编排的 SoundView 桩，精确模拟原版 AbstractSoundInstance 的危险语义：
     * sound 字段只有 resolve() 才赋值，未解析时 getVolume() 直接 NPE
     * （AbstractSoundInstance.getVolume = this.volume * this.sound.getVolume()）。
     */
    private static final class StubView implements SoundPrintCaptureRules.SoundView {
        /** 当前解析状态（初始 null = 未解析，与事件回调期的真实时序一致）。 */
        Object resolved;
        /** resolveOnce() 应当解析出的声效（null = 解析不出任何声效）。 */
        Object afterResolve;
        boolean resolveThrows;
        boolean resolvedSoundThrows;
        boolean volumeThrows;
        boolean resolveCalled;
        final float volume;

        StubView(Object afterResolve, float volume) {
            this.afterResolve = afterResolve;
            this.volume = volume;
        }

        /** 预解析桩：构造时即已解析（个别第三方实现如此）。 */
        static StubView preResolved(Object sound, float volume) {
            StubView v = new StubView(null, volume);
            v.resolved = sound;
            return v;
        }

        @Override
        public Object resolvedSound() {
            if (resolvedSoundThrows) throw new IllegalStateException("stub: resolvedSound broke");
            return resolved;
        }

        @Override
        public void resolveOnce() {
            resolveCalled = true;
            if (resolveThrows) throw new IllegalStateException("stub: resolve broke");
            this.resolved = afterResolve;
        }

        @Override
        public boolean isUnplayable(Object sound) {
            return sound == UNPLAYABLE;
        }

        @Override
        public float rawVolume() {
            // 未解析即取响度 = 原版行为：this.sound.getVolume() NPE。
            if (resolved == null) {
                throw new NullPointerException("stub: unresolved, mirrors vanilla AbstractSoundInstance");
            }
            if (volumeThrows) throw new IllegalStateException("stub: volume broke");
            return volume;
        }
    }

    // ---- 响度安全读取（safeLoudness，v2.7.1 修复①）------------------------

    @Test
    void unresolvedInstanceIsResolvedBeforeVolumeRead() {
        // 崩溃根因回归：v2.7.0 在此场景直接 getVolume() → NPE 炸穿客户端 tick。
        StubView view = new StubView(PLAYABLE, 0.7f);
        assertEquals(0.7f, SoundPrintCaptureRules.safeLoudness(view), 1e-6f);
        assertTrue(view.resolveCalled, "必须先补一次 resolve 再读响度");
    }

    @Test
    void unplayableSoundIsSkipped() {
        // 未注册 / 刻意留空的声效：引擎只会 warn 不播放——
        // “听得到才采集”原则下不应产生声纹。
        StubView view = new StubView(UNPLAYABLE, 0.7f);
        assertTrue(Float.isNaN(SoundPrintCaptureRules.safeLoudness(view)));
    }

    @Test
    void resolveFailureFallsBackToFullLoudness() {
        StubView view = new StubView(PLAYABLE, 0.7f);
        view.resolveThrows = true;
        assertEquals(1f, SoundPrintCaptureRules.safeLoudness(view), 1e-6f);
    }

    @Test
    void unresolvableImplementationFallsBackToFullLoudness() {
        // Connector 等转换出的非常规实现：resolve 成功但永远给不出声效。
        StubView view = new StubView(null, 0.7f);
        assertEquals(1f, SoundPrintCaptureRules.safeLoudness(view), 1e-6f);
    }

    @Test
    void resolvedSoundAccessFailureFallsBackToFullLoudness() {
        StubView view = new StubView(PLAYABLE, 0.7f);
        view.resolvedSoundThrows = true;
        assertEquals(1f, SoundPrintCaptureRules.safeLoudness(view), 1e-6f);
    }

    @Test
    void volumeFailureFallsBackToFullLoudness() {
        StubView view = StubView.preResolved(PLAYABLE, 0.7f);
        view.volumeThrows = true;
        assertEquals(1f, SoundPrintCaptureRules.safeLoudness(view), 1e-6f);
    }

    @Test
    void preResolvedInstanceReadsVolumeDirectly() {
        StubView view = StubView.preResolved(PLAYABLE, 0.55f);
        view.resolveThrows = true; // 已解析则必须跳过 resolve，重解析即失败
        assertEquals(0.55f, SoundPrintCaptureRules.safeLoudness(view), 1e-6f);
        assertFalse(view.resolveCalled, "已解析的实例不得重复 resolve");
    }

    // ---- 自声判定（boxDistanceSqr / isOwnSound，v2.7.1 修复②）--------------

    private static boolean isOwnSound(double soundX, double soundY, double soundZ) {
        // 玩家站桩：脚底 (0, 64, 0)，碰撞箱 0.6 宽 × 1.8 高。
        return SoundPrintCaptureRules.isOwnSound(0, 64, 0, soundX, soundY, soundZ);
    }

    @Test
    void ownFootstepAtFeetIsInsideBox() {
        // 自己的脚步：playStepSound 在脚部播放 —— 旧实现以眼睛 (0, 65.62, 0) 做
        // 3D 距离差 1.62m > 旧阈值 0.9m，从未过滤（崩溃正是它触发的）；
        // 新判定箱内距离 = 0，稳定过滤。
        assertTrue(isOwnSound(0, 64, 0));
        assertTrue(isOwnSound(0.2, 64.5, -0.2), "箱内任意点（跳跃中途的落地声）都算自声");
    }

    @Test
    void ownHeadLevelSoundIsInsideBox() {
        // 自己的受击 / 疲劳叹气等在头部高度发声 —— 旧实现恰好只有这类声能被过滤。
        assertTrue(isOwnSound(0, 65.5, 0));
    }

    @Test
    void adjacentEnemyFootstepIsNotSwallowed() {
        // 贴脸敌人（中心距 1.0m）：碰撞箱间隙 0.7m —— 必须照常产生声纹。
        assertFalse(isOwnSound(1.0, 64, 0));
        // 碰撞箱间隙 0.4m（近战贴身）：仍在阈值外。
        assertFalse(isOwnSound(0.7, 64, 0));
    }

    @Test
    void floorAndCeilingStepsAreNotOwn() {
        // 楼下 / 楼上声源：垂直间隙显著，不属于自声。
        assertFalse(isOwnSound(0, 66.5, 0), "头顶上方 0.7m 的楼上脚步");
        assertFalse(isOwnSound(0, 62.0, 0), "脚下 2m 的楼下脚步");
    }

    @Test
    void boxDistanceMathMirrorsVanillaAabb() {
        // 与原版 AABB.distanceToSqr 逐点对拍（每轴 max(max(min-p, p-max), 0) 平方和）。
        assertEquals(0d, SoundPrintCaptureRules.boxDistanceSqr(0, 64, 0, 0.3, 64.9, 0.3), 1e-9);
        // x 间隙 0.4、y 间隙 0.5：d² = 0.16 + 0.25 + 0 = 0.41。
        assertEquals(0.41d, SoundPrintCaptureRules.boxDistanceSqr(0, 64, 0, 0.7, 66.3, 0), 1e-9);
        // 箱体角落对角：x 0.4 / y 0.7 / z 0.2 → 0.16+0.49+0.04 = 0.69。
        assertEquals(0.69d, SoundPrintCaptureRules.boxDistanceSqr(0, 64, 0, 0.7, 66.5, -0.5), 1e-9);
    }

    // ==================== v2.8.0 自声枪声投影 ====================

    @Test
    void projectAheadNormalizesArbitraryLengthView() {
        // 未归一的视线（长度 2）：投影 4m 后应落在单位方向 × 4 处。
        double[] p = SoundPrintCaptureRules.projectAhead(0, 64, 0, 1, 0, 1, 4.0);
        assertEquals(4.0 / Math.sqrt(2), p[0], 1e-9);
        assertEquals(64.0, p[1], 1e-9);
        assertEquals(4.0 / Math.sqrt(2), p[2], 1e-9);
        // 已归一视线：直接距离倍。
        double[] q = SoundPrintCaptureRules.projectAhead(10, 70, -10, 0, 1, 0, 4.0);
        assertEquals(10, q[0], 1e-9);
        assertEquals(74, q[1], 1e-9);
        assertEquals(-10, q[2], 1e-9);
    }

    @Test
    void projectAheadZeroViewFallsBackToEye() {
        double[] p = SoundPrintCaptureRules.projectAhead(3, 65, -7, 0, 0, 0, 4.0);
        assertEquals(3, p[0], 1e-9);
        assertEquals(65, p[1], 1e-9);
        assertEquals(-7, p[2], 1e-9);
    }
}
