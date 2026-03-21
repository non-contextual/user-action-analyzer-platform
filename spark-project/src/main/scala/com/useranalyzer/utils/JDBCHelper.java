package com.useranalyzer.utils;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

/**
 * JDBC 工具类 - 负责与 MySQL 的连接和操作
 */
public class JDBCHelper {

    private static String jdbcUrl;
    private static String jdbcUser;
    private static String jdbcPassword;

    static {
        try {
            InputStream is = JDBCHelper.class.getClassLoader()
                    .getResourceAsStream("config.properties");
            Properties props = new Properties();
            props.load(is);
            Class.forName(props.getProperty("jdbc.driver"));
            jdbcUrl = props.getProperty("jdbc.url");
            jdbcUser = props.getProperty("jdbc.user");
            jdbcPassword = props.getProperty("jdbc.password");
        } catch (Exception e) {
            throw new RuntimeException("JDBC 初始化失败", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    /**
     * 查询任务参数 JSON
     * @param taskId 任务 ID
     * @return JSON 字符串，如 {"startDate":"2019-01-01","endDate":"2019-12-31",...}
     */
    public static String getTaskParam(long taskId) {
        String sql = "SELECT task_param FROM task WHERE task_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("task_param");
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询任务参数失败 taskId=" + taskId, e);
        }
        throw new RuntimeException("任务不存在 taskId=" + taskId);
    }

    /**
     * 执行更新（INSERT / UPDATE / DELETE）
     */
    public static void executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL 执行失败: " + sql, e);
        }
    }
}
