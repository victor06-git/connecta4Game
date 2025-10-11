package com.shared;

import org.json.JSONObject;

public class GameObject {
    public String id;
    public double center_x;
    public double center_y;
    public double radius;
    public int col;
    public int row;
    public String color;
    public double originalX;
    public double originalY;

    public GameObject(String id, double center_x, double center_y, double radius, int col, int row) {
        this.id = id;
        this.center_x = center_x;
        this.center_y = center_y;
        this.radius = radius;
        this.col = col;
        this.row = row;
        this.color = "red";
        this.originalX = center_x;
        this.originalY = center_y;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }

    // Converteix l'objecte a JSON
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("center_x", center_x);
        obj.put("center_y", center_y);
        obj.put("radius", radius);
        obj.put("col", col);
        obj.put("row", row);
        obj.put("color", color != null ? color : "red");
        return obj;
    }

    // Crea un GameObjects a partir de JSON
    public static GameObject fromJSON(JSONObject obj) {
        GameObject go = new GameObject(
                obj.optString("id", null),
                obj.optDouble("center_x", 0.0),
                obj.optDouble("center_y", 0.0),
                obj.optDouble("radius", 0.0),
                obj.optInt("col", 1),
                obj.optInt("row", 1));
        go.color = obj.optString("color", "red");
        return go;
    }
}
