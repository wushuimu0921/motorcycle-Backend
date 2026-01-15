package com.motor.erp.controller;

import com.motor.erp.config.DatabaseConfig;
import com.motor.erp.model.Vehicle;
import io.javalin.http.Context;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VehicleController {

    public static void register(Context ctx) {
        // 從前端接收 JSON 資料
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String vin = body.get("vin");
        String modelName = body.get("modelName");
        String engineNo = body.get("engineNo");

        String sql = "INSERT INTO vehicles (vin, model_name, engine_no, status) VALUES (?, ?, ?, '在庫')";

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, vin);
            pstmt.setString(2, modelName);
            pstmt.setString(3, engineNo);

            pstmt.executeUpdate();
            ctx.status(201).result("車輛入庫成功，VIN: " + vin);

        } catch (SQLException e) {
            e.printStackTrace();
            ctx.status(400).result("入庫失敗：車架號或引擎號碼可能重複。");
        }
    }

    public static void getAll(Context ctx) {
        List<Vehicle> vehicles = new ArrayList<>();
        String sql = "SELECT vin, model_name, engine_no, status FROM vehicles ORDER BY created_at DESC";

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                vehicles.add(new Vehicle(
                        rs.getString("vin"),
                        rs.getString("model_name"),
                        rs.getString("engine_no"),
                        rs.getString("status")
                ));
            }

            // Javalin 會自動將 List 轉換為 JSON Array []
            ctx.json(vehicles);

        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("資料庫查詢失敗: " + e.getMessage());
        }
    }
}
