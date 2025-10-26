package com.connect4.ctrlPlay;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.connect4.Main;
import com.connect4.PlayGrid;
import com.shared.GameObject;

public class GameLogicUtils {

    /**
     * Updates the board state matrix from server data
     * 
     * @param state
     * @param boardState
     * @return updated current turn
     */
    public String updateGameState(JSONObject state, String[][] boardState) {
        String currentTurn = state.optString("currentTurn", "RED");

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
        return currentTurn;
    }

    /**
     * Handles server acceptance of play action
     * 
     * @param pieceId
     * @param col
     * @param row
     * @param winner
     * @param winningLineCoords
     * @param boardState
     * @param gameObjectsMap
     * @return the game piece that was played
     */
    public GameObject handlePlayAccepted(String pieceId, int col, int row, String winner, int[] winningLineCoords,
            String[][] boardState, Map<String, GameObject> gameObjectsMap) {
        boardState[row][col] = pieceId;

        GameObject piece = gameObjectsMap.get(pieceId);
        if (piece != null) {
            piece.row = row;
            piece.col = col;

            // CRITICAL: Also update Main.objects to synchronize the piece
            for (GameObject go : Main.objects) {
                if (go.id.equals(pieceId)) {
                    go.row = row;
                    go.col = col;
                    break;
                }
            }
        }

        return piece;
    }

    /**
     * Handles server rejection of play action
     * 
     * @param pieceId
     * @param selectedObject
     * @param originalPoolPositions
     * @return null to clear selected object
     */
    public GameObject handlePlayRejected(String pieceId, GameObject selectedObject,
            Map<String, GameObject> originalPoolPositions) {
        if (selectedObject != null && selectedObject.id.equals(pieceId)) {
            // Return to original position
            returnPieceToOriginalPosition(selectedObject, originalPoolPositions);
            return null; // Clear selection
        }
        return selectedObject;
    }

    /**
     * Returns a piece to its original position in the pool
     * 
     * @param piece
     * @param originalPoolPositions
     */
    public void returnPieceToOriginalPosition(GameObject piece, Map<String, GameObject> originalPoolPositions) {
        if (originalPoolPositions.containsKey(piece.id)) {
            GameObject original = originalPoolPositions.get(piece.id);
            piece.center_x = original.center_x;
            piece.center_y = original.center_y;
            piece.col = -1;
            piece.row = -1;

            // CRITICAL: Also update Main.objects so the visual representation updates
            for (GameObject go : Main.objects) {
                if (go.id.equals(piece.id)) {
                    go.center_x = original.center_x;
                    go.center_y = original.center_y;
                    go.col = -1;
                    go.row = -1;
                    break;
                }
            }
        }
    }

    /**
     * Checks if a position is inside the drop zone
     * 
     * @param x
     * @param y
     * @param grid
     * @param dropZoneHeight
     * @return true if inside drop zone
     */
    public boolean isPositionInDropZone(double x, double y, PlayGrid grid, double dropZoneHeight) {
        double gridStartX = grid.getStartX();
        double gridEndX = gridStartX + (grid.getCols() * grid.getCellSize());
        double dropZoneStartY = grid.getStartY() - dropZoneHeight;
        double dropZoneEndY = grid.getStartY();

        return x >= gridStartX && x <= gridEndX &&
                y >= dropZoneStartY && y <= dropZoneEndY;
    }

    /**
     * Gets the column index based on x position in drop zone
     * 
     * @param x
     * @param grid
     * @return column index or -1 if outside
     */
    public int getDropZoneColumn(double x, PlayGrid grid) {
        if (x < grid.getStartX() || x > grid.getStartX() + grid.getCols() * grid.getCellSize()) {
            return -1;
        }
        int col = (int) ((x - grid.getStartX()) / grid.getCellSize());
        return Math.max(0, Math.min(col, grid.getCols() - 1));
    }

    /**
     * Verifies if the player can move a piece
     * 
     * @param piece
     * @param myColor
     * @param currentTurn
     * @param clientName
     * @return true if can move
     */
    public boolean canMoveThisPiece(GameObject piece, String myColor, String currentTurn, String clientName) {
        // Get my color if empty
        if (myColor.isEmpty()) {
            myColor = Main.clients.stream()
                    .filter(c -> c.name.equals(clientName))
                    .map(c -> c.color)
                    .findFirst()
                    .orElse("");
        }

        // Debug: show information
        System.out.println("My color: " + myColor + ", Current turn: " + currentTurn + ", Piece: " + piece.id);

        // Verify it's my turn and the piece is my color
        boolean isMyTurn = currentTurn.equals(myColor);
        boolean isMyPiece = piece.id.startsWith(myColor.charAt(0) + "_");

        return isMyTurn && isMyPiece;
    }

    /**
     * Verifies if mouse position is inside a piece (circle)
     * 
     * @param mouseX
     * @param mouseY
     * @param centerX
     * @param centerY
     * @param radius
     * @return true if inside circle
     */
    public boolean isMouseInsidePiece(double mouseX, double mouseY, double centerX, double centerY, double radius) {
        double dx = mouseX - centerX; // horizontal distance
        double dy = mouseY - centerY; // vertical distance
        double distanceSquared = dx * dx + dy * dy; // squared distance
        return distanceSquared <= radius * radius; // compare with squared radius
    }

    /**
     * Gets my color from clients list
     * 
     * @param clientName
     * @return player color
     */
    public String getMyColor(String clientName) {
        return Main.clients.stream()
                .filter(c -> c.name.equals(clientName))
                .map(c -> c.color)
                .findFirst()
                .orElse("");
    }
}