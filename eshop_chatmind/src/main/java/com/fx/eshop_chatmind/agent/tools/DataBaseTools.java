package com.fx.eshop_chatmind.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DataBaseTools implements Tool {

    private final JdbcTemplate jdbcTemplate;

    public DataBaseTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getName() {
        return "dataBaseTool";
    }

    @Override
    public String getDescription() {
        return "一个用于执行数据库查询操作的工具，主要用于从 PostgreSQL 中读取数据。";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    /**
     * 第一步：获取数据库表结构信息，包括所有表名、列名和列类型。
     * Agent 在生成 SQL 之前应先调用此工具了解可用的表和列，
     * 然后结合用户的自然语言查询提取出可能的列名和查询条件，
     * 最后再调用 databaseQuery 生成并执行 SQL。
     *
     * @return 格式化的表结构信息
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "getTableSchema",
            description = "获取数据库中所有表的 schema 信息（表名、列名、列类型）。在生成 SQL 查询之前必须首先调用此工具，以便了解可用的表和列，从而提取正确的列名和查询条件。"
    )
    public String getTableSchema() {
        try {
            String sql = """
                    SELECT table_name, column_name, data_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                    ORDER BY table_name, ordinal_position
                    """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            Map<String, List<String>> tableColumns = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("table_name");
                String columnName = (String) row.get("column_name");
                String dataType = (String) row.get("data_type");
                String isNullable = (String) row.get("is_nullable");

                tableColumns.computeIfAbsent(tableName, k -> new ArrayList<>())
                        .add(columnName + " (" + dataType + ", " + ("YES".equals(isNullable) ? "可空" : "非空") + ")");
            }

            if (tableColumns.isEmpty()) {
                return "未找到任何数据库表结构信息。";
            }

            StringBuilder result = new StringBuilder();
            result.append("数据库表结构如下：\n\n");
            for (Map.Entry<String, List<String>> entry : tableColumns.entrySet()) {
                result.append("表名: ").append(entry.getKey()).append("\n");
                result.append("列:\n");
                for (String col : entry.getValue()) {
                    result.append("  - ").append(col).append("\n");
                }
                result.append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("获取表结构失败: {}", e.getMessage(), e);
            return "错误：获取表结构失败 - " + e.getMessage();
        }
    }

    /**
     * 第二步：执行一条 SQL 查询，从数据库中查询数据。
     * 必须先调用 getTableSchema 了解表结构，提取列名和查询条件后，再调用本工具生成并执行 SQL。
     *
     * @param sql SQL 查询语句（仅支持 SELECT 查询）
     * @return 格式化的查询结果字符串
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "databaseQuery",
            description = "在 PostgreSQL 中执行只读查询（SELECT）。调用前必须先使用 getTableSchema 获取表结构，根据表结构提取用户问题中的列名和查询条件，然后再生成 SQL 语句。该工具仅用于检索数据，严禁任何写入或修改操作。"
    )
    public String query(String sql) {
        try {
            // 验证 SQL 语句安全性（只允许 SELECT 查询）
            String trimmedSql = sql.trim().toUpperCase();
            if (!trimmedSql.startsWith("SELECT")) {
                log.warn("拒绝执行非 SELECT 查询: {}", sql);
                return "错误：仅支持 SELECT 查询语句。提供的 SQL: " + sql;
            }

            // 执行查询
            List<String> rows = jdbcTemplate.query(sql, (ResultSet rs) -> {
                List<String> resultRows = new ArrayList<>();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                if (columnCount == 0) {
                    resultRows.add("查询结果为空（无列）");
                    return resultRows;
                }

                // 获取列名和计算每列的最大宽度
                List<String> columnNames = new ArrayList<>();
                List<Integer> columnWidths = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    columnNames.add(columnName);
                    columnWidths.add(columnName.length());
                }

                // 收集所有行数据并计算列宽
                List<List<String>> dataRows = new ArrayList<>();
                while (rs.next()) {
                    List<String> rowData = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        String valueStr = value == null ? "NULL" : value.toString();
                        rowData.add(valueStr);
                        // 更新列宽
                        int currentWidth = columnWidths.get(i - 1);
                        if (valueStr.length() > currentWidth) {
                            columnWidths.set(i - 1, valueStr.length());
                        }
                    }
                    dataRows.add(rowData);
                }

                // 格式化表头
                StringBuilder header = new StringBuilder();
                header.append("| ");
                for (int i = 0; i < columnCount; i++) {
                    String columnName = columnNames.get(i);
                    int width = columnWidths.get(i);
                    header.append(String.format("%-" + width + "s", columnName)).append(" | ");
                }
                resultRows.add(header.toString());

                // 添加分隔线
                StringBuilder separator = new StringBuilder();
                separator.append("|");
                for (int i = 0; i < columnCount; i++) {
                    int width = columnWidths.get(i);
                    separator.append("-".repeat(width + 2)).append("|");
                }
                resultRows.add(separator.toString());

                // 格式化数据行
                if (dataRows.isEmpty()) {
                    StringBuilder emptyRow = new StringBuilder();
                    emptyRow.append("| ");
                    int totalWidth = columnWidths.stream().mapToInt(w -> w + 3).sum() - 1;
                    emptyRow.append(String.format("%-" + (totalWidth - 2) + "s", "(无数据)"));
                    emptyRow.append(" |");
                    resultRows.add(emptyRow.toString());
                } else {
                    for (List<String> rowData : dataRows) {
                        StringBuilder row = new StringBuilder();
                        row.append("| ");
                        for (int i = 0; i < columnCount; i++) {
                            String value = rowData.get(i);
                            int width = columnWidths.get(i);
                            row.append(String.format("%-" + width + "s", value)).append(" | ");
                        }
                        resultRows.add(row.toString());
                    }
                }

                return resultRows;
            });

            int dataRowCount = rows.size() - 2; // 减去表头和分隔线
            if (rows.size() > 2 && rows.get(rows.size() - 1).contains("(无数据)")) {
                dataRowCount = 0;
            }

            log.info("成功执行 SQL 查询，返回 {} 行数据", dataRowCount);
            // 将结果格式化为字符串
            return "查询结果:\n" + String.join("\n", rows);
        } catch (Exception e) {
            log.error("未知错误: {}", e.getMessage(), e);
            return "错误：操作失败 - " + e.getMessage() + "\nSQL: " + sql;
        }
    }
}
