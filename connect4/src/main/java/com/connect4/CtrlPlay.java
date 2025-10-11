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

        for (GameObject go : Main.objects) {
            // Verificar si el clic está dentro del círculo usando distancia euclidiana
            double dx = mouseX - go.center_x;
            double dy = mouseY - go.center_y;
            double distancia = Math.sqrt(dx * dx + dy * dy);

            if (distancia <= go.radius) {
                selectedObject = new GameObject(go.id, go.center_x, go.center_y, go.radius, go.row, go.col);
                mouseDragging = true;
                mouseOffsetX = mouseX - go.center_x;
                mouseOffsetY = mouseY - go.center_y;
                break;
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

        // Draw grid
        drawGrid();

        // Draw mouse circles
        for (ClientData clientData : Main.clients) {
            gc.setFill(getColor(clientData.color));
            gc.fillOval(clientData.mouseX - 5, clientData.mouseY - 5, 20, 20);
        }

        // Draw objects
        for (GameObject go : Main.objects) {
            if (selectedObject != null && go.id.equals(selectedObject.id)) {
                drawObject(selectedObject);
            } else {
                drawObject(go);
            }
        }

        // Draw FPS if needed
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
    }

    // Dibuja la celda
    public void drawGrid() {
        gc.setStroke(Color.BLACK);

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellSize = grid.getCellSize();
                double x = grid.getStartX() + col * cellSize;
                double y = grid.getStartY() + row * cellSize;
                gc.strokeRect(x, y, cellSize, cellSize);
            }
        }
    }

    // Dibujar fichas
    public void drawObject(GameObject obj) {
        double centerX = obj.center_x;
        double centerY = obj.center_y;
        double radius = obj.radius;

        // Seleccionar un color basat en l'objectId
        Color color = Color.RED;

        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Dibuixar el contorn
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(2);
        gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Opcionalment, afegir text (per exemple, l'objectId)
        // gc.setFill(Color.YELLOW);
        // gc.setFont(new Font(12));
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
