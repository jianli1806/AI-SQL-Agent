// SqlAgentController.java
package com.agent.controller;

import com.agent.model.QueryRequest;
import com.agent.model.QueryResponse;
import com.agent.service.SqlAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sql-agent")
public class SqlAgentController {

    private static final Logger logger = LoggerFactory.getLogger(SqlAgentController.class);

    @Autowired
    private SqlAgentService sqlAgentService;

    /**
     * 处理自然语言查询
     */
    @PostMapping("/query")
    public QueryResponse processQuery(@RequestBody QueryRequest request) {
        try {
            logger.info("收到查询请求: {}", request.getUserQuery());
            return sqlAgentService.processQuery(request);
        } catch (Exception e) {
            logger.error("处理查询失败", e);
            return QueryResponse.error("查询处理失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库表列表
     */
    @GetMapping("/tables")
    public QueryResponse getTables() {
        try {
            return sqlAgentService.getTables();
        } catch (Exception e) {
            logger.error("获取表列表失败", e);
            return QueryResponse.error("获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取表结构
     */
    @GetMapping("/tables/{tableName}/schema")
    public QueryResponse getTableSchema(@PathVariable String tableName) {
        try {
            return sqlAgentService.getTableSchema(tableName);
        } catch (Exception e) {
            logger.error("获取表结构失败", e);
            return QueryResponse.error("获取表结构失败: " + e.getMessage());
        }
    }
}