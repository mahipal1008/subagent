package com.subscrub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * Runs validated SQL using Spring's NamedParameterJdbcTemplate.
 */
@Service
@RequiredArgsConstructor
public class QueryExecutor {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Executes a named-parameter SELECT and returns rows as List&lt;Map&lt;String,Object&gt;&gt;.
     * Each map key is the column name, value is the cell value.
     */
    public List<Map<String, Object>> execute(String sql, Map<String, Object> params) {
        return jdbc.queryForList(sql, params);
    }
}
