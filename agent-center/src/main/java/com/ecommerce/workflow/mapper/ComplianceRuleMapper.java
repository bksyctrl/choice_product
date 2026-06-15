package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.ComplianceRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ComplianceRuleMapper extends BaseMapper<ComplianceRule> {

    @Select("SELECT * FROM compliance_rule WHERE rule_type = #{ruleType} AND platform IN (#{platform}, 'all') AND enabled = 1 AND deleted = 0 ORDER BY priority ASC")
    List<ComplianceRule> findRulesByTypeAndPlatform(@Param("ruleType") String ruleType, @Param("platform") String platform);

    @Select("SELECT * FROM compliance_rule WHERE platform IN (#{platform}, 'all') AND enabled = 1 AND deleted = 0 ORDER BY priority ASC")
    List<ComplianceRule> findAllEnabledRulesByPlatform(@Param("platform") String platform);
}
