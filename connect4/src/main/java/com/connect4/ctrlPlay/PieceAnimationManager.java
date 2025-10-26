package com.connect4.ctrlPlay;

import com.connect4.Main;
import com.connect4.PlayGrid;
import com.shared.GameObject;

/**
 * Small manager to handle piece drop animation.
 */
public class PieceAnimationManager {

    /**
     * Initializes drop animation for a piece.
     * 
     * @return target Y position for the animation
     */
    public double startDropAnimation(GameObject piece, int col, int row, PlayGrid grid) {
        double cellSize = grid.getCellSize();
        piece.center_x = grid.getCellX(col) + cellSize / 2;
        piece.center_y = grid.getStartY() - 20;

        return grid.getCellY(row) + cellSize / 2; // target Y
    }

    /**
     * Updates animation position for the piece.
     */
    public boolean updateAnimation(GameObject piece, double animationTargetY, double animationSpeed, double fps) {
        if (fps < 1) {
            return true; // Continue animation
        }

        double deltaTime = 1.0 / fps; // used to calculate movement per frame
        double movement = animationSpeed * deltaTime; // pixels to move this frame

        if (piece.center_y < animationTargetY) {
            piece.center_y += movement;

            // Update in Main.objects
            for (GameObject go : Main.objects) {
                if (go.id.equals(piece.id)) {
                    go.center_x = piece.center_x;
                    go.center_y = piece.center_y;
                    break;
                }
            }

            if (piece.center_y >= animationTargetY) {
                piece.center_y = animationTargetY;
                return false; // Animation finished
            }
            return true; // Continue animation
        }
        return false; // Animation finished
    }
}
