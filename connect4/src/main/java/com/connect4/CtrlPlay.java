package com.connect4;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

import com.shared.ClientData;
import com.shared.GameObject;

public class CtrlPlay implements Initializable {

    @FXML
    public javafx.scene.control.Label title;

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;

    private PlayTimer animationTimer;
    private PlayGrid grid; // La cuadrícula del juego

    private Boolean mouseDragging = false; // Si se está arrastrando una ficha
    private double mouseOffsetX, mouseOffsetY;

    private GameObject selectedObject = null; // Ficha seleccionada

    private boolean isAnimating = false; // Si se está animando una ficha
    private double animationTargetY = 0; // Animación en columna
    private double animationSpeed = 600; // Velocidad animación

    // Zona del tablero para dejar caer la ficha
    private double dropZoneHeight = 40;
    private int hoveredColumn = -1;

    // pool (mesa donde estan las fichas)
    private double poolX, poolY, poolWidth, poolHeight;
    private static final double BOARD_POOL_GAP = 50;
    private static final double FIXED_CELL_SIZE = 80;
    private static final double LEFT_MARGIN = 50;

    // Winner variables
    private int[] winningLineCoords = null;
    private String winningColor = null;

    // Matriz de las posiciones de las fichas, se inicializa null
    private String[][] boardState = new String[6][7];

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

        // Inicializar estado del tablero
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 7; j++) {
                boardState[i][j] = null;
            }
        }

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> {
            onSizeChanged();
        });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> {
            onSizeChanged();
        });

        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        // Define grid
        double startX = LEFT_MARGIN;
        double startY = dropZoneHeight + 50;
        grid = new PlayGrid(startX, startY, FIXED_CELL_SIZE, 6, 7);

        updatePoolDimensions();

        // Start run/draw timer bucle
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    /**
     * Function called when the window size changes
     * 
     */
    public void onSizeChanged() {

        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();

        // Calcular tamaño mínimo necesario
        double minWidth = LEFT_MARGIN + (7 * FIXED_CELL_SIZE) + BOARD_POOL_GAP + 200 + 50; // width minimum
        double minHeight = dropZoneHeight + 50 + (6 * FIXED_CELL_SIZE) + 50; // height minimum

        // Aplicar tamaño mínimo
        width = Math.max(width, minWidth);
        height = Math.max(height, minHeight);

        canvas.setWidth(width); // set minimum width
        canvas.setHeight(height); // set minimum height
    }

    /**
     * Function to update the dimensions of the pool based on grid size
     * 
     */
    private void updatePoolDimensions() {
        // El pool empieza después del tablero + el gap
        double boardEndX = grid.getStartX() + (grid.getCols() * grid.getCellSize());
        poolX = boardEndX + BOARD_POOL_GAP;

        // Dimensiones fijas del pool
        poolWidth = 250; // Ancho fijo del pool
        poolHeight = grid.getRows() * grid.getCellSize(); // Misma altura que el tablero

        // Misma posición Y que el tablero
        poolY = grid.getStartY();
    }

    /**
     * Function to check if the position is inside the drop zone
     * 
     * @param x
     * @param y
     * @return
     */
    private boolean isPositionInDropZone(double x, double y) {
        double gridStartX = grid.getStartX();
        double gridEndX = gridStartX + (grid.getCols() * grid.getCellSize());
        double dropZoneStartY = grid.getStartY() - dropZoneHeight;
        double dropZoneEndY = grid.getStartY();

        return x >= gridStartX && x <= gridEndX &&
                y >= dropZoneStartY && y <= dropZoneEndY;
    }

    /**
     * Function to get the column index based on x position in drop zone
     * 
     * @param x
     * @return
     */
    private int getDropZoneColumn(double x) {
        if (x < grid.getStartX() || x > grid.getStartX() + grid.getCols() * grid.getCellSize()) {
            return -1;
        }
        int col = (int) ((x - grid.getStartX()) / grid.getCellSize());
        return Math.max(0, Math.min(col, grid.getCols() - 1));
    }

    /**
     * Function to get the lowest available row in a column
     * 
     * @param col
     * @return
     */
    private int getLowestAvailableRow(int col) {
        for (int row = grid.getRows() - 1; row >= 0; row--) {
            if (boardState[row][col] == null) {
                return row;
            }
        }
        return -1; // Columna llena
    }

    // Start animation timer
    public void start() {
        animationTimer.start();
    }

    // Stop animation timer
    public void stop() {
        animationTimer.stop();
    }

    /**
     * Function for mouse moved
     * 
     * @param event
     */
    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        // Update hovered column only if not animating
        if (!isAnimating) {
            if (isPositionInDropZone(mouseX, mouseY)) {
                hoveredColumn = getDropZoneColumn(mouseX);
            } else {
                hoveredColumn = -1;
            }
        }

        String color = Main.clients.stream()
                .filter(c -> c.name.equals(Main.clientName))
                .map(c -> c.color)
                .findFirst()
                .orElse("gray");

        ClientData cd = new ClientData(
                Main.clientName,
                color,
                (int) mouseX,
                (int) mouseY,
                grid.isPositionInsideGrid(mouseX, mouseY) ? grid.getRow(mouseY) : -1,
                grid.isPositionInsideGrid(mouseX, mouseY) ? grid.getCol(mouseX) : -1);

        JSONObject msg = new JSONObject();
        msg.put("type", "clientMouseMoving");
        msg.put("value", cd.toJSON());

        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msg.toString());
        }
    }

    /**
     * Function for mouse pressed
     * 
     * @param event
     */
    private void onMousePressed(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        selectedObject = null;
        mouseDragging = false;

        // Radio correcto igual al del tablero
        double correctRadius = grid.getCellSize() * 0.40;

        for (GameObject go : Main.objects) {
            if (go.col == -1 && go.row == -1) {
                // Verificar si el mouse está dentro del círculo de la ficha
                if (isMouseInsideCircle(mouseX, mouseY, go.center_x, go.center_y, correctRadius)) {
                    selectedObject = go; // Seleccionar la ficha
                    mouseDragging = true;
                    mouseOffsetX = mouseX - go.center_x;
                    mouseOffsetY = mouseY - go.center_y;
                    break;
                }
            }
        }
    }

    /**
     * 
     * Function to verify position mouse on object (piece game)
     * 
     * @param mouseX
     * @param mouseY
     * @param centerX
     * @param centerY
     * @param radius
     * @return
     */
    private boolean isMouseInsideCircle(double mouseX, double mouseY, double centerX, double centerY, double radius) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distanceSquared = dx * dx + dy * dy;
        return distanceSquared <= radius * radius;
    }

    /**
     * Function for mouse dragged
     * 
     * @param event
     */
    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging && selectedObject != null) {
            double centerX = event.getX() - mouseOffsetX;
            double centerY = event.getY() - mouseOffsetY;

            selectedObject.center_x = centerX;
            selectedObject.center_y = centerY;

            // Actualizar columna hover
            if (isPositionInDropZone(centerX, centerY)) {
                hoveredColumn = getDropZoneColumn(centerX);
            } else {
                hoveredColumn = -1;
            }

            JSONObject msg = new JSONObject();
            msg.put("type", "clientPieceMoving");
            msg.put("value", selectedObject.toJSON());

            if (Main.wsClient != null) {
                Main.wsClient.safeSend(msg.toString());
            }
        }
        setOnMouseMoved(event);
    }

    /**
     * Function for mouse released
     * 
     * @param event
     */
    private void onMouseReleased(MouseEvent event) {
        if (selectedObject != null) {
            double centerX = event.getX() - mouseOffsetX;
            double centerY = event.getY() - mouseOffsetY;

            // Verificar si se soltó en la drop zone
            if (isPositionInDropZone(centerX, centerY)) {
                int col = getDropZoneColumn(centerX);
                int row = getLowestAvailableRow(col);

                if (row != -1) {

                    // Iniciar animación de caída
                    startDropAnimation(col, row);

                    // Actualizar estado del tablero
                    boardState[row][col] = selectedObject.id;

                    // Enviar jugada al servidor
                    JSONObject msg = new JSONObject();
                    msg.put("type", "clientPlay");
                    msg.put("pieceId", selectedObject.id);
                    msg.put("column", col);
                    msg.put("row", row);

                    if (Main.wsClient != null) {
                        Main.wsClient.safeSend(msg.toString());
                    }

                    // Desactivate dragging and selection
                    mouseDragging = false;
                    hoveredColumn = -1;
                    return;
                }
            }

            // If not valid drop, return to pool position
            selectedObject = null;
            mouseDragging = false;
            hoveredColumn = -1;
        }
    }

    /**
     * Function to start the drop animation from the hover
     * 
     * @param piece
     * @param col
     * @param row
     */
    private void startDropAnimation(int col, int row) {

        isAnimating = true;

        double cellSize = grid.getCellSize();
        selectedObject.col = col;
        selectedObject.row = row;
        selectedObject.center_x = grid.getCellX(col) + cellSize / 2;
        animationTargetY = grid.getCellY(row) + cellSize / 2;

        selectedObject.center_y = grid.getStartY() - 20;

    }

    /**
     * Main loop update function
     * 
     * @param fps
     */
    private void run(double fps) {

        if (animationTimer.fps < 1) {
            return;
        }

        if (isAnimating && selectedObject != null) {
            double deltaTime = 1.0 / fps;
            double movement = animationSpeed * deltaTime;

            if (selectedObject.center_y < animationTargetY) {
                selectedObject.center_y += movement;

                if (selectedObject.center_y >= animationTargetY) {
                    selectedObject.center_y = animationTargetY;

                    for (GameObject go : Main.objects) {
                        if (go.id.equals(selectedObject.id)) {
                            go.center_x = selectedObject.center_x;
                            go.center_y = selectedObject.center_y;
                            go.row = selectedObject.row;
                            go.col = selectedObject.col;
                            break;
                        }
                    }

                    // Winning
                    checkWinner();

                    isAnimating = false;
                    selectedObject = null;
                }
            }
        }
    }

    /**
     * Function that checks the winner
     * 
     */
    private void checkWinner() {
        // Horizontal
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 4; col++) {
                String piece = boardState[row][col];
                if (piece != null && piece.equals(boardState[row][col + 1]) && piece.equals(boardState[row][col + 2])
                        && piece.equals(boardState[row][col + 3])) {
                    winningLineCoords = new int[] { row, col, row, col + 3 };
                    winningColor = piece;
                    return;
                }
            }
        }

        // Vertical
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                String piece = boardState[row][col];
                if (piece != null && piece.equals(boardState[row + 1][col]) && piece.equals(boardState[row + 2][col])
                        && piece.equals(boardState[row + 3][col])) {
                    winningLineCoords = new int[] { row, col, row + 3, col };
                    winningColor = piece;
                }
            }
        }

        // Diagonal a la izquierda (\)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                String piece = boardState[row][col];
                if (piece != null && piece.equals(boardState[row + 1][col + 1])
                        && piece.equals(boardState[row + 2][col + 2]) && piece.equals(boardState[row + 3][col + 3])) {
                    winningLineCoords = new int[] { row, col, row + 3, col + 3 };
                    winningColor = piece;
                }
            }
        }

        // Diagonal a la derecha (/)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                String piece = boardState[row][col];
                if (piece != null && piece.equals(boardState[row - 1][col + 1])
                        && piece.equals(boardState[row - 2][col + 2]) && piece.equals(boardState[row - 3][col + 3])) {
                    winningLineCoords = new int[] { row, col, row - 3, col + 3 };
                    winningColor = piece;
                }
            }
        }
    }

    // Draw game to canvas
    // Dibujar celdas con background azul, interior blanco y borde en gris
    /**
     * Function to draw the game
     * 
     */
    public void draw() {

        if (Main.clients == null) {
            return;
        }

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        drawDropZone();

        // Draw colored 'over' cells
        for (ClientData clientData : Main.clients) {
            // Comprovar si està dins dels límits de la graella
            if (clientData.row >= 0 && clientData.col >= 0) {
                Color base = getColor(clientData.color);
                Color alpha = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0.5);
                gc.setFill(alpha);
                gc.fillRect(grid.getCellX(clientData.col), grid.getCellY(clientData.row), grid.getCellSize(),
                        grid.getCellSize());
            }
        }

        // Crear gradiente simple de madera (marrón medio a claro)
        LinearGradient woodGradient = new LinearGradient(
                0, 0, 0, 1, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(139, 90, 43)), // Marrón medio
                new Stop(1, Color.rgb(120, 80, 40)) // Marrón más oscuro
        );

        // Dibujar pool con dimensiones adaptativas
        gc.setFill(woodGradient);
        gc.fillRect(poolX, poolY, poolWidth, poolHeight);

        // Borde exterior simple
        gc.setStroke(Color.rgb(80, 50, 20));
        gc.setLineWidth(3);
        gc.strokeRect(poolX, poolY, poolWidth, poolHeight);

        // Draw grid
        drawBoard();

        // Draw pieces of the board (selected)
        for (GameObject go : Main.objects) {
            if (go.row != -1 && go.col != -1) {
                // Saltar la ficha que está siendo arrastrada o animándose
                if (selectedObject != null && go.id.equals(selectedObject.id))
                    continue;
                drawObject(go);
            }
        }

        // Draw pieces of pool (non-selected)
        for (GameObject go : Main.objects) {
            if (go.row == -1 && go.col == -1) {
                // Saltar la ficha que está siendo arrastrada o animándose
                if (selectedObject != null && go.id.equals(selectedObject.id))
                    continue;
                drawObject(go);
            }
        }

        // Draw animating piece
        if (selectedObject != null) {
            drawObject(selectedObject);
        }

        if (winningLineCoords != null) {
            drawWinningLine();
        }

        // Draw mouse circles (Consigue el color de clients)
        for (ClientData clientData : Main.clients) {
            gc.setFill(getColor(clientData.color));
            gc.fillOval(clientData.mouseX - 5, clientData.mouseY - 5, 20, 20);
        }

        // Draw FPS if needed
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
    }

    /**
     * Function that draws the circles of the winner
     * 
     */
    private void drawWinningLine() {
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
     * Function that draw the drop zone
     * 
     */
    private void drawDropZone() {
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
            gc.setStroke(getColor("gray"));
            gc.setLineWidth(1);
            gc.strokeRect(x, startY, cellSize, dropZoneHeight);

            // Letra de la columna (A-G)
            gc.setFill(getColor("black"));
            gc.setFont(new Font("Arial Bold", 24));
            String letter = String.valueOf((char) ('A' + col));
            gc.fillText(letter, x + cellSize / 2 - 8, startY + dropZoneHeight / 2 + 8);
        }
    }

    /**
     * Function that draw the board
     * 
     */
    public void drawBoard() {
        double cellSize = grid.getCellSize();
        double gridWidth = grid.getCols() * cellSize;
        double gridHeight = grid.getRows() * cellSize;
        double startX = grid.getStartX();
        double startY = grid.getStartY();

        // Dibujar el fondo azul del tablero
        gc.setFill(getColor("dodger_blue"));
        gc.fillRect(startX, startY, gridWidth, gridHeight);

        // Dibujar los círculos grises (sombra) en cada celda
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellX = startX + col * cellSize;
                double cellY = startY + row * cellSize;

                double centerX = cellX + cellSize / 2;
                double centerY = cellY + cellSize / 2;

                double holeRadius = cellSize * 0.45;

                gc.setFill(getColor("gray"));
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

                gc.setFill(getColor("white"));
                gc.fillOval(centerX - holeRadius, centerY - holeRadius, holeRadius * 2, holeRadius * 2);
            }
        }

        // Dibujar borde del tablero
        gc.setStroke(getColor("dark_blue"));
        gc.setLineWidth(3);
        gc.strokeRect(startX, startY, gridWidth, gridHeight);
    }

    /**
     * Function that created the object (fichas)
     * 
     * @param obj
     */
    public void drawObject(GameObject obj) {
        double centerX = obj.center_x;
        double centerY = obj.center_y;
        double radius = grid.getCellSize() * 0.40;

        // Seleccionar un color basat en l'objectId
        Color color;
        if (obj.color != null && !obj.color.isEmpty()) {
            color = getColor(obj.color);
        } else {
            // Color por ID
            if (obj.id.startsWith("R_")) {
                color = getColor("red");
            } else if (obj.id.startsWith("Y_")) {
                color = getColor("yellow");
            } else {
                color = getColor("gray");
            }
        }

        // Dibuixar el cercle
        gc.setFill(color);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Dibuixar el contorn
        gc.setStroke(obj.id.startsWith("R_") ? getColor("dark_red") : getColor("dark_yellow"));
        gc.setLineWidth(5);
        gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

    }

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
