package dev.draginventory.client;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TacticalMarkerTest {
    private record Click(String target, boolean doubled) {}
    private final List<Click> emitted = new ArrayList<>();
    private final TacticalClickGesture<String> gesture = new TacticalClickGesture<>();
    private void press(String hit, long time) { gesture.press(hit, time, (h, d) -> emitted.add(new Click(h, d))); }
    private void flush(long time) { gesture.flush(time, (h, d) -> emitted.add(new Click(h, d))); }

    @Test void singleClickWaitsForDoubleClickWindow() {
        press("ground", 1_000);
        flush(1_450);
        assertTrue(emitted.isEmpty());
        flush(1_451);
        flush(1_600);
        assertEquals(List.of(new Click("ground", false)), emitted);
    }

    @Test void exactThresholdSecondClickCreatesOnlyOneEnemyAction() {
        press("ground", 1_000);
        press("enemy", 1_450);
        flush(2_000);
        assertEquals(List.of(new Click("enemy", true)), emitted);
    }

    @Test void delayedTickDoesNotMergeTwoSlowClicks() {
        press("one", 1_000);
        press("two", 1_451);
        flush(2_000);
        assertEquals(List.of(new Click("one", false), new Click("two", false)), emitted);
    }

    @Test void thirdClickStartsANewGesture() {
        press("enemy", 1_000);
        press("enemy", 1_100);
        press("item", 1_200);
        flush(1_651);
        assertEquals(List.of(new Click("enemy", true), new Click("item", false)), emitted);
    }

    @Test void skySecondClickPreservesOriginalSingleClick() {
        press("ground", 1_000);
        press(null, 1_100);
        assertEquals(List.of(new Click("ground", false)), emitted);
    }

    @Test void firstSkyClickCanBeFollowedByEnemyDoubleClick() {
        press(null, 1_000);
        press("enemy", 1_100);
        assertEquals(List.of(new Click("enemy", true)), emitted);
    }

    @Test void openingMenuCancelsPendingClickAndResetsDoubleClick() {
        press("ground", 1_000);
        gesture.cancel();
        press("enemy", 1_100);
        flush(2_000);
        assertEquals(List.of(new Click("enemy", false)), emitted);
    }

    @Test void lifetimeUsesElapsedTimeWithExactSixtySecondBoundary() {
        assertFalse(TacticalMarkerLogic.expired(60_999, 1_000));
        assertTrue(TacticalMarkerLogic.expired(61_000, 1_000));
        assertFalse(TacticalMarkerLogic.expired(61_000, 60_000));
    }

    @Test void distanceUsesEuclideanBlocksAndRoundsDown() {
        assertEquals(13, TacticalMarkerLogic.distanceMeters(3, 4, 12));
        assertEquals(9, TacticalMarkerLogic.distanceMeters(-9.7, 0, 0));
        assertEquals(0, TacticalMarkerLogic.distanceMeters(0, 0, 0));
    }

    @Test void opacityIsSmoothMonotonicAndClamped() {
        assertEquals(0.2f, TacticalMarkerLogic.alpha(0));
        assertEquals(1f, TacticalMarkerLogic.alpha(75));
        assertEquals(1f, TacticalMarkerLogic.alpha(1000));
        float previous = 0.2f;
        for (int d = 1; d <= 100; d++) {
            float alpha = TacticalMarkerLogic.alpha(d);
            assertTrue(alpha >= previous && alpha <= 1);
            previous = alpha;
        }
    }

    private static Matrix4f projection() { return new Matrix4f().perspective((float) Math.toRadians(90), 2, 0.05f, 512); }

    @Test void visibleProjectionAndDistanceHaveKnownCoordinates() {
        var center = TacticalMarkerLogic.project(projection(), 0, 0, -10, 800, 400);
        assertFalse(center.edge());
        assertEquals(400, center.x(), 0.001);
        assertEquals(200, center.y(), 0.001);
        var right = TacticalMarkerLogic.project(projection(), 10, 0, -10, 800, 400);
        assertEquals(600, right.x(), 0.001);
    }

    @Test void aimZoomChangesProjection() {
        var wide = TacticalMarkerLogic.project(projection(), 1, 0, -10, 800, 400);
        var zoomed = TacticalMarkerLogic.project(new Matrix4f().perspective((float) Math.toRadians(30), 2, 0.05f, 512), 1, 0, -10, 800, 400);
        assertTrue(zoomed.x() > wide.x());
    }

    @Test void offscreenTargetStaysInsidePaddedScreen() {
        var point = TacticalMarkerLogic.project(projection(), 200, 30, -1, 800, 400);
        assertTrue(point.edge());
        assertEquals(770, point.x(), 0.001);
        assertTrue(point.y() >= 30 && point.y() <= 370);
    }

    @Test void behindRightTargetPointsRightInsteadOfFlipping() {
        var point = TacticalMarkerLogic.project(projection(), 10, 0, 10, 800, 400);
        assertTrue(point.edge());
        assertEquals(770, point.x(), 0.001);
    }

    @Test void directlyBehindAndAtCameraPlaneRemainFinite() {
        var behind = TacticalMarkerLogic.project(projection(), 0, 0, 10, 800, 400);
        assertTrue(behind.edge());
        assertEquals(370, behind.y(), 0.001);
        var plane = TacticalMarkerLogic.project(projection(), 10, 0, 0, 800, 400);
        assertTrue(plane.edge());
        assertTrue(Float.isFinite(plane.x()) && Float.isFinite(plane.y()));
    }

    @Test void cameraRotationKeepsFrontTargetAtCrosshair() {
        Matrix4f matrix = projection().rotateY((float) Math.PI / 2);
        var point = TacticalMarkerLogic.project(matrix, 10, 0, 0, 800, 400);
        assertEquals(400, point.x(), 0.001);
        assertEquals(200, point.y(), 0.001);
        assertFalse(point.edge());
    }

    @Test void relaxedDoubleClickAcceptsFourHundredMilliseconds() {
        press("enemy", 1_000);
        flush(1_350);
        assertTrue(emitted.isEmpty());
        press("enemy", 1_400);
        flush(2_000);
        assertEquals(List.of(new Click("enemy", true)), emitted);
    }

    @Test void fiveMarkersRemainAndSixthEvictsFirstAcrossTypes() {
        var markers = new LinkedHashMap<String, String>();
        for (String key : List.of("locationA", "enemyA", "itemA", "locationB", "enemyB"))
            TacticalMarkerLogic.putMarker(markers, key, key);
        assertEquals(5, markers.size());
        TacticalMarkerLogic.putMarker(markers, "itemB", "itemB");
        assertEquals(List.of("enemyA", "itemA", "locationB", "enemyB", "itemB"), List.copyOf(markers.keySet()));
        TacticalMarkerLogic.putMarker(markers, "locationC", "locationC");
        assertEquals(List.of("itemA", "locationB", "enemyB", "itemB", "locationC"), List.copyOf(markers.keySet()));
    }

    @Test void refreshingAtCapacityPreservesFifoOrderAndDoesNotEvict() {
        var markers = new LinkedHashMap<Integer, Long>();
        for (int i = 0; i < 5; i++) TacticalMarkerLogic.putMarker(markers, i, 100L);
        TacticalMarkerLogic.putMarker(markers, 0, 500L);
        assertEquals(5, markers.size());
        assertEquals(500L, markers.get(0));
        assertEquals(List.of(0, 1, 2, 3, 4), List.copyOf(markers.keySet()));
        TacticalMarkerLogic.putMarker(markers, 5, 600L);
        assertEquals(List.of(1, 2, 3, 4, 5), List.copyOf(markers.keySet()));
    }

    @Test void removedMarkerFreesCapacityBeforeNextAddition() {
        var markers = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < 5; i++) TacticalMarkerLogic.putMarker(markers, i, "marker");
        markers.remove(2);
        TacticalMarkerLogic.putMarker(markers, 5, "new");
        assertEquals(List.of(0, 1, 3, 4, 5), List.copyOf(markers.keySet()));
    }

    @Test void manySequentialPingsKeepOnlyLastFive() {
        var markers = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; i < 100; i++) {
            TacticalMarkerLogic.putMarker(markers, i, i);
            assertEquals(Math.min(i + 1, 5), markers.size());
        }
        assertEquals(List.of(95, 96, 97, 98, 99), List.copyOf(markers.keySet()));
    }

    @Test void appearanceStartsSmallAndTransparentAndSettlesExactly() {
        var start = TacticalMarkerLogic.appearance(0);
        assertEquals(0.55f, start.scale(), 0.00001f);
        assertEquals(8f, start.offsetY());
        assertEquals(0f, start.opacity());
        var rest = new TacticalMarkerLogic.Appearance(1, 0, 1);
        assertEquals(rest, TacticalMarkerLogic.appearance(320));
        assertEquals(rest, TacticalMarkerLogic.appearance(60_000));
        assertEquals(start, TacticalMarkerLogic.appearance(-1));
    }

    @Test void appearanceHasBoundedOvershootAndNonlinearTravel() {
        float previousY = 8, previousAlpha = 0, maximumScale = 0;
        for (int age = 0; age <= 320; age++) {
            var pose = TacticalMarkerLogic.appearance(age);
            assertTrue(pose.offsetY() <= previousY && pose.offsetY() >= 0);
            assertTrue(pose.opacity() >= previousAlpha && pose.opacity() <= 1);
            assertTrue(pose.scale() >= 0.5499f && pose.scale() < 1.05f);
            previousY = pose.offsetY(); previousAlpha = pose.opacity();
            maximumScale = Math.max(maximumScale, pose.scale());
        }
        assertTrue(maximumScale > 1.03f);
        assertTrue(TacticalMarkerLogic.appearance(160).offsetY() < 2); // Linear would be 4.
        assertEquals(1f, TacticalMarkerLogic.appearance(130).opacity());
    }

    @Test void appearanceUsesElapsedTimeAndCanReplayOnRefresh() {
        long createdAt = 1_000;
        var beforeRefresh = TacticalMarkerLogic.appearance(2_000 - createdAt);
        createdAt = 2_000;
        var afterRefresh = TacticalMarkerLogic.appearance(2_000 - createdAt);
        assertEquals(1, beforeRefresh.scale());
        assertTrue(afterRefresh.scale() < beforeRefresh.scale());
        // Sampling other frames does not change the pose at a given elapsed time.
        var expected = TacticalMarkerLogic.appearance(160);
        for (int ms = 0; ms < 160; ms += 7) TacticalMarkerLogic.appearance(ms);
        assertEquals(expected, TacticalMarkerLogic.appearance(160));
    }
}
