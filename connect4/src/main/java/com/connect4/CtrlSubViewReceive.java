package com.connect4;

import java.util.Objects;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;

public class CtrlSubViewReceive {

    @FXML
    private Button rejectButton, acceptButton;

    @FXML
    private ImageView userImage;

    @FXML
    private Text userName;

    public void setUser(String user) {
        this.userName.setText(user);
    }

    public void setImage(String imagePath) {
        try {
            Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imagePath)));
            this.userImage.setImage(image);
        } catch (NullPointerException e) {
            System.err.println("Error loading image asset: " + imagePath);
            e.printStackTrace();
        }
    }

    public void rejectInvitation() {

        //Conectar al servidor y envio acción denegar
        if (Main.wsClient != null && Main.wsClient.isOpen()) {
            
            JSONObject json = new JSONObject();
            json.put("type", "clientAnswerInvitation");
            json.put("value", false);

            Main.wsClient.safeSend(json.toString());

            //Eliminar subView del ControllerOpponentSelection (VBox)
            CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("opponent_selection");
        }
    }

    public void acceptInvitation() {
        // Hacer cambio al counter y comenzar partida
        //Conectar al servidor
        if (Main.wsClient != null && Main.wsClient.isOpen()) {
            
            JSONObject json = new JSONObject();
            json.put("type", "clientAnswerInvitation");
            json.put("value", true);

            Main.wsClient.safeSend(json.toString());
        }
    }

}
