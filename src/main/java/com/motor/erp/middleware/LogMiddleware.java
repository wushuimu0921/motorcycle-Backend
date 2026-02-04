package com.motor.erp.middleware;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogMiddleware {
    private static final Logger logger = LoggerFactory.getLogger(LogMiddleware.class);

    // 紀錄進來的要求
    public static void logRequest(Context ctx) {
        String method = ctx.method().name();
        String path = ctx.path();
        String query = ctx.queryString() != null ? "?" + ctx.queryString() : "";
        String body = ctx.body().isEmpty() ? "[Empty Body]" : ctx.body();

        logger.info("===> REQUEST: {} {}{}", method, path, query);
        if (!method.equals("GET")) { // GET 通常沒 Body，避免冗餘
            logger.info("BODY: {}", body);
        }
    }

    // 紀錄出去的回應
    public static void logResponse(Context ctx) {
        int status = ctx.status().getCode();
        String result = ctx.result();

        logger.info("<=== RESPONSE: Status [{}], Body: {}",
                status,
                (result != null && result.length() > 1024) ? "Result too large..." : result
        );
    }

    // 處理異常 (Exception)
    public static void handleException(Exception e, Context ctx) {
        logger.error("!!! ERROR on {} {}: {}", ctx.method(), ctx.path(), e.getMessage(), e);
        ctx.status(500).json(java.util.Map.of(
                "error", "伺服器內部錯誤",
                "detail", e.getMessage()
        ));
    }
}
