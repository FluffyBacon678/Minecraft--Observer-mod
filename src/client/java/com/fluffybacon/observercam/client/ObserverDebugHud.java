package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.entity.ObserverCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class ObserverDebugHud {
    private ObserverDebugHud() {
    }

    public static void render(GuiGraphics graphics) {
        if (!ObserverCamConfig.get().debugHud) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.getCameraEntity() instanceof ObserverCameraEntity observer)) {
            return;
        }
        String target = observer.getTarget() == null ? "none" : observer.getTarget().getName().getString();
        double distance = observer.getTarget() == null ? 0.0 : observer.distanceTo(observer.getTarget());
        String[] lines = {
                "Observer Cam",
                "State: " + observer.cameraState(),
                String.format(Locale.ROOT, "Distance: %.1f", distance),
                "Target: " + target,
                "Visibility: " + observer.visibleSamples() + "/4",
                String.format(Locale.ROOT, "Shot Score: %.1f", observer.shotScore()),
                String.format(Locale.ROOT, "Indoor: %.0f%%", observer.indoorFactor() * 100.0F),
                String.format(Locale.ROOT, "Speed: %.2f", observer.getDeltaMovement().length()),
                "Candidates: " + observer.candidateCount()
        };
        int width = 126;
        int height = lines.length * 10 + 8;
        graphics.fill(6, 6, 6 + width, 6 + height, 0xA0101010);
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(client.font, lines[i], 11, 11 + i * 10, i == 0 ? 0xFF88FF88 : 0xFFFFFFFF, true);
        }
    }
}
