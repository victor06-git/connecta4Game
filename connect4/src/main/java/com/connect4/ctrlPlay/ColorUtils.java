package com.connect4.ctrlPlay;

import javafx.scene.paint.Color;

public class ColorUtils {
    /**
     * Function to get color by name
     * 
     * @param colorName
     * @return
     */
    public Color getColor(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red":
                return Color.RED;
            case "dark_red":
                return Color.rgb(117, 4, 4, 1);
            case "blue":
                return Color.BLUE;
            case "green":
                return Color.GREEN;
            case "yellow":
                return Color.YELLOW;
            case "dark_yellow":
                return Color.rgb(124, 129, 3, 1);
            case "orange":
                return Color.ORANGE;
            case "purple":
                return Color.PURPLE;
            case "pink":
                return Color.PINK;
            case "brown":
                return Color.BROWN;
            case "gray":
                return Color.GRAY;
            case "black":
                return Color.BLACK;
            case "dark_blue":
                return Color.DARKBLUE;
            case "white":
                return Color.WHITE;
            case "dodger_blue":
                return Color.DODGERBLUE;
            default:
                return Color.LIGHTGRAY; // Default color
        }
    }
}
