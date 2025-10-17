package com.connect4;

import java.util.Objects;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.scene.Node;
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

    private String name;

    private Node rootNode;

    public String getUser() {
        return this.userName.getText();
    }

    public void setUser(String user) {
        this.userName.setText(user);
        name = user;
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

    // Método para establecer el nodo raíz desde el controlador padre
    public void setRootNode(Node node) {
        this.rootNode = node;
    }

    @FXML
    public void rejectInvitation() {

        // Conectar al servidor y envio acción denegar
        if (Main.wsClient != null && Main.wsClient.isOpen()) {

            JSONObject json = new JSONObject();
            json.put("type", "clientAnswerInvitation");
            json.put("sendFrom", name);
            json.put("sendTo", Main.clientName);
            json.put("value", false);
            // json.put("clientName", userName.getText()); //Conseguir user

            Main.wsClient.safeSend(json.toString());

            // Eliminar subView del ControllerOpponentSelection (VBox)
            removeInvitation();
        }
    }

    @FXML
    public void acceptInvitation() {
        
        // Hacer cambio al counter y comenzar partida
        // Conectar al servidor
        if (Main.wsClient != null && Main.wsClient.isOpen()) {

            JSONObject json = new JSONObject();
            json.put("type", "clientAnswerInvitation");
            json.put("sendFrom", userName.getText());
            json.put("sendTo", Main.clientName);
            json.put("value", true);

            Main.wsClient.safeSend(json.toString());

            // Eliminar subView del ControllerOpponentSelection
            removeInvitation();
        }
    }

    private void removeInvitation() {
        if (rootNode != null) {
            CtrlOpponentSelection ctrl = ((CtrlOpponentSelection)UtilsViews.getController("ViewOpponentSelection"));
            if (ctrl != null) {
                ctrl.removeFromReceiveList(rootNode);
                ctrl.removeFromSendInvitation(name);
                ctrl.reactivateFromSendList(name);
            }
        }
    }

}
