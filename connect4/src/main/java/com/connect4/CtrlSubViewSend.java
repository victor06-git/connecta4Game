package com.connect4;

import java.util.Objects;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;

public class CtrlSubViewSend {

    @FXML
    private ImageView userImage;

    @FXML
    private Text userName;

    @FXML
    private Button sendButton;

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

    public void sendInvitation() {
        // Crear invitación y enviarla
    }
}
