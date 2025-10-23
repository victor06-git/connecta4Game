package com.connect4;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public class CtrlResult implements Initializable {

    @FXML
    public Label txtTitle;

    @FXML
    public Canvas canvas;

    private GraphicsContext gc;
    private String result = ""; // "WIN", "LOSE", "DRAW"
    private String myColor = "";
    private String winnerColor = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.gc = canvas.getGraphicsContext2D();
    }

    public void setResultData(String result, String myColor, String winnerColor, String[][] boardState) {
        this.result = result;
        this.myColor = myColor;
        this.winnerColor = winnerColor;

        updateTitle();
        drawFinalBoard(boardState);
    }

    private void updateTitle() {
        if (result.equals("WIN")) {
            txtTitle.setText("YOU WIN!");
            txtTitle.setTextFill(Color.web("#00aa00"));
        } else if (result.equals("LOSE")) {
            txtTitle.setText("YOU LOSE!");
            txtTitle.setTextFill(Color.web("#ff0000"));
        } else if (result.equals("DRAW")) {
            txtTitle.setText("DRAW!");
            txtTitle.setTextFill(Color.web("#ffaa00"));
        }
    }

    private void drawFinalBoard(String[][] boardState) {
        double startX = 50;
        double startY = 50;
        double cellSize = 80;
        double radius = cellSize * 0.35;

        // Limpiar canvas
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Dibujar tablero
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                double x = startX + col * cellSize;
                double y = startY + row * cellSize;

                // Fondo de celda
                gc.setFill(Color.web("#4a90e2"));
                gc.fillRect(x, y, cellSize, cellSize);

                // Borde
                gc.setStroke(Color.GRAY);
                gc.setLineWidth(2);
                gc.strokeRect(x, y, cellSize, cellSize);

                // Dibujar pieza si existe
                if (boardState[row][col] != null) {
                    String pieceId = boardState[row][col];
                    double centerX = x + cellSize / 2;
                    double centerY = y + cellSize / 2;

                    Color pieceColor = pieceId.startsWith("R") ? Color.RED : Color.YELLOW;
                    gc.setFill(pieceColor);
                    gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

                    // Borde de pieza
                    gc.setStroke(Color.DARKGRAY);
                    gc.setLineWidth(1);
                    gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                }
            }
        }
    }

    @FXML
    private void backToMenu() {
        Main.ctrlPlay.stop();
        UtilsViews.setViewAnimating("ViewOpponentSelection");
    }

    @FXML
    private void exitApp() {
        System.exit(0);
    }
}