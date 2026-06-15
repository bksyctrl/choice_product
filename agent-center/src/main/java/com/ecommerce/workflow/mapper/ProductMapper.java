package com.ecommerce.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.workflow.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    
    @Select("SELECT * FROM biz_product WHERE product_id = #{productId} AND deleted = 0")
    Product selectByProductId(@Param("productId") String productId);
    
    @Select("SELECT * FROM biz_product " +
            "WHERE category = #{category} AND score_level = #{level} AND status = 'ACTIVE' AND deleted = 0 " +
            "ORDER BY six_dimension_score DESC LIMIT #{limit}")
    List<Product> selectPotentialExplosiveProducts(@Param("category") String category, 
                                                    @Param("level") String level, 
                                                    @Param("limit") int limit);
    
    @Update("UPDATE biz_product SET six_dimension_score = #{sixDimensionScore}, " +
            "score_level = #{scoreLevel}, updated_at = NOW() " +
            "WHERE product_id = #{productId} AND deleted = 0")
    int update(Product product);
}
