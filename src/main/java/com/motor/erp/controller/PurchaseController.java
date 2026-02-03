package com.motor.erp.controller;

import com.motor.erp.config.DatabaseConfig;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PurchaseController {

    /**
     * POST /api/purchase
     * 處理進車單儲存與庫存更新
     */
    public static void createInvoice(Context ctx) {
        // 1. 取得請求資料 (Map 結構簡化處理，亦可定義專門的 DTO)
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String invoiceNo = (String) body.get("invoice_no");
        String purchaseDate = (String) body.get("purchase_date");
        String supplier = (String) body.get("supplier");
        List<Map<String, Object>> details = (List<Map<String, Object>>) body.get("details");

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            // 2. 開啟事務 (Transaction)
            conn.setAutoCommit(false);

            try {
                // Step A: 寫入進車單主表 (purchase_invoices)
                String sqlMain = "INSERT INTO purchase_invoices (invoice_no, purchase_date, supplier) VALUES (?, ?, ?) RETURNING id";
                int masterId;
                try (PreparedStatement pstmt = conn.prepareStatement(sqlMain)) {
                    pstmt.setString(1, invoiceNo);
                    pstmt.setDate(2, Date.valueOf(purchaseDate.substring(0, 10))); // 處理 ISO 日期格式
                    pstmt.setString(3, supplier);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        masterId = rs.getInt(1);
                    } else {
                        throw new SQLException("建立單據失敗");
                    }
                }

                // Step B: 遍歷明細，寫入關聯表與更新庫存
                for (Map<String, Object> item : details) {
                    String vin = (String) item.get("vin");
                    String modelName = (String) item.get("model_name");
                    String color = (String) item.get("color");
                    String engineNo = (String) item.get("engine_no");
                    double price = Double.parseDouble(item.get("purchase_price").toString());

                    // B1. 寫入明細表 (purchase_invoice_details)
                    String sqlDetail = "INSERT INTO purchase_invoice_details (invoice_id, vin, purchase_price) VALUES (?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlDetail)) {
                        pstmt.setInt(1, masterId);
                        pstmt.setString(2, vin);
                        pstmt.setDouble(3, price);
                        pstmt.executeUpdate();
                    }

                    // B2. 更新或新增庫存表 (vehicles)
                    // 使用 Upsert 語法，如果 VIN 已存在則更新狀態
                    String sqlVehicle = "INSERT INTO vehicles (vin, model_name, engine_no, color, status, last_invoice_id) " +
                            "VALUES (?, ?, ?, ?, '在庫', ?) " +
                            "ON CONFLICT (vin) DO UPDATE SET status = '在庫', last_invoice_id = EXCLUDED.last_invoice_id";
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlVehicle)) {
                        pstmt.setString(1, vin);
                        pstmt.setString(2, modelName);
                        pstmt.setString(3, engineNo);
                        pstmt.setString(4, color);
                        pstmt.setInt(5, masterId);
                        pstmt.executeUpdate();
                    }
                }

                // 3. 提交事務
                conn.commit();
                ctx.status(201).json(Map.of("message", "進車過帳成功", "id", masterId));

            } catch (Exception e) {
                // 4. 若失敗則回滾
                conn.rollback();
                ctx.status(500).json(Map.of("error", "資料庫寫入失敗: " + e.getMessage()));
            }
        } catch (SQLException e) {
            ctx.status(500).json(Map.of("error", "資料庫連線錯誤"));
        }
    }
    // 1. 查詢進車單列表
    public static void getInvoices(Context ctx) {
        // 1. 取得分頁參數 (給予預設值)
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int pageSize = ctx.queryParamAsClass("pageSize", Integer.class).getOrDefault(10);
        String supplier = ctx.queryParam("supplier");

        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> list = new ArrayList<>();
        int total = 0;

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            // A. 先查詢總筆數 (用於前端分頁器顯示總數)
            String countSql = "SELECT COUNT(*) FROM purchase_invoices WHERE 1=1 ";
            if (supplier != null && !supplier.isEmpty()) countSql += " AND supplier LIKE ?";

            try (PreparedStatement pstmt = conn.prepareStatement(countSql)) {
                if (supplier != null && !supplier.isEmpty()) pstmt.setString(1, "%" + supplier + "%");
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) total = rs.getInt(1);
            }

            // B. 查詢該頁數據
            String sql = "SELECT * FROM purchase_invoices WHERE 1=1 ";
            if (supplier != null && !supplier.isEmpty()) sql += " AND supplier LIKE ?";
            sql += " ORDER BY purchase_date DESC LIMIT ? OFFSET ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int paramIdx = 1;
                if (supplier != null && !supplier.isEmpty()) pstmt.setString(paramIdx++, "%" + supplier + "%");
                pstmt.setInt(paramIdx++, pageSize);
                pstmt.setInt(paramIdx++, offset);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    list.add(Map.of(
                            "id", rs.getInt("id"),
                            "invoice_no", rs.getString("invoice_no"),
                            "purchase_date", rs.getDate("purchase_date").toString(),
                            "supplier", rs.getString("supplier"),
                            "total_amount", rs.getDouble("total_amount")
                    ));
                }
            }

            // 回傳包含數據與總數的物件
            ctx.json(Map.of("data", list, "total", total));
        } catch (SQLException e) {
            ctx.status(500).result(e.getMessage());
        }
    }

    // 2. 查詢特定單據明細 (包含車輛資訊)
    public static void getInvoiceDetails(Context ctx) {
        int invoiceId = Integer.parseInt(ctx.pathParam("id"));
        List<Map<String, Object>> details = new ArrayList<>();

        String sql = "SELECT d.*, v.model_name, v.color, v.engine_no " +
                "FROM purchase_invoice_details d " +
                "JOIN vehicles v ON d.vin = v.vin " +
                "WHERE d.invoice_id = ?";

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, invoiceId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                details.add(Map.of(
                        "vin", rs.getString("vin"),
                        "model_name", rs.getString("model_name"),
                        "engine_no", rs.getString("engine_no"),
                        "color", rs.getString("color"),
                        "purchase_price", rs.getDouble("purchase_price")
                ));
            }
            ctx.json(details);
        } catch (SQLException e) {
            ctx.status(500).result(e.getMessage());
        }
    }
    public static void updateInvoice(Context ctx) {
        int invoiceId = Integer.parseInt(ctx.pathParam("id"));
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        List<Map<String, Object>> newDetails = (List<Map<String, Object>>) body.get("details");

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Step 1: 刪除舊的明細 (庫存車輛通常保持 '在庫'，只需更新關聯與資訊)
                // 實務上建議先比對差異，這裡採簡化做法：刪除明細重寫
                String deleteDetails = "DELETE FROM purchase_invoice_details WHERE invoice_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteDetails)) {
                    pstmt.setInt(1, invoiceId);
                    pstmt.executeUpdate();
                }

                // Step 2: 更新主表資訊 (供應商、日期等)
                String updateMain = "UPDATE purchase_invoices SET purchase_date = ?, supplier = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateMain)) {
                    pstmt.setDate(1, Date.valueOf(body.get("purchase_date").toString().substring(0, 10)));
                    pstmt.setString(2, (String) body.get("supplier"));
                    pstmt.setInt(3, invoiceId);
                    pstmt.executeUpdate();
                }

                // Step 3: 寫入新明細並更新車輛狀態
                for (Map<String, Object> item : newDetails) {
                    String vin = (String) item.get("vin");
                    String modelName = (String) item.get("model_name");
                    String color = (String) item.get("color");
                    String engineNo = (String) item.get("engine_no");
                    double price = Double.parseDouble(item.get("purchase_price").toString());
                    // 寫入明細表
                    String insDetail = "INSERT INTO purchase_invoice_details (invoice_id, vin, purchase_price) VALUES (?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(insDetail)) {
                        pstmt.setInt(1, invoiceId);
                        pstmt.setString(2, vin);
                        pstmt.setDouble(3, price);
                        pstmt.executeUpdate();
                    }

                    // 更新車輛表 (確保車輛資訊同步更新)
                    String upsertVehicle = "INSERT INTO vehicles (vin, model_name, engine_no, color, status, last_invoice_id) " +
                            "VALUES (?, ?, ?, ?, '在庫', ?) " +
                            "ON CONFLICT (vin) DO UPDATE SET model_name = EXCLUDED.model_name, color = EXCLUDED.color";
                    try (PreparedStatement pstmt = conn.prepareStatement(upsertVehicle)) {
                        pstmt.setString(1, vin);
                        pstmt.setString(2, modelName);
                        pstmt.setString(3, engineNo);
                        pstmt.setString(4, color);
                        pstmt.setInt(5, invoiceId);
                        pstmt.executeUpdate();
                    }
                }

                conn.commit();
                ctx.json(Map.of("message", "更新成功"));
            } catch (Exception e) {
                conn.rollback();
                ctx.status(500).result("調整失敗: " + e.getMessage());
            }
        } catch (SQLException e) {
            ctx.status(500).result("資料庫連線錯誤");
        }
    }
    /**
     * GET /api/purchase/master/{id}
     * 獲取進車單主表資訊
     */
    public static void getInvoiceMaster(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));

        String sql = "SELECT id, invoice_no, purchase_date, supplier FROM purchase_invoices WHERE id = ?";

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                ctx.json(Map.of(
                        "id", rs.getInt("id"),
                        "invoice_no", rs.getString("invoice_no"),
                        "purchase_date", rs.getDate("purchase_date").toString(),
                        "supplier", rs.getString("supplier")
                ));
            } else {
                ctx.status(404).result("找不到該筆進車單");
            }
        } catch (SQLException e) {
            ctx.status(500).result("資料庫查詢失敗: " + e.getMessage());
        }
    }
}