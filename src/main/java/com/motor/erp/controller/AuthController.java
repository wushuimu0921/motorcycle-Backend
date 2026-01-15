package com.motor.erp.controller;

import com.motor.erp.model.User;
import io.javalin.http.Context;
import java.util.Map;

public class AuthController {
    public static void login(Context ctx) {
        User loginReq = ctx.bodyAsClass(User.class);

        // 實務上應從資料庫查詢並比對 BCrypt 密碼
        if ("admin".equals(loginReq.username) && "admin123".equals(loginReq.password)) {
            ctx.json(Map.of(
                    "token", "fake-jwt-token-for-demo",
                    "username", "admin",
                    "role", "admin"
            ));
        } else {
            ctx.status(401).result("帳號或密碼錯誤");
        }
    }
}
