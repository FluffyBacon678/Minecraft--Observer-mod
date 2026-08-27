package com.fluffybacon.observercam.client.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ObserverRecordingHud {
    private ObserverRecordingHud() {
    }

    public static void render(GuiGraphics graphics) {
        ObserverRecordingManager manager = ObserverRecordingManager.get();
        RecordingState state = manager.state();
        ReplayState replayState = manager.replayState();
        boolean showRecording = state == RecordingState.RECORDING || state == RecordingState.FINALIZING;
        boolean showReplay = replayState == ReplayState.BUFFERING || replayState == ReplayState.SAVING;
        if (!showRecording && !showReplay) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        long seconds = TimeUnit.NANOSECONDS.toSeconds(showRecording
                ? manager.elapsedNanos() : manager.replayElapsedNanos());
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
        String size = String.format(Locale.ROOT, "%.1f MB", (showRecording
                ? manager.estimatedBytes() : manager.replayEstimatedBytes()) / 1_000_000.0);
        String text;
        int indicatorColor;
        if (state == RecordingState.FINALIZING) {
            text = "Saving Observer video…";
            indicatorColor = 0xFFFFA020;
        } else if (state == RecordingState.RECORDING) {
            text = "REC  " + time + "  " + size + "  dropped " + manager.droppedFrames();
            indicatorColor = 0xFFFF2020;
        } else if (replayState == ReplayState.SAVING) {
            text = "Saving instant replay…";
            indicatorColor = 0xFFFFA020;
        } else {
            text = "REPLAY  " + time + "  " + size + "  F9 save  dropped "
                    + manager.replayDroppedFrames();
            indicatorColor = 0xFFE03030;
        }
        int width = client.font.width(text) + 20;
        int x = client.getWindow().getGuiScaledWidth() - width - 7;
        graphics.fill(x, 7, x + width, 25, 0xB0101010);
        graphics.fill(x + 6, 13, x + 12, 19, indicatorColor);
        graphics.drawString(client.font, text, x + 16, 12, 0xFFFFFFFF, true);
    }
}
