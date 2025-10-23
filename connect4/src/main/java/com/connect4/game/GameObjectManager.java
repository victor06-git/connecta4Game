package com.connect4.game;

import com.shared.GameObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestiona todos los objetos del juego (fichas).
 * Mantiene un registro de las posiciones originales y actuales de cada ficha.
 */
public class GameObjectManager {

    private Map<String, GameObject> gameObjectsMap = new HashMap<>();
    private Map<String, GameObject> originalPoolPositions = new HashMap<>();

    /**
     * Inicializa el gestor con la lista de objetos del juego.
     * 
     * @param objects Lista de objetos a gestionar
     */
    public void initialize(List<GameObject> objects) {
        gameObjectsMap.clear();
        originalPoolPositions.clear();

        for (GameObject obj : objects) {
            // Guardar posición original para poder resetear
            originalPoolPositions.put(obj.id,
                    new GameObject(obj.id, obj.center_x, obj.center_y, obj.radius, obj.col, obj.row));

            // Guardar objeto actual
            gameObjectsMap.put(obj.id, obj);
        }
    }

    /**
     * Obtiene un objeto por su ID.
     * 
     * @param id El ID del objeto
     * @return El objeto o null si no existe
     */
    public GameObject getObject(String id) {
        return gameObjectsMap.get(id);
    }

    /**
     * Devuelve una ficha a su posición original en el pool.
     * 
     * @param piece La ficha a devolver
     */
    public void returnPieceToOriginalPosition(GameObject piece) {
        if (originalPoolPositions.containsKey(piece.id)) {
            GameObject original = originalPoolPositions.get(piece.id);
            piece.center_x = original.center_x;
            piece.center_y = original.center_y;
            piece.col = -1;
            piece.row = -1;
        }
    }

    /**
     * Actualiza la posición de una ficha en el tablero.
     * 
     * @param pieceId ID de la ficha
     * @param row     Fila del tablero
     * @param col     Columna del tablero
     */
    public void updatePiecePosition(String pieceId, int row, int col) {
        GameObject piece = gameObjectsMap.get(pieceId);
        if (piece != null) {
            piece.row = row;
            piece.col = col;
        }
    }

    /**
     * Obtiene todas las fichas que están en el pool (no colocadas en el tablero).
     * 
     * @return Lista de fichas en el pool
     */
    public List<GameObject> getPoolPieces() {
        return gameObjectsMap.values().stream()
                .filter(obj -> obj.row == -1 && obj.col == -1)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las fichas que están en el tablero.
     * 
     * @return Lista de fichas en el tablero
     */
    public List<GameObject> getBoardPieces() {
        return gameObjectsMap.values().stream()
                .filter(obj -> obj.row >= 0 && obj.col >= 0)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las fichas del juego.
     * 
     * @return Lista de todas las fichas
     */
    public List<GameObject> getAllPieces() {
        return List.copyOf(gameObjectsMap.values());
    }

    /**
     * Verifica si una ficha está en el pool.
     * 
     * @param pieceId ID de la ficha
     * @return true si está en el pool
     */
    public boolean isPieceInPool(String pieceId) {
        GameObject piece = gameObjectsMap.get(pieceId);
        return piece != null && piece.row == -1 && piece.col == -1;
    }

    /**
     * Verifica si una ficha está en el tablero.
     * 
     * @param pieceId ID de la ficha
     * @return true si está en el tablero
     */
    public boolean isPieceOnBoard(String pieceId) {
        GameObject piece = gameObjectsMap.get(pieceId);
        return piece != null && piece.row >= 0 && piece.col >= 0;
    }

    /**
     * Busca qué ficha está en una posición específica del mouse.
     * 
     * @param mouseX Coordenada X del mouse
     * @param mouseY Coordenada Y del mouse
     * @param radius Radio de búsqueda
     * @return La ficha encontrada o null
     */
    public GameObject findPieceAtPosition(double mouseX, double mouseY, double radius) {
        for (GameObject obj : getPoolPieces()) {
            double dx = mouseX - obj.center_x;
            double dy = mouseY - obj.center_y;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance <= radius) {
                return obj;
            }
        }
        return null;
    }
}
