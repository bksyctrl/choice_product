package com.ecommerce.workflow.service.video;

import com.ecommerce.workflow.service.media.FfmpegService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 视频混剪服务
 * 提供视频合并、转场效果添加等混剪功能
 */
@Service
public class VideoMixingService {

    private static final Logger log = LoggerFactory.getLogger(VideoMixingService.class);

    @Autowired
    private FfmpegService ffmpegService;

    @Value("${media.output.dir:./uploads/media/processed}")
    private String outputDir;

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    /**
     * 合并多个视频并添加转场效果
     * 
     * @param videoPaths 视频文件路径列表
     * @param transitionType 转场类型（fade, wipe, slide, zoom）
     * @param transitionDuration 转场时长（秒）
     * @return 合并后的视频文件路径
     */
    public String mergeVideosWithTransition(List<String> videoPaths, String transitionType, double transitionDuration) {
        try {
            if (videoPaths == null || videoPaths.isEmpty()) {
                throw new IllegalArgumentException("视频文件路径列表不能为空");
            }

            if (videoPaths.size() == 1) {
                log.info("只有一个视频，无需合并");
                return videoPaths.get(0);
            }

            log.info("开始合并视频: {} 个文件, 转场类型={}, 转场时长={}s", 
                    videoPaths.size(), transitionType, transitionDuration);

            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            String outputFileName = "mixed_" + UUID.randomUUID().toString().substring(0, 8) + ".mp4";
            String outputPathStr = outputPath.resolve(outputFileName).toString();

            if (transitionType == null || transitionType.equals("none")) {
                return ffmpegService.mergeVideos(videoPaths);
            }

            List<String> command = buildTransitionMergeCommand(videoPaths, transitionType, transitionDuration, outputPathStr);

            log.info("执行带转场的视频合并命令: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("FFmpeg: {}", line);
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("视频混剪成功: {}", outputPathStr);
                return outputPathStr;
            } else {
                log.error("视频混剪失败，退出码: {}", exitCode);
                throw new RuntimeException("视频混剪失败");
            }

        } catch (Exception e) {
            log.error("视频混剪失败", e);
            throw new RuntimeException("视频混剪失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建带转场效果的视频合并命令
     * 使用简单可靠的 concat 方式
     */
    private List<String> buildTransitionMergeCommand(List<String> videoPaths, String transitionType, 
                                                      double transitionDuration, String outputPath) {
        // 如果视频数量较多或转场复杂，使用简单拼接（避免复杂的filter_complex错误）
        if (videoPaths.size() > 3) {
            log.info("视频数量较多({})，使用简单拼接方式", videoPaths.size());
            return buildSimpleConcatCommand(videoPaths, outputPath);
        }
        
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);

        for (int i = 0; i < videoPaths.size(); i++) {
            command.add("-i");
            command.add(videoPaths.get(i));
        }

        String transitionFilter = getTransitionFilter(transitionType, transitionDuration, videoPaths.size());

        command.add("-filter_complex");
        command.add(transitionFilter);
        command.add("-map");
        command.add("[outv]");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("fast");
        command.add("-crf");
        command.add("23");
        command.add("-y");
        command.add(outputPath);

        return command;
    }
    
    /**
     * 构建简单拼接命令（无转场，最可靠）
     */
    private List<String> buildSimpleConcatCommand(List<String> videoPaths, String outputPath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);

        for (String videoPath : videoPaths) {
            command.add("-i");
            command.add(videoPath);
        }

        // 构建 concat filter
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < videoPaths.size(); i++) {
            filter.append("[").append(i).append(":v:0]");
        }
        filter.append("concat=n=").append(videoPaths.size()).append(":v=1:a=0[outv]");

        command.add("-filter_complex");
        command.add(filter.toString());
        command.add("-map");
        command.add("[outv]");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("fast");
        command.add("-crf");
        command.add("23");
        command.add("-y");
        command.add(outputPath);

        return command;
    }

    /**
     * 获取转场效果滤镜
     */
    private String getTransitionFilter(String transitionType, double duration, int videoCount) {
        StringBuilder filter = new StringBuilder();

        switch (transitionType.toLowerCase()) {
            case "fade":
                filter.append(buildFadeFilter(duration, videoCount));
                break;
            case "wipe":
                filter.append(buildWipeFilter(duration, videoCount));
                break;
            case "slide":
                filter.append(buildSlideFilter(duration, videoCount));
                break;
            case "zoom":
                filter.append(buildZoomFilter(duration, videoCount));
                break;
            default:
                filter.append(buildFadeFilter(duration, videoCount));
                break;
        }

        return filter.toString();
    }

    /**
     * 淡入淡出转场 - 使用 xfade 滤镜（FFmpeg 4.4+ 支持）
     * 修复：正确处理 filter chain，确保只有一个最终输出
     */
    private String buildFadeFilter(double duration, int videoCount) {
        StringBuilder filter = new StringBuilder();
        
        for (int i = 0; i < videoCount - 1; i++) {
            double offset = 1.0 + i * (1.0 - duration);
            boolean isLast = (i == videoCount - 2);
            String outputLabel = isLast ? "[outv]" : String.format("[v%d]", i);
            
            if (i == 0) {
                filter.append(String.format("[%d:v][%d:v]xfade=transition=fade:duration=%.2f:offset=%.2f%s%s",
                        i, i + 1, duration, offset, outputLabel, isLast ? "" : ";"));
            } else {
                filter.append(String.format("[v%d][%d:v]xfade=transition=fade:duration=%.2f:offset=%.2f%s%s",
                        i - 1, i + 1, duration, offset, outputLabel, isLast ? "" : ";"));
            }
        }
        
        return filter.toString();
    }

    /**
     * 擦除转场 - 使用 xfade 滤镜
     * 修复：正确处理 filter chain，确保只有一个最终输出
     */
    private String buildWipeFilter(double duration, int videoCount) {
        StringBuilder filter = new StringBuilder();
        
        for (int i = 0; i < videoCount - 1; i++) {
            double offset = 1.0 + i * (1.0 - duration);
            boolean isLast = (i == videoCount - 2);
            String outputLabel = isLast ? "[outv]" : String.format("[v%d]", i);
            
            if (i == 0) {
                filter.append(String.format("[%d:v][%d:v]xfade=transition=wipeleft:duration=%.2f:offset=%.2f%s%s",
                        i, i + 1, duration, offset, outputLabel, isLast ? "" : ";"));
            } else {
                filter.append(String.format("[v%d][%d:v]xfade=transition=wipeleft:duration=%.2f:offset=%.2f%s%s",
                        i - 1, i + 1, duration, offset, outputLabel, isLast ? "" : ";"));
            }
        }
        
        return filter.toString();
    }

    /**
     * 滑动转场 - 使用 xfade 滤镜
     * 修复：正确处理 filter chain，确保只有一个最终输出
     */
    private String buildSlideFilter(double duration, int videoCount) {
        StringBuilder filter = new StringBuilder();
        
        for (int i = 0; i < videoCount - 1; i++) {
            double offset = 1.0 + i * (1.0 - duration);
            boolean isLast = (i == videoCount - 2);
            String outputLabel = isLast ? "[outv]" : String.format("[v%d]", i);
            
            if (i == 0) {
                filter.append(String.format("[%d:v][%d:v]xfade=transition=slideleft:duration=%.2f:offset=%.2f%s%s",
                        i, i + 1, duration, offset, outputLabel, isLast ? "" : ";"));
            } else {
                filter.append(String.format("[v%d][%d:v]xfade=transition=slideleft:duration=%.2f:offset=%.2f%s%s",
                        i - 1, i + 1, duration, offset, outputLabel, isLast ? "" : ";"));
            }
        }

        return filter.toString();
    }

    /**
     * 缩放转场 - 使用 xfade 滤镜（fade 效果）
     * 修复：正确处理 filter chain，确保只有一个最终输出
     */
    private String buildZoomFilter(double duration, int videoCount) {
        StringBuilder filter = new StringBuilder();
        
        for (int i = 0; i < videoCount - 1; i++) {
            // 计算 offset：每个视频播放1秒后触发转场
            double offset = 1.0 + i * (1.0 - duration);
            
            if (i == 0) {
                // 第一个转场：输入是 [0:v][1:v]
                if (i == videoCount - 2) {
                    // 也是最后一个转场，直接输出到 [outv]
                    filter.append(String.format("[%d:v][%d:v]xfade=transition=fade:duration=%.2f:offset=%.2f[outv]",
                            i, i + 1, duration, offset));
                } else {
                    // 不是最后一个，输出到中间标签 [v0]
                    filter.append(String.format("[%d:v][%d:v]xfade=transition=fade:duration=%.2f:offset=%.2f[v%d];",
                            i, i + 1, duration, offset, i));
                }
            } else {
                // 后续转场：输入是前一个转场的输出 [v%d] 和下一个视频 [%d:v]
                if (i == videoCount - 2) {
                    // 最后一个转场，直接输出到 [outv]
                    filter.append(String.format("[v%d][%d:v]xfade=transition=fade:duration=%.2f:offset=%.2f[outv]",
                            i - 1, i + 1, duration, offset));
                } else {
                    // 不是最后一个，输出到中间标签
                    filter.append(String.format("[v%d][%d:v]xfade=transition=fade:duration=%.2f:offset=%.2f[v%d];",
                            i - 1, i + 1, duration, offset, i));
                }
            }
        }

        return filter.toString();
    }

    /**
     * 批量并发生成视频后合并
     * 
     * @param videoUrls 视频URL列表
     * @param autoMix 是否自动混剪
     * @return 合并后的视频URL或单个视频URL
     */
    public String processBatchVideos(List<String> videoUrls, boolean autoMix) {
        List<String> localVideoPaths = new ArrayList<>();
        try {
            if (videoUrls == null || videoUrls.isEmpty()) {
                throw new IllegalArgumentException("视频URL列表不能为空");
            }

            if (!autoMix || videoUrls.size() == 1) {
                return videoUrls.get(0);
            }

            log.info("开始批量视频处理: {} 个视频, 自动混剪={}", videoUrls.size(), autoMix);

            // 第一步：下载所有远程视频到本地（如果URL是HTTP/HTTPS）
            for (int i = 0; i < videoUrls.size(); i++) {
                String videoUrl = videoUrls.get(i);
                if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                    try {
                        log.info("下载远程视频到本地: {}", videoUrl);
                        String localPath = downloadVideoToLocal(videoUrl, i);
                        localVideoPaths.add(localPath);
                        log.info("视频下载完成: {} -> {}", videoUrl, localPath);
                    } catch (Exception e) {
                        log.error("视频下载失败: {}, 错误: {}", videoUrl, e.getMessage(), e);
                        throw new RuntimeException("视频下载失败: " + videoUrl + ", 错误: " + e.getMessage(), e);
                    }
                } else {
                    // 已经是本地路径
                    localVideoPaths.add(videoUrl);
                }
            }

            // 第二步：尝试使用带转场效果的混剪
            log.info("尝试带转场混剪: {} 个本地视频", localVideoPaths.size());
            try {
                String mixedVideo = mergeVideosWithTransition(localVideoPaths, "fade", 0.5);
                log.info("带转场混剪成功: {}", mixedVideo);
                return mixedVideo;
            } catch (Exception e) {
                log.warn("带转场混剪失败: {}，尝试简单拼接", e.getMessage());
                
                // 第三步：降级为简单拼接（无转场）
                try {
                    String simpleMixedVideo = mergeVideosWithTransition(localVideoPaths, "none", 0);
                    log.info("简单拼接成功: {}", simpleMixedVideo);
                    return simpleMixedVideo;
                } catch (Exception e2) {
                    log.error("简单拼接也失败: {}", e2.getMessage());
                    // 所有混剪方式都失败，抛出异常
                    throw new RuntimeException("视频混剪失败: 转场混剪和简单拼接都失败", e2);
                }
            }

        } catch (Exception e) {
            log.error("批量视频处理失败", e);
            throw new RuntimeException("批量视频处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载远程视频到本地
     */
    private String downloadVideoToLocal(String videoUrl, int index) throws Exception {
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        String fileName = "video_" + UUID.randomUUID().toString().substring(0, 8) + "_" + index + ".mp4";
        Path localPath = outputPath.resolve(fileName);

        try (java.io.InputStream in = new java.net.URL(videoUrl).openStream()) {
            java.nio.file.Files.copy(in, localPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        return localPath.toString();
    }
}
