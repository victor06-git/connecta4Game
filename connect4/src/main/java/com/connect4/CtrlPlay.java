package com.connect4;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
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
    private PlayGrid grid;

    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    private GameObject selectedObject = null;
    private GameObject animatingPiece = null;
    private double animationTargetY = 0;
    private double animationSpeed = 500;

    private List<GameObject> piecePool = new ArrayList<>();
    private double poolAreaX = 100;
    private double poolAreaY = 50;
    private double poolAreaWidth = 200;
    private double poolAreaHeight = 500;
    private Random random = new Random();

    // Zona del tablero para dejar caer la ficha
    private double dropZoneHeight = 30;
    private int hoveredColumn = -1;

    // Matriz de las posiciones de las fichas
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
        // grid = new PlayGrid(100, 100, 100, 6, 7);
        double initialWidth = canvas.getWidth() > 0 ? canvas.getWidth() : 800;
        double initialHeight = canvas.getHeight() > 0 ? canvas.getHeight() : 600;
        updateGridSize(initialWidth, initialHeight);

        // initializePiecePool(); // Initialize the pieces in the pool

        // Start run/draw timer bucle
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    // When window changes its size
    public void onSizeChanged() {

        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);

        updateGridSize(width, height);
        updatePoolArea(width, height);
    }

    // Updates the cell size
    // Hacer que tenga una medida concreta y que la vista no se haga más pequeña
    private void updateGridSize(double canvasWidth, double canvasHeight) {
        int rows = 6;
        int cols = 7;

        // Calcular tamaño de celda según espacio
        double availableWidth = canvasWidth * 0.8;
        double availableHeight = (canvasHeight - dropZoneHeight) * 0.8;

        // El tamaño de celda será el menor entre ancho y alto disponible
        double cellSizeByWidth = availableWidth / cols;
        double cellSizeByHeight = availableHeight / rows;
        double cellSize = Math.min(cellSizeByWidth, cellSizeByHeight);

        // Centrar el grid en el canvas
        double gridWidth = cellSize * cols;
        double gridHeight = cellSize * rows;
        double startX = (canvasWidth - gridWidth) / 2;
        double startY = dropZoneHeight + (canvasHeight - gridHeight) / 2;

        // Actualizar el grid
        grid = new PlayGrid(startX, startY, cellSize, rows, cols);
    }

    // Update pool area
    private void updatePoolArea(double canvasWidth, double canvasHeight) {
        // Calcular posición del pool a la derecha del tablero
        double gridRightEdge = grid.getStartX() + (grid.getCols() * grid.getCellSize());
        double availableSpace = canvasWidth - gridRightEdge - 40; // margen de 40px

        poolAreaWidth = Math.min(200, availableSpace * 0.8);

        // Misma altura y posición vertical que el tablero
        poolAreaY = grid.getStartY();
        poolAreaHeight = grid.getRows() * grid.getCellSize();

        // Centrar horizontalmente en el espacio disponible
        poolAreaX = gridRightEdge + (availableSpace - poolAreaWidth) / 2;

        // Reposicionar fichas si es necesario
        repositionPoolPieces();
    }

    // Initialize fichas en el pool
    /*
     * private void initializePiecePool() {
     * piecePool.clear();
     * 
     * double radius = 20;
     * double minDistance = radius * 3; // Distancia mínima entre fichas (3 veces el
     * radio)
     * 
     * List<String> colors = new ArrayList<>();
     * // Crear lista de colores alternados
     * for (int i = 0; i < maxPoolPieces / 2; i++) {
     * colors.add("RED");
     * colors.add("YELLOW");
     * }
     * 
     * // Mezclar los colores para distribución aleatoria
     * java.util.Collections.shuffle(colors);
     * 
     * int attempts = 0;
     * int maxAttempts = 100; // Máximo de intentos por ficha
     * 
     * for (int i = 0; i < maxPoolPieces; i++) {
     * boolean validPosition = false;
     * double x = 0, y = 0;
     * 
     * // Intentar encontrar una posición válida
     * while (!validPosition && attempts < maxAttempts) {
     * // Posición aleatoria dentro del área del pool con márgenes
     * x = poolAreaX + radius + 20 + random.nextDouble() * (poolAreaWidth - 2 *
     * radius - 40);
     * y = poolAreaY + radius + 20 + random.nextDouble() * (poolAreaHeight - 2 *
     * radius - 40);
     * 
     * // Verificar que no esté muy cerca de otras fichas
     * validPosition = true;
     * for (GameObject existingPiece : piecePool) {
     * double dx = x - existingPiece.center_x;
     * double dy = y - existingPiece.center_y;
     * double distance = Math.sqrt(dx * dx + dy * dy);
     * 
     * if (distance < minDistance) {
     * validPosition = false;
     * break;
     * }
     * }
     * attempts++;
     * }
     * 
     * // Si encontró posición válida, crear la ficha
     * if (validPosition) {
     * String id = "pool_" + i + "_" + System.currentTimeMillis();
     * GameObject piece = new GameObject(id, x, y, radius, -1, -1);
     * piece.color = colors.get(i);
     * piecePool.add(piece);
     * attempts = 0; // Reset para la siguiente ficha
     * }
     * }
     * }
     */

    // Add pieces to the pool
    /*
     * private void addPieceToPool(String color, double x, double y) {
     * double radius = 20;
     * 
     * String id = "pool_" + System.currentTimeMillis() + "_" +
     * random.nextInt(10000);
     * GameObject piece = new GameObject(id, x, y, radius, -1, -1);
     * piece.color = color;
     * 
     * piecePool.add(piece);
     * }
     */

    // Reposicionar fichas del pool
    private void repositionPoolPieces() {
        for (GameObject piece : piecePool) {
            // Mantener dentro de los límites del pool
            if (piece.center_x < poolAreaX || piece.center_x > poolAreaX + poolAreaWidth) {
                piece.center_x = poolAreaX + poolAreaWidth / 2;
            }
            if (piece.center_y < poolAreaY || piece.center_y > poolAreaY + poolAreaHeight) {
                piece.center_y = poolAreaY + poolAreaHeight / 2;
            }
        }
    }

    // Verificar si una posición está en la zona de drop
    private boolean isPositionInDropZone(double x, double y) {
        double gridStartX = grid.getStartX();
        double gridEndX = gridStartX + (grid.getCols() * grid.getCellSize());
        double dropZoneStartY = grid.getStartY() - dropZoneHeight;
        double dropZoneEndY = grid.getStartY();

        return x >= gridStartX && x <= gridEndX &&
                y >= dropZoneStartY && y <= dropZoneEndY;
    }

    // Obtener columna sobre la que está el mouse en la drop zone
    private int getDropZoneColumn(double x) {
        if (x < grid.getStartX() || x > grid.getStartX() + grid.getCols() * grid.getCellSize()) {
            return -1;
        }
        int col = (int) ((x - grid.getStartX()) / grid.getCellSize());
        return Math.max(0, Math.min(col, grid.getCols() - 1));
    }

    // Encontrar la fila más baja disponible en una columna
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

    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        // Actualizar columna hover
        if (isPositionInDropZone(mouseX, mouseY)) {
            hoveredColumn = getDropZoneColumn(mouseX);
        } else {
            hoveredColumn = -1;
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

    // Función para eventos de presionar el mouse
    private void onMousePressed(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        selectedObject = null;
        mouseDragging = false;

        for (GameObject go : Main.objects) {
            if (isPositionInsideObject(mouseX, mouseY, go.center_x, go.center_y, go.col, go.row)) {
                selectedObject = new GameObject(go.id, go.center_x, go.center_y, 20, go.col, go.row);
                mouseDragging = true;
                mouseOffsetX = event.getX() - go.center_x;
                mouseOffsetY = event.getY() - go.center_y;
                break;
            }
        }
    }

    // Función para cuando arrastrar el mouse con la ficha
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

    // Función cuando deja ir el ratón
    private void onMouseReleased(MouseEvent event) {
        if (selectedObject != null) {
            double centerX = event.getX() - mouseOffsetX;
            double centerY = event.getY() - mouseOffsetY;

            // Verificar si se soltó en la drop zone
            if (isPositionInDropZone(centerX, centerY)) {
                int col = getDropZoneColumn(centerX);

                // Enviar jugada al servidor (el servidor validará si es posible)
                JSONObject msg = new JSONObject();
                msg.put("type", "clientPlay");
                msg.put("column", col);

                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(msg.toString());
                }

                // La ficha desaparece, el servidor responderá con serverData
                // y entonces animaremos la caída basándonos en lastMove
            }

            selectedObject = null;
            mouseDragging = false;
            hoveredColumn = -1;
        }
    }

    // Iniciar animación de caída
    private void startDropAnimation(GameObject piece, int col, int row) {
        animatingPiece = new GameObject(piece.id, piece.center_x, piece.center_y, piece.radius, col, row);
        animatingPiece.color = piece.color;

        // Calcular posición objetivo
        double cellSize = grid.getCellSize();
        animatingPiece.center_x = grid.getCellX(col) + cellSize / 2;
        animationTargetY = grid.getCellY(row) + cellSize / 2;

        // La pieza empieza desde arriba de la columna
        animatingPiece.center_y = grid.getStartY() - 20;

        selectedObject = null;
    }

    // Snap piece so its left-top corner sits exactly on the grid cell under its
    // left tip.
    /*
     * private void snapObjectCenter(GameObject obj) {
     * int col = grid.getCol(obj.center_x); // centerX -> columna
     * int row = grid.getRow(obj.center_y); // centerY -> fila
     * 
     * // mantener dentro del grid
     * col = (int) Math.max(0, Math.min(col, grid.getCols() - 1));
     * row = (int) Math.max(0, Math.min(row, grid.getRows() - 1));
     * 
     * // Centrar el círculo en la celda
     * double cellSize = grid.getCellSize();
     * obj.center_x = grid.getCellX(col) + cellSize / 2;
     * obj.center_y = grid.getCellY(row) + cellSize / 2;
     * 
     * // Guardar posición de celda
     * obj.col = col;
     * obj.row = row;
     * }
     */

    // Función validación si el objeto se encuentra dentro de la celda

    public Boolean isPositionInsideObject(double positionX, double positionY, double objX, double objY, int cols,
            int rows) {
        double cellSize = grid.getCellSize();
        double objectWidth = cols * cellSize;
        double objectHeight = rows * cellSize;

        double objectLeftX = objX;
        double objectRightX = objX + objectWidth;
        double objectTopY = objY;
        double objectBottomY = objY + objectHeight;

        return positionX >= objectLeftX && positionX < objectRightX &&
                positionY >= objectTopY && positionY < objectBottomY;
    }

    // Run game (and animations)
    private void run(double fps) {

        if (animationTimer.fps < 1) {
            return;
        }

        // Actualizar animación de caída
        if (animatingPiece != null) {
            double deltaTime = 1.0 / fps;
            double movement = animationSpeed * deltaTime;

            if (animatingPiece.center_y < animationTargetY) {
                animatingPiece.center_y += movement;

                // Si llegó al objetivo
                if (animatingPiece.center_y >= animationTargetY) {
                    animatingPiece.center_y = animationTargetY;

                    // Añadir a objetos del tablero (representación visual local)
                    // El servidor ya tiene el estado actualizado
                    boolean exists = Main.objects.stream()
                            .anyMatch(obj -> obj.col == animatingPiece.col && obj.row == animatingPiece.row);

                    if (!exists) {
                        Main.objects.add(animatingPiece);
                    }

                    animatingPiece = null;
                }
            }
        }
    }

    // Draw game to canvas
    // Dibujar celdas con background azul, interior blanco y borde en gris
    public void draw() {

        if (Main.clients == null) {
            return;
        }

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        drawDropZone();

        // Gradiente de colores vibrantes
        gc.setFill(Color.rgb(255, 200, 100)); // Naranja claro
        gc.fillOval(poolAreaX, poolAreaY, poolAreaWidth, poolAreaHeight);

        // Añadir un círculo interno más claro para efecto de profundidad
        gc.setFill(Color.rgb(255, 230, 150, 0.7)); // Más claro y translúcido
        gc.fillOval(poolAreaX + 20, poolAreaY + 20, poolAreaWidth - 40, poolAreaHeight - 40);

        // Borde del pool
        gc.setStroke(Color.rgb(200, 120, 50)); // Marrón/naranja oscuro
        gc.setLineWidth(3);
        gc.strokeOval(poolAreaX, poolAreaY, poolAreaWidth, poolAreaHeight);

        // Draw pool label
        gc.setFill(Color.rgb(100, 60, 20)); // Marrón oscuro
        gc.setFont(new Font("Arial", 16));
        gc.fillText("Fichas disponibles", poolAreaX + 15, poolAreaY - 10);

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

        // Draw objects (fichas en el tablero)
        for (GameObject go : Main.objects) {
            if (selectedObject != null && go.id.equals(selectedObject.id)) { // <- Esto filtra TODO
                drawObject(go);
            }
        }

        // Draw animating piece
        if (animatingPiece != null) {
            drawObject(animatingPiece);
        }

        // Draw grid
        drawGrid();

        // Draw pool pieces
        for (GameObject piece : piecePool) {
            drawObject(piece);
        }

        // Draw selected object on top
        if (selectedObject != null && mouseDragging) {
            drawObject(selectedObject);
        }

        // Draw mouse circles
        for (ClientData clientData : Main.clients) {
            gc.setFill(getColor(clientData.color));
            gc.fillOval(clientData.mouseX - 5, clientData.mouseY - 5, 20, 20);
        }

        // Draw FPS if needed
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
    }

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
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(1);
            gc.strokeRect(x, startY, cellSize, dropZoneHeight);

            // Letra de la columna (A-G)
            gc.setFill(Color.BLACK);
            gc.setFont(new Font("Arial Bold", 24));
            String letter = String.valueOf((char) ('A' + col));
            gc.fillText(letter, x + cellSize / 2 - 8, startY + dropZoneHeight / 2 + 8);
        }
    }

    // Dibuja el tablero
    public void drawGrid() {
        double cellSize = grid.getCellSize();
        double gridWidth = grid.getCols() * cellSize;
        double gridHeight = grid.getRows() * cellSize;
        double startX = grid.getStartX();
        double startY = grid.getStartY();

        // Dibujar el fondo azul del tablero
        gc.setFill(Color.DODGERBLUE);
        gc.fillRect(startX, startY, gridWidth, gridHeight);

        // Dibujar los círculos grises (sombra) en cada celda
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellX = startX + col * cellSize;
                double cellY = startY + row * cellSize;

                double centerX = cellX + cellSize / 2;
                double centerY = cellY + cellSize / 2;

                double holeRadius = cellSize * 0.45;

                gc.setFill(Color.GRAY);
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

                gc.setFill(Color.WHITE);
                gc.fillOval(centerX - holeRadius, centerY - holeRadius, holeRadius * 2, holeRadius * 2);
            }
        }

        // Dibujar borde del tablero
        gc.setStroke(Color.DARKBLUE);
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
        double radius = obj.radius;

        // Seleccionar un color basat en l'objectId
        Color color = obj.color != null ? getColor(obj.color) : Color.RED;

        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Dibuixar el contorn
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(2);
        gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

    }

    // Conseguir color
    public Color getColor(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red":
                return Color.RED;
            case "blue":
                return Color.BLUE;
            case "green":
                return Color.GREEN;
            case "yellow":
                return Color.YELLOW;
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
            default:
                return Color.LIGHTGRAY; // Default color
        }
    }
}
