package com.connect4.game;

import org.json.JSONArray;
import org.json.JSONObject;
import com.connect4.Main;

/**
 * Gestiona el estado del juego Connect 4.
 * Mantiene información sobre el tablero, turnos, ganador y estado actual.
 */
public class GameState {

    // Estado del tablero (6 filas x 7 columnas)
    private String[][] boardState = new String[6][7];

    // Información de turnos
    private String currentTurn = "";
    private String myColor = "";

    // Estado del ganador
    private boolean gameEnded = false;
    private String winnerColor = null;
    private int[] winningLineCoords = null;

    // Drop zone
    private int hoveredColumn = -1;

    /**
     * Constructor que inicializa el tablero vacío.
     */
    public GameState() {
        initializeBoard();
    }

    /**
     * Inicializa el tablero con valores null.
     */
    private void initializeBoard() {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 7; j++) {
                boardState[i][j] = null;
            }
        }
    }

    /**
     * Actualiza el estado del juego desde el servidor.
     * 
     * @param state JSONObject con los datos del servidor
     */
    public void updateFromServer(JSONObject state) {
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
     * Establece la información del ganador.
     * 
     * @param winner Color del ganador ("RED", "YELLOW", "DRAW")
     * @param coords Coordenadas de la línea ganadora [startRow, startCol, endRow,
     *               endCol]
     */
    public void setWinner(String winner, int[] coords) {
        this.winnerColor = winner;
        this.winningLineCoords = coords;
        this.gameEnded = winner != null && !winner.equals("DRAW");
    }

    /**
     * Actualiza mi color obtenido de la lista de clientes.
     */
    public void updateMyColor() {
        if (myColor.isEmpty() && Main.clients != null) {
            myColor = Main.clients.stream()
                    .filter(c -> c.name.equals(Main.clientName))
                    .map(c -> c.color)
                    .findFirst()
                    .orElse("");
        }
    }

    /**
     * Verifica si es mi turno.
     * 
     * @return true si es mi turno
     */
    public boolean isMyTurn() {
        updateMyColor();
        return currentTurn.equals(myColor);
    }

    /**
     * Coloca una ficha en el tablero.
     * 
     * @param row     Fila donde colocar la ficha
     * @param col     Columna donde colocar la ficha
     * @param pieceId ID de la ficha
     */
    public void placePiece(int row, int col, String pieceId) {
        if (row >= 0 && row < 6 && col >= 0 && col < 7) {
            boardState[row][col] = pieceId;
        }
    }

    /**
     * Obtiene la ficha en una posición específica.
     * 
     * @param row Fila
     * @param col Columna
     * @return ID de la ficha o null si está vacío
     */
    public String getPieceAt(int row, int col) {
        if (row >= 0 && row < 6 && col >= 0 && col < 7) {
            return boardState[row][col];
        }
        return null;
    }

    /**
     * Verifica si una columna está llena.
     * 
     * @param col Columna a verificar
     * @return true si la columna está llena
     */
    public boolean isColumnFull(int col) {
        if (col < 0 || col >= 7) {
            return true;
        }
        return boardState[0][col] != null;
    }

    /**
     * Verifica si hay un ganador.
     * 
     * @return true si el juego ha terminado con un ganador
     */
    public boolean hasWinner() {
        return gameEnded && winnerColor != null && !winnerColor.equals("DRAW");
    }

    /**
     * Reinicia el estado del juego.
     */
    public void reset() {
        initializeBoard();
        currentTurn = "";
        gameEnded = false;
        winnerColor = null;
        winningLineCoords = null;
        hoveredColumn = -1;
    }

    // ==================== Getters y Setters ====================

    public String[][] getBoardState() {
        return boardState;
    }

    public String getCurrentTurn() {
        return currentTurn;
    }

    public void setCurrentTurn(String currentTurn) {
        this.currentTurn = currentTurn;
    }

    public String getMyColor() {
        updateMyColor();
        return myColor;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public String getWinnerColor() {
        return winnerColor;
    }

    public int[] getWinningLineCoords() {
        return winningLineCoords;
    }

    public int getHoveredColumn() {
        return hoveredColumn;
    }

    public void setHoveredColumn(int hoveredColumn) {
        this.hoveredColumn = hoveredColumn;
    }

    public int getRows() {
        return 6;
    }

    public int getCols() {
        return 7;
    }
}
