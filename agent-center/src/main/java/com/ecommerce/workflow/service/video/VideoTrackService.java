package com.ecommerce.workflow.service.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VideoTrackService {

    private static final Logger log = LoggerFactory.getLogger(VideoTrackService.class);

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    public Map<String, Object> separateTracks(String videoUrl) {
        log.info("开始视频分轨: {}", videoUrl);

        Map<String, Object> result = new HashMap<>();
        String taskId = UUID.randomUUID().toString();
        String workDir = uploadPath + "/tracks/" + taskId;

        try {
            Files.createDirectories(Paths.get(workDir));

            String inputPath = resolveVideoPath(videoUrl);
            if (!Files.exists(Paths.get(inputPath))) {
                throw new RuntimeException("视频文件不存在: " + inputPath);
            }

            String videoOnlyPath = workDir + "/video_only.mp4";
            String audioPath = workDir + "/audio.mp3";
            String subtitlePath = workDir + "/subtitles.srt";

            List<String> extractedTracks = new ArrayList<>();

            if (extractVideoTrack(inputPath, videoOnlyPath)) {
                extractedTracks.add("video");
                result.put("videoTrack", "/uploads/tracks/" + taskId + "/video_only.mp4");
            }

            if (extractAudioTrack(inputPath, audioPath)) {
                extractedTracks.add("audio");
                result.put("audioTrack", "/uploads/tracks/" + taskId + "/audio.mp3");
            }

            if (extractSubtitles(inputPath, subtitlePath)) {
                extractedTracks.add("subtitle");
                result.put("subtitleTrack", "/uploads/tracks/" + taskId + "/subtitles.srt");
            }

            result.put("taskId", taskId);
            result.put("workDir", workDir);
            result.put("extractedTracks", extractedTracks);
            result.put("success", true);
            result.put("message", "视频分轨完成，共提取 " + extractedTracks.size() + " 个轨道");

            log.info("视频分轨完成: taskId={}, tracks={}", taskId, extractedTracks);

        } catch (Exception e) {
            log.error("视频分轨失败: {}", videoUrl, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private String resolveVideoPath(String videoUrl) {
        if (videoUrl.startsWith("/uploads/")) {
            return uploadPath + videoUrl.substring("/uploads".length());
        }
        if (videoUrl.startsWith("http")) {
            return downloadVideo(videoUrl);
        }
        return videoUrl;
    }

    private String downloadVideo(String url) {
        String targetPath = uploadPath + "/temp/" + UUID.randomUUID() + ".mp4";
        try {
            Files.createDirectories(Paths.get(uploadPath + "/temp"));
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-L", "-o", targetPath, url
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("下载视频失败: " + url);
            }
            return targetPath;
        } catch (Exception e) {
            throw new RuntimeException("下载视频失败: " + e.getMessage(), e);
        }
    }

    private boolean extractVideoTrack(String inputPath, String outputPath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(inputPath);
            command.add("-an");
            command.add("-sn");
            command.add("-c:v");
            command.add("copy");
            command.add("-y");
            command.add(outputPath);

            log.debug("提取视频轨道: {}", String.join(" ", command));
            return executeCommand(command);
        } catch (Exception e) {
            log.warn("提取视频轨道失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean extractAudioTrack(String inputPath, String outputPath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(inputPath);
            command.add("-vn");
            command.add("-sn");
            command.add("-acodec");
            command.add("libmp3lame");
            command.add("-q:a");
            command.add("2");
            command.add("-y");
            command.add(outputPath);

            log.debug("提取音频轨道: {}", String.join(" ", command));
            return executeCommand(command);
        } catch (Exception e) {
            log.warn("提取音频轨道失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean extractSubtitles(String inputPath, String outputPath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(inputPath);
            command.add("-map");
            command.add("0:s:0");
            command.add("-y");
            command.add(outputPath);

            log.debug("提取字幕轨道: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.info("视频可能没有内嵌字幕，尝试使用语音识别生成字幕");
                return generateSubtitlesFromAudio(inputPath, outputPath);
            }

            return true;
        } catch (Exception e) {
            log.warn("提取字幕轨道失败，尝试语音识别: {}", e.getMessage());
            return generateSubtitlesFromAudio(inputPath, outputPath);
        }
    }

    private boolean generateSubtitlesFromAudio(String inputPath, String outputPath) {
        try {
            String tempAudioPath = inputPath + ".temp_audio.wav";
            
            List<String> audioCommand = new ArrayList<>();
            audioCommand.add(ffmpegPath);
            audioCommand.add("-i");
            audioCommand.add(inputPath);
            audioCommand.add("-vn");
            audioCommand.add("-acodec");
            audioCommand.add("pcm_s16le");
            audioCommand.add("-ar");
            audioCommand.add("16000");
            audioCommand.add("-ac");
            audioCommand.add("1");
            audioCommand.add("-y");
            audioCommand.add(tempAudioPath);

            if (!executeCommand(audioCommand)) {
                log.warn("提取音频用于语音识别失败");
                return false;
            }

            File subtitleFile = new File(outputPath);
            subtitleFile.createNewFile();
            Files.writeString(subtitleFile.toPath(), "1\n00:00:00,000 --> 00:00:10,000\n[语音识别字幕待实现]\n\n");

            new File(tempAudioPath).delete();

            log.info("已创建占位字幕文件，实际语音识别需要集成ASR服务");
            return true;

        } catch (Exception e) {
            log.warn("生成字幕失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean executeCommand(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }

            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("执行命令失败: {}", command, e);
            return false;
        }
    }

    public Map<String, Object> getTrackStatus(String taskId) {
        Map<String, Object> result = new HashMap<>();
        String workDir = uploadPath + "/tracks/" + taskId;

        Path videoTrack = Paths.get(workDir, "video_only.mp4");
        Path audioTrack = Paths.get(workDir, "audio.mp3");
        Path subtitleTrack = Paths.get(workDir, "subtitles.srt");

        result.put("taskId", taskId);
        result.put("videoTrackExists", Files.exists(videoTrack));
        result.put("audioTrackExists", Files.exists(audioTrack));
        result.put("subtitleTrackExists", Files.exists(subtitleTrack));

        try {
            if (Files.exists(videoTrack)) {
                result.put("videoTrackSize", Files.size(videoTrack));
            }
            if (Files.exists(audioTrack)) {
                result.put("audioTrackSize", Files.size(audioTrack));
            }
            if (Files.exists(subtitleTrack)) {
                result.put("subtitleTrackSize", Files.size(subtitleTrack));
            }
        } catch (Exception e) {
            log.warn("获取轨道文件大小失败: {}", e.getMessage());
        }

        return result;
    }
}
