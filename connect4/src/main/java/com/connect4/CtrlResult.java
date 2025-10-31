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
    private static final double FIXED_CELL_SIZE = 80;
    private static final double LEFT_MARGIN = 20;

    private String result = ""; // "WIN", "LOSE", "DRAW"
    private String myColor = "";
    private String winnerColor = "";
    private String[][] finalBoard = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialize canvas drawing context if canvas is present
        if (resultCanvas != null) {
            gc = resultCanvas.getGraphicsContext2D();
        }

        // Create default grid (will be repositioned on size change)
        grid = new PlayGrid(LEFT_MARGIN, 80, FIXED_CELL_SIZE, 6, 7);

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
     * Called from Main when the game ends. Updates labels according to result.
     */
    public void setResultData(String result, String myColor, String winnerColor, String[][] boardState) {
        this.result = result;
        this.myColor = myColor;
        this.winnerColor = winnerColor;
        this.finalBoard = boardState;

        updateViewFromResult();
        // Draw the final board snapshot if we have a canvas and board data
        drawBoardSnapshot();
    }

    private void onSizeChanged() {
        if (resultCanvas == null)
            return;

        double width = LEFT_MARGIN + (7 * FIXED_CELL_SIZE) + LEFT_MARGIN; // enough to draw board
        double height = 80 + (6 * FIXED_CELL_SIZE) + 40;

        resultCanvas.setWidth(width);
        resultCanvas.setHeight(height);

        // update grid start positions in case sizes changed
        grid = new PlayGrid(LEFT_MARGIN, 80, FIXED_CELL_SIZE, 6, 7);

        // Redraw if we already have the final board
        drawBoardSnapshot();
    }

    private void drawBoardSnapshot() {
        if (resultCanvas == null || gc == null || finalBoard == null)
            return;

        // Clear
        gc.clearRect(0, 0, resultCanvas.getWidth(), resultCanvas.getHeight());

        // Draw board background and holes
        drawUtils.drawBoard(gc, grid, utils);

        // Draw pieces from finalBoard
        drawUtils.drawBoardPieces(gc, finalBoard, grid, utils, null);

        // Optionally draw winner highlight if we can infer it - but Main passes only
        // winner color
        // We could draw a small legend
        if (winnerColor != null && !winnerColor.isEmpty()) {
            gc.setFill(utils.getColor(winnerColor));
            gc.fillOval(resultCanvas.getWidth() - 60, 20, 30, 30);
            gc.setFill(Color.BLACK);
            gc.fillText("Winner", resultCanvas.getWidth() - 95, 40);
        }
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
                winnerMsg.setText("Winner: " + (winnerColor != null ? winnerColor : ""));
                break;
            case "LOSE":
                nameWinner.setText("YOU LOSE!");
                nameWinner.setTextFill(Color.web("#ff0000"));
                winnerMsg.setText("Winner: " + (winnerColor != null ? winnerColor : ""));
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