package dev.draginventory.client;

import java.util.function.BiConsumer;

/** Defers a single click so an enemy double click produces exactly one marker. */
final class TacticalClickGesture<T> {
    static final long DOUBLE_CLICK_MS = 450;
    private T pending;
    private long pressedAt;
    private boolean waiting;

    void press(T hit, long now, BiConsumer<T, Boolean> emit) {
        if (waiting && now - pressedAt <= DOUBLE_CLICK_MS) {
            T first = pending;
            cancel();
            // Looking at empty sky on the second click preserves the first single click.
            emit.accept(hit == null ? first : hit, hit != null);
        } else {
            flush(now, emit);
            pending = hit;
            pressedAt = now;
            waiting = true;
        }
    }

    void flush(long now, BiConsumer<T, Boolean> emit) {
        if (waiting && now - pressedAt > DOUBLE_CLICK_MS) {
            T hit = pending;
            cancel();
            emit.accept(hit, false);
        }
    }

    void cancel() { pending = null; waiting = false; }
}
