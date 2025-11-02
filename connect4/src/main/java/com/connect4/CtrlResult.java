package com.connect4;

import java.net.URL;
import java.util.ResourceBundle;

import com.connect4.ctrlPlay.ColorUtils;
import com.connect4.ctrlPlay.DrawUtils;
import com.shared.ClientData;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public class CtrlResult implements Initializable {

    @FXML
    public Label nameWinner;

    @FXML
    public Label winnerMsg;

    @FXML
    public Canvas resultCanvas;

    private GraphicsContext gc;
    private PlayGrid grid;
    private DrawUtils drawUtils = new DrawUtils();
    private ColorUtils utils = new ColorUtils();
    // Use a smaller cell size in the Result preview so the board looks like a compact
    // snapshot. Adjust LEFT_MARGIN to move it closer to the left edge of the canvas.
    private static final double FIXED_CELL_SIZE = 50; // preview size (was 80)
    private static final double LEFT_MARGIN = 10; // move board more to the left
    private static final double TOP_MARGIN = 10;

    private String result = ""; // "WIN", "LOSE", "DRAW"
    private String myColor = "";
    private String winnerColor = "";
    private String[][] finalBoard = null; // final board state to draw

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialize canvas drawing context if canvas is present
        if (resultCanvas != null) {
            gc = resultCanvas.getGraphicsContext2D();
        }

        // Create default grid (will be repositioned on size change)
        grid = new PlayGrid(LEFT_MARGIN, TOP_MARGIN, FIXED_CELL_SIZE, 6, 7);

        // Listen for parent size changes to adjust canvas
        try {
            UtilsViews.parentContainer.widthProperty().addListener((obs, oldV, newV) -> onSizeChanged());
            UtilsViews.parentContainer.heightProperty().addListener((obs, oldV, newV) -> onSizeChanged());
        } catch (Exception ex) {
            // ignore if UtilsViews not initialized yet
        }

        onSizeChanged();
    }

    /**
     * Return the client name that has the specified color. If not found, returns
     * empty string.
     */
    private String getNameFromColor(String color) {
        if (color == null || color.isEmpty())
            return "";

        for (ClientData client : Main.clients) {
            if (client != null && color.equals(client.color)) {
                return client.name != null ? client.name : "";
            }
        }
        return "";
    }

    /**
     * Called from Main when the game ends. Updates labels according to result.
     */
    public void setResultData(String result, String myColor, String winnerColor, String[][] boardState) {
        this.result = result;
        this.myColor = myColor;
        this.winnerColor = winnerColor;
        this.finalBoard = boardState;

        updateViewFromResult();
        // Draw the final board snapshot if we have a canvas and board data
        drawBoardResult();
    }

    private void onSizeChanged() {
        if (resultCanvas == null)
            return;

        double width = LEFT_MARGIN + (7 * FIXED_CELL_SIZE) + LEFT_MARGIN; // enough to draw board
        double height = TOP_MARGIN + (6 * FIXED_CELL_SIZE) + 20;

        resultCanvas.setWidth(width);
        resultCanvas.setHeight(height);

        // update grid start positions in case sizes changed
        grid = new PlayGrid(LEFT_MARGIN, TOP_MARGIN, FIXED_CELL_SIZE, 6, 7);

        // Redraw if we already have the final board
        drawBoardResult();
    }

    private void drawBoardResult() {
        if (resultCanvas == null || gc == null || finalBoard == null)
            return;

        // Clear
        gc.clearRect(0, 0, resultCanvas.getWidth(), resultCanvas.getHeight());

        // Draw board background and holes
        drawUtils.drawBoard(gc, grid, utils);

        // Draw pieces from finalBoard
        drawUtils.drawBoardPieces(gc, finalBoard, grid, utils, null);

    }

    /**
     * Updates the view labels based on the game result
     */
    private void updateViewFromResult() {
        if (result == null)
            result = "";

        switch (result) {
            case "WIN":
                nameWinner.setText("YOU WIN!");
                nameWinner.setTextFill(Color.web("#00aa00"));
                // Mostrar nombre del ganador y color entre paréntesis
                String winName = getNameFromColor(winnerColor);
                winnerMsg.setText("Winner: " + (winName != null && !winName.isEmpty() ? winName : winnerColor)
                        + " (" + (winnerColor != null ? winnerColor : "") + ")");
                break;
            case "LOSE":
                nameWinner.setText("YOU LOSE!");
                nameWinner.setTextFill(Color.web("#ff0000"));
                String loseName = getNameFromColor(winnerColor);
                winnerMsg.setText("Winner: " + (loseName != null && !loseName.isEmpty() ? loseName : winnerColor)
                        + " (" + (winnerColor != null ? winnerColor : "") + ")");
                break;
            case "DRAW":
                nameWinner.setText("DRAW");
                nameWinner.setTextFill(Color.web("#ffaa00"));
                winnerMsg.setText("DRAW");
                break;
            default:
                nameWinner.setText("RESULT");
                winnerMsg.setText("N/A"); // No result available
        }
    }

    /**
     * Handles the "Back to Opponent Selection" button click
     */
    @FXML
    private void toOpponentSelection() {
        // Stop play timer if running
        try {
            if (Main.ctrlPlay != null) {
                Main.ctrlPlay.stop();
                // Reset the board and all game state for a new game
                Main.ctrlPlay.resetBoard();
            }
        } catch (Exception ex) {
            System.out.println("Error resetting board: " + ex.getMessage());
        }

        // Set isPlaying to false for all clients to re-enable buttons
        for (ClientData client : Main.clients) {
            client.SetIsPlaying(false);
        }

        // Limpiar las invitaciones pendientes
        CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
        if (ctrl != null) {
            ctrl.clearSendInvitations();
        }

        // Notificar al servidor que el juego ha terminado
        System.out.println("🎮 Enviando mensaje gameEnded al servidor...");
        if (Main.wsClient != null && Main.wsClient.isOpen()) {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("type", "gameEnded");
            json.put("clientName", Main.clientName);
            Main.wsClient.safeSend(json.toString());
            System.out.println("Mensaje gameEnded enviado: " + json.toString());
        } else {
            System.out.println("No se pudo enviar gameEnded - WebSocket no conectado");
        }

        // Navigate back to opponent selection view
        UtilsViews.setViewAnimating("ViewOpponentSelection");
    }

}