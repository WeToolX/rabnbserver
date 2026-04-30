package com.ra.rabnbserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ra.rabnbserver.VO.team.TeamAreaItemVO;
import com.ra.rabnbserver.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

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

    /**
     * 统计邀请码与钱包地址不一致的用户数量。
     *
     * @return 不一致的用户数量
     */
    @Select("SELECT COUNT(1) FROM user WHERE NOT (invite_code <=> user_wallet_address)")
    long countInviteCodeMismatch();

    /**
     * 启动时批量修正邀请码，将邀请码同步为用户自己的钱包地址。
     *
     * @return 受影响的记录数
     */
    @Update("UPDATE user SET invite_code = user_wallet_address WHERE NOT (invite_code <=> user_wallet_address)")
    int syncInviteCodeWithWalletAddress();

    @Select("""
            SELECT
                downline.id AS userId,
                downline.user_wallet_address AS address,
                1 AS teamCount,
                COALESCE(COUNT(um.id), 0) AS purchasedCount,
                COALESCE(SUM(CASE
                    WHEN um.status = 1
                     AND um.payment_date IS NOT NULL
                     AND um.payment_date > DATE_SUB(NOW(), INTERVAL 30 DAY)
                    THEN 1 ELSE 0 END), 0) AS activeCount,
                MAX(um.create_time) AS lastExchangeTime
            FROM user root
            JOIN user downline
                ON downline.path LIKE CONCAT(COALESCE(root.path, '0,'), root.id, ',%')
            LEFT JOIN user_miner um
                ON um.user_id = downline.id
               AND um.nft_burn_status = 1
            WHERE root.id = #{userId}
            GROUP BY downline.id, downline.user_wallet_address
            ORDER BY
                purchasedCount DESC,
                CASE WHEN lastExchangeTime IS NULL THEN 1 ELSE 0 END ASC,
                lastExchangeTime ASC,
                downline.id ASC
            """)
    List<TeamAreaItemVO> selectDirectAreaStats(@Param("userId") Long userId);

    @Select("""
            SELECT
                direct_user.id AS userId,
                direct_user.user_wallet_address AS address,
                COALESCE(COUNT(DISTINCT member.id), 0) AS teamCount,
                COALESCE(COUNT(um.id), 0) AS purchasedCount,
                COALESCE(SUM(CASE
                    WHEN um.status = 1
                     AND um.payment_date IS NOT NULL
                     AND um.payment_date > DATE_SUB(NOW(), INTERVAL 30 DAY)
                    THEN 1 ELSE 0 END), 0) AS activeCount,
                MAX(um.create_time) AS lastExchangeTime
            FROM user root
            JOIN user direct_user
                ON direct_user.parent_id = root.id
            LEFT JOIN user member
                ON member.id = direct_user.id
                OR member.path LIKE CONCAT(COALESCE(direct_user.path, '0,'), direct_user.id, ',%')
            LEFT JOIN user_miner um
                ON um.user_id = member.id
               AND um.nft_burn_status = 1
            WHERE root.id = #{userId}
            GROUP BY direct_user.id, direct_user.user_wallet_address
            ORDER BY
                purchasedCount DESC,
                CASE WHEN lastExchangeTime IS NULL THEN 1 ELSE 0 END ASC,
                lastExchangeTime ASC,
                direct_user.id ASC
            """)
    List<TeamAreaItemVO> selectDirectTeamAreaStats(@Param("userId") Long userId);

    @Select("""
            SELECT
                COALESCE(COUNT(um.id), 0) AS purchasedCount,
                COALESCE(SUM(CASE
                    WHEN um.status = 1
                     AND um.payment_date IS NOT NULL
                     AND um.payment_date > DATE_SUB(NOW(), INTERVAL 30 DAY)
                    THEN 1 ELSE 0 END), 0) AS activeCount,
                MAX(um.create_time) AS lastExchangeTime
            FROM user root
            LEFT JOIN user member
                ON member.path LIKE CONCAT(COALESCE(root.path, '0,'), root.id, ',%')
            LEFT JOIN user_miner um
                ON um.user_id = member.id
               AND um.nft_burn_status = 1
            WHERE root.id = #{userId}
            GROUP BY root.id
            """)
    TeamAreaItemVO selectTeamMinerStats(@Param("userId") Long userId);

}
