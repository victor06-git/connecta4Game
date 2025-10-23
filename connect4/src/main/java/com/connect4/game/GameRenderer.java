package com.connect4.game;

import com.connect4.PlayGrid;
import com.shared.GameObject;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

/**
 * Renderiza todos los elementos visuales del juego.
 * Maneja el dibujo del tablero, fichas, indicadores y efectos visuales.
 */
public class GameRenderer {

    private static final double DROP_ZONE_HEIGHT = 60.0;

    // Pool dimensions
    private final double poolX = 20;
    private final double poolY = 500;
    private final double poolWidth = 150;
    private final double poolHeight = 200;

    private final GraphicsContext gc;
    private final PlayGrid grid;
    private final GameState gameState;
    private final GameObjectManager objectManager;

    /**
     * Constructor del renderizador.
     * 
     * @param gc            Contexto gráfico del Canvas
     * @param grid          Grid del tablero
     * @param gameState     Estado del juego
     * @param objectManager Gestor de objetos
     */
    public GameRenderer(GraphicsContext gc, PlayGrid grid, GameState gameState,
            GameObjectManager objectManager) {
        this.gc = gc;
        this.grid = grid;
        this.gameState = gameState;
        this.objectManager = objectManager;
    }

    /**
     * Limpia todo el canvas.
     * 
     * @param width  Ancho del canvas
     * @param height Alto del canvas
     */
    public void clear(double width, double height) {
        gc.clearRect(0, 0, width, height);
    }

    /**
     * Dibuja el indicador de turno en la esquina superior izquierda.
     */
    public void drawTurnIndicator() {
        double indicatorX = 20;
        double indicatorY = 10;

        // Fondo blanco semi-transparente
        gc.setFill(Color.rgb(255, 255, 255, 0.8));
        gc.fillRoundRect(indicatorX, indicatorY, 200, 50, 10, 10);

        // Borde negro
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeRoundRect(indicatorX, indicatorY, 200, 50, 10, 10);

        // Texto del turno
        gc.setFill(Color.BLACK);
        gc.setFont(new Font("Arial Bold", 16));
        gc.fillText("Turn: " + gameState.getCurrentTurn(), indicatorX + 10, indicatorY + 25);

        // Círculo indicador del color del turno
        Color turnColor = ColorUtils.getColor(gameState.getCurrentTurn().toLowerCase());
        gc.setFill(turnColor);
        gc.fillOval(indicatorX + 150, indicatorY + 15, 20, 20);

        // Flecha verde si es mi turno
        if (gameState.isMyTurn()) {
            gc.setFill(Color.GREEN);
            gc.fillText("▶", indicatorX + 180, indicatorY + 30);
        }
    }

    /**
     * Dibuja la zona de drop sobre el tablero con letras A-G.
     */
    public void drawDropZone() {
        double startX = grid.getStartX();
        double startY = grid.getStartY() - DROP_ZONE_HEIGHT;
        double cellSize = grid.getCellSize();
        int hoveredCol = gameState.getHoveredColumn();

        for (int col = 0; col < grid.getCols(); col++) {
            double x = startX + col * cellSize;

            // Fondo de la columna (iluminada si está hover)
            if (col == hoveredCol) {
                gc.setFill(Color.rgb(100, 200, 255, 0.5));
            } else {
                gc.setFill(Color.rgb(200, 200, 200, 0.3));
            }
            gc.fillRect(x, startY, cellSize, DROP_ZONE_HEIGHT);

            // Borde
            gc.setStroke(ColorUtils.getColor("gray"));
            gc.setLineWidth(1);
            gc.strokeRect(x, startY, cellSize, DROP_ZONE_HEIGHT);

            // Letra de la columna (A-G)
            gc.setFill(ColorUtils.getColor("black"));
            gc.setFont(new Font("Arial Bold", 24));
            String letter = String.valueOf((char) ('A' + col));
            gc.fillText(letter, x + cellSize / 2 - 8, startY + DROP_ZONE_HEIGHT / 2 + 8);
        }
    }

    /**
     * Dibuja celdas semi-transparentes para mostrar posiciones de otros jugadores.
     * 
     * @param row       Fila
     * @param col       Columna
     * @param colorName Color del jugador
     */
    public void drawOverCell(int row, int col, String colorName) {
        if (row >= 0 && col >= 0) {
            Color alpha = ColorUtils.getColorWithAlpha(colorName, 0.5);
            gc.setFill(alpha);
            gc.fillRect(grid.getCellX(col), grid.getCellY(row),
                    grid.getCellSize(), grid.getCellSize());
        }
    }

    /**
     * Dibuja el pool de fichas con efecto de madera.
     */
    public void drawPool() {
        // Gradiente de madera
        LinearGradient woodGradient = new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(139, 90, 43)),
                new Stop(1, Color.rgb(120, 80, 40)));

        // Fondo del pool
        gc.setFill(woodGradient);
        gc.fillRect(poolX, poolY, poolWidth, poolHeight);

        // Borde exterior
        gc.setStroke(Color.rgb(80, 50, 20));
        gc.setLineWidth(3);
        gc.strokeRect(poolX, poolY, poolWidth, poolHeight);
    }

    /**
     * Dibuja el tablero de Connect 4 con efecto azul y agujeros.
     */
    public void drawBoard() {
        double cellSize = grid.getCellSize();
        double gridWidth = grid.getCols() * cellSize;
        double gridHeight = grid.getRows() * cellSize;
        double startX = grid.getStartX();
        double startY = grid.getStartY();

        // Fondo azul del tablero
        gc.setFill(ColorUtils.getColor("dodger_blue"));
        gc.fillRect(startX, startY, gridWidth, gridHeight);

        // Círculos grises (sombra de agujeros)
        double holeRadiusShadow = cellSize * 0.45;
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double centerX = grid.getCellX(col) + cellSize / 2;
                double centerY = grid.getCellY(row) + cellSize / 2;

                gc.setFill(ColorUtils.getColor("gray"));
                gc.fillOval(centerX - holeRadiusShadow, centerY - holeRadiusShadow,
                        holeRadiusShadow * 2, holeRadiusShadow * 2);
            }
        }

        // Círculos blancos (agujeros reales)
        double holeRadius = cellSize * 0.4;
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double centerX = grid.getCellX(col) + cellSize / 2;
                double centerY = grid.getCellY(row) + cellSize / 2;

                gc.setFill(ColorUtils.getColor("white"));
                gc.fillOval(centerX - holeRadius, centerY - holeRadius,
                        holeRadius * 2, holeRadius * 2);
            }
        }

        // Borde del tablero
        gc.setStroke(ColorUtils.getColor("dark_blue"));
        gc.setLineWidth(3);
        gc.strokeRect(startX, startY, gridWidth, gridHeight);
    }

    /**
     * Dibuja todas las fichas que están en el tablero.
     */
    public void drawBoardPieces() {
        double cellSize = grid.getCellSize();
        double radius = cellSize * 0.40;

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                String pieceId = gameState.getPieceAt(row, col);

                if (pieceId != null) {
                    double centerX = grid.getCellX(col) + cellSize / 2;
                    double centerY = grid.getCellY(row) + cellSize / 2;

                    drawPieceAt(centerX, centerY, radius, pieceId);
                }
            }
        }
    }

    /**
     * Dibuja las fichas del pool (no seleccionadas).
     * 
     * @param selectedPieceId ID de la ficha seleccionada (para omitirla)
     */
    public void drawPoolPieces(String selectedPieceId) {
        for (GameObject piece : objectManager.getPoolPieces()) {
            // Saltar la ficha seleccionada
            if (selectedPieceId != null && piece.id.equals(selectedPieceId)) {
                continue;
            }
            drawGameObject(piece);
        }
    }

    /**
     * Dibuja un GameObject (ficha) en su posición actual.
     * 
     * @param piece El GameObject a dibujar
     */
    public void drawGameObject(GameObject piece) {
        double radius = grid.getCellSize() * 0.40;
        drawPieceAt(piece.center_x, piece.center_y, radius, piece.id);
    }

    /**
     * Dibuja una ficha en una posición específica.
     * 
     * @param centerX Centro X
     * @param centerY Centro Y
     * @param radius  Radio de la ficha
     * @param pieceId ID de la ficha
     */
    private void drawPieceAt(double centerX, double centerY, double radius, String pieceId) {
        Color color = ColorUtils.getPieceColor(pieceId);
        Color borderColor = ColorUtils.getPieceBorderColor(pieceId);

        // Ficha
        gc.setFill(color);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Borde
        gc.setStroke(borderColor);
        gc.setLineWidth(5);
        gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    }

    /**
     * Dibuja círculos verdes fosforitos sobre las fichas ganadoras.
     */
    public void drawWinningLine() {
        int[] coords = gameState.getWinningLineCoords();
        if (coords == null) {
            return;
        }

        double cellSize = grid.getCellSize();
        double radius = cellSize * 0.40;

        int startRow = coords[0];
        int startCol = coords[1];
        int endRow = coords[2];
        int endCol = coords[3];

        // Calcular dirección
        int rowStep = (endRow > startRow) ? 1 : (endRow < startRow) ? -1 : 0;
        int colStep = (endCol > startCol) ? 1 : (endCol < startCol) ? -1 : 0;

        // Dibujar círculo en cada una de las 4 fichas ganadoras
        int currentRow = startRow;
        int currentCol = startCol;

        for (int i = 0; i < 4; i++) {
            double centerX = grid.getCellX(currentCol) + cellSize / 2;
            double centerY = grid.getCellY(currentRow) + cellSize / 2;

            // Sombra
            gc.setFill(Color.rgb(0, 0, 0, 0.3));
            gc.fillOval(centerX - radius + 3, centerY - radius + 3, radius * 2, radius * 2);

            // Círculo verde neón
            gc.setFill(Color.rgb(0, 255, 0, 0.7));
            gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            // Borde fosforito
            gc.setStroke(Color.rgb(50, 255, 50));
            gc.setLineWidth(4);
            gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            currentRow += rowStep;
            currentCol += colStep;
        }
    }

    /**
     * Dibuja círculos pequeños para mostrar la posición del mouse de los jugadores.
     * 
     * @param mouseX    Posición X del mouse
     * @param mouseY    Posición Y del mouse
     * @param colorName Color del jugador
     */
    public void drawMouseCursor(double mouseX, double mouseY, String colorName) {
        gc.setFill(ColorUtils.getColor(colorName));
        gc.fillOval(mouseX - 5, mouseY - 5, 20, 20);
    }
}
