package com.connect4.game;

import javafx.scene.paint.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para gestionar colores del juego de forma centralizada.
 * Proporciona acceso rápido a los colores predefinidos.
 */
public class ColorUtils {

    private static final Map<String, Color> COLOR_MAP = new HashMap<>();

    static {
        // Colores de fichas
        COLOR_MAP.put("red", Color.RED);
        COLOR_MAP.put("dark_red", Color.rgb(117, 4, 4));
        COLOR_MAP.put("yellow", Color.YELLOW);
        COLOR_MAP.put("dark_yellow", Color.rgb(124, 129, 3));

        // Colores del tablero
        COLOR_MAP.put("dodger_blue", Color.DODGERBLUE);
        COLOR_MAP.put("dark_blue", Color.DARKBLUE);
        COLOR_MAP.put("blue", Color.BLUE);

        // Colores básicos
        COLOR_MAP.put("white", Color.WHITE);
        COLOR_MAP.put("black", Color.BLACK);
        COLOR_MAP.put("gray", Color.GRAY);
        COLOR_MAP.put("green", Color.GREEN);
        COLOR_MAP.put("orange", Color.ORANGE);
        COLOR_MAP.put("purple", Color.PURPLE);
        COLOR_MAP.put("pink", Color.PINK);
        COLOR_MAP.put("brown", Color.BROWN);
    }

    /**
     * Obtiene un color por su nombre.
     * 
     * @param colorName El nombre del color (case-insensitive)
     * @return El color correspondiente, o LIGHTGRAY si no se encuentra
     */
    public static Color getColor(String colorName) {
        return COLOR_MAP.getOrDefault(colorName.toLowerCase(), Color.LIGHTGRAY);
    }

    /**
     * Obtiene un color con un canal alpha específico.
     * 
     * @param colorName El nombre del color
     * @param alpha     La opacidad (0.0 a 1.0)
     * @return El color con la opacidad especificada
     */
    public static Color getColorWithAlpha(String colorName, double alpha) {
        Color base = getColor(colorName);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /**
     * Obtiene el color basado en el ID de la ficha.
     * 
     * @param pieceId El ID de la ficha (ej: "R_5", "Y_3")
     * @return El color de la ficha
     */
    public static Color getPieceColor(String pieceId) {
        if (pieceId.startsWith("R_")) {
            return getColor("red");
        } else if (pieceId.startsWith("Y_")) {
            return getColor("yellow");
        }
        return getColor("gray");
    }

    /**
     * Obtiene el color del borde basado en el ID de la ficha.
     * 
     * @param pieceId El ID de la ficha (ej: "R_5", "Y_3")
     * @return El color del borde de la ficha
     */
    public static Color getPieceBorderColor(String pieceId) {
        if (pieceId.startsWith("R_")) {
            return getColor("dark_red");
        } else if (pieceId.startsWith("Y_")) {
            return getColor("dark_yellow");
        }
        return getColor("black");
    }
}
