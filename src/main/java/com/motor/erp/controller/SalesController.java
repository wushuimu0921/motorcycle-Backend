package com.motor.erp.controller;

import com.alibaba.excel.util.DateUtils;
import com.motor.erp.config.DatabaseConfig;
import com.motor.erp.dto.SaleRequest;
import io.javalin.http.Context;
import org.sql2o.Connection;
import org.sql2o.Sql2o;

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
}
