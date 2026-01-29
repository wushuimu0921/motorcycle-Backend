package com.motor.erp.controller;

import com.motor.erp.config.DatabaseConfig;
import com.motor.erp.model.Menu;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuthController {
    public static List<Menu> getMenusByRole(String roleKey) {
        List<Menu> menus = new ArrayList<>();
        String sql = "SELECT m.title, m.menu_code, component_name, m.icon FROM sys_menus m " +
                "JOIN sys_role_menus rm ON m.id = rm.menu_id " +
                "WHERE rm.role_key = ? ORDER BY m.sort_order";

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, roleKey);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                menus.add(new Menu(
                        rs.getString("title"),
                        rs.getString("menu_code"),
                        rs.getString("component_name"),
                        rs.getString("icon")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return menus;
    }

    // 登入方法內調用
    public static void login(Context ctx) {
        // 1. 接收前端 JSON 傳來的帳密
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String username = body.get("username");
        String password = body.get("password");

        // SQL：驗證使用者並獲取其角色
        String userSql = "SELECT username, role FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(userSql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String role = rs.getString("role");

                // 2. 驗證成功，獲取該角色的動態選單
                List<Menu> menus = getMenusByRole(role);

                // 3. 回傳 Token (範例) 與權限資料
                ctx.json(Map.of(
                        "token", "token_" + UUID.randomUUID(), // 這裡暫時產生隨機 ID
                        "username", username,
                        "role", role,
                        "menus", menus
                ));
            } else {
                // 4. 驗證失敗
                ctx.status(401).result("帳號或密碼錯誤");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            ctx.status(500).result("伺服器內部錯誤");
        }
    }
}
