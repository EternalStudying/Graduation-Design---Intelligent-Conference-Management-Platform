package com.llf.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper {

    @Select("""
                SELECT id
                FROM sys_user
                WHERE username = #{username}
                LIMIT 1
            """)
    Long exists(@Param("username") String username);

    @Select("""
              SELECT
                id as id,
                username as username,
                display_name as displayName,
                password_hash as passwordHash,
                role as role,
                status as status
              FROM sys_user
              WHERE username = #{username}
              LIMIT 1
            """)
    SysUserDO findByUsername(@Param("username") String username);

    @Select("""
            SELECT
              id,
              username,
              display_name AS displayName
            FROM sys_user
            WHERE status = 'ACTIVE'
              AND display_name LIKE CONCAT('%', #{keyword}, '%')
              AND (#{excludeUserId} IS NULL OR id <> #{excludeUserId})
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<UserSearchRow> searchActiveUsersByDisplayName(@Param("keyword") String keyword,
                                                       @Param("limit") int limit,
                                                       @Param("excludeUserId") Long excludeUserId);

    @Select("""
            <script>
            SELECT
              id,
              username,
              display_name AS displayName
            FROM sys_user
            WHERE status = 'ACTIVE'
              AND id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
              </foreach>
            </script>
            """)
    List<UserSearchRow> selectActiveUsersByIds(@Param("userIds") List<Long> userIds);

    class SysUserDO {
        public Long id;
        public String username;
        public String displayName;
        public String passwordHash;
        public String role;
        public String status;
    }

    class UserSearchRow {
        public Long id;
        public String username;
        public String displayName;

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
