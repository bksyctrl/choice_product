package com.ecommerce.workflow.service.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FFmpeg媒体处理服务
 * 提供音视频处理功能，包括音频提取、格式转换等
 */
@Service
public class FfmpegService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${media.output.dir:./uploads/media/processed}")
    private String outputDir;

    /**
     * 从视频中提取音频
     * 
     * @param videoPath 视频文件路径
     * @return 音频文件路径
     */
    public String extractAudio(String videoPath) {
        try {
            // 确保输出目录存在
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            // 生成输出文件名
            String audioFileName = "audio_" + UUID.randomUUID().toString().substring(0, 8) + ".mp3";
            String audioPath = outputPath.resolve(audioFileName).toString();

            // 构建FFmpeg命令
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(videoPath);
            command.add("-vn"); // 禁用视频流
            command.add("-acodec");
            command.add("libmp3lame"); // MP3编码器
            command.add("-ab");
            command.add("128k");
            command.add("-ar");
            command.add("44100");
            command.add("-y"); // 覆盖输出文件
            command.add(audioPath);
            
            log.info("执行音频提取命令: {}", String.join(" ", command));
            
            // 执行FFmpeg命令
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("音频提取成功: {}", audioPath);
                return audioPath;
            } else {
                log.error("音频提取失败，退出码: {}", exitCode);
                throw new RuntimeException("音频提取失败: " + exitCode);
            }

        } catch (Exception e) {
            log.error("音频提取失败", e);
            throw new RuntimeException("音频提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分割视频
     * 
     * @param videoPath 源视频文件路径
     * @param startTime 开始时间（秒）
     * @param duration  分割时长（秒）
     * @return 分割后的视频文件路径
     */
    public String splitVideo(String videoPath, double startTime, double duration) {
        try {
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            String outputFileName = "split_" + UUID.randomUUID().toString().substring(0, 8) + ".mp4";
            String outputPathStr = outputPath.resolve(outputFileName).toString();

            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(videoPath);
            command.add("-ss");
            command.add(String.valueOf(startTime)); // 开始时间
            command.add("-t");
            command.add(String.valueOf(duration)); // 持续时间
            command.add("-c");
            command.add("copy"); // 直接复制流，不重新编码以提高速度
            command.add("-y");
            command.add(outputPathStr);

            log.info("执行视频分割: {} -> {} ({}s - {}s)", videoPath, outputPathStr, startTime, startTime + duration);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("视频分割成功: {}", outputPathStr);
                return outputPathStr;
            } else {
                log.error("视频分割失败，退出码: {}", exitCode);
                throw new RuntimeException("视频分割失败");
            }

        } catch (Exception e) {
            log.error("视频分割失败", e);
            throw new RuntimeException("视频分割失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取视频信息
     * 
     * @param videoPath 视频文件路径
     * @return 视频信息的JSON字符串
     */
    public String getVideoInfo(String videoPath) {
        try {
            // 使用ffprobe替代ffmpeg来获取媒体信息
            String ffprobePath = ffmpegPath.replace("ffmpeg.exe", "ffprobe.exe");

            List<String> command = new ArrayList<>();
            command.add(ffprobePath);
            command.add("-v");
            command.add("quiet");
            command.add("-print_format");
            command.add("json");
            command.add("-show_format");
            command.add("-show_streams");
            command.add(videoPath);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("获取视频信息成功: {}", videoPath);
                return output.toString();
            } else {
                log.warn("ffprobe执行失败: {}", exitCode);
                return "{}";
            }

        } catch (Exception e) {
            log.error("获取视频信息失败", e);
            throw new RuntimeException("获取视频信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 合并多个视频文件
     * 
     * @param videoPaths 视频文件路径列表
     * @return 合并后的视频文件路径
     */
    public String mergeVideos(List<String> videoPaths) {
        try {
            if (videoPaths == null || videoPaths.isEmpty()) {
                throw new IllegalArgumentException("视频文件路径列表不能为空");
            }

            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            String outputFileName = "merged_" + UUID.randomUUID().toString().substring(0, 8) + ".mp4";
            String outputPathStr = outputPath.resolve(outputFileName).toString();

            // 创建临时文件列表
            Path listFile = outputPath.resolve("merge_list_" + UUID.randomUUID().toString() + ".txt");
            StringBuilder listContent = new StringBuilder();
            for (String path : videoPaths) {
                listContent.append("file '").append(path.replace("\\", "/")).append("'\n");
            }
            Files.writeString(listFile, listContent.toString());

            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-f");
            command.add("concat");
            command.add("-safe");
            command.add("0");
            command.add("-i");
            command.add(listFile.toString());
            command.add("-c");
            command.add("copy");
            command.add("-y");
            command.add(outputPathStr);

            log.info("执行视频合并: {} 个文件", videoPaths.size());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }

            int exitCode = process.waitFor();

            // 删除临时文件列表
            Files.deleteIfExists(listFile);

            if (exitCode == 0) {
                log.info("视频合并成功: {}", outputPathStr);
                return outputPathStr;
            } else {
                log.error("视频合并失败，退出码: {}", exitCode);
                throw new RuntimeException("视频合并失败");
            }

        } catch (Exception e) {
            log.error("视频合并失败", e);
            throw new RuntimeException("视频合并失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查FFmpeg是否可用
     * 
     * @return 是否可用
     */
    public boolean isFfmpegAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegPath, "-version");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("FFmpeg不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从视频中提取帧图片
     * 
     * @param videoPath 视频文件路径或URL
     * @param timestamp 提取时间点（秒），默认取第1秒
     * @return 提取的帧图片路径
     */
    public String extractFrame(String videoPath, Double timestamp) {
        try {
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            String frameFileName = "frame_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            String framePath = outputPath.resolve(frameFileName).toString();

            double time = timestamp != null ? timestamp : 1.0;

            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-ss");
            command.add(String.valueOf(time));
            command.add("-i");
            command.add(videoPath);
            command.add("-vframes");
            command.add("1");
            command.add("-q:v");
            command.add("2");
            command.add("-y");
            command.add(framePath);

            log.info("执行视频帧提取: {} -> {} (时间点: {}s)", videoPath, framePath, time);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("视频帧提取成功: {}", framePath);
                return framePath;
            } else {
                log.error("视频帧提取失败，退出码: {}", exitCode);
                throw new RuntimeException("视频帧提取失败: " + exitCode);
            }

        } catch (Exception e) {
            log.error("视频帧提取失败", e);
            throw new RuntimeException("视频帧提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从视频中提取多帧图片（用于质量分析）
     * 
     * @param videoPath 视频文件路径或URL
     * @param frameCount 提取帧数，默认3帧
     * @return 提取的帧图片路径列表
     */
    public List<String> extractMultipleFrames(String videoPath, Integer frameCount) {
        try {
            int count = frameCount != null ? frameCount : 3;
            List<String> framePaths = new ArrayList<>();
            
            for (int i = 0; i < count; i++) {
                double timestamp = (i + 1) * 1.0;
                String framePath = extractFrame(videoPath, timestamp);
                framePaths.add(framePath);
            }
            
            log.info("视频多帧提取完成: {} 帧", framePaths.size());
            return framePaths;
            
        } catch (Exception e) {
            log.error("视频多帧提取失败", e);
            throw new RuntimeException("视频多帧提取失败: " + e.getMessage(), e);
        }
    }
}
