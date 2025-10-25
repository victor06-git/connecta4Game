package com.connect4;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public class CtrlResult implements Initializable {

    @FXML
    public Label nameWinner;

    @FXML
    public Label winnerMsg;

    private String result = ""; // "WIN", "LOSE", "DRAW"
    private String myColor = "";
    private String winnerColor = "";
    private String[][] finalBoard = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Nothing to initialize visually in this controller — FXML contains labels only
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
    }

    private void updateViewFromResult() {
        if (result == null)
            result = "";

        switch (result) {
            case "WIN":
                nameWinner.setText("YOU WIN!");
                nameWinner.setTextFill(Color.web("#00aa00"));
                winnerMsg.setText("Winner color: " + (winnerColor != null ? winnerColor : ""));
                break;
            case "LOSE":
                nameWinner.setText("YOU LOSE!");
                nameWinner.setTextFill(Color.web("#ff0000"));
                winnerMsg.setText("Winner color: " + (winnerColor != null ? winnerColor : ""));
                break;
            case "DRAW":
                nameWinner.setText("DRAW");
                nameWinner.setTextFill(Color.web("#ffaa00"));
                winnerMsg.setText("");
                break;
            default:
                nameWinner.setText("RESULT");
                winnerMsg.setText("");
        }
    }

    @FXML
    private void toOpponentSelection() {
        // Stop play timer if running and go back to opponent selection view
        try {
            if (Main.ctrlPlay != null)
                Main.ctrlPlay.stop();
        } catch (Exception ex) {
            // ignore
        }
        UtilsViews.setViewAnimating("ViewOpponentSelection");
    }

}