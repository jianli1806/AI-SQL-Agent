package com.agent.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TestController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/test")
    public Map<String, Object> test() {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("SELECT 1 as test");
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "数据库连接测试成功");
            response.put("data", result);
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "数据库连接测试失败: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/test/tables")
    public Map<String, Object> testTables() {
        try {
            List<String> tables = jdbcTemplate.queryForList("SHOW TABLES", String.class);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "获取表列表成功");
            response.put("tables", tables);
            response.put("count", tables.size());
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "获取表列表失败: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/test/connection")
    public Map<String, Object> testConnection() {
        try {
            // 测试数据库连接和基本查询
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "数据库连接正常");
            response.put("mysql_version", version);
            response.put("current_database", currentDb);

            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "数据库连接失败: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/test/sample-data")
    public Map<String, Object> testSampleData() {
        try {
            // 测试查询一些示例数据
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");

            // 查询用户数量
            Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            response.put("user_count", userCount);

            // 查询商品数量
            Integer productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
            response.put("product_count", productCount);

            // 查询订单数量
            Integer orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
            response.put("order_count", orderCount);

            // 查询前3个用户
            List<Map<String, Object>> sampleUsers = jdbcTemplate.queryForList("SELECT id, name, email FROM users LIMIT 3");
            response.put("sample_users", sampleUsers);

            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "查询示例数据失败: " + e.getMessage());
            return response;
        }
    }
}