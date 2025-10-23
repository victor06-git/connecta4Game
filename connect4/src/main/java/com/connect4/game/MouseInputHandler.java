package com.connect4.game;

import com.shared.GameObject;
import javafx.scene.input.MouseEvent;

/**
 * Gestiona todos los eventos del mouse durante el juego.
 * Controla la selección, arrastre y colocación de fichas.
 */
public class MouseInputHandler {

    private static final double PIECE_RADIUS = 20.0;
    private static final double BOARD_LEFT = 200.0;
    private static final double CELL_SIZE = 60.0;
    private static final int BOARD_COLS = 7;

    private GameObject selectedPiece = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    private final GameObjectManager objectManager;
    private final GameState gameState;

    /**
     * Constructor del manejador de entrada del mouse.
     * 
     * @param objectManager Gestor de objetos del juego
     * @param gameState     Estado del juego
     */
    public MouseInputHandler(GameObjectManager objectManager, GameState gameState) {
        this.objectManager = objectManager;
        this.gameState = gameState;
    }

    /**
     * Maneja el evento de mouse presionado.
     * Selecciona una ficha del pool si es el turno del jugador.
     * 
     * @param event Evento del mouse
     * @return true si se seleccionó una ficha
     */
    public boolean handleMousePressed(MouseEvent event) {
        // No permitir selección si no es nuestro turno o el juego terminó
        if (!gameState.isMyTurn() || gameState.hasWinner()) {
            return false;
        }

        // Buscar ficha en la posición del mouse
        selectedPiece = objectManager.findPieceAtPosition(
                event.getX(), event.getY(), PIECE_RADIUS);

        if (selectedPiece != null) {
            // Calcular offset para un arrastre suave
            dragOffsetX = event.getX() - selectedPiece.center_x;
            dragOffsetY = event.getY() - selectedPiece.center_y;
            return true;
        }

        return false;
    }

    /**
     * Maneja el evento de mouse arrastrado.
     * Mueve la ficha seleccionada siguiendo el cursor.
     * 
     * @param event Evento del mouse
     * @return true si se movió una ficha
     */
    public boolean handleMouseDragged(MouseEvent event) {
        if (selectedPiece == null) {
            return false;
        }

        // Actualizar posición de la ficha
        selectedPiece.center_x = event.getX() - dragOffsetX;
        selectedPiece.center_y = event.getY() - dragOffsetY;

        return true;
    }

    /**
     * Maneja el evento de mouse soltado.
     * Intenta colocar la ficha en el tablero o la devuelve al pool.
     * 
     * @param event         Evento del mouse
     * @param onPiecePlaced Callback que se ejecuta cuando se coloca una ficha
     *                      (recibe columna)
     * @return true si se procesó el evento
     */
    public boolean handleMouseReleased(MouseEvent event, PiecePlacedCallback onPiecePlaced) {
        if (selectedPiece == null) {
            return false;
        }

        // Calcular columna del tablero donde se soltó
        int targetCol = getColumnFromPosition(event.getX());

        // Verificar si la columna es válida y tiene espacio
        if (targetCol >= 0 && targetCol < BOARD_COLS && !gameState.isColumnFull(targetCol)) {
            // Notificar que se colocó una ficha
            if (onPiecePlaced != null) {
                onPiecePlaced.onPiecePlaced(selectedPiece.id, targetCol);
            }
        } else {
            // Devolver la ficha a su posición original
            objectManager.returnPieceToOriginalPosition(selectedPiece);
        }

        selectedPiece = null;
        return true;
    }

    /**
     * Calcula en qué columna del tablero está una posición X.
     * 
     * @param mouseX Coordenada X del mouse
     * @return Índice de columna (0-6) o -1 si está fuera del tablero
     */
    private int getColumnFromPosition(double mouseX) {
        double relativeX = mouseX - BOARD_LEFT;

        if (relativeX < 0 || relativeX > BOARD_COLS * CELL_SIZE) {
            return -1;
        }

        int col = (int) (relativeX / CELL_SIZE);
        return (col >= 0 && col < BOARD_COLS) ? col : -1;
    }

    /**
     * Cancela la selección actual y devuelve la ficha al pool.
     */
    public void cancelSelection() {
        if (selectedPiece != null) {
            objectManager.returnPieceToOriginalPosition(selectedPiece);
            selectedPiece = null;
        }
    }

    /**
     * Obtiene la ficha actualmente seleccionada.
     * 
     * @return La ficha seleccionada o null
     */
    public GameObject getSelectedPiece() {
        return selectedPiece;
    }

    /**
     * Verifica si hay una ficha seleccionada.
     * 
     * @return true si hay una ficha seleccionada
     */
    public boolean hasSelectedPiece() {
        return selectedPiece != null;
    }

    /**
     * Libera la selección actual sin devolver la ficha al pool.
     * Útil cuando la ficha ya fue procesada por el servidor.
     */
    public void releaseSelection() {
        selectedPiece = null;
    }

    /**
     * Interface funcional para callback cuando se coloca una ficha.
     */
    @FunctionalInterface
    public interface PiecePlacedCallback {
        void onPiecePlaced(String pieceId, int column);
    }
}
