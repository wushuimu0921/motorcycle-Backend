package com.motor.erp.controller;

import com.motor.erp.config.DatabaseConfig;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportController {
    public static void importExcel(Context ctx) throws Exception {
        UploadedFile file = ctx.uploadedFile("file");
        String supplier = ctx.formParam("supplier");
        String purchaseDate = ctx.formParam("purchase_date");
        String invoiceNo = "IMP" + System.currentTimeMillis();

        try (InputStream is = file.content(); Workbook workbook = new XSSFWorkbook(is); Connection conn = DatabaseConfig.getDataSource().getConnection()) {

            Sheet sheet = workbook.getSheetAt(0);
            conn.setAutoCommit(false);

            try {
                // 1. 建立進車單主檔
                String sqlMain = "INSERT INTO purchase_invoices (invoice_no, purchase_date, supplier) VALUES (?, ?, ?) RETURNING id";
                int invoiceId;
                try (PreparedStatement pstmt = conn.prepareStatement(sqlMain)) {
                    pstmt.setString(1, invoiceNo);
                    pstmt.setDate(2, java.sql.Date.valueOf(purchaseDate));
                    pstmt.setString(3, supplier);
                    ResultSet rs = pstmt.executeQuery();
                    rs.next();
                    invoiceId = rs.getInt(1);
                }

                // 2. 迴圈讀取 Excel 內容 (從第 2 列開始，略過標題)
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String vin = row.getCell(0).getStringCellValue();
                    String model = row.getCell(1).getStringCellValue();
                    String engine = row.getCell(2).getStringCellValue();
                    String color = row.getCell(3).getStringCellValue();
                    double price = row.getCell(4).getNumericCellValue();

                    // 寫入明細表
                    String insDetail = "INSERT INTO purchase_invoice_details (invoice_id, vin, purchase_price) VALUES (?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(insDetail)) {
                        pstmt.setInt(1, invoiceId);
                        pstmt.setString(2, vin);
                        pstmt.setDouble(3, price);
                        pstmt.executeUpdate();
                    }
                }

                conn.commit();
                ctx.result("成功匯入 " + sheet.getLastRowNum() + " 筆資料");
            } catch (Exception e) {
                conn.rollback();
                ctx.status(500).result("匯入失敗: " + e.getMessage());
            }
        }
    }

    public static void previewExcel(Context ctx) throws Exception {
        UploadedFile file = ctx.uploadedFile("file");
        List<Map<String, Object>> previewList = new ArrayList<>();

        try (InputStream is = file.content();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            // 迴圈讀取資料 (從第 1 列標題後開始)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("vin", getCellValue(row.getCell(0)));
                item.put("model_name", getCellValue(row.getCell(1)));
                item.put("engine_no", getCellValue(row.getCell(2)));
                item.put("color", getCellValue(row.getCell(3)));
                item.put("purchase_price", getNumericValue(row.getCell(4)));
                previewList.add(item);
            }
            ctx.json(previewList);
        } catch (Exception e) {
            ctx.status(400).result("解析失敗: " + e.getMessage());
        }
    }

    // 輔助方法：處理不同類型的 Cell
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private static double getNumericValue(Cell cell) {
        try {
            return cell.getNumericCellValue();
        } catch (Exception e) {
            return 0;
        }
    }
}
