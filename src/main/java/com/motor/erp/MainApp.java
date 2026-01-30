package com.motor.erp;

import com.motor.erp.controller.AuthController;
import com.motor.erp.controller.PurchaseController;
import com.motor.erp.controller.VehicleController;
import io.javalin.Javalin;

public class MainApp {
    public static void main(String[] args) {
        // 啟動 Javalin 伺服器，監聽 8080 端口
        Javalin app = Javalin.create(config -> {
            // 開啟 CORS，允許 Vue 前端跨網域存取
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });
        }).start(8080);

        // 定義 API 路由
        app.get("/", ctx -> ctx.result("機車進銷存系統 API 運作中"));

        // 車輛相關 API
        app.post("/api/vehicles/register", VehicleController::register);
        app.get("/api/vehicles", VehicleController::getAll);
        app.post("/api/login", AuthController::login);
        app.post("/api/purchase", PurchaseController::createInvoice);
        app.get("/api/purchase", PurchaseController::getInvoices);
        app.get("/api/purchase/{id}", PurchaseController::getInvoiceDetails);
        app.put("/api/purchase/{id}", PurchaseController::updateInvoice);

        System.out.println("=== 系統已啟動：http://localhost:8080 ===");
    }
}