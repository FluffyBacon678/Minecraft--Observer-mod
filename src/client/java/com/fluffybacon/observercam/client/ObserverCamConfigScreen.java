package com.fluffybacon.observercam.client;

import com.fluffybacon.observercam.assistant.AssistantFactScheduler;
import com.fluffybacon.observercam.config.ObserverCamConfig;
import com.fluffybacon.observercam.client.recording.ObserverRecordingManager;
import com.fluffybacon.observercam.client.recording.RecordingState;
import com.fluffybacon.observercam.client.recording.ReplayState;
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
    private Button cameramanButton;
    private Button povButton;
    private Button recordingButton;
    private Button pictureInPictureButton;
    private Button outputDirectoryButton;

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
        cameramanButton = addRenderableWidget(Button.builder(cameramanQuickText(cameraman), button -> {
                    cameraman.setter.accept(!cameraman.getter.getAsBoolean());
                    button.setMessage(cameramanQuickText(cameraman));
                })
                .tooltip(shortcutTooltip(cameraman.tooltip(), ObserverCamClient.cameramanKeyText()))
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

        recordingButton = addRenderableWidget(Button.builder(recordingText(), button ->
                        ObserverRecordingManager.get().toggle(minecraft))
                .tooltip(shortcutTooltip(Component.translatable("observercam.config.recording.tooltip"),
                        ObserverCamClient.recordingKeyText()))
                .bounds(x, 70, 148, 20)
                .build());
        pictureInPictureButton = addRenderableWidget(Button.builder(pictureInPictureText(), button -> {
                    ObserverPictureInPicture.toggle();
                    button.setMessage(pictureInPictureText());
                })
                .tooltip(Tooltip.create(Component.translatable("observercam.config.pip.tooltip")))
                .bounds(x + 152, 70, 148, 20)
                .build());

        outputDirectoryButton = addRenderableWidget(Button.builder(outputDirectoryText(), button ->
                        RecordingDirectoryPicker.choose(() -> {
                            button.setMessage(outputDirectoryText());
                            button.setTooltip(outputDirectoryTooltip());
                        }))
                .tooltip(outputDirectoryTooltip())
                .bounds(x, 94, CONTENT_WIDTH, 20)
                .build());

        Category[] categories = Category.values();
        int categoryColumns = categories.length > 8 ? 3 : 2;
        int categoryGap = 4;
        int expandedWidth = categoryColumns == 3 && width >= 480 ? 452 : CONTENT_WIDTH;
        int categoryWidth = (expandedWidth - (categoryColumns - 1) * categoryGap) / categoryColumns;
        int categoryStartX = width / 2 - expandedWidth / 2;
        for (int index = 0; index < categories.length; index++) {
            Category category = categories[index];
            int categoryX = categoryStartX + (index % categoryColumns) * (categoryWidth + categoryGap);
            int categoryY = 118 + (index / categoryColumns) * 24;
            addRenderableWidget(Button.builder(category.title(), button -> minecraft.setScreen(new CategoryScreen(this, category)))
                    .tooltip(Tooltip.create(category.description()))
                    .bounds(categoryX, categoryY, categoryWidth, 20)
                    .build());
        }
        int categoryRows = (categories.length + categoryColumns - 1) / categoryColumns;
        int actionY = Math.min(height - 30, 126 + categoryRows * 24);
        addRenderableWidget(Button.builder(Component.translatable("observercam.config.reset"), button -> confirmReset())
                .tooltip(Tooltip.create(Component.translatable("observercam.config.reset.tooltip")))
                .bounds(x, actionY, 148, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x + 152, actionY, 148, 20)
                .build());
    }

    private static Component toggleText(BooleanSetting setting) {
        Component state = Component.translatable(setting.getter.getAsBoolean()
                ? "observercam.config.on" : "observercam.config.off");
        return Component.translatable("observercam.config.value", setting.label(), state);
    }

    private static Component cameramanQuickText(BooleanSetting setting) {
        Component state = Component.translatable(setting.getter.getAsBoolean()
                ? "observercam.config.on" : "observercam.config.off");
        return Component.translatable("observercam.config.quick.cameraman", state,
                ObserverCamClient.cameramanKeyText());
    }

    private static Tooltip shortcutTooltip(Component description, Component key) {
        return Tooltip.create(description.copy().append("\n")
                .append(Component.translatable("observercam.config.shortcut", key)));
    }

    private static Component povText() {
        if (ObserverCamClient.isViewingObserver()) {
            return Component.translatable("observercam.config.pov.exit");
        }
        return Component.translatable(ObserverCamClient.isPovRequestPending()
                ? "observercam.config.pov.cancel" : "observercam.config.pov.enter");
    }

    private static Component recordingText() {
        ObserverRecordingManager manager = ObserverRecordingManager.get();
        Component action = switch (manager.state()) {
            case RECORDING -> Component.translatable("observercam.config.recording.stop_time",
                    formatTime(manager.elapsedNanos()));
            case STARTING -> Component.translatable("observercam.config.recording.starting");
            case FINALIZING -> Component.translatable("observercam.config.recording.saving");
            case IDLE -> Component.translatable("observercam.config.recording.start");
        };
        return Component.translatable("observercam.config.quick.recording", action,
                ObserverCamClient.recordingKeyText());
    }

    private static String formatTime(long elapsedNanos) {
        long seconds = Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(elapsedNanos));
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private static Component pictureInPictureText() {
        return Component.translatable(ObserverPictureInPicture.isEnabled()
                ? "observercam.config.pip.hide" : "observercam.config.pip.show");
    }

    private Component outputDirectoryText() {
        Component label = Component.translatable("observercam.config.setting.recording_output_directory");
        String path = ObserverCamConfig.get().recordingOutputPath().toString();
        int availableWidth = CONTENT_WIDTH - 18 - font.width(label) - font.width(": ");
        return Component.translatable("observercam.config.value", label,
                Component.literal(shortenMiddle(path, availableWidth)));
    }

    private Tooltip outputDirectoryTooltip() {
        return Tooltip.create(Component.translatable(
                        "observercam.config.setting.recording_output_directory.tooltip")
                .copy().append("\n").append(ObserverCamConfig.get().recordingOutputPath().toString()));
    }

    private String shortenMiddle(String value, int maximumWidth) {
        if (maximumWidth <= font.width("…") || font.width(value) <= maximumWidth) {
            return value;
        }
        int leftLength = Math.min(3, value.length());
        String left = value.substring(0, leftLength);
        String right = value.substring(leftLength);
        while (!right.isEmpty() && font.width(left + "…" + right) > maximumWidth) {
            right = right.substring(1);
        }
        return left + "…" + right;
    }

    private void confirmReset() {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ObserverCamConfig.get().reset();
                ObserverCamClient.setCameramanEnabled(false);
                ObserverPictureInPicture.reset();
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
        if (cameramanButton != null) {
            BooleanSetting cameraman = bool("cameraman_enabled",
                    () -> ObserverCamConfig.get().cameramanEnabled, ObserverCamClient::setCameramanEnabled);
            cameramanButton.setMessage(cameramanQuickText(cameraman));
        }
        if (povButton != null) {
            povButton.setMessage(povText());
            povButton.active = minecraft != null && minecraft.level != null && minecraft.player != null;
        }
        if (recordingButton != null) {
            RecordingState state = ObserverRecordingManager.get().state();
            recordingButton.setMessage(recordingText());
            recordingButton.active = state == RecordingState.RECORDING
                    || state == RecordingState.IDLE
                    && ObserverRecordingManager.get().hasCaptureSource(minecraft)
                    && (ObserverRecordingManager.get().replayState() == ReplayState.IDLE
                    || ObserverRecordingManager.get().replayState() == ReplayState.BUFFERING);
        }
        if (pictureInPictureButton != null) {
            pictureInPictureButton.setMessage(pictureInPictureText());
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
        DIRECTOR("director"),
        ASSISTANT("assistant"),
        RECORDING("recording"),
        VIDEO("video"),
        PICTURE_IN_PICTURE("picture_in_picture"),
        REPLAY("replay"),
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
                case DIRECTOR -> List.of(
                        number("background_importance", "none", 0, 1.5, 2, () -> c.backgroundImportance, v -> c.backgroundImportance = v),
                        number("player_visibility", "none", 0.5, 2, 2, () -> c.playerVisibilityImportance, v -> c.playerVisibilityImportance = v),
                        number("shot_stability", "none", 0, 1, 2, () -> c.shotStability, v -> c.shotStability = v),
                        number("reframe_threshold", "score", 20, 90, 0, () -> c.reframeThreshold, v -> c.reframeThreshold = v),
                        number("player_screen_size", "percent", 0.15, 0.65, 0, 100, () -> c.preferredPlayerScreenSize, v -> c.preferredPlayerScreenSize = v),
                        number("prediction_ticks", "ticks", 0, 12, 1, () -> c.movementPredictionTicks, v -> c.movementPredictionTicks = v),
                        bool("cameraman_enabled", () -> c.cameramanEnabled, ObserverCamClient::setCameramanEnabled),
                        bool("follow_automatically", () -> c.followTargetAutomatically, v -> c.followTargetAutomatically = v),
                        bool("allow_front_shots", () -> c.allowFrontFacingShots, v -> c.allowFrontFacingShots = v)
                );
                case ASSISTANT -> List.of(
                        bool("assistant_enabled", () -> c.assistantEnabled, v -> c.assistantEnabled = v),
                        bool("assistant_facts_enabled", () -> c.assistantFactsEnabled,
                                v -> c.assistantFactsEnabled = v),
                        number("assistant_fact_interval", "minutes",
                                AssistantFactScheduler.MINIMUM_INTERVAL_MINUTES,
                                AssistantFactScheduler.MAXIMUM_INTERVAL_MINUTES,
                                0, () -> c.assistantFactIntervalMinutes,
                                v -> c.assistantFactIntervalMinutes = Math.round(v))
                );
                case RECORDING -> List.of(
                        directory("recording_output_directory"),
                        action("open_recording_folder", RecordingFolderActions::open),
                        number("recording_storage_limit", "gigabytes", 0.5, 100, 1,
                                () -> c.recordingStorageLimitGb,
                                v -> {
                                    c.recordingStorageLimitGb = v;
                                    c.instantReplayStorageLimitGb = Math.min(c.instantReplayStorageLimitGb, v);
                                }),
                        file("recording_ffmpeg", () -> c.recordingFfmpegPath, FFmpegExecutablePicker::choose),
                        bool("recording_audio_enabled", () -> c.recordingAudioEnabled,
                                v -> c.recordingAudioEnabled = v),
                        audioDevice("recording_audio_device")
                );
                case VIDEO -> List.of(
                        choice("recording_video_format",
                                () -> Component.translatable("observercam.config.format." + c.recordingVideoFormat.id()),
                                () -> c.recordingVideoFormat = c.recordingVideoFormat.next(), false),
                        choice("recording_resolution",
                                () -> Component.translatable("observercam.config.resolution." + c.recordingResolution.id()),
                                () -> c.recordingResolution = c.recordingResolution.next(), false),
                        choice("recording_quality",
                                () -> Component.translatable("observercam.config.quality." + c.recordingQuality.id()),
                                () -> c.recordingQuality = c.recordingQuality.next(), false),
                        number("recording_frame_rate", "frames_per_second", 15, 120, 0,
                                () -> c.recordingFrameRate, v -> c.recordingFrameRate = (int) Math.round(v)),
                        bool("recording_include_hud", () -> c.recordingIncludeHud, v -> c.recordingIncludeHud = v)
                );
                case PICTURE_IN_PICTURE -> List.of(
                        bool("picture_in_picture_enabled", () -> c.pictureInPictureEnabled,
                                ObserverPictureInPicture::setEnabled),
                        choice("picture_in_picture_resolution",
                                () -> Component.translatable("observercam.config.pip_resolution."
                                        + c.pictureInPictureResolution.id()),
                                () -> c.pictureInPictureResolution = c.pictureInPictureResolution.next(), false),
                        number("picture_in_picture_frame_rate", "frames_per_second", 2, 60, 0,
                                () -> c.pictureInPictureFrameRate,
                                v -> c.pictureInPictureFrameRate = (int) Math.round(v)),
                        number("picture_in_picture_opacity", "percent", 25, 100, 0,
                                () -> c.pictureInPictureOpacity * 100.0,
                                v -> c.pictureInPictureOpacity = v / 100.0)
                );
                case REPLAY -> List.of(
                        action("save_instant_replay", () -> ObserverRecordingManager.get()
                                .saveInstantReplay(net.minecraft.client.Minecraft.getInstance())),
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

    private static FileSetting file(String key, Supplier<String> getter, java.util.function.Consumer<Runnable> chooser) {
        return new FileSetting(key, getter, chooser);
    }

    private static ChoiceSetting choice(String key, Supplier<Component> getter, Runnable advance, boolean rebuild) {
        return new ChoiceSetting(key, getter, advance, rebuild);
    }

    private static AudioDeviceSetting audioDevice(String key) {
        return new AudioDeviceSetting(key);
    }

    private static ActionSetting action(String key, Runnable action) {
        return new ActionSetting(key, action);
    }

    private sealed interface Setting permits NumberSetting, BooleanSetting, DirectorySetting, FileSetting,
            AudioDeviceSetting, ChoiceSetting, ActionSetting {
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

    private record FileSetting(String key, Supplier<String> getter,
                               java.util.function.Consumer<Runnable> chooser) implements Setting {
    }

    private record AudioDeviceSetting(String key) implements Setting {
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
            List<Setting> settings = category.settings();
            boolean twoColumns = settings.size() > 7 && width >= 500;
            int rows = twoColumns ? (settings.size() + 1) / 2 : settings.size();
            int widgetWidth = twoColumns ? 224 : CONTENT_WIDTH;
            int totalWidth = twoColumns ? widgetWidth * 2 + 4 : widgetWidth;
            int startX = width / 2 - totalWidth / 2;
            int startY = 43;
            int step = rows <= 1 ? 24
                    : Math.min(24, Math.max(20, (height - 57 - startY) / (rows - 1)));
            for (int index = 0; index < settings.size(); index++) {
                Setting setting = settings.get(index);
                int column = twoColumns ? index / rows : 0;
                int row = twoColumns ? index % rows : index;
                int x = startX + column * (widgetWidth + 4);
                int y = startY + row * step;
                if (setting instanceof NumberSetting number) {
                    addRenderableWidget(new ValueSlider(x, y, widgetWidth, number));
                } else if (setting instanceof BooleanSetting bool) {
                    Button toggle = Button.builder(toggleText(bool), button -> {
                                bool.setter.accept(!bool.getter.getAsBoolean());
                                button.setMessage(toggleText(bool));
                                ObserverCamClient.syncCameraSettings();
                            })
                            .tooltip(Tooltip.create(bool.tooltip()))
                            .bounds(x, y, widgetWidth, 20)
                            .build();
                    addRenderableWidget(toggle);
                } else if (setting instanceof DirectorySetting directory) {
                    Button browse = Button.builder(directoryText(directory), button ->
                            RecordingDirectoryPicker.choose(() -> {
                                    button.setMessage(directoryText(directory));
                                    button.setTooltip(directoryTooltip(directory));
                                }))
                            .tooltip(directoryTooltip(directory))
                            .bounds(x, y, widgetWidth, 20)
                            .build();
                    addRenderableWidget(browse);
                } else if (setting instanceof FileSetting file) {
                    Button browse = Button.builder(fileText(file), button -> file.chooser.accept(() -> {
                                button.setMessage(fileText(file));
                                button.setTooltip(fileTooltip(file));
                            }))
                            .tooltip(fileTooltip(file))
                            .bounds(x, y, widgetWidth, 20)
                            .build();
                    addRenderableWidget(browse);
                } else if (setting instanceof AudioDeviceSetting audio) {
                    Button choose = Button.builder(audioDeviceText(audio), button ->
                                    FFmpegAudioDevicePicker.choose(() -> {
                                        button.setMessage(audioDeviceText(audio));
                                        button.setTooltip(audioDeviceTooltip(audio));
                                        rebuildWidgets();
                                    }))
                            .tooltip(audioDeviceTooltip(audio))
                            .bounds(x, y, widgetWidth, 20)
                            .build();
                    addRenderableWidget(choose);
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
                            .bounds(x, y, widgetWidth, 20)
                            .build();
                    addRenderableWidget(choiceButton);
                } else if (setting instanceof ActionSetting action) {
                    addRenderableWidget(Button.builder(action.label(), button -> action.action.run())
                            .tooltip(Tooltip.create(action.tooltip()))
                            .bounds(x, y, widgetWidth, 20)
                            .build());
                }
            }
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(width / 2 - 75, height - 30, 150, 20)
                    .build());
        }

        private static Component toggleText(BooleanSetting setting) {
            return ObserverCamConfigScreen.toggleText(setting);
        }

        private Component directoryText(DirectorySetting setting) {
            var outputPath = ObserverCamConfig.get().recordingOutputPath();
            String displayName = outputPath.toString();
            return Component.translatable("observercam.config.value", setting.label(),
                    Component.literal(shortenMiddle(displayName, CONTENT_WIDTH - 130)));
        }

        private String shortenMiddle(String value, int maximumWidth) {
            if (maximumWidth <= font.width("…") || font.width(value) <= maximumWidth) {
                return value;
            }
            int leftLength = Math.min(3, value.length());
            String left = value.substring(0, leftLength);
            String right = value.substring(leftLength);
            while (!right.isEmpty() && font.width(left + "…" + right) > maximumWidth) {
                right = right.substring(1);
            }
            return left + "…" + right;
        }

        private static Tooltip directoryTooltip(DirectorySetting setting) {
            return Tooltip.create(setting.tooltip().copy()
                    .append("\n")
                    .append(ObserverCamConfig.get().recordingOutputPath().toString()));
        }

        private static Component fileText(FileSetting setting) {
            String configured = setting.getter.get();
            String displayName;
            try {
                var fileName = java.nio.file.Path.of(configured).getFileName();
                displayName = fileName == null ? configured : fileName.toString();
            } catch (java.nio.file.InvalidPathException ignored) {
                displayName = configured;
            }
            return Component.translatable("observercam.config.value", setting.label(),
                    Component.literal(displayName));
        }

        private static Tooltip fileTooltip(FileSetting setting) {
            return Tooltip.create(setting.tooltip().copy().append("\n").append(setting.getter.get()));
        }

        private static Component audioDeviceText(AudioDeviceSetting setting) {
            String device = ObserverCamConfig.get().recordingAudioDevice;
            Component value = device.isBlank()
                    ? Component.translatable("observercam.config.audio.not_selected")
                    : Component.literal(device);
            return Component.translatable("observercam.config.value", setting.label(), value);
        }

        private static Tooltip audioDeviceTooltip(AudioDeviceSetting setting) {
            String device = ObserverCamConfig.get().recordingAudioDevice;
            var tooltip = setting.tooltip().copy();
            if (!device.isBlank()) {
                tooltip.append("\n").append(device);
            }
            return Tooltip.create(tooltip);
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
