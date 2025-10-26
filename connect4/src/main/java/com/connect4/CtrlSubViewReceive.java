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

    private String name;

    @FXML
    private Button rejectButton, acceptButton;

    @FXML
    private ImageView userImage;

    @FXML
    private Text userName;

    private Node rootNode;

    /**
     * Get user name
     * 
     * @return user name
     */
    public String getUser() {
        return this.userName.getText();
    }

    /**
     * Set user name
     * 
     * @param user
     */
    public void setUser(String user) {
        this.userName.setText(user);
        this.name = user;
    }

    /**
     * Set user image
     * 
     * @param imagePath
     */
    public void setImage(String imagePath) {
        try {
            Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imagePath)));
            this.userImage.setImage(image);
        } catch (NullPointerException e) {
            System.err.println("Error loading image asset: " + imagePath);
            e.printStackTrace();
        }
    }

    /**
     * Set the root node from the parent controller
     */
    public void setRootNode(Node node) {
        this.rootNode = node;
    }

    /**
     * Reject invitation
     * 
     */
    @FXML
    public void rejectInvitation() {

        // Conectar al servidor y envio acción denegar
        if (Main.wsClient != null && Main.wsClient.isOpen()) {

            JSONObject json = new JSONObject();
            json.put("type", "clientAnswerInvitation");
            json.put("sendFrom", name);
            json.put("sendTo", Main.clientName);
            json.put("value", false);

            Main.wsClient.safeSend(json.toString());

            // Eliminar subView del ControllerOpponentSelection (VBox)
            removeInvitation();

            CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
            if (ctrl != null) {
                ctrl.reactivateFromSendList(name); // Reactivar botón del usuario que envió la invitación
            }
        }
    }

    /**
     * Accept invitation
     * 
     */
    @FXML
    public void acceptInvitation() {

        // Hacer cambio al counter y comenzar partida
        // Conectar al servidor
        if (Main.wsClient != null && Main.wsClient.isOpen()) {

            JSONObject json = new JSONObject();
            json.put("type", "clientAnswerInvitation");
            json.put("sendFrom", name);
            json.put("sendTo", Main.clientName);
            json.put("value", true);

            Main.wsClient.safeSend(json.toString());

            // Eliminar subView del ControllerOpponentSelection
            removeInvitation(); // Remove
        }
    }

    /**
     * Remove invitation from the list in the parent controller
     */
    private void removeInvitation() {
        if (rootNode != null) {
            CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
            if (ctrl != null) {
                ctrl.removeFromReceiveList(rootNode);
            }
        }
    }

}
