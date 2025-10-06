package com.example.pwm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.util.Map;

@RestController
public class HealthController {
    private final DataSource ds;
    public HealthController(DataSource ds){ this.ds = ds; }
    @GetMapping("/api/health")
    public Map<String,Object> health() throws Exception {
        try (var c = ds.getConnection()) {
            return Map.of("status","OK","dbProduct", c.getMetaData().getDatabaseProductName());
        }
    }
}
