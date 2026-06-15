package com.ecommerce.workflow.checkpoint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.checkpoint.entity.FilesystemSnapshot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FilesystemSnapshotMapper extends BaseMapper<FilesystemSnapshot> {
}
