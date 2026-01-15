package com.motor.erp.model;

public class Vehicle {
    public String vin;
    public String modelName;
    public String engineNo;
    public String status;

    // 建構子
    public Vehicle(String vin, String modelName, String engineNo, String status) {
        this.vin = vin;
        this.modelName = modelName;
        this.engineNo = engineNo;
        this.status = status;
    }
}
