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

    private List<GameObject> piecePool = new ArrayList<>();
    private double poolAreaX = 0;
    private double poolAreaY = 50;
    private double poolAreaWidth = 200;
    private double poolAreaHeight = 500;
    private int maxPoolPieces = 15; // Máximo de fichas visibles
    private Random random = new Random();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

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
    }

    // Updates the cell size
    // Hacer que tenga una medida concreta y que la vista no se haga más pequeña
    private void updateGridSize(double canvasWidth, double canvasHeight) {
        int rows = 6;
        int cols = 7;

        // Calcular tamaño de celda según espacio
        double availableWidth = canvasWidth * 0.8;
        double availableHeight = canvasHeight * 0.8;

        // El tamaño de celda será el menor entre ancho y alto disponible
        double cellSizeByWidth = availableWidth / cols;
        double cellSizeByHeight = availableHeight / rows;
        double cellSize = Math.min(cellSizeByWidth, cellSizeByHeight);

        // Centrar el grid en el canvas
        double gridWidth = cellSize * cols;
        double gridHeight = cellSize * rows;
        double startX = (canvasWidth - gridWidth) / 2;
        double startY = (canvasHeight - gridHeight) / 2;

        // Actualizar el grid
        grid = new PlayGrid(startX, startY, cellSize, rows, cols);
    }

    // Update pool area
    private void updatePoolArea(double canvasWidth, double canvasHeight) {
        poolAreaX = canvasWidth - canvasHeight - 20;
        poolAreaY = 50;
        poolAreaHeight = canvasHeight - 100;

        // Reposicionar fichas
        // repositionPoolPieces();
    }

    // Initialize fichas en el pool
    private void initializePiecePool() {
        piecePool.clear();

        // Get color of player
        for (int i = 0; i < maxPoolPieces / 2; i++) {
            addPieceToPool("RED");
            addPieceToPool("YELLOW");
        }
    }

    // Add pieces to the pool
    private void addPieceToPool(String color) {
        double radius = 20;

        double x = poolAreaX + radius + random.nextDouble() * (poolAreaWidth - 2 * radius);
        double y = poolAreaY + radius + random.nextDouble() * (poolAreaHeight - 2 * radius);

        String id = "pool_" + System.currentTimeMillis() + "_" + random.nextInt(10000);
        GameObject piece = new GameObject(id, x, y, radius, -1, -1);
        piece.color = color;

        piecePool.add(piece);
    }

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

    // Verificar si una posición está dentro del pool
    private boolean isPositionInPool(double x, double y) {
        return x >= poolAreaX && x <= poolAreaX + poolAreaWidth &&
                y >= poolAreaY && y <= poolAreaY + poolAreaHeight;
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

        // Primero buscar en el pool de fichas
        for (int i = piecePool.size() - 1; i >= 0; i--) {
            GameObject go = piecePool.get(i);
            double dx = mouseX - go.center_x;
            double dy = mouseY - go.center_y;
            double distancia = Math.sqrt(dx * dx + dy * dy);

            if (distancia <= go.radius) {
                // Crear una copia de la ficha para arrastrar
                selectedObject = new GameObject(go.id, go.center_x, go.center_y, go.radius, go.row, go.col);
                selectedObject.color = go.color;
                mouseDragging = true;
                mouseOffsetX = mouseX - go.center_x;
                mouseOffsetY = mouseY - go.center_y;

                // Generar una nueva ficha en el pool en la misma posición aproximada
                String playerColor = Main.clients.stream()
                        .filter(c -> c.name.equals(Main.clientName))
                        .map(c -> c.color)
                        .findFirst()
                        .orElse("red");

                // Añadir nueva ficha con ligera variación de posición
                double newX = go.center_x + (random.nextDouble() - 0.5) * 40;
                double newY = go.center_y + (random.nextDouble() - 0.5) * 40;
                newX = Math.max(poolAreaX + go.radius, Math.min(poolAreaX + poolAreaWidth - go.radius, newX));
                newY = Math.max(poolAreaY + go.radius, Math.min(poolAreaY + poolAreaHeight - go.radius, newY));

                String newId = "pool_" + System.currentTimeMillis() + "_" + random.nextInt(10000);
                GameObject newPiece = new GameObject(newId, newX, newY, go.radius, -1, -1);
                newPiece.color = playerColor;
                piecePool.add(newPiece);

                return;
            }

        }
    }

    // Función para cuando arrastrar el mouse con la ficha
    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging) {
            double centerX = event.getX() - mouseOffsetX;
            double centerY = event.getY() - mouseOffsetY;

            selectedObject = new GameObject(
                    selectedObject.id,
                    centerX,
                    centerY,
                    selectedObject.radius,
                    selectedObject.row,
                    selectedObject.col);
            selectedObject.color = selectedObject.color;

            JSONObject msg = new JSONObject();
            msg.put("type", "clientPieceMoving"); // Usar el mismo tipo que en el servidor
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
            double centerX = event.getX() - mouseOffsetX; // left tip X
            double centerY = event.getY() - mouseOffsetY; // left tip Y

            // build object with dragged position (size stays in col/row)
            selectedObject = new GameObject(
                    selectedObject.id,
                    centerX,
                    centerY,
                    selectedObject.radius,
                    selectedObject.col,
                    selectedObject.row);
            selectedObject.color = selectedObject.color;

            // snap by left-top corner to underlying cell
            if (grid.isPositionInsideGrid(centerX, centerY)) {
                snapObjectCenter(selectedObject);
            }

            // Enviar missatge al servidor
            JSONObject msg = new JSONObject();
            msg.put("type", "clientPieceMoving");
            msg.put("value", selectedObject.toJSON());
            if (Main.wsClient != null)
                Main.wsClient.safeSend(msg.toString());

            mouseDragging = false;
            selectedObject = null;
        }
    }

    // Snap piece so its left-top corner sits exactly on the grid cell under its
    // left tip.
    private void snapObjectCenter(GameObject obj) {
        int col = grid.getCol(obj.center_x); // centerX -> columna
        int row = grid.getRow(obj.center_y); // centerY -> fila

        // mantener dentro del grid
        col = (int) Math.max(0, Math.min(col, grid.getCols() - 1));
        row = (int) Math.max(0, Math.min(row, grid.getRows() - 1));

        // Centrar el círculo en la celda
        double cellSize = grid.getCellSize();
        obj.center_x = grid.getCellX(col) + cellSize / 2;
        obj.center_y = grid.getCellY(row) + cellSize / 2;

        // Guardar posición de celda
        obj.col = col;
        obj.row = row;
    }

    // Función validación si el objeto se encuentra dentro de la celda
    public Boolean isPositionInsideObject(double positionX, double positionY, int objX, int objY, int cols, int rows) {
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

        // Update objects and animations here
    }

    // Draw game to canvas
    // Dibujar celdas con background azul, interior blanco y borde en gris
    public void draw() {

        if (Main.clients == null) {
            return;
        }

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw pool area background (ovalado y colorido)
        // Crear un gradiente radial colorido
        double centerPoolX = poolAreaX + poolAreaWidth / 2;
        double centerPoolY = poolAreaY + poolAreaHeight / 2;

        // Gradiente de colores vibrantes
        gc.setFill(Color.rgb(255, 200, 100)); // Naranja claro
        gc.fillOval(poolAreaX, poolAreaY, poolAreaWidth, poolAreaHeight);

        // Añadir un círculo interno más claro para efecto de profundidad
        gc.setFill(Color.rgb(255, 230, 150, 0.7)); // Más claro y translúcido
        gc.fillOval(poolAreaX + 20, poolAreaY + 20, poolAreaWidth - 40, poolAreaHeight - 40);

        // Borde del óvalo
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
            if (selectedObject != null && go.id.equals(selectedObject.id)) {
                drawObject(selectedObject);
            } else {
                drawObject(go);
            }
        }

        // Draw pool pieces
        for (GameObject piece : piecePool) {
            drawObject(piece);
        }

        // Draw selected object on top
        if (selectedObject != null && mouseDragging) {
            drawObject(selectedObject);
        }

        // Draw grid
        drawGrid();

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

        // Dibujar los círculos grises (agujeros) en cada celda
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellX = startX + col * cellSize;
                double cellY = startY + row * cellSize;

                // Centro de la celda
                double centerX = cellX + cellSize / 2;
                double centerY = cellY + cellSize / 2;

                // Radio del círculo (un poco más pequeño que la celda para dejar margen)
                double holeRadius = cellSize * 0.45;

                // Dibujar el círculo blanco (agujero)
                gc.setFill(Color.GRAY);
                gc.fillOval(centerX - holeRadius, centerY - holeRadius, holeRadius * 2, holeRadius * 2);
            }
        }

        // Dibujar los círculos blancos (agujeros) en cada celda
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellX = startX + col * cellSize;
                double cellY = startY + row * cellSize;

                // Centro de la celda
                double centerX = cellX + cellSize / 2;
                double centerY = cellY + cellSize / 2;

                // Radio del círculo (un poco más pequeño que la celda para dejar margen)
                double holeRadius = cellSize * 0.4;

                // Dibujar el círculo blanco (agujero)
                gc.setFill(Color.WHITE);
                gc.fillOval(centerX - holeRadius, centerY - holeRadius, holeRadius * 2, holeRadius * 2);
            }
        }

        // Opcional: Dibujar borde del tablero
        gc.setStroke(Color.DARKBLUE);
        gc.setLineWidth(3);
        gc.strokeRect(startX, startY, gridWidth, gridHeight);
    }

    // Dibujar fichas
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
