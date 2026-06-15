package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.video.VideoTrackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/video-track")
public class VideoTrackController {

    @Autowired
    private VideoTrackService videoTrackService;

    @PostMapping("/separate")
    public ApiResponse<Map<String, Object>> separateTracks(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        if (videoUrl == null || videoUrl.isEmpty()) {
            return ApiResponse.error("视频URL不能为空");
        }

        Map<String, Object> result = videoTrackService.separateTracks(videoUrl);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("error"));
        }
    }

    @GetMapping("/status/{taskId}")
    public ApiResponse<Map<String, Object>> getTrackStatus(@PathVariable String taskId) {
        Map<String, Object> result = videoTrackService.getTrackStatus(taskId);
        return ApiResponse.success(result);
    }
}
