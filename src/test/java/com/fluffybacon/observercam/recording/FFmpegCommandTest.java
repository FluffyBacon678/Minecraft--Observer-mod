package com.fluffybacon.observercam.recording;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FFmpegCommandTest {
    @Test
    void mp4UsesRawRgbaAndCompatibilityFocusedH264() {
        List<String> command = FFmpegCommand.build("ffmpeg", RecordingVideoFormat.MP4,
                1920, 1080, 30, Path.of("clip.partial.mp4"));

        assertTrue(containsPair(command, "-f", "rawvideo"));
        assertTrue(containsPair(command, "-pixel_format", "rgba"));
        assertTrue(containsPair(command, "-video_size", "1920x1080"));
        assertTrue(containsPair(command, "-framerate", "30"));
        assertTrue(containsPair(command, "-c:v", "libx264"));
        assertTrue(containsPair(command, "-pix_fmt", "yuv420p"));
        assertTrue(containsPair(command, "-movflags", "+faststart"));
        assertEquals("clip.partial.mp4", command.getLast());
    }

    @Test
    void webmUsesVp9WithoutMp4Flags() {
        List<String> command = FFmpegCommand.build("custom-ffmpeg", RecordingVideoFormat.WEBM,
                1279, 719, 60, Path.of("clip.partial.webm"));

        assertEquals("custom-ffmpeg", command.getFirst());
        assertTrue(containsPair(command, "-c:v", "libvpx-vp9"));
        assertTrue(containsPair(command, "-deadline", "realtime"));
        assertFalse(command.contains("+faststart"));
        assertTrue(containsPair(command, "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2"));
    }

    @Test
    void rejectsUnsafeIncompleteSettings() {
        assertThrows(IllegalArgumentException.class, () -> FFmpegCommand.build(
                "", RecordingVideoFormat.MP4, 1920, 1080, 30, Path.of("out.mp4")));
        assertThrows(IllegalArgumentException.class, () -> FFmpegCommand.build(
                "ffmpeg", RecordingVideoFormat.MP4, 0, 1080, 30, Path.of("out.mp4")));
    }

    @Test
    void buildsKeyframeAlignedH264ReplaySegments() {
        List<String> command = FFmpegCommand.buildReplayBuffer("ffmpeg", RecordingVideoFormat.MP4,
                1920, 1080, 30, Path.of("segment-%08d.ts"));

        assertTrue(containsPair(command, "-f", "segment"));
        assertTrue(containsPair(command, "-segment_time", "2"));
        assertTrue(containsPair(command, "-segment_format", "mpegts"));
        assertTrue(containsPair(command, "-g", "60"));
        assertTrue(containsPair(command, "-reset_timestamps", "1"));
        assertTrue(command.contains("expr:gte(t,n_forced*2)"));
    }

    @Test
    void buildsVp9ReplaySegmentsAndCopyExport() {
        List<String> buffer = FFmpegCommand.buildReplayBuffer("ffmpeg", RecordingVideoFormat.WEBM,
                1280, 720, 60, Path.of("segment-%08d.webm"));
        assertTrue(containsPair(buffer, "-segment_format", "webm"));
        assertTrue(containsPair(buffer, "-c:v", "libvpx-vp9"));
        assertTrue(containsPair(buffer, "-g", "120"));

        List<String> export = FFmpegCommand.buildReplayExport("ffmpeg", RecordingVideoFormat.WEBM,
                Path.of("segments.ffconcat"), Path.of("replay.partial.webm"));
        assertTrue(containsPair(export, "-f", "concat"));
        assertTrue(containsPair(export, "-c:v", "copy"));
        assertFalse(export.contains("+faststart"));
    }

    @Test
    void enablesFastStartForMp4ReplayExport() {
        List<String> command = FFmpegCommand.buildReplayExport("ffmpeg", RecordingVideoFormat.MP4,
                Path.of("segments.ffconcat"), Path.of("replay.partial.mp4"));

        assertTrue(containsPair(command, "-movflags", "+faststart"));
        assertEquals("ts", FFmpegCommand.replaySegmentExtension(RecordingVideoFormat.MKV));
        assertEquals("webm", FFmpegCommand.replaySegmentExtension(RecordingVideoFormat.WEBM));
    }

    @Test
    void appliesResolutionAndQualityToLiveAndReplayCommands() {
        List<String> live = FFmpegCommand.build("ffmpeg", RecordingVideoFormat.MP4,
                RecordingResolution.FULL_HD_1080, RecordingQuality.HIGH,
                1600, 900, 30, Path.of("quality.partial.mp4"));
        assertTrue(containsPair(live, "-crf", "18"));
        assertTrue(containsPair(live, "-vf", RecordingResolution.FULL_HD_1080.videoFilter()));

        List<String> replay = FFmpegCommand.buildReplayBuffer("ffmpeg", RecordingVideoFormat.WEBM,
                RecordingResolution.HD_720, RecordingQuality.SMALL,
                1600, 900, 30, Path.of("segment-%08d.webm"));
        assertTrue(containsPair(replay, "-crf", "38"));
        assertTrue(containsPair(replay, "-vf", RecordingResolution.HD_720.videoFilter()));
    }

    @Test
    void recordsDirectShowAudioOnTheSameLiveFfmpegTimeline() {
        RecordingAudio audio = RecordingAudio.directShow("CABLE Output (VB-Audio Virtual Cable)");
        List<String> command = FFmpegCommand.build("ffmpeg", RecordingVideoFormat.MP4,
                RecordingResolution.CURRENT, RecordingQuality.BALANCED, audio,
                1920, 1080, 30, Path.of("with-audio.partial.mp4"));

        assertTrue(containsPair(command, "-f", "dshow"));
        assertTrue(command.contains("audio=CABLE Output (VB-Audio Virtual Cable)"));
        assertTrue(containsPair(command, "-map", "0:v:0"));
        assertTrue(containsPair(command, "-map", "1:a:0"));
        assertTrue(containsPair(command, "-c:a", "aac"));
        assertTrue(containsPair(command, "-b:a", "192k"));
        assertTrue(command.contains("-shortest"));
        assertFalse(command.contains("-an"));
    }

    @Test
    void carriesOpusAudioThroughWebmReplaySegmentsAndExport() {
        RecordingAudio audio = RecordingAudio.directShow("Stereo Mix");
        List<String> buffer = FFmpegCommand.buildReplayBuffer("ffmpeg", RecordingVideoFormat.WEBM,
                RecordingResolution.HD_720, RecordingQuality.BALANCED, audio,
                1280, 720, 30, Path.of("segment-%08d.webm"));
        assertTrue(containsPair(buffer, "-c:a", "libopus"));
        assertTrue(containsPair(buffer, "-b:a", "160k"));
        assertFalse(buffer.contains("-shortest"));

        List<String> export = FFmpegCommand.buildReplayExport("ffmpeg", RecordingVideoFormat.WEBM,
                Path.of("segments.ffconcat"), Path.of("replay.partial.webm"));
        assertTrue(containsPair(export, "-map", "0:a:0?"));
        assertTrue(containsPair(export, "-c:a", "copy"));
    }

    @Test
    void videoOnlyCommandsExplicitlyDisableAudio() {
        List<String> command = FFmpegCommand.build("ffmpeg", RecordingVideoFormat.MKV,
                1280, 720, 30, Path.of("video-only.mkv"));
        assertTrue(command.contains("-an"));
        assertFalse(command.contains("dshow"));
    }

    private static boolean containsPair(List<String> values, String first, String second) {
        for (int index = 0; index + 1 < values.size(); index++) {
            if (values.get(index).equals(first) && values.get(index + 1).equals(second)) {
                return true;
            }
        }
        return false;
    }
}
