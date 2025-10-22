package com.connect4;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.shared.ClientData;

import animatefx.animation.Pulse;
import animatefx.animation.Wobble;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;

public class CtrlOpponentSelection implements Initializable {

    private Map<Node, CtrlSubViewSend> controllersSend = new HashMap<>();
    private Map<Node, CtrlSubViewReceive> controllersReceive = new HashMap<>();
    private List<String> sendInvitations = new ArrayList<>();

    @FXML
    private VBox list_send, list_receive;

    @Override
    public void initialize(URL location, java.util.ResourceBundle resources) {
        loadSendList();
        loadReceiveList();

        new Wobble(list_send).play();
        new Pulse(list_receive).play();
    }

    // Cargar la lista de usuarios disponibles para enviar invitación
    public void loadSendList() {
        try {
            list_send.getChildren().clear();

            // Iterar sobre todos los clientes conectados
            for (ClientData client : Main.clients) {
                // Filtrar: no mostrar el propio usuario
                if (!client.name.equals(Main.clientName)) {

                    URL resource = getClass().getResource("/assets/subviewSend.fxml");
                    FXMLLoader loader = new FXMLLoader(resource);
                    Parent itemPane = loader.load();
                    CtrlSubViewSend itemController = loader.getController();

                    controllersSend.put(itemPane, itemController);

                    itemController.setUser(client.name);
                    // itemController.setImage("/assets/images/default-avatar.png"); //User Image

                    list_send.getChildren().add(itemPane);

                    System.out.println(client.isPlaying);

                    if (client.isPlaying || sendInvitations.contains(client.name)) {
                        itemPane.setDisable(true);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error al cargar la lista de usuarios disponibles");
            e.printStackTrace();
        }
    }

    // Cargar la lista de invitaciones recibidas
    // (Cambiar metodo de conseguir los clientes invitados)
    public void loadReceiveList() {
        try {
            list_receive.getChildren().clear();

            // Cambiar a la lista de invitaciones pendientes
            // (Actualmente clientes conectados)
            for (ClientData client : Main.clients) {

                URL resource = getClass().getResource("/assets/subviewReceive.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent itemPane = loader.load();
                CtrlSubViewReceive itemController = loader.getController();

                // Configurar los datos del cliente
                itemController.setUser(client.name);
                // itemController.setImage("/assets/images/"); //si existe

                list_receive.getChildren().add(itemPane);
            }
        } catch (Exception e) {
            System.err.println("Error al cargar las invitaciones recibidas");
            e.printStackTrace();
        }
    }

    // Actualizar ambas listas
    public void updateLists() {
        loadSendList();
        loadReceiveList();
    }

    // Método para eliminar la parte
    public void removeFromReceiveList(Node node) {
        list_receive.getChildren().remove(node);
        loadSendList();
    }

    // Añadimos una petición a partir de un nombre de usuario
    public void addFromReceiveList(String name) {
        try {
            URL resource = getClass().getResource("/assets/subviewReceive.fxml");
            FXMLLoader loader = new FXMLLoader(resource);
            Parent itemPane = loader.load();
            CtrlSubViewReceive itemController = loader.getController();

            // Configurar los datos del cliente
            itemController.setUser(name);
            itemController.setRootNode(itemPane);

            controllersReceive.put(itemPane, itemController);

            removeFromSendList(name);
            list_receive.getChildren().add(itemPane);

        } catch (Exception e) {
            System.err.println("Error al cargar las invitaciones recibidas");
            e.printStackTrace();
        }
    }

    public void removeFromReceiveList(String name) {
        Node toRemove = null;
        for (Node n : list_send.getChildren()) {
            if (controllersSend.get(n).getUserName().equals(name)) {
                toRemove = n;
                break;
            }
        }
        list_receive.getChildren().remove(toRemove);
    }

    public void removeFromSendList(String name) {
        for (Node n : list_send.getChildren()) {
            if (controllersSend.get(n).getUserName().equals(name)) {
                n.setDisable(true);
                break;
            }
        }
    }

    public void reactivateFromSendList(String name) {
        for (Node n : list_send.getChildren()) {
            if (controllersSend.get(n).getUserName().equals(name)) {
                n.setDisable(false);
                break;
            }
        }
    }

    public void addToSendInvitation(String name) {
        sendInvitations.add(name);
    }

    public void removeFromSendInvitation(String name) {
        sendInvitations.remove(name);
    }

    public void rejectAllPetitions() {

        if (list_receive.getChildren().isEmpty())
            return;

        for (Node n : list_receive.getChildren()) {
            controllersReceive.get(n).rejectInvitation();
        }
    }
}
