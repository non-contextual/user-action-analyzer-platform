package com.useranalyzer.util;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * JDBC 工具类 - 封装 MySQL 连接与常用操作
 */
public class JDBCHelper {

    private static String jdbcDriver;
    private static String jdbcUrl;
    private static String jdbcUser;
    private static String jdbcPassword;

    static {
        try {
            InputStream is = JDBCHelper.class.getClassLoader()
                    .getResourceAsStream("config.properties");
            if (is == null) {
                throw new RuntimeException("找不到 config.properties");
            }
            Properties props = new Properties();
            props.load(is);
            jdbcDriver   = props.getProperty("jdbc.driver");
            jdbcUrl      = props.getProperty("jdbc.url");
            jdbcUser     = props.getProperty("jdbc.user");
            jdbcPassword = props.getProperty("jdbc.password");
            Class.forName(jdbcDriver);
            System.out.println("[JDBCHelper] 初始化成功, URL=" + jdbcUrl);
        } catch (Exception e) {
            throw new RuntimeException("JDBC 初始化失败: " + e.getMessage(), e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    /**
     * 查询任务参数 JSON 字符串
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
     * 执行一条更新语句（INSERT / UPDATE / DELETE）
     */
    public static void executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL 执行失败: " + sql + " | " + e.getMessage(), e);
        }
    }

    /**
     * 批量执行（减少数据库往返次数）
     */
    public static void executeBatch(String sql, List<Object[]> paramsList) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Object[] params : paramsList) {
                    for (int i = 0; i < params.length; i++) {
                        ps.setObject(i + 1, params[i]);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("批量 SQL 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询任务类型
     */
    public static String getTaskType(long taskId) {
        String sql = "SELECT task_type FROM task WHERE task_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String t = rs.getString("task_type");
                return t != null ? t.trim().toUpperCase() : "SESSION";
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询任务类型失败 taskId=" + taskId, e);
        }
        return "SESSION";
    }

    /**
     * 查询单列字符串列表
     */
    public static List<String> queryForList(String sql, Object... params) {
        List<String> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
        return list;
    }
}
