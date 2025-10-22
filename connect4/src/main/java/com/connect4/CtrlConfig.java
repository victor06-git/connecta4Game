package com.connect4;

import java.net.URL;
import java.util.ResourceBundle;

import animatefx.animation.FadeIn;
import animatefx.animation.Shake;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class CtrlConfig implements Initializable {

    @FXML
    public TextField txtPlayerName;

    @FXML
    public TextField txtProtocol;

    @FXML
    public TextField txtHost;

    @FXML
    public TextField txtPort;

    @FXML
    public Label txtMessage;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    @FXML
    private void connectToServer() {
        String playerName = txtPlayerName.getText().trim();
        System.out.println("Player name: " + playerName);
        // Mensaje de error si no se ha introducido un nombre de jugador
        if (playerName.isEmpty()) {
            txtMessage.setStyle("-fx-text-fill: red;");
            txtMessage.setText("Please enter a player name");

            // Shake del campo de texto con velocidad personalizada
            new Shake(txtPlayerName).setSpeed(1.5).play();

            // FadeIn del mensaje de error
            txtMessage.setOpacity(0);
            new FadeIn(txtMessage).play();

            // Cambiar el borde del campo a rojo temporalmente
            txtPlayerName.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            return;
        }
        Main.connectToServer();
    }

    @FXML
    private void setConfigLocal() {
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void setConfigProxmox() {
        txtProtocol.setText("wss");
        txtHost.setText("vasensiobermudez.ieti.site");
        txtPort.setText("443");
    }
}