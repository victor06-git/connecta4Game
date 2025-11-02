package com.connect4;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONObject;

import com.connect4.ctrlPlay.ColorUtils;
import com.connect4.ctrlPlay.DrawUtils;
import com.connect4.ctrlPlay.GameLogicUtils;
import com.connect4.ctrlPlay.PieceAnimationManager;
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
    private double animationSpeed = 300; // Velocidad animación (reducida para caída más lenta)

    // Zona del tablero para dejar caer la ficha
    private double dropZoneHeight = 40;
    private int hoveredColumn = -1;

    // Mapa para trackear la columna hover de cada cliente
    private Map<String, Integer> clientHoveredColumns = new HashMap<>();

    // pool (mesa donde estan las fichas)
    private double poolX, poolY, poolWidth, poolHeight;
    private static final double BOARD_POOL_GAP = 50;
    private static final double FIXED_CELL_SIZE = 80;
    private static final double LEFT_MARGIN = 50;

    // Winner variables
    private int[] winningLineCoords = null;
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
    private GameLogicUtils logic = new GameLogicUtils();
    private PieceAnimationManager anim = new PieceAnimationManager();

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

        // NOTE: gameObjectsMap will be populated later via initializeGameObjects()
        // when Main.objects is populated by the server
    }

    /**
     * Initialize game objects map after Main.objects is populated by server
     * This must be called after objects are received from server
     */
    public void initializeGameObjects() {
        // Only initialize if map is empty to avoid redundant initialization
        if (!gameObjectsMap.isEmpty()) {
            return;
        }

        gameObjectsMap.clear();
        originalPoolPositions.clear();

        // Ensure pool dimensions are up-to-date so we can position pieces over the pool
        updatePoolDimensions();

        // Layout pieces inside the pool area with a small right offset so they appear
        // slightly more to the right (over the pool). We'll arrange them in rows.
        double startX = poolX + 20; // padding from left edge of pool
        double startY = poolY + 20; // padding from top edge of pool
        double spacing = Math.min(45, FIXED_CELL_SIZE * 0.5); // spacing between pieces
        int piecesPerRow = Math.max(1, (int) ((poolWidth - 40) / spacing));

        int idx = 0;
        for (GameObject obj : Main.objects) {
            // Only reposition pieces that are in the pool (not placed on board)
            if (obj.row == -1 && obj.col == -1) {
                int colIndex = idx % piecesPerRow;
                int rowIndex = idx / piecesPerRow;

                double newCenterX = startX + colIndex * spacing;
                double newCenterY = startY + rowIndex * spacing;

                // apply a small extra right offset so they are "over" the pool visually
                double extraRight = 10; // tweak this value to move more/less to the right
                obj.center_x = newCenterX + extraRight;
                obj.center_y = newCenterY;

                // store the computed original pool position
                originalPoolPositions.put(obj.id,
                        new GameObject(obj.id, obj.center_x, obj.center_y, obj.radius, obj.col, obj.row));
                gameObjectsMap.put(obj.id, obj);

                idx++;
            } else {
                // Keep objects that are already placed on board as-is
                originalPoolPositions.put(obj.id,
                        new GameObject(obj.id, obj.center_x, obj.center_y, obj.radius, obj.col, obj.row));
                gameObjectsMap.put(obj.id, obj);
            }
        }

    }

    // Para evitar spam en consola
    private String lastLoggedState = "";

    /**
     * Function that updates the boardState matrix
     * 
     * @param state
     */
    public void updateGameState(JSONObject state) {
        // Delegate to GameLogicUtils to update boardState and obtain current turn
        currentTurn = logic.updateGameState(state, boardState);
        // Ensure local myColor is in sync with Main.myColor
        myColor = Main.myColor;

        // DEBUG: Solo mostrar cuando hay cambios importantes (turno cambia o número de
        // clientes cambia)
        String currentState = myColor + "|" + currentTurn + "|" + Main.clients.size();
        if (!currentState.equals(lastLoggedState)) {
            System.out.println("🎨 updateGameState - myColor: " + myColor + " | currentTurn: " + currentTurn);
            System.out.println("📋 Clientes conectados: " + Main.clients.size());
            for (ClientData client : Main.clients) {
                System.out.println("  - " + client.name + " (" + client.color + ")");
            }

            // Advertencia si solo hay 1 jugador
            if (Main.clients.size() < 2) {
                System.out.println("⚠️ WARNING: Se necesitan 2 jugadores para comenzar la partida!");
            }

            lastLoggedState = currentState;
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
        // Delegate logic to GameLogicUtils
        GameObject piece = logic.handlePlayAccepted(pieceId, col, row, winner, winningLineCoords, boardState,
                gameObjectsMap);

        if (piece != null) {
            selectedObject = piece;
            // Initialize drop animation via logic helper (it returns target Y)
            animationTargetY = anim.startDropAnimation(piece, col, row, grid);
            isAnimating = true;
        }

        // Actualizar información del ganador si existe
        if (winner != null) {
            this.winnerColor = winner;
            this.winningLineCoords = winningLineCoords;
            this.gameEnded = !winner.equals("DRAW");
            // Notify server that game ended (so server can broadcast or take action)
            try {
                JSONObject endMsg = new JSONObject();
                endMsg.put("type", "clientGameEnded");
                endMsg.put("winner", winner);
                endMsg.put("gameEnded", this.gameEnded);
                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(endMsg.toString());
                }
            } catch (Exception e) {
                System.out.println("Error building clientGameEnded message: " + e.getMessage());
            }
        }
    }

    /**
     * Function that handles if the current action is rejected for the server
     * 
     * @param pieceId
     */
    public void handlePlayRejected(String pieceId) {
        // Delegate rejection handling to GameLogicUtils which can return updated
        // selection
        GameObject rejectedPiece = selectedObject;
        selectedObject = logic.handlePlayRejected(pieceId, selectedObject, originalPoolPositions);
        mouseDragging = false;
        hoveredColumn = -1;

        // Limpiar también selectedObject si fue rechazada
        if (rejectedPiece != null && rejectedPiece.id.equals(pieceId)) {
            selectedObject = null;
        }
    }

    /**
     * Update hover column for other clients
     * 
     * @param clientName
     * @param column
     */
    public void setOtherClientHover(String clientName, int column) {
        clientHoveredColumns.put(clientName, column);
    }

    /**
     * Clear hover column for a specific client
     * 
     * @param clientName
     */
    public void clearOtherClientHover(String clientName) {
        clientHoveredColumns.remove(clientName);
    }

    /**
     * Function to return the piece to its original position in the pool
     * 
     * @param piece
     */
    private void returnPieceToOriginalPosition(GameObject piece) {
        logic.returnPieceToOriginalPosition(piece, originalPoolPositions);
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
        return logic.isPositionInDropZone(x, y, grid, dropZoneHeight);
    }

    /**
     * Function to get the column index based on x position in drop zone
     * 
     * @param x
     * @return
     */
    private int getDropZoneColumn(double x) {
        return logic.getDropZoneColumn(x, grid);
    }

    /**
     * Function that verify if the player can move a piece (ficha)
     * 
     * @param piece
     * @return
     */
    private boolean canMoveThisPiece(GameObject piece) {
        return logic.canMoveThisPiece(piece, myColor, currentTurn, Main.clientName);
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

        // Usar Main.myColor en lugar de buscar en clients
        String color = Main.myColor.isEmpty() ? logic.getMyColor(Main.clientName) : Main.myColor;

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
        double mouseX = event.getX(); // Get mouse X position
        double mouseY = event.getY(); // Get mouse Y position

        selectedObject = null; // Reset selected object
        mouseDragging = false; // Reset dragging state

        double correctRadius = grid.getCellSize() * 0.40; // Smaller than half cell size

        for (GameObject go : Main.objects) {
            if (go.col == -1 && go.row == -1) {
                // Check if mouse is inside piece circle
                if (isMouseInsidePiece(mouseX, mouseY, go.center_x, go.center_y, correctRadius)) {
                    if (!canMoveThisPiece(go)) {
                        System.out.println("Cannot move piece " + go.id + " - not your turn or not your piece");
                        return;
                    }

                    selectedObject = go; // Select the piece
                    mouseDragging = true; // Start dragging
                    mouseOffsetX = mouseX - go.center_x; // Calculate offset to center the piece under the mouse
                    mouseOffsetY = mouseY - go.center_y; // Calculate offset to center the piece under the mouse

                    System.out.println("Selected piece: " + go.id); // R_4 / Y_2 etc.
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
    private boolean isMouseInsidePiece(double mouseX, double mouseY, double centerX, double centerY, double radius) {
        return logic.isMouseInsidePiece(mouseX, mouseY, centerX, centerY, radius);
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
            msg.put("hoveredColumn", hoveredColumn); // Enviar columna hover

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

                    // Enviar que ya no hay hover
                    JSONObject clearHoverMsg = new JSONObject();
                    clearHoverMsg.put("type", "clientPieceMoving");
                    clearHoverMsg.put("value", selectedObject.toJSON());
                    clearHoverMsg.put("hoveredColumn", -1);
                    if (Main.wsClient != null) {
                        Main.wsClient.safeSend(clearHoverMsg.toString());
                    }

                    // NO limpiar selectedObject aquí - esperamos la respuesta del servidor
                    return;
                }
            }

            // If not valid drop, return to pool position
            // IMPORTANTE: Devolver la pieza a su posición original del pool
            returnPieceToOriginalPosition(selectedObject);
            selectedObject = null;
            mouseDragging = false;
            hoveredColumn = -1;
        }
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
            System.out.println("🎮 ANIMATING: " + selectedObject.id + " | Y: " + selectedObject.center_y
                    + " | Target: " + animationTargetY + " | FPS: " + fps);
            boolean continueAnim = anim.updateAnimation(selectedObject, animationTargetY, animationSpeed, fps);
            if (!continueAnim) {
                // Animación terminada
                System.out.println("✅ ANIMATION FINISHED for " + selectedObject.id);

                // Eliminar la ficha de Main.objects para que no se pueda mover más
                String pieceId = selectedObject.id;
                Main.objects.removeIf(go -> go.id.equals(pieceId));
                gameObjectsMap.remove(pieceId);
                System.out.println("🗑️ Removed piece from pool: " + pieceId);

                // Limpiar estado de animación
                // La ficha ya se dibujará desde boardState en drawBoardPieces()
                isAnimating = false;
                selectedObject = null;
            }
        }
    }

    /**
     * Check if a piece ID is already placed on the board
     * 
     * @param pieceId
     * @return true if the piece is in boardState
     */
    private boolean isPieceInBoardState(String pieceId) {
        for (int row = 0; row < boardState.length; row++) {
            for (int col = 0; col < boardState[row].length; col++) {
                if (pieceId.equals(boardState[row][col])) {
                    return true;
                }
            }
        }
        return false;
    }

    // Expose fields so other controllers or Main can read them (avoid unused
    // warnings)
    public boolean isGameEnded() {
        return this.gameEnded;
    }

    /**
     * Get the color of the winning player
     * 
     * @return winner color
     */
    public String getWinnerColor() {
        return this.winnerColor;
    }

    // Draw game to canvas
    // Dibujar celdas con background azul, interior blanco y borde en gris
    /**
     * Function to draw the game
     * 
     */
    public void draw() {

        if (Main.clients == null || Main.clients.isEmpty()) {
            return;
        }

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        drawUtils.drawTurnIndicator(gc, currentTurn, myColor, Main.clientName);

        drawUtils.drawDropZone(gc, grid, dropZoneHeight, hoveredColumn, clientHoveredColumns,
                Main.clients, Main.clientName, utils);

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
                CycleMethod.REFLECT, // Cambiar a no_cycle
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

        // Crear una copia snapshot para evitar ConcurrentModificationException
        List<GameObject> objectsSnapshot = new ArrayList<>(Main.objects);

        GameObject currentSelected = selectedObject; // Capturar referencia local
        boolean currentAnimating = isAnimating; // Capturar estado local
        boolean currentDragging = mouseDragging; // Capturar estado de arrastre

        // Draw pieces from pool (not placed on board)
        for (GameObject go : objectsSnapshot) {
            // Solo dibujar fichas que están en el pool (row=-1, col=-1)
            // Y además verificar que NO estén en boardState (por si el servidor las envía
            // con row/col)
            if (go.row == -1 && go.col == -1 && !isPieceInBoardState(go.id)) {
                // Skip selected object during animation/dragging - will be drawn on top
                if (currentSelected != null && go.id.equals(currentSelected.id)) {
                    if (currentAnimating || currentDragging) {
                        continue; // Skip: will be drawn on top for visibility
                    }
                }
                drawUtils.drawObject(go, gc, grid, utils);
            }
        }

        // Draw selected/animating piece on top for visibility
        if (currentSelected != null) {
            drawUtils.drawObject(currentSelected, gc, grid, utils);
        }

        // Draw pieces placed on the board from boardState
        // Pass the ID of the animating piece so it's not drawn twice
        String animatingPieceId = (currentSelected != null && currentAnimating) ? currentSelected.id : null;
        drawUtils.drawBoardPieces(gc, boardState, grid, utils, animatingPieceId);

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

    /**
     * Reset the board and all game state to start a new game
     * Preserves client names but resets everything else
     */
    public void resetBoard() {
        System.out.println("🔄 Reseteando tablero...");

        // Stop any animations
        isAnimating = false;
        selectedObject = null;
        mouseDragging = false;
        hoveredColumn = -1;

        // Clear board state
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 7; j++) {
                boardState[i][j] = null;
            }
        }

        // Reset game variables
        gameEnded = false;
        winnerColor = null;
        winningLineCoords = null;
        currentTurn = "";

        // Sincronizar myColor con Main.myColor
        myColor = Main.myColor;
        System.out.println("🎨 Color sincronizado: " + myColor + " (clientName: " + Main.clientName + ")");

        // Clear hover columns
        clientHoveredColumns.clear();

        // Reset all pieces to original pool positions
        for (GameObject obj : Main.objects) {
            GameObject original = originalPoolPositions.get(obj.id);
            if (original != null) {
                obj.center_x = original.center_x;
                obj.center_y = original.center_y;
                obj.col = -1;
                obj.row = -1;
            }
        }

        // Clear gameObjectsMap to allow re-initialization
        gameObjectsMap.clear();

        System.out.println("✅ Reset completado");
    }

}