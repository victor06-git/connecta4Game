package com.connect4;

import java.net.URL;

import com.shared.ClientData;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;

public class CtrlOpponentSelection {

    @FXML
    private VBox list_send, list_receive;

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

                    itemController.setUser(client.name);
                    // itemController.setImage("/assets/images/default-avatar.png"); //User Image

                    list_send.getChildren().add(itemPane);
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
    }
}