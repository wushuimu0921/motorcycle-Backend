package com.motor.erp.controller;

import io.javalin.http.Context;

import java.io.InputStream;

public class DownloadController {
    public static void downloadTemplate(Context ctx) {
        // 使用 ClassLoader 讀取資源檔案
        InputStream is = DownloadController.class.getClassLoader()
                .getResourceAsStream("templates/vehicle_template.xlsx");

        if (is != null) {
            // 設定下載檔名，讓瀏覽器彈出儲存對話框
            ctx.header("Content-Disposition", "attachment; filename=vehicle_template.xlsx");
            // 設定正確的 Excel MIME 類型
            ctx.contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            ctx.result(is);
        } else {
            // 如果後端 Console 看到這行，代表檔案位置放錯了
            System.err.println("Error: Template file not found in resources/templates/");
            ctx.status(404).result("系統找不到 Excel 範本檔案，請聯絡系統管理員。");
        }
    }
}
