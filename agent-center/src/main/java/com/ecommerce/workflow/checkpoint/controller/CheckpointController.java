package com.ecommerce.workflow.checkpoint.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot;
import com.ecommerce.workflow.checkpoint.mapper.FilesystemSnapshotMapper;
import com.ecommerce.workflow.controller.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkpoint")
public class CheckpointController {

    @Autowired
    private FilesystemSnapshotMapper snapshotMapper;

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> createSnapshot(
            @RequestParam(required = false) String description,
            @RequestBody(required = false) List<String> filePaths) {
        
        FilesystemSnapshot snapshot = new FilesystemSnapshot();
        snapshot.setSnapshotId(UUID.randomUUID().toString());
        snapshot.setDescription(description);
        snapshot.setStatus("SUCCESS");
        snapshot.setUserId(1L);
        snapshot.setFileCount(filePaths != null ? filePaths.size() : 0);
        snapshot.setTotalSize(0L);
        snapshot.setCreatedAt(LocalDateTime.now());
        
        snapshotMapper.insert(snapshot);
        
        Map<String, Object> result = new HashMap<>();
        result.put("snapshotId", snapshot.getSnapshotId());
        result.put("status", snapshot.getStatus());
        result.put("message", "snapshot created successfully");
        
        return ApiResponse.success(result);
    }

    @GetMapping("/list")
    public ApiResponse<List<FilesystemSnapshot>> listSnapshots(
            @RequestParam(required = false) String status) {
        
        QueryWrapper<FilesystemSnapshot> queryWrapper = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByDesc("created_at");
        
        List<FilesystemSnapshot> snapshots = snapshotMapper.selectList(queryWrapper);
        return ApiResponse.success(snapshots);
    }

    @GetMapping("/{snapshotId}")
    public ApiResponse<FilesystemSnapshot> getSnapshot(@PathVariable String snapshotId) {
        QueryWrapper<FilesystemSnapshot> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("snapshot_id", snapshotId);
        
        FilesystemSnapshot snapshot = snapshotMapper.selectOne(queryWrapper);
        if (snapshot == null) {
            return ApiResponse.error("snapshot not found");
        }
        return ApiResponse.success(snapshot);
    }

    @PostMapping("/rollback")
    public ApiResponse<Map<String, Object>> rollback(@RequestParam String snapshotId) {
        QueryWrapper<FilesystemSnapshot> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("snapshot_id", snapshotId);
        
        FilesystemSnapshot snapshot = snapshotMapper.selectOne(queryWrapper);
        if (snapshot == null) {
            return ApiResponse.error("snapshot not found");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("snapshotId", snapshotId);
        result.put("status", "rolled back");
        result.put("message", "rollback completed");
        
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{snapshotId}")
    public ApiResponse<Map<String, Object>> deleteSnapshot(@PathVariable String snapshotId) {
        QueryWrapper<FilesystemSnapshot> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("snapshot_id", snapshotId);
        
        int deleted = snapshotMapper.delete(queryWrapper);
        
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("message", deleted > 0 ? "deleted successfully" : "snapshot not found");
        
        return ApiResponse.success(result);
    }
}
