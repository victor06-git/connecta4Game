package com.connect4;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONObject;

import com.connect4.game.GameObjectManager;
import com.connect4.game.GameRenderer;
import com.connect4.game.GameState;
import com.connect4.game.MouseInputHandler;
import com.connect4.game.PieceAnimationManager;
import com.shared.ClientData;
import com.shared.GameObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

/**
 * Controlador principal del juego Connect 4.
 * Orquesta los diferentes componentes del juego y maneja la comunicación con el
 * servidor.
 */
public class CtrlPlay implements Initializable {

    @FXML
    public Label title;

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;

    private PlayTimer animationTimer;
    private PlayGrid grid;

    // Componentes del juego
    private GameState gameState;
    private GameObjectManager objectManager;
    private PieceAnimationManager animationManager;
    private MouseInputHandler mouseHandler;
    private GameRenderer renderer;

    // Constantes de diseño
    private static final double BOARD_POOL_GAP = 50;
    private static final double FIXED_CELL_SIZE = 80;
    private static final double LEFT_MARGIN = 50;
    private static final double DROP_ZONE_HEIGHT = 40;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.gc = canvas.getGraphicsContext2D();

        // Inicializar componentes del juego
        gameState = new GameState();
        objectManager = new GameObjectManager();
        animationManager = new PieceAnimationManager();
        mouseHandler = new MouseInputHandler(objectManager, gameState);

        // Configurar grid
        double startX = LEFT_MARGIN;
        double startY = DROP_ZONE_HEIGHT + 50;
        grid = new PlayGrid(startX, startY, FIXED_CELL_SIZE, 6, 7);

        // Inicializar renderer
        renderer = new GameRenderer(gc, grid, gameState, objectManager);

        // Inicializar objetos del juego
        objectManager.initialize(Main.objects);

        // Configurar listeners de tamaño
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> {
            onSizeChanged();
        });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> {
            onSizeChanged();
        });

        // Configurar listeners de mouse
        canvas.setOnMouseMoved(this::onMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        // Iniciar timer de animación
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    /**
     * Obtiene el estado actual del tablero. (Getter)
     * 
     * @return Matriz 6x7 con el estado del tablero
     */
    public String[][] getBoardState() {
        return gameState.getBoardState();
    }

    /**
     * Actualiza el estado del juego desde el servidor.
     */
    public void updateGameState(JSONObject state) {
        gameState.updateFromServer(state);
    }

    /**
     * Maneja cuando el servidor acepta una jugada.
     */
    public void handlePlayAccepted(String pieceId, int col, int row, String winner, int[] winningLineCoords) {
        gameState.placePiece(row, col, pieceId);
        objectManager.updatePiecePosition(pieceId, row, col);

        GameObject piece = objectManager.getObject(pieceId);
        if (piece != null) {
            startDropAnimation(piece, col, row);
        }

        if (winner != null) {
            gameState.setWinner(winner, winningLineCoords);
        }
    }

    /**
     * Maneja cuando el servidor rechaza una jugada.
     */
    public void handlePlayRejected(String pieceId) {
        GameObject piece = objectManager.getObject(pieceId);
        if (piece != null) {
            objectManager.returnPieceToOriginalPosition(piece);
        }
        mouseHandler.cancelSelection();
        gameState.setHoveredColumn(-1);
    }

    /**
     * Inicia la animación de caída de una ficha.
     */
    private void startDropAnimation(GameObject piece, int col, int row) {
        double targetX = grid.getCellX(col) + grid.getCellSize() / 2;
        double targetY = grid.getCellY(row) + grid.getCellSize() / 2;
        animationManager.startAnimation(piece, targetX, targetY);
    }

    /**
     * Llamado cuando cambia el tamaño de la ventana.
     */
    public void onSizeChanged() {
        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();

        double minWidth = LEFT_MARGIN + (7 * FIXED_CELL_SIZE) + BOARD_POOL_GAP + 200 + 50;
        double minHeight = DROP_ZONE_HEIGHT + 50 + (6 * FIXED_CELL_SIZE) + 50;

        width = Math.max(width, minWidth);
        height = Math.max(height, minHeight);

        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // ==================== Eventos del mouse ====================

    private void onMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        // Actualizar columna hover si no hay animación activa
        if (!animationManager.hasActiveAnimations()) {
            int col = getDropZoneColumn(mouseX, mouseY);
            gameState.setHoveredColumn(col);
        }

        // Enviar posición del mouse al servidor
        sendMousePosition(mouseX, mouseY);
    }

    private void onMousePressed(MouseEvent event) {
        if (mouseHandler.handleMousePressed(event)) {
            System.out.println("Selected piece: " + mouseHandler.getSelectedPiece().id);
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (mouseHandler.handleMouseDragged(event)) {
            // Actualizar columna hover
            int col = getDropZoneColumn(event.getX(), event.getY());
            gameState.setHoveredColumn(col);

            // Enviar ficha en movimiento al servidor
            sendPieceMoving(mouseHandler.getSelectedPiece());
        }
        onMouseMoved(event);
    }

    private void onMouseReleased(MouseEvent event) {
        mouseHandler.handleMouseReleased(event, (pieceId, column) -> {
            // Callback: enviar jugada al servidor
            JSONObject msg = new JSONObject();
            msg.put("type", "clientPlay");
            msg.put("pieceId", pieceId);
            msg.put("col", column);

            if (Main.wsClient != null) {
                Main.wsClient.safeSend(msg.toString());
            }

            System.out.println("Play sent: piece=" + pieceId + ", col=" + column);
        });

        gameState.setHoveredColumn(-1);
    }

    /**
     * Obtiene la columna de la drop zone basada en la posición del mouse.
     */
    private int getDropZoneColumn(double mouseX, double mouseY) {
        double gridStartX = grid.getStartX();
        double gridEndX = gridStartX + (grid.getCols() * grid.getCellSize());
        double dropZoneStartY = grid.getStartY() - DROP_ZONE_HEIGHT;
        double dropZoneEndY = grid.getStartY();

        if (mouseX >= gridStartX && mouseX <= gridEndX &&
                mouseY >= dropZoneStartY && mouseY <= dropZoneEndY) {
            int col = (int) ((mouseX - gridStartX) / grid.getCellSize());
            return Math.max(0, Math.min(col, grid.getCols() - 1));
        }
        return -1;
    }

    // ==================== Comunicación WebSocket ====================

    private void sendMousePosition(double mouseX, double mouseY) {
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

    private void sendPieceMoving(GameObject piece) {
        JSONObject msg = new JSONObject();
        msg.put("type", "clientPieceMoving");
        msg.put("value", piece.toJSON());

        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msg.toString());
        }
    }

    // ==================== Ciclo de juego ====================

    public void start() {
        animationTimer.start();
    }

    public void stop() {
        animationTimer.stop();
    }

    private void run(double fps) {
        // Actualizar animaciones
        animationManager.updateAnimations(objectManager);
    }

    private void draw() {
        // Limpiar canvas
        renderer.clear(canvas.getWidth(), canvas.getHeight());

        // Dibujar indicador de turno
        renderer.drawTurnIndicator();

        // Dibujar drop zone
        renderer.drawDropZone();

        // Dibujar celdas hover de otros jugadores
        for (ClientData clientData : Main.clients) {
            if (clientData.row >= 0 && clientData.col >= 0) {
                renderer.drawOverCell(clientData.row, clientData.col, clientData.color);
            }
        }

        // Dibujar pool de fichas
        renderer.drawPool();

        // Dibujar tablero
        renderer.drawBoard();

        // Dibujar fichas del pool (excepto la seleccionada)
        String selectedId = mouseHandler.hasSelectedPiece() ? mouseHandler.getSelectedPiece().id : null;
        renderer.drawPoolPieces(selectedId);

        // Dibujar fichas del tablero
        renderer.drawBoardPieces();

        // Dibujar línea ganadora
        renderer.drawWinningLine();

        // Dibujar cursores de mouse de los jugadores
        for (ClientData clientData : Main.clients) {
            renderer.drawMouseCursor(clientData.mouseX, clientData.mouseY, clientData.color);
        }

        // Dibujar FPS si está habilitado
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
    }

}
