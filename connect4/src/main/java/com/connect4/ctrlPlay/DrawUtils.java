package com.connect4.ctrlPlay;

import com.shared.GameObject;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class DrawUtils {

    private ColorUtils colorUtils;

    public DrawUtils() {
        this.colorUtils = new ColorUtils();
    }

    /**
     * Draw turn indicator
     * 
     * @param gc
     * @param currentTurn
     * @param myColor
     * @param clientName
     */
    public void drawTurnIndicator(GraphicsContext gc, String currentTurn, String myColor, String clientName) {
        if (myColor.isEmpty()) {
            myColor = com.connect4.Main.clients.stream()
                    .filter(c -> c.name.equals(clientName))
                    .map(c -> c.color)
                    .findFirst()
                    .orElse("");
        }

        double indicatorX = 40;
        double indicatorY = 20;

        // Dibujar fondo
        gc.setFill(Color.rgb(255, 255, 255, 0.8));
        gc.fillRoundRect(indicatorX, indicatorY, 200, 50, 10, 10);

        // Dibujar borde
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeRoundRect(indicatorX, indicatorY, 200, 50, 10, 10);

        // Texto del turno
        gc.setFill(Color.BLACK);
        gc.setFont(new Font("Arial Bold", 16));

        boolean isMyTurn = currentTurn.equals(myColor);
        gc.fillText("Turn: " + currentTurn, indicatorX + 10, indicatorY + 25);

        // Indicador de color del turno actual
        Color turnColor = colorUtils.getColor(currentTurn.toLowerCase());
        gc.setFill(turnColor);
        gc.fillOval(indicatorX + 150, indicatorY + 15, 20, 20);

        // Si es tu turno, añadir indicador extra
        if (isMyTurn) {
            gc.setFill(Color.GREEN);
            gc.fillText("▶", indicatorX + 180, indicatorY + 30);
        }
    }

    /**
     * Function draw board pieces
     * 
     * @param gc
     * @param boardState
     * @param grid
     * @param utils
     */
    public void drawBoardPieces(GraphicsContext gc, String[][] boardState, com.connect4.PlayGrid grid,
            ColorUtils utils) {
        double cellSize = grid.getCellSize();
        double radius = cellSize * 0.40;

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                String pieceId = boardState[row][col];

                if (pieceId != null) {

                    double centerX = grid.getCellX(col) + cellSize / 2;
                    double centerY = grid.getCellY(row) + cellSize / 2;

                    Color color;
                    Color borderColor;
                    if (pieceId.startsWith("R_")) {
                        color = utils.getColor("red");
                        borderColor = utils.getColor("dark_red");
                    } else if (pieceId.startsWith("Y_")) {
                        color = utils.getColor("yellow");
                        borderColor = utils.getColor("dark_yellow");
                    } else {
                        color = utils.getColor("gray");
                        borderColor = utils.getColor("black");
                    }

                    gc.setFill(color);
                    gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

                    gc.setStroke(borderColor);
                    gc.setLineWidth(5);
                    gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                }
            }
        }
    }

    /**
     * Function
     * 
     * @param gc
     * @param winningLineCoords
     * @param grid
     */
    public void drawWinningCircles(GraphicsContext gc, int[] winningLineCoords, com.connect4.PlayGrid grid) {
        if (winningLineCoords == null)
            return;

        double cellSize = grid.getCellSize();
        double radius = cellSize * 0.40;

        int startRow = winningLineCoords[0];
        int startCol = winningLineCoords[1];
        int endRow = winningLineCoords[2];
        int endCol = winningLineCoords[3];

        // Calcular dirección
        int rowStep = (endRow > startRow) ? 1 : (endRow < startRow) ? -1 : 0;
        int colStep = (endCol > startCol) ? 1 : (endCol < startCol) ? -1 : 0;

        // Dibujar círculo en cada una de las 4 fichas ganadoras
        int currentRow = startRow;
        int currentCol = startCol;

        for (int i = 0; i < 4; i++) {
            double centerX = grid.getCellX(currentCol) + cellSize / 2;
            double centerY = grid.getCellY(currentRow) + cellSize / 2;

            // Sombra del círculo
            gc.setFill(Color.rgb(0, 0, 0, 0.3));
            gc.fillOval(centerX - radius + 3, centerY - radius + 3, radius * 2, radius * 2);

            // Círculo verde fosforito (brillante)
            gc.setFill(Color.rgb(0, 255, 0, 0.7)); // Verde neón con transparencia
            gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            // Borde del círculo verde más brillante
            gc.setStroke(Color.rgb(50, 255, 50)); // Verde fosforito
            gc.setLineWidth(4);
            gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            // Avanzar a la siguiente ficha
            currentRow += rowStep;
            currentCol += colStep;
        }
    }

    /**
     * Function draw drop zone
     * 
     * @param gc
     * @param grid
     * @param dropZoneHeight
     * @param hoveredColumn
     * @param utils
     */
    public void drawDropZone(GraphicsContext gc, com.connect4.PlayGrid grid, double dropZoneHeight,
            int hoveredColumn, ColorUtils utils) {
        double startX = grid.getStartX();
        double startY = grid.getStartY() - dropZoneHeight;
        double cellSize = grid.getCellSize();

        for (int col = 0; col < grid.getCols(); col++) {
            double x = startX + col * cellSize;

            // Fondo de la columna
            if (col == hoveredColumn) {
                // Columna iluminada
                gc.setFill(Color.rgb(100, 200, 255, 0.5));
            } else {
                gc.setFill(Color.rgb(200, 200, 200, 0.3));
            }
            gc.fillRect(x, startY, cellSize, dropZoneHeight);

            // Borde
            gc.setStroke(utils.getColor("gray"));
            gc.setLineWidth(1);
            gc.strokeRect(x, startY, cellSize, dropZoneHeight);

            // Letra de la columna (A-G)
            gc.setFill(utils.getColor("black"));
            gc.setFont(new Font("Arial Bold", 24));
            String letter = String.valueOf((char) ('A' + col));
            gc.fillText(letter, x + cellSize / 2 - 8, startY + dropZoneHeight / 2 + 8);
        }
    }

    /**
     * Function draw board
     * 
     * @param gc
     * @param grid
     * @param utils
     */
    public void drawBoard(GraphicsContext gc, com.connect4.PlayGrid grid, ColorUtils utils) {
        double cellSize = grid.getCellSize();
        double gridWidth = grid.getCols() * cellSize;
        double gridHeight = grid.getRows() * cellSize;
        double startX = grid.getStartX();
        double startY = grid.getStartY();

        // Dibujar el fondo azul del tablero
        gc.setFill(utils.getColor("dodger_blue"));
        gc.fillRect(startX, startY, gridWidth, gridHeight);

        // Dibujar los círculos grises (sombra) en cada celda
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellX = startX + col * cellSize;
                double cellY = startY + row * cellSize;

                double centerX = cellX + cellSize / 2;
                double centerY = cellY + cellSize / 2;

                double holeRadius = cellSize * 0.45;

                gc.setFill(utils.getColor("gray"));
                gc.fillOval(centerX - holeRadius, centerY - holeRadius, holeRadius * 2, holeRadius * 2);
            }
        }

        // Dibujar los círculos blancos (agujeros) en cada celda
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellX = startX + col * cellSize;
                double cellY = startY + row * cellSize;

                double centerX = cellX + cellSize / 2;
                double centerY = cellY + cellSize / 2;

                double holeRadius = cellSize * 0.4;

                gc.setFill(utils.getColor("white"));
                gc.fillOval(centerX - holeRadius, centerY - holeRadius, holeRadius * 2, holeRadius * 2);
            }
        }

        // Dibujar borde del tablero
        gc.setStroke(utils.getColor("dark_blue"));
        gc.setLineWidth(3);
        gc.strokeRect(startX, startY, gridWidth, gridHeight);
    }

    /**
     * Function draw object
     * 
     * @param obj
     * @param gc
     * @param grid
     * @param utils
     */
    public void drawObject(GameObject obj, GraphicsContext gc, com.connect4.PlayGrid grid, ColorUtils utils) {
        double centerX = obj.center_x;
        double centerY = obj.center_y;
        double radius = grid.getCellSize() * 0.40;

        // Seleccionar un color basat en l'objectId
        Color color;
        if (obj.color != null && !obj.color.isEmpty()) {
            color = utils.getColor(obj.color);
        } else {
            // Color por ID
            if (obj.id.startsWith("R_")) {
                color = utils.getColor("red");
            } else if (obj.id.startsWith("Y_")) {
                color = utils.getColor("yellow");
            } else {
                color = utils.getColor("gray");
            }
        }

        // Dibuixar el cercle
        gc.setFill(color);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Dibuixar el contorn
        gc.setStroke(obj.id.startsWith("R_") ? utils.getColor("dark_red") : utils.getColor("dark_yellow"));
        gc.setLineWidth(5);
        gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

    }
}
