package com.connect4;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import com.connect4.ctrlPlay.ColorUtils;
import com.connect4.ctrlPlay.DrawUtils;
import com.shared.ClientData;
import com.shared.GameObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

public class CtrlPlay implements Initializable {

    @FXML
    public Label title;

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
    private boolean gameEnded = false;
    private String winnerColor = null;

    // Matriz de las posiciones de las fichas, se inicializa null
    public String[][] boardState = new String[6][7];
    private String currentTurn = ""; // Actual turn
    private String myColor = "";

    private Map<String, GameObject> gameObjectsMap = new HashMap<>();
    private Map<String, GameObject> originalPoolPositions = new HashMap<>(); // To have the original position of every
                                                                             // piece and return to it if needed
    private ColorUtils utils = new ColorUtils(); // utils.getColor function
    private DrawUtils drawUtils = new DrawUtils();

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

        for (GameObject obj : Main.objects) {
            originalPoolPositions.put(obj.id,
                    new GameObject(obj.id, obj.center_x, obj.center_y, obj.radius, obj.col, obj.row));
            gameObjectsMap.put(obj.id, obj);
        }
    }

    /**
     * Function that updates the boardState matrix
     * 
     * @param state
     */
    public void updateGameState(JSONObject state) {
        currentTurn = state.optString("currentTurn", "RED");

        if (state.has("boardState")) {
            JSONArray boardArray = state.getJSONArray("boardState");
            for (int i = 0; i < 6; i++) {
                JSONArray row = boardArray.getJSONArray(i);
                for (int j = 0; j < 7; j++) {
                    Object cell = row.get(j);
                    boardState[i][j] = cell == JSONObject.NULL ? null : (String) cell;
                }
            }
        }
    }

    /**
     * Function that handles from the server if the current action is accepted
     * 
     * @param pieceId
     * @param col
     * @param row
     * @param winner
     * @param winningLineCoords
     */
    public void handlePlayAccepted(String pieceId, int col, int row, String winner, int[] winningLineCoords) {
        boardState[row][col] = pieceId;

        GameObject piece = gameObjectsMap.get(pieceId);
        if (piece != null) {
            piece.row = row;
            piece.col = col;
            selectedObject = piece;
            startDropAnimation(col, row);
        }

        // Actualizar información del ganador si existe
        if (winner != null) {
            this.winnerColor = winner;
            this.winningLineCoords = winningLineCoords;
            this.gameEnded = !winner.equals("DRAW");
        }
    }

    /**
     * Function that handles if the current action is rejected for the server
     * 
     * @param pieceId
     */
    public void handlePlayRejected(String pieceId) {
        if (selectedObject != null && selectedObject.id.equals(pieceId)) {
            // Devolver a posición original
            returnPieceToOriginalPosition(selectedObject);
            selectedObject = null;
        }
        mouseDragging = false;
        hoveredColumn = -1;
    }

    /**
     * Function to return the piece to its original position in the pool
     * 
     * @param piece
     */
    private void returnPieceToOriginalPosition(GameObject piece) {
        if (originalPoolPositions.containsKey(piece.id)) {
            GameObject original = originalPoolPositions.get(piece.id);
            piece.center_x = original.center_x;
            piece.center_y = original.center_y;
            piece.col = -1;
            piece.row = -1;
        }
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
     * Function that verify if the player can move a piece (ficha)
     * 
     * @param piece
     * @return
     */
    private boolean canMoveThisPiece(GameObject piece) {
        if (myColor.isEmpty()) {
            myColor = Main.clients.stream()
                    .filter(c -> c.name.equals(Main.clientName))
                    .map(c -> c.color)
                    .findFirst()
                    .orElse("");
        }

        // Debug: mostrar información
        System.out.println("My color: " + myColor + ", Current turn: " + currentTurn + ", Piece: " + piece.id);

        // Verificar que sea mi turno y que la pieza sea de mi color
        boolean isMyTurn = currentTurn.equals(myColor);
        boolean isMyPiece = piece.id.startsWith(myColor.charAt(0) + "_");

        return isMyTurn && isMyPiece;
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
                    if (!canMoveThisPiece(go)) {
                        System.out.println("Cannot move piece " + go.id + " - not your turn or not your piece");
                        return;
                    }

                    selectedObject = go; // Seleccionar la ficha
                    // originalX = go.center_x;
                    // originalY = go.center_y;
                    mouseDragging = true;
                    mouseOffsetX = mouseX - go.center_x;
                    mouseOffsetY = mouseY - go.center_y;

                    System.out.println("Selected piece: " + go.id);
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

            for (GameObject go : Main.objects) {
                if (go.id.equals(selectedObject.id)) {
                    go.center_x = centerX;
                    go.center_y = centerY;
                    break;
                }
            }

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

                if (col != -1) {
                    // Enviar jugada al servidor
                    JSONObject msg = new JSONObject();
                    msg.put("type", "clientRequestPlay");
                    msg.put("pieceId", selectedObject.id);
                    msg.put("column", col);

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
            returnPieceToOriginalPosition(selectedObject);
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

                for (GameObject go : Main.objects) {
                    if (go.id.equals(selectedObject.id)) {
                        go.center_x = selectedObject.center_x;
                        go.center_y = selectedObject.center_y;
                        break;
                    }
                }

                if (selectedObject.center_y >= animationTargetY) {
                    selectedObject.center_y = animationTargetY;

                    isAnimating = false;
                    selectedObject = null;
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

        drawUtils.drawTurnIndicator(gc, currentTurn, myColor, Main.clientName);

        drawUtils.drawDropZone(gc, grid, dropZoneHeight, hoveredColumn, utils);

        // Draw colored 'over' cells
        for (ClientData clientData : Main.clients) {
            // Comprovar si està dins dels límits de la graella
            if (clientData.row >= 0 && clientData.col >= 0) {
                Color base = utils.getColor(clientData.color);
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
        drawUtils.drawBoard(gc, grid, utils);

        // Draw pieces of pool (non-selected)
        for (GameObject go : Main.objects) {
            if (go.row == -1 && go.col == -1) {
                // Saltar la ficha que está siendo arrastrada o animándose
                if (selectedObject != null && go.id.equals(selectedObject.id))
                    continue;
                drawUtils.drawObject(go, gc, grid, utils);
            }
        }

        // Draw piece on client (selected or animating)
        // if (selectedObject != null) {
        // drawObject(selectedObject);
        // }

        drawUtils.drawBoardPieces(gc, boardState, grid, utils);

        if (winningLineCoords != null) {
            drawUtils.drawWinningCircles(gc, winningLineCoords, grid);
        }

        // Draw mouse circles (Consigue el color de clients)
        for (ClientData clientData : Main.clients) {
            gc.setFill(utils.getColor(clientData.color));
            gc.fillOval(clientData.mouseX - 5, clientData.mouseY - 5, 20, 20);
        }

        // Draw FPS if needed
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
    }

}