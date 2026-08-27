package com.fluffybacon.observercam.recording;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the fixed, user-safe FFmpeg commands used by live and replay recording. */
public final class FFmpegCommand {
    public static final int REPLAY_SEGMENT_SECONDS = 2;

    private FFmpegCommand() {
    }

    public static List<String> build(String executable, RecordingVideoFormat format,
                                     int width, int height, int framesPerSecond, Path outputFile) {
        return build(executable, format, RecordingResolution.CURRENT, RecordingQuality.BALANCED,
                width, height, framesPerSecond, outputFile);
    }

    public static List<String> build(String executable, RecordingVideoFormat format,
                                     RecordingResolution resolution, RecordingQuality quality,
                                     int width, int height, int framesPerSecond, Path outputFile) {
        return build(executable, format, resolution, quality, RecordingAudio.disabled(),
                width, height, framesPerSecond, outputFile);
    }

    public static List<String> build(String executable, RecordingVideoFormat format,
                                     RecordingResolution resolution, RecordingQuality quality,
                                     RecordingAudio audio, int width, int height,
                                     int framesPerSecond, Path outputFile) {
        if (executable == null || executable.isBlank()) {
            throw new IllegalArgumentException("FFmpeg executable must not be blank");
        }
        if (format == null || resolution == null || quality == null || audio == null || width <= 0 || height <= 0
                || framesPerSecond <= 0 || outputFile == null) {
            throw new IllegalArgumentException("Invalid recording parameters");
        }

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-nostdin");
        command.add("-y");
        command.add("-f");
        command.add("rawvideo");
        command.add("-pixel_format");
        command.add("rgba");
        command.add("-video_size");
        command.add(width + "x" + height);
        command.add("-framerate");
        command.add(Integer.toString(framesPerSecond));
        command.add("-i");
        command.add("pipe:0");
        addAudioInput(command, audio);
        command.add("-vf");
        command.add(resolution.videoFilter());
        addStreamMaps(command, audio);

        switch (format) {
            case MP4, MKV -> {
                command.add("-c:v");
                command.add("libx264");
                command.add("-preset");
                command.add("veryfast");
                command.add("-crf");
                command.add(Integer.toString(quality.crf(format)));
                command.add("-pix_fmt");
                command.add("yuv420p");
                if (format == RecordingVideoFormat.MP4) {
                    command.add("-movflags");
                    command.add("+faststart");
                }
            }
            case WEBM -> {
                command.add("-c:v");
                command.add("libvpx-vp9");
                command.add("-deadline");
                command.add("realtime");
                command.add("-cpu-used");
                command.add("6");
                command.add("-crf");
                command.add(Integer.toString(quality.crf(format)));
                command.add("-b:v");
                command.add("0");
                command.add("-pix_fmt");
                command.add("yuv420p");
            }
        }
        addAudioOutput(command, format, audio, true);
        command.add(outputFile.toString());
        return List.copyOf(command);
    }

    public static List<String> buildReplayBuffer(String executable, RecordingVideoFormat format,
                                                  int width, int height, int framesPerSecond,
                                                  Path segmentPattern) {
        return buildReplayBuffer(executable, format, RecordingResolution.CURRENT,
                RecordingQuality.BALANCED, width, height, framesPerSecond, segmentPattern);
    }

    public static List<String> buildReplayBuffer(String executable, RecordingVideoFormat format,
                                                  RecordingResolution resolution, RecordingQuality quality,
                                                  int width, int height, int framesPerSecond,
                                                  Path segmentPattern) {
        return buildReplayBuffer(executable, format, resolution, quality, RecordingAudio.disabled(),
                width, height, framesPerSecond, segmentPattern);
    }

    public static List<String> buildReplayBuffer(String executable, RecordingVideoFormat format,
                                                  RecordingResolution resolution, RecordingQuality quality,
                                                  RecordingAudio audio, int width, int height,
                                                  int framesPerSecond, Path segmentPattern) {
        validate(executable, format, resolution, quality, audio, width, height, framesPerSecond, segmentPattern);
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-nostdin");
        command.add("-y");
        command.add("-f");
        command.add("rawvideo");
        command.add("-pixel_format");
        command.add("rgba");
        command.add("-video_size");
        command.add(width + "x" + height);
        command.add("-framerate");
        command.add(Integer.toString(framesPerSecond));
        command.add("-i");
        command.add("pipe:0");
        addAudioInput(command, audio);
        command.add("-vf");
        command.add(resolution.videoFilter());
        addStreamMaps(command, audio);
        addVideoCodec(command, format, quality);
        addAudioOutput(command, format, audio, false);

        int groupSize = Math.multiplyExact(framesPerSecond, REPLAY_SEGMENT_SECONDS);
        command.add("-g");
        command.add(Integer.toString(groupSize));
        if (format != RecordingVideoFormat.WEBM) {
            command.add("-keyint_min");
            command.add(Integer.toString(groupSize));
            command.add("-sc_threshold");
            command.add("0");
        }
        command.add("-force_key_frames");
        command.add("expr:gte(t,n_forced*" + REPLAY_SEGMENT_SECONDS + ")");
        command.add("-f");
        command.add("segment");
        command.add("-segment_time");
        command.add(Integer.toString(REPLAY_SEGMENT_SECONDS));
        command.add("-segment_time_delta");
        command.add(String.format(Locale.ROOT, "%.9f", 1.0 / (2.0 * framesPerSecond)));
        command.add("-reset_timestamps");
        command.add("1");
        command.add("-segment_format");
        command.add(format == RecordingVideoFormat.WEBM ? "webm" : "mpegts");
        command.add(segmentPattern.toString());
        return List.copyOf(command);
    }

    public static List<String> buildReplayExport(String executable, RecordingVideoFormat format,
                                                  Path concatList, Path outputFile) {
        if (executable == null || executable.isBlank() || format == null
                || concatList == null || outputFile == null) {
            throw new IllegalArgumentException("Invalid replay export parameters");
        }
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-nostdin");
        command.add("-y");
        command.add("-f");
        command.add("concat");
        command.add("-safe");
        command.add("0");
        command.add("-i");
        command.add(concatList.toString());
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("0:a:0?");
        command.add("-c:v");
        command.add("copy");
        command.add("-c:a");
        command.add("copy");
        if (format == RecordingVideoFormat.MP4) {
            command.add("-movflags");
            command.add("+faststart");
        }
        command.add(outputFile.toString());
        return List.copyOf(command);
    }

    public static String replaySegmentExtension(RecordingVideoFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("Video format must not be null");
        }
        return format == RecordingVideoFormat.WEBM ? "webm" : "ts";
    }

    private static void addVideoCodec(List<String> command, RecordingVideoFormat format,
                                      RecordingQuality quality) {
        switch (format) {
            case MP4, MKV -> {
                command.add("-c:v");
                command.add("libx264");
                command.add("-preset");
                command.add("veryfast");
                command.add("-crf");
                command.add(Integer.toString(quality.crf(format)));
                command.add("-pix_fmt");
                command.add("yuv420p");
            }
            case WEBM -> {
                command.add("-c:v");
                command.add("libvpx-vp9");
                command.add("-deadline");
                command.add("realtime");
                command.add("-cpu-used");
                command.add("6");
                command.add("-crf");
                command.add(Integer.toString(quality.crf(format)));
                command.add("-b:v");
                command.add("0");
                command.add("-pix_fmt");
                command.add("yuv420p");
            }
        }
    }

    private static void addAudioInput(List<String> command, RecordingAudio audio) {
        if (!audio.enabled()) {
            return;
        }
        command.add("-thread_queue_size");
        command.add("512");
        command.add("-f");
        command.add("dshow");
        command.add("-i");
        command.add("audio=" + audio.deviceName());
    }

    private static void addStreamMaps(List<String> command, RecordingAudio audio) {
        command.add("-map");
        command.add("0:v:0");
        if (audio.enabled()) {
            command.add("-map");
            command.add("1:a:0");
        } else {
            command.add("-an");
        }
    }

    private static void addAudioOutput(List<String> command, RecordingVideoFormat format,
                                       RecordingAudio audio, boolean stopWithVideo) {
        if (!audio.enabled()) {
            return;
        }
        command.add("-af");
        command.add("aresample=async=1000:first_pts=0");
        command.add("-c:a");
        command.add(format == RecordingVideoFormat.WEBM ? "libopus" : "aac");
        command.add("-b:a");
        command.add(format == RecordingVideoFormat.WEBM ? "160k" : "192k");
        if (stopWithVideo) {
            command.add("-shortest");
        }
    }

    private static void validate(String executable, RecordingVideoFormat format,
                                 RecordingResolution resolution, RecordingQuality quality, RecordingAudio audio,
                                 int width, int height, int framesPerSecond, Path output) {
        if (executable == null || executable.isBlank() || format == null || resolution == null
                || quality == null || audio == null || width <= 0 || height <= 0
                || framesPerSecond <= 0 || output == null) {
            throw new IllegalArgumentException("Invalid recording parameters");
        }
    }
}
