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
    private GameObject animatingPiece = null;
    private double animationTargetY = 0;
    private double animationSpeed = 500;

    // Zona del tablero para dejar caer la ficha
    private double dropZoneHeight = 30;
    private int hoveredColumn = -1;

    // pool (mesa donde estan las fichas)
    private double poolX, poolY, poolWidth, poolHeight;
    private static final double BOARD_POOL_GAP = 50;
    private static final double FIXED_CELL_SIZE = 80;
    private static final double LEFT_MARGIN = 50;
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
        double startX = LEFT_MARGIN;
        double startY = dropZoneHeight + 50;
        grid = new PlayGrid(startX, startY, FIXED_CELL_SIZE, 6, 7);

        updatePoolDimensions();

        // Start run/draw timer bucle
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    // When window changes its size
    public void onSizeChanged() {

        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();

        // Calcular tamaño mínimo necesario
        double minWidth = LEFT_MARGIN + (7 * FIXED_CELL_SIZE) + BOARD_POOL_GAP + 200 + 50; // tablero + gap + pool +
                                                                                           // margen
        double minHeight = dropZoneHeight + 50 + (6 * FIXED_CELL_SIZE) + 50; // dropZone + margen + tablero + margen

        // Aplicar tamaño mínimo
        width = Math.max(width, minWidth);
        height = Math.max(height, minHeight);

        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // Calcular dimensiones del pool
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
        javafx.scene.paint.LinearGradient woodGradient = new javafx.scene.paint.LinearGradient(
                0, 0, 0, 1, true,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.rgb(139, 90, 43)), // Marrón medio
                new javafx.scene.paint.Stop(1, Color.rgb(120, 80, 40)) // Marrón más oscuro
        );

        // Dibujar pool con dimensiones adaptativas
        gc.setFill(woodGradient);
        gc.fillRect(poolX, poolY, poolWidth, poolHeight);

        // Borde exterior simple
        gc.setStroke(Color.rgb(80, 50, 20));
        gc.setLineWidth(3);
        gc.strokeRect(poolX, poolY, poolWidth, poolHeight);

        // Draw objects (fichas en el tablero)
        for (GameObject go : Main.objects) {
            // Saltar la ficha que está siendo arrastrada o animándose
            if (selectedObject != null && go.id.equals(selectedObject.id))
                continue;
            if (animatingPiece != null && go.id.equals(animatingPiece.id))
                continue;

            drawObject(go);
        }

        // Draw animating piece
        if (animatingPiece != null) {
            drawObject(animatingPiece);
        }

        // Draw grid
        drawGrid();

        // Draw selected object on top
        if (selectedObject != null && mouseDragging) {
            drawObject(selectedObject);
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
        double radius = (grid.getCellSize() / 2) * 0.9;

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

        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Dibuixar el contorn
        gc.setStroke(getColor("gray"));
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
