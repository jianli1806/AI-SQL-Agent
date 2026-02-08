// src/main/java/com/agent/service/OllamaService.java
package com.agent.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class OllamaService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaService.class);

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2:latest}")
    private String defaultModel;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OllamaService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 生成SQL查询
     */
    public String generateSQL(String userQuery, String schemaContext) {
        String prompt = buildSQLPrompt(userQuery, schemaContext);

        try {
            logger.debug("发送请求到Ollama: {}", prompt.substring(0, Math.min(100, prompt.length())));

            OllamaRequest request = new OllamaRequest();
            request.setModel(defaultModel);
            request.setPrompt(prompt);
            request.setStream(false);
            request.setOptions(createOptions());

            OllamaResponse response = webClient.post()
                    .uri(ollamaBaseUrl + "/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (response != null && response.getResponse() != null) {
                String generatedSQL = extractSQL(response.getResponse());
                logger.debug("Ollama生成的SQL: {}", generatedSQL);
                return generatedSQL;
            }

        } catch (Exception e) {
            logger.error("调用Ollama失败", e);
            throw new RuntimeException("AI模型调用失败: " + e.getMessage());
        }

        throw new RuntimeException("AI模型返回空结果");
    }

    /**
     * 构建SQL生成提示词
     */
    private String buildSQLPrompt(String userQuery, String schemaContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个MySQL数据库专家。根据用户的中文查询需求，生成准确的SQL语句。\n\n");

        prompt.append("数据库表结构信息：\n");
        prompt.append(schemaContext);
        prompt.append("\n\n");

        prompt.append("用户查询需求：").append(userQuery).append("\n\n");

        prompt.append("请根据以上信息生成对应的MySQL SQL语句。要求：\n");
        prompt.append("1. 只返回可执行的SQL语句，不要任何解释\n");
        prompt.append("2. 使用标准的MySQL语法\n");
        prompt.append("3. 如果需要分页，自动添加LIMIT 100\n");
        prompt.append("4. 字段名和表名要准确匹配schema中的定义\n");
        prompt.append("5. 如果涉及中文内容，理解其对应的英文表名和字段名\n\n");

        prompt.append("SQL语句：\n");

        return prompt.toString();
    }

    /**
     * 从响应中提取SQL语句
     */
    private String extractSQL(String response) {
        // 移除可能的解释文本，只保留SQL
        String cleanResponse = response.trim();

        // 查找SQL语句的开始
        String[] lines = cleanResponse.split("\n");
        StringBuilder sqlBuilder = new StringBuilder();
        boolean foundSQL = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 跳过空行和解释性文字
            if (trimmedLine.isEmpty() ||
                    trimmedLine.startsWith("这个") ||
                    trimmedLine.startsWith("根据") ||
                    trimmedLine.startsWith("以上") ||
                    trimmedLine.startsWith("注意")) {
                continue;
            }

            // 检查是否是SQL语句
            if (trimmedLine.toUpperCase().startsWith("SELECT") ||
                    trimmedLine.toUpperCase().startsWith("INSERT") ||
                    trimmedLine.toUpperCase().startsWith("UPDATE") ||
                    trimmedLine.toUpperCase().startsWith("DELETE")) {
                foundSQL = true;
            }

            if (foundSQL) {
                sqlBuilder.append(trimmedLine).append(" ");
            }
        }

        String result = sqlBuilder.toString().trim();

        // 如果没有找到标准SQL，返回原始响应的第一行
        if (result.isEmpty()) {
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && trimmedLine.contains("SELECT")) {
                    return trimmedLine;
                }
            }
            return cleanResponse.split("\n")[0].trim();
        }

        return result;
    }

    /**
     * 创建Ollama请求选项
     */
    private Map<String, Object> createOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.1);  // 降低随机性，提高准确性
        options.put("top_p", 0.9);
        options.put("repeat_penalty", 1.1);
        return options;
    }

    /**
     * 检测Ollama服务是否可用
     */
    public boolean isAvailable() {
        try {
            webClient.get()
                    .uri(ollamaBaseUrl + "/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return true;
        } catch (Exception e) {
            logger.warn("Ollama服务不可用: {}", e.getMessage());
            return false;
        }
    }

    // 内部类
    public static class OllamaRequest {
        private String model;
        private String prompt;
        private boolean stream = false;
        private Map<String, Object> options;

        // Getters and setters
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }

        public boolean isStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }

        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }

    public static class OllamaResponse {
        private String model;
        @JsonProperty("created_at")
        private String createdAt;
        private String response;
        private boolean done;

        // Getters and setters
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }

        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
    }
}