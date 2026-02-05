package com.motor.erp.controller;

import com.motor.erp.config.DatabaseConfig;
import io.javalin.http.Context;
import org.sql2o.Connection;
import org.sql2o.Sql2o;

import java.util.List;
import java.util.Map;

public class InventoryController {
    public static void getAvailableInventory(Context ctx) {
        String q = ctx.queryParam("q") != null ? ctx.queryParam("q") : "";
        Sql2o sql2o = DatabaseConfig.getSql2o();

        try (Connection conn = sql2o.open()) {
            // status = 0 代表在庫
            String sql = "SELECT vin, model_name, color " +
                    "FROM vehicles " +
                    "WHERE status = '在庫' " +
                    "AND (vin ILIKE :search OR model_name ILIKE :search) ";

            List<Map<String, Object>> list = conn.createQuery(sql)
                    .addParameter("search", "%" + q + "%")
                    .executeAndFetchTable()
                    .asList();

            ctx.json(list);
        }
    }
}
