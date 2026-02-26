package com.ra.rabnbserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ra.rabnbserver.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子更新余额
     * @param userId 用户ID
     * @param amount 变动金额 (正数增加，负数减少)
     * @return 影响行数。如果为 0，说明条件不满足（如余额不足）
     */
    @Update("UPDATE user SET balance = balance + #{amount} " +
            "WHERE id = #{userId} AND (balance + #{amount} >= 0)")
    int updateBalanceAtomic(@Param("userId") Long userId, @Param("amount") BigDecimal amount);


    @Update("${sql}")
    void executeRawSql(@Param("sql") String sql);

    @Update("UPDATE user SET path = CONCAT(#{newFullPrefix}, SUBSTRING(path, #{offset})), level = level + #{levelOffset} WHERE path LIKE CONCAT(#{oldFullPrefix}, '%')")
    void updateUserPaths(@Param("newFullPrefix") String newFullPrefix,
                         @Param("offset") int offset,
                         @Param("levelOffset") int levelOffset,
                         @Param("oldFullPrefix") String oldFullPrefix);

}
