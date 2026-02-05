package com.motor.erp.dto;

import java.util.List;

public class SaleRequest {
    // 對應銷貨主表
    public String customerName;
    public String customerPhone;
    public String saleDate;
    public double totalAmount;

    // 對應銷貨明細清單
    public List<SaleItem> items;

    // 內部類別，代表每一台賣出的車
    public static class SaleItem {
        public String vin; // 可選，主要用於 Log 紀錄
        public String model_name;
        public double salePrice;
    }
}
