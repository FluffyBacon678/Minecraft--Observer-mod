package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.recording.InstantReplayLimitMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class ObserverCamConfigScreen extends Screen {
    private static final int CONTENT_WIDTH = 300;
    private final Screen parent;
    private Button povButton;

    public ObserverCamConfigScreen(Screen parent) {
        super(Component.translatable("observercam.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - CONTENT_WIDTH / 2;
        ObserverCamConfig config = ObserverCamConfig.get();
        BooleanSetting cameraman = bool("cameraman_enabled", () -> config.cameramanEnabled,
                ObserverCamClient::setCameramanEnabled);
        addRenderableWidget(Button.builder(toggleText(cameraman), button -> {
                    cameraman.setter.accept(!cameraman.getter.getAsBoolean());
                    button.setMessage(toggleText(cameraman));
                })
                .tooltip(Tooltip.create(cameraman.tooltip()))
                .bounds(x, 46, 148, 20)
                .build());
        povButton = addRenderableWidget(Button.builder(povText(), button -> {
                    ObserverCamClient.togglePov();
                    button.setMessage(povText());
                })
                .tooltip(Tooltip.create(Component.translatable("observercam.config.pov.tooltip")))
                .bounds(x + 152, 46, 148, 20)
                .build());
        povButton.active = minecraft != null && minecraft.level != null && minecraft.player != null;

        Category[] categories = Category.values();
        for (int index = 0; index < categories.length; index++) {
            Category category = categories[index];
            int categoryX = x + (index % 2) * 152;
            int categoryY = 74 + (index / 2) * 24;
            addRenderableWidget(Button.builder(category.title(), button -> minecraft.setScreen(new CategoryScreen(this, category)))
                    .tooltip(Tooltip.create(category.description()))
                    .bounds(categoryX, categoryY, 148, 20)
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("observercam.config.reset"), button -> confirmReset())
                .tooltip(Tooltip.create(Component.translatable("observercam.config.reset.tooltip")))
                .bounds(x, 154, 148, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x + 152, 154, 148, 20)
                .build());
    }

    private static Component toggleText(BooleanSetting setting) {
        Component state = Component.translatable(setting.getter.getAsBoolean()
                ? "observercam.config.on" : "observercam.config.off");
        return Component.translatable("observercam.config.value", setting.label(), state);
    }

    private static Component povText() {
        if (ObserverCamClient.isViewingObserver()) {
            return Component.translatable("observercam.config.pov.exit");
        }
        return Component.translatable(ObserverCamClient.isPovRequestPending()
                ? "observercam.config.pov.cancel" : "observercam.config.pov.enter");
    }

    private void confirmReset() {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ObserverCamConfig.get().reset();
                ObserverCamClient.setCameramanEnabled(false);
                minecraft.setScreen(new ObserverCamConfigScreen(parent));
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("observercam.config.reset.confirm.title"),
                Component.translatable("observercam.config.reset.confirm.message"),
                Component.translatable("observercam.config.reset"), CommonComponents.GUI_CANCEL));
    }

    @Override
    public void onClose() {
        ObserverCamConfig.get().save();
        ObserverCamClient.syncCameraSettings();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (povButton != null) {
            povButton.setMessage(povText());
            povButton.active = minecraft != null && minecraft.level != null && minecraft.player != null;
        }
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("observercam.config.live_hint"), width / 2, 31, 0xFFAAAAAA);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Category {
        CAMERA("camera"),
        MOVEMENT("movement"),
        CINEMATOGRAPHY("cinematography"),
        BEHAVIOR("behavior"),
        STORAGE("storage"),
        DEBUG("debug");

        private final String key;

        Category(String key) {
            this.key = key;
        }

        Component title() {
            return Component.translatable("observercam.config.category." + key);
        }

        Component description() {
            return Component.translatable("observercam.config.category." + key + ".tooltip");
        }

        List<Setting> settings() {
            ObserverCamConfig c = ObserverCamConfig.get();
            return switch (this) {
                case CAMERA -> List.of(
                        number("outdoor_distance", "blocks", 4, 20, 1, () -> c.outdoorDistance, v -> c.outdoorDistance = v),
                        number("indoor_distance", "blocks", 2.5, 10, 1, () -> c.indoorDistance, v -> c.indoorDistance = v),
                        number("minimum_distance", "blocks", 2, 10, 1, () -> c.minimumDistance,
                                v -> {
                                    c.minimumDistance = v;
                                    c.maximumDistance = Math.max(c.maximumDistance, v);
                                }),
                        number("maximum_distance", "blocks", 6, 30, 1, () -> c.maximumDistance,
                                v -> {
                                    c.maximumDistance = v;
                                    c.minimumDistance = Math.min(c.minimumDistance, v);
                                }),
                        number("camera_height", "blocks", 0, 8, 1, () -> c.cameraHeight, v -> c.cameraHeight = v),
                        number("camera_fov", "degrees", 35, 110, 0, () -> c.cameraFov, v -> c.cameraFov = v)
                );
                case MOVEMENT -> List.of(
                        number("maximum_speed", "blocks_per_tick", 0.1, 2, 2, () -> c.maximumSpeed, v -> c.maximumSpeed = v),
                        number("acceleration", "blocks_per_tick_squared", 0.01, 0.5, 2, () -> c.acceleration, v -> c.acceleration = v),
                        number("rotation_speed", "degrees_per_tick", 1, 30, 1, () -> c.rotationSpeed, v -> c.rotationSpeed = v),
                        number("position_smoothing", "none", 0.05, 0.8, 2, () -> c.positionSmoothing, v -> c.positionSmoothing = v),
                        number("rotation_smoothing", "none", 0.05, 0.8, 2, () -> c.rotationSmoothing, v -> c.rotationSmoothing = v),
                        number("catch_up_distance", "blocks", 6, 40, 1, () -> c.catchUpDistance,
                                v -> {
                                    c.catchUpDistance = v;
                                    c.emergencyTeleportDistance = Math.max(c.emergencyTeleportDistance, v + 2.0);
                                }),
                        number("emergency_distance", "blocks", 10, 96, 1, () -> c.emergencyTeleportDistance,
                                v -> c.emergencyTeleportDistance = Math.max(v, c.catchUpDistance + 2.0))
                );
                case CINEMATOGRAPHY -> List.of(
                        number("background_importance", "none", 0, 1.5, 2, () -> c.backgroundImportance, v -> c.backgroundImportance = v),
                        number("player_visibility", "none", 0.5, 2, 2, () -> c.playerVisibilityImportance, v -> c.playerVisibilityImportance = v),
                        number("shot_stability", "none", 0, 1, 2, () -> c.shotStability, v -> c.shotStability = v),
                        number("reframe_threshold", "score", 20, 90, 0, () -> c.reframeThreshold, v -> c.reframeThreshold = v),
                        number("player_screen_size", "percent", 0.15, 0.65, 0, 100, () -> c.preferredPlayerScreenSize, v -> c.preferredPlayerScreenSize = v),
                        number("prediction_ticks", "ticks", 0, 12, 1, () -> c.movementPredictionTicks, v -> c.movementPredictionTicks = v)
                );
                case BEHAVIOR -> List.of(
                        bool("cameraman_enabled", () -> c.cameramanEnabled, ObserverCamClient::setCameramanEnabled),
                        bool("follow_automatically", () -> c.followTargetAutomatically, v -> c.followTargetAutomatically = v),
                        bool("allow_front_shots", () -> c.allowFrontFacingShots, v -> c.allowFrontFacingShots = v)
                );
                case STORAGE -> List.of(
                        directory("recording_output_directory"),
                        action("open_recording_folder", RecordingFolderActions::open),
                        choice("recording_video_format",
                                () -> Component.translatable("observercam.config.format." + c.recordingVideoFormat.id()),
                                () -> c.recordingVideoFormat = c.recordingVideoFormat.next(), false),
                        number("recording_storage_limit", "gigabytes", 0.5, 100, 1,
                                () -> c.recordingStorageLimitGb,
                                v -> {
                                    c.recordingStorageLimitGb = v;
                                    c.instantReplayStorageLimitGb = Math.min(c.instantReplayStorageLimitGb, v);
                                }),
                        bool("instant_replay_enabled", () -> c.instantReplayEnabled, v -> c.instantReplayEnabled = v),
                        choice("instant_replay_limit_mode",
                                () -> Component.translatable("observercam.config.replay_limit." + c.instantReplayLimitMode.id()),
                                () -> c.instantReplayLimitMode = c.instantReplayLimitMode.next(), true),
                        c.instantReplayLimitMode == InstantReplayLimitMode.TIME
                                ? number("instant_replay_time_limit", "minutes", 0.5, 30, 1,
                                () -> c.instantReplayDurationMinutes, v -> c.instantReplayDurationMinutes = v)
                                : number("instant_replay_size_limit", "gigabytes", 0.25, 10, 2,
                                () -> c.instantReplayStorageLimitGb,
                                v -> c.instantReplayStorageLimitGb = Math.min(v, c.recordingStorageLimitGb))
                );
                case DEBUG -> List.of(
                        bool("debug_hud", () -> c.debugHud, v -> c.debugHud = v),
                        bool("show_candidates", () -> c.showCandidatePositions, v -> c.showCandidatePositions = v),
                        bool("show_rays", () -> c.showRaycasts, v -> c.showRaycasts = v),
                        bool("show_selected", () -> c.showSelectedCameraPosition, v -> c.showSelectedCameraPosition = v),
                        bool("show_collisions", () -> c.showCollisionChecks, v -> c.showCollisionChecks = v)
                );
            };
        }
    }

    private static NumberSetting number(String key, String unit, double min, double max, int decimals,
                                        DoubleSupplier getter, DoubleConsumer setter) {
        return number(key, unit, min, max, decimals, 1.0, getter, setter);
    }

    private static NumberSetting number(String key, String unit, double min, double max, int decimals,
                                        double displayScale, DoubleSupplier getter, DoubleConsumer setter) {
        return new NumberSetting(key, unit, min, max, decimals, displayScale, getter, setter);
    }

    private static BooleanSetting bool(String key, BooleanSupplier getter, BooleanConsumer setter) {
        return new BooleanSetting(key, getter, setter);
    }

    private static DirectorySetting directory(String key) {
        return new DirectorySetting(key);
    }

    private static ChoiceSetting choice(String key, Supplier<Component> getter, Runnable advance, boolean rebuild) {
        return new ChoiceSetting(key, getter, advance, rebuild);
    }

    private static ActionSetting action(String key, Runnable action) {
        return new ActionSetting(key, action);
    }

    private sealed interface Setting permits NumberSetting, BooleanSetting, DirectorySetting, ChoiceSetting, ActionSetting {
        String key();

        default Component label() {
            return Component.translatable("observercam.config.setting." + key());
        }

        default Component tooltip() {
            return Component.translatable("observercam.config.setting." + key() + ".tooltip");
        }
    }

    private record NumberSetting(String key, String unit, double min, double max, int decimals, double displayScale,
                                 DoubleSupplier getter, DoubleConsumer setter) implements Setting {
    }

    private record BooleanSetting(String key, BooleanSupplier getter, BooleanConsumer setter) implements Setting {
    }

    private record DirectorySetting(String key) implements Setting {
    }

    private record ChoiceSetting(String key, Supplier<Component> getter, Runnable advance, boolean rebuild) implements Setting {
    }

    private record ActionSetting(String key, Runnable action) implements Setting {
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private static final class CategoryScreen extends Screen {
        private final Screen parent;
        private final Category category;

        private CategoryScreen(Screen parent, Category category) {
            super(Component.translatable("observercam.config.category.title", category.title()));
            this.parent = parent;
            this.category = category;
        }

        @Override
        protected void init() {
            int x = width / 2 - CONTENT_WIDTH / 2;
            int y = 46;
            for (Setting setting : category.settings()) {
                if (setting instanceof NumberSetting number) {
                    addRenderableWidget(new ValueSlider(x, y, CONTENT_WIDTH, number));
                } else if (setting instanceof BooleanSetting bool) {
                    Button toggle = Button.builder(toggleText(bool), button -> {
                                bool.setter.accept(!bool.getter.getAsBoolean());
                                button.setMessage(toggleText(bool));
                                ObserverCamClient.syncCameraSettings();
                            })
                            .tooltip(Tooltip.create(bool.tooltip()))
                            .bounds(x, y, CONTENT_WIDTH, 20)
                            .build();
                    addRenderableWidget(toggle);
                } else if (setting instanceof DirectorySetting directory) {
                    Button browse = Button.builder(directoryText(directory), button ->
                            RecordingDirectoryPicker.choose(() -> {
                                    button.setMessage(directoryText(directory));
                                    button.setTooltip(directoryTooltip(directory));
                                }))
                            .tooltip(directoryTooltip(directory))
                            .bounds(x, y, CONTENT_WIDTH, 20)
                            .build();
                    addRenderableWidget(browse);
                } else if (setting instanceof ChoiceSetting choice) {
                    Button choiceButton = Button.builder(choiceText(choice), button -> {
                                choice.advance.run();
                                ObserverCamConfig.get().save();
                                if (choice.rebuild) {
                                    rebuildWidgets();
                                } else {
                                    button.setMessage(choiceText(choice));
                                }
                            })
                            .tooltip(Tooltip.create(choice.tooltip()))
                            .bounds(x, y, CONTENT_WIDTH, 20)
                            .build();
                    addRenderableWidget(choiceButton);
                } else if (setting instanceof ActionSetting action) {
                    addRenderableWidget(Button.builder(action.label(), button -> action.action.run())
                            .tooltip(Tooltip.create(action.tooltip()))
                            .bounds(x, y, CONTENT_WIDTH, 20)
                            .build());
                }
                y += 24;
            }
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(width / 2 - 75, height - 30, 150, 20)
                    .build());
        }

        private static Component toggleText(BooleanSetting setting) {
            return ObserverCamConfigScreen.toggleText(setting);
        }

        private static Component directoryText(DirectorySetting setting) {
            var outputPath = ObserverCamConfig.get().recordingOutputPath();
            var fileName = outputPath.getFileName();
            String displayName = fileName == null ? outputPath.toString() : fileName.toString();
            return Component.translatable("observercam.config.value", setting.label(),
                    Component.literal(displayName));
        }

        private static Tooltip directoryTooltip(DirectorySetting setting) {
            return Tooltip.create(setting.tooltip().copy()
                    .append("\n")
                    .append(ObserverCamConfig.get().recordingOutputPath().toString()));
        }

        private static Component choiceText(ChoiceSetting setting) {
            return Component.translatable("observercam.config.value", setting.label(), setting.getter.get());
        }

        @Override
        public void onClose() {
            ObserverCamConfig.get().save();
            ObserverCamClient.syncCameraSettings();
            minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
            graphics.drawCenteredString(font, category.description(), width / 2, 31, 0xFFAAAAAA);
            super.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final NumberSetting setting;

        private ValueSlider(int x, int y, int width, NumberSetting setting) {
            super(x, y, width, 20, Component.empty(), normalize(setting));
            this.setting = setting;
            setTooltip(Tooltip.create(setting.tooltip()));
            updateMessage();
        }

        private static double normalize(NumberSetting setting) {
            return (setting.getter.getAsDouble() - setting.min) / (setting.max - setting.min);
        }

        @Override
        protected void updateMessage() {
            double displayed = setting.getter.getAsDouble() * setting.displayScale;
            String formatted = String.format(Locale.ROOT, "%1$." + setting.decimals + "f", displayed);
            Component valueText = Component.literal(formatted)
                    .append(Component.translatable("observercam.config.unit." + setting.unit));
            setMessage(Component.translatable("observercam.config.value", setting.label(), valueText));
        }

        @Override
        protected void applyValue() {
            setting.setter.accept(setting.min + value * (setting.max - setting.min));
            value = normalize(setting);
            ObserverCamClient.syncCameraSettings();
        }
    }
}
