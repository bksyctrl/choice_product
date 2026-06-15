package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.media.FfmpegService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 媒体处理控制器
 * 负责音视频文件的处理和转换操作
 */
@RestController
@RequestMapping("/api/media")
public class MediaProcessingController {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingController.class);

    @Autowired
    private FfmpegService ffmpegService;

    /**
     * 检查FFmpeg是否可用
     */
    @GetMapping("/check")
    public ApiResponse<Map<String, Object>> checkFfmpeg() {
        Map<String, Object> result = new HashMap<>();
        boolean available = ffmpegService.isFfmpegAvailable();
        result.put("available", available);
        result.put("message", available ? "FFmpeg可用" : "FFmpeg不可用");

        if (available) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error("FFmpeg未安装或配置错误");
        }
    }

    /**
     * 提取视频中的音频
     */
    @PostMapping("/extract-audio")
    public ApiResponse<Map<String, Object>> extractAudio(@RequestBody Map<String, String> request) {
        try {
            String videoPath = request.get("videoPath");
            if (videoPath == null || videoPath.isEmpty()) {
                return ApiResponse.error("视频路径不能为空");
            }

            log.info("开始提取音频: {}", videoPath);

            String audioPath = ffmpegService.extractAudio(videoPath);

            Map<String, Object> result = new HashMap<>();
            result.put("audioPath", audioPath);
            result.put("message", "音频提取成功");

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("音频提取失败", e);
            return ApiResponse.error("音频提取失败: " + e.getMessage());
        }
    }

    /**
     * 分割视频
     */
    @PostMapping("/split-video")
    public ApiResponse<Map<String, Object>> splitVideo(@RequestBody Map<String, Object> request) {
        try {
            String videoPath = (String) request.get("videoPath");
            Double startTime = ((Number) request.get("startTime")).doubleValue();
            Double duration = ((Number) request.get("duration")).doubleValue();

            if (videoPath == null || videoPath.isEmpty()) {
                return ApiResponse.error("视频路径不能为空");
            }

            log.info("开始分割视频: {} ({}s - {}s)", videoPath, startTime, startTime + duration);

            String outputPath = ffmpegService.splitVideo(videoPath, startTime, duration);

            Map<String, Object> result = new HashMap<>();
            result.put("outputPath", outputPath);
            result.put("message", "视频分割成功");

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("视频分割失败", e);
            return ApiResponse.error("视频分割失败: " + e.getMessage());
        }
    }

    /**
     * 获取视频信息
     */
    @PostMapping("/video-info")
    public ApiResponse<Map<String, Object>> getVideoInfo(@RequestBody Map<String, String> request) {
        try {
            String videoPath = request.get("videoPath");
            if (videoPath == null || videoPath.isEmpty()) {
                return ApiResponse.error("视频路径不能为空");
            }

            log.info("获取视频信息: {}", videoPath);

            String info = ffmpegService.getVideoInfo(videoPath);

            Map<String, Object> result = new HashMap<>();
            result.put("info", info);
            result.put("message", "视频信息获取成功");

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("视频信息获取失败", e);
            return ApiResponse.error("视频信息获取失败: " + e.getMessage());
        }
    }

    /**
     * 合并多个视频
     */
    @PostMapping("/merge-videos")
    public ApiResponse<Map<String, Object>> mergeVideos(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> videoPaths = (List<String>) request.get("videoPaths");

            if (videoPaths == null || videoPaths.isEmpty()) {
                return ApiResponse.error("视频路径列表不能为空");
            }

            log.info("开始合并视频文件，共 {} 个视频", videoPaths.size());

            String outputPath = ffmpegService.mergeVideos(videoPaths);

            Map<String, Object> result = new HashMap<>();
            result.put("outputPath", outputPath);
            result.put("message", "视频合并成功");

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("视频合并失败", e);
            return ApiResponse.error("视频合并失败: " + e.getMessage());
        }
    }
}
