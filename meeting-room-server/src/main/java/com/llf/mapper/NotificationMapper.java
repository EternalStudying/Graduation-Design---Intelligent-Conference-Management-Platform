package com.llf.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Select("""
            SELECT
              category,
              COUNT(1) AS unreadCount
            FROM notification
            WHERE user_id = #{userId}
              AND read_flag = 0
            GROUP BY category
            """)
    List<CategoryUnreadRow> countUnreadByCategory(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM notification
            WHERE user_id = #{userId}
              <if test="category != null and category != ''">
                AND category = #{category}
              </if>
            </script>
            """)
    long countNotifications(@Param("userId") Long userId,
                            @Param("category") String category);

    @Select("""
            <script>
            SELECT
              id,
              category,
              title,
              content,
              DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
              CASE WHEN read_flag = 1 THEN TRUE ELSE FALSE END AS readFlag,
              route,
              route_query AS routeQueryText,
              extra,
              status
            FROM notification
            WHERE user_id = #{userId}
              <if test="category != null and category != ''">
                AND category = #{category}
              </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<NotificationRow> selectNotifications(@Param("userId") Long userId,
                                              @Param("category") String category,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    @Select("""
            SELECT
              id,
              CASE WHEN read_flag = 1 THEN TRUE ELSE FALSE END AS readFlag
            FROM notification
            WHERE id = #{id}
              AND user_id = #{userId}
            LIMIT 1
            """)
    NotificationReadRow selectNotificationReadState(@Param("userId") Long userId,
                                                    @Param("id") Long id);

    @Update("""
            UPDATE notification
            SET read_flag = 1,
                read_at = NOW()
            WHERE id = #{id}
              AND user_id = #{userId}
              AND read_flag = 0
            """)
    int markRead(@Param("userId") Long userId,
                 @Param("id") Long id);

    @Update("""
            UPDATE notification
            SET read_flag = 1,
                read_at = NOW()
            WHERE user_id = #{userId}
              AND category = #{category}
              AND read_flag = 0
            """)
    int markAllReadByCategory(@Param("userId") Long userId,
                              @Param("category") String category);

    @Select("""
            <script>
            SELECT id
            FROM sys_user
            WHERE CONCAT(status, '') IN ('ACTIVE', '1')
            <choose>
              <when test="recipientScope == 'USERS'">
                AND (UPPER(CONCAT(role, '')) = 'USER' OR CONCAT(role, '') = '1')
              </when>
              <when test="recipientScope == 'ADMINS'">
                AND (UPPER(CONCAT(role, '')) = 'ADMIN' OR CONCAT(role, '') = '2')
              </when>
            </choose>
            ORDER BY id ASC
            </script>
            """)
    List<Long> selectActiveNotificationRecipientIds(@Param("recipientScope") String recipientScope);

    @Insert("""
            INSERT INTO notification(
              user_id,
              category,
              title,
              content,
              read_flag,
              route,
              route_query,
              extra,
              status,
              created_at,
              read_at
            )
            VALUES(
              #{userId},
              #{category},
              #{title},
              #{content},
              0,
              #{route},
              #{routeQuery},
              #{extra},
              #{status},
              NOW(),
              NULL
            )
            """)
    int insertNotification(@Param("userId") Long userId,
                           @Param("category") String category,
                           @Param("title") String title,
                           @Param("content") String content,
                           @Param("route") String route,
                           @Param("routeQuery") String routeQuery,
                           @Param("extra") String extra,
                           @Param("status") String status);

    @Data
    class CategoryUnreadRow {
        private String category;
        private Long unreadCount;
    }

    @Data
    class NotificationRow {
        private Long id;
        private String category;
        private String title;
        private String content;
        private String createdAt;
        private Boolean readFlag;
        private String route;
        private String routeQueryText;
        private String extra;
        private String status;
    }

    @Data
    class NotificationReadRow {
        private Long id;
        private Boolean readFlag;
    }
}
