package com.connect4.game;

import com.shared.GameObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestiona las animaciones de caída de las fichas.
 * Controla el estado de animación, velocidad y posiciones objetivo.
 */
public class PieceAnimationManager {

    private static final double ANIMATION_SPEED = 8.0; // Velocidad de caída en píxeles por frame
    private static final double SNAP_THRESHOLD = 5.0; // Umbral para considerar que llegó a destino

    // Estado de animación para cada ficha
    private Map<String, AnimationState> animations = new HashMap<>();

    /**
     * Clase interna para almacenar el estado de animación de una ficha.
     */
    private static class AnimationState {
        boolean isAnimating;
        double targetX;
        double targetY;

        AnimationState() {
            this.isAnimating = false;
            this.targetX = 0;
            this.targetY = 0;
        }
    }

    /**
     * Inicia una animación de caída para una ficha.
     * 
     * @param piece   La ficha que se va a animar
     * @param targetX Coordenada X objetivo
     * @param targetY Coordenada Y objetivo
     */
    public void startAnimation(GameObject piece, double targetX, double targetY) {
        AnimationState state = animations.computeIfAbsent(piece.id, k -> new AnimationState());
        state.isAnimating = true;
        state.targetX = targetX;
        state.targetY = targetY;
    }

    /**
     * Actualiza la posición de todas las fichas que están siendo animadas.
     * 
     * @return true si hay alguna animación en progreso
     */
    public boolean updateAnimations(GameObjectManager objectManager) {
        boolean anyAnimating = false;

        for (Map.Entry<String, AnimationState> entry : animations.entrySet()) {
            String pieceId = entry.getKey();
            AnimationState state = entry.getValue();

            if (!state.isAnimating) {
                continue;
            }

            GameObject piece = objectManager.getObject(pieceId);
            if (piece == null) {
                state.isAnimating = false;
                continue;
            }

            // Calcular distancia al objetivo
            double dx = state.targetX - piece.center_x;
            double dy = state.targetY - piece.center_y;
            double distance = Math.sqrt(dx * dx + dy * dy);

            // Si está cerca del objetivo, ajustar a la posición exacta
            if (distance < SNAP_THRESHOLD) {
                piece.center_x = state.targetX;
                piece.center_y = state.targetY;
                state.isAnimating = false;
            } else {
                // Mover hacia el objetivo
                double ratio = ANIMATION_SPEED / distance;
                piece.center_x += dx * ratio;
                piece.center_y += dy * ratio;
                anyAnimating = true;
            }
        }

        return anyAnimating;
    }

    /**
     * Verifica si una ficha específica está siendo animada.
     * 
     * @param pieceId ID de la ficha
     * @return true si está animando
     */
    public boolean isAnimating(String pieceId) {
        AnimationState state = animations.get(pieceId);
        return state != null && state.isAnimating;
    }

    /**
     * Verifica si hay alguna animación en progreso.
     * 
     * @return true si hay alguna animación activa
     */
    public boolean hasActiveAnimations() {
        return animations.values().stream()
                .anyMatch(state -> state.isAnimating);
    }

    /**
     * Detiene la animación de una ficha específica.
     * 
     * @param pieceId ID de la ficha
     */
    public void stopAnimation(String pieceId) {
        AnimationState state = animations.get(pieceId);
        if (state != null) {
            state.isAnimating = false;
        }
    }

    /**
     * Detiene todas las animaciones en progreso.
     */
    public void stopAllAnimations() {
        animations.values().forEach(state -> state.isAnimating = false);
    }

    /**
     * Limpia todos los estados de animación.
     */
    public void clear() {
        animations.clear();
    }

    /**
     * Obtiene la posición objetivo de una ficha que está siendo animada.
     * 
     * @param pieceId ID de la ficha
     * @return Array con [targetX, targetY] o null si no está animando
     */
    public double[] getTargetPosition(String pieceId) {
        AnimationState state = animations.get(pieceId);
        if (state != null && state.isAnimating) {
            return new double[] { state.targetX, state.targetY };
        }
        return null;
    }
}
