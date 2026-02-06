package com.motor.erp.controller;

import com.alibaba.excel.util.DateUtils;
import com.motor.erp.config.DatabaseConfig;
import com.motor.erp.dto.SaleRequest;
import io.javalin.http.Context;
import org.sql2o.Connection;
import org.sql2o.Sql2o;

import java.util.List;
import java.util.Map;

public class SalesController {
    public static void createSale(Context ctx) {
        SaleRequest req = ctx.bodyAsClass(SaleRequest.class);

        Sql2o sql2o = DatabaseConfig.getSql2o();
        try (Connection conn = sql2o.beginTransaction()) {
            // 1. 插入銷貨主表
            String insertInvoice = "INSERT INTO sales_invoices (invoice_no, customer_name, sale_date, total_amount) VALUES (:no, :name, :date, :total)";
            Long invoiceId = conn.createQuery(insertInvoice, true)
                    .addParameter("no", "SAL-" + System.currentTimeMillis())
                    .addParameter("name", req.customerName)
                    .addParameter("date", DateUtils.parseDate(req.saleDate))
                    .addParameter("total", req.totalAmount)
                    .executeUpdate().getKey(Long.class);

            // 2. 處理每一台賣出的車
            for (SaleRequest.SaleItem item : req.items) {
                // 插入明細
                conn.createQuery("INSERT INTO sales_items (invoice_id, vin, sale_price) VALUES (:iId, :vin, :price)")
                        .addParameter("iId", invoiceId)
                        .addParameter("vin", item.vin)
                        .addParameter("price", item.salePrice)
                        .executeUpdate();

                // 更新車輛狀態 (status: 0=在庫, 1=已售出)
                conn.createQuery("UPDATE vehicles SET status = '已售出' WHERE vin = :vin")
                        .addParameter("vin", item.vin)
                        .executeUpdate();
            }

            conn.commit();
            ctx.status(201).result("銷貨成功");
        } catch (Exception e) {
            ctx.status(500).result("銷貨失敗: " + e.getMessage());
        }
    }
    public static void getSalesHistory(Context ctx) {
        Sql2o sql2o = DatabaseConfig.getSql2o();
        try (Connection conn = sql2o.open()) {
            // 查詢主表，按日期降冪排列
            String sql = "SELECT id, invoice_no, customer_name, customer_phone, sale_date, total_amount " +
                    "FROM sales_invoices ORDER BY sale_date DESC, id DESC";

            List<Map<String, Object>> history = conn.createQuery(sql)
                    .executeAndFetchTable()
                    .asList();

            ctx.json(history);
        }
    }

    public static void getSaleDetail(Context ctx) {
        int invoiceId = Integer.parseInt(ctx.pathParam("id"));
        Sql2o sql2o = DatabaseConfig.getSql2o();
        try (Connection conn = sql2o.open()) {
            // 關聯查詢銷貨明細與車輛基本資料
            String sql = "SELECT v.vin, v.model_name, v.color, si.sale_price " +
                    "FROM sales_items si " +
                    "JOIN vehicles v ON si.vin = v.vin " +
                    "WHERE si.invoice_id = :iId";

            List<Map<String, Object>> details = conn.createQuery(sql)
                    .addParameter("iId", invoiceId)
                    .executeAndFetchTable()
                    .asList();

            ctx.json(details);
        }
    }
}
