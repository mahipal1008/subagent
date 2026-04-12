package com.subscrub.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads SQL metric definitions from YAML files in classpath:metrics/.
 * Each intent maps to a metric definition including a named-parameter SQL template.
 */
@Slf4j
@Component
public class SemanticLayer {

    // key = intent name (e.g. "LIST_SUBSCRIPTIONS")
    // value = metric definition map from YAML
    private final Map<String, Map<String, Object>> metrics = new HashMap<>();

    private static final String METRIC_VERSION = "1.0";

    @PostConstruct
    public void load() throws Exception {
        Yaml yaml = new Yaml();
        PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:metrics/*.yaml");

        for (Resource r : resources) {
            try (InputStream is = r.getInputStream()) {
                Map<String, Object> def = yaml.load(is);
                String intent = (String) def.get("intent");
                metrics.put(intent, def);
                log.info("Loaded metric: {}", intent);
            }
        }
        log.info("SemanticLayer loaded {} metrics (version {})",
            metrics.size(), METRIC_VERSION);
    }

    public Map<String, Object> getMetric(String intent) {
        return metrics.get(intent);
    }

    public String getSqlTemplate(String intent) {
        Map<String, Object> m = metrics.get(intent);
        return m != null ? (String) m.get("sql") : null;
    }

    public String getMetricVersion() { return METRIC_VERSION; }

    public boolean hasMetric(String intent) {
        return metrics.containsKey(intent);
    }
}
