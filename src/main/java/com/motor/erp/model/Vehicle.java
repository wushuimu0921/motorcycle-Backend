package com.motor.erp.model;

public class Vehicle {
    public String vin;
    public String modelName;
    public String engineNo;
    public String status;
    public String color;

    // 建構子
    public Vehicle(String vin, String modelName, String engineNo, String status, String color) {
        this.vin = vin;
        this.modelName = modelName;
        this.engineNo = engineNo;
        this.status = status;
        this.color = color;
    }
}
