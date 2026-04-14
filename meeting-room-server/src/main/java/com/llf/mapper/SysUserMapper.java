package com.llf.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    class SysUserDO {
        public Long id;
        public String username;
        public String displayName;
        public String passwordHash;
        public String role;
        public String status;
    }
}