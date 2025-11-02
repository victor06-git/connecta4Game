package com.connect4;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.ClientData;
import com.shared.GameObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static String clientName = "";
    public static String playerName = ""; // Nombre elegido por el jugador
    public static String myColor = ""; // Color asignado por el servidor
    public static List<ClientData> clients = new ArrayList<>(); // Usuarios conectados
    public static List<GameObject> objects = new ArrayList<>(); // Objetos del juego

    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    public static CtrlPlay ctrlPlay;
    public static CtrlOpponentSelection ctrlOpponentSelection;
    public static CtrlResult ctrlResult;

    public static void main(String[] args) {

        // Iniciar app JavaFX
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 940;
        final int windowHeight = 700;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");

        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewOpponentSelection", "/assets/opponent_selection.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlOpponentSelection = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
        ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

        Scene scene = new Scene(UtilsViews.parentContainer);

        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Call close method when closing window
        stage.setTitle("JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }
    }

    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    /**
     * Function to connect to the WebSocket server
     * 
     */
    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.GREEN);
        ctrlConfig.txtMessage.setText("Connecting ...");
        ctrlConfig.txtMessage.setStyle("-fx-text-fill: green;");
        new animatefx.animation.BounceIn(ctrlConfig.txtMessage).play(); // Animación de entrada

        pauseDuring(1500, () -> { // Give time to show connecting message ...

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            playerName = ctrlConfig.txtPlayerName.getText(); // Nombre elegido por el jugador

            System.out.println(playerName); // DEBUG

            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port); // Create WebSocket client

            // Define playerName on open server connection
            wsClient.onOpen((response) -> {
                System.out.println("WebSocket conectado, enviando nombre del jugador: " + playerName);
                // Enviar el nombre del jugador al servidor
                JSONObject msgObj = new JSONObject();
                msgObj.put("type", "setPlayerName");
                msgObj.put("name", playerName);
                wsClient.safeSend(msgObj.toString());
                System.out.println("Mensaje enviado al servidor: " + msgObj.toString());
            });

            wsClient.onMessage((response) -> {
                Platform.runLater(() -> {
                    wsMessage(response);
                });
            });
            wsClient.onError((response) -> {
                Platform.runLater(() -> {
                    wsError(response);
                });
            });
        });
    }

    private static void wsMessage(String response) {

        JSONObject msgObj = new JSONObject(response);

        switch (msgObj.getString("type")) {

            case "clientName":
                clientName = msgObj.getString("value");
                break;

            case "serverData":
                clientName = msgObj.getString("clientName");

                JSONArray arrClients = msgObj.getJSONArray("clientsList");
                List<ClientData> newClients = new ArrayList<>();
                for (int i = 0; i < arrClients.length(); i++) {
                    JSONObject obj = arrClients.getJSONObject(i);
                    newClients.add(ClientData.fromJSON(obj));
                }

                clients = newClients;

                myColor = ""; // Reset primero
                for (ClientData client : clients) {
                    if (client.name.equals(clientName)) {
                        myColor = client.color;
                        break;
                    }
                }

                JSONArray arrObjects = msgObj.getJSONArray("objectsList");
                List<GameObject> newObjects = new ArrayList<>();
                for (int i = 0; i < arrObjects.length(); i++) {
                    JSONObject obj = arrObjects.getJSONObject(i);
                    newObjects.add(GameObject.fromJSON(obj));
                }
                objects = newObjects;

                if (ctrlPlay != null) {
                    ctrlPlay.updateGameState(msgObj);
                    // Initialize game objects map after objects are populated
                    ctrlPlay.initializeGameObjects();
                }

                if (clients.size() == 1) {
                    ctrlWait.txtPlayer0.setText(clients.get(0).name);
                } else if (clients.size() > 1) {
                    ctrlWait.txtPlayer0.setText(clients.get(0).name);
                    ctrlWait.txtPlayer1.setText(clients.get(1).name);
                    ctrlPlay.title.setText(clients.get(0).name + " vs " + clients.get(1).name);
                }

                if (UtilsViews.getActiveView().equals("ViewConfig")) {
                    UtilsViews.setViewAnimating("ViewOpponentSelection");
                }

                break;

            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);

                if (!UtilsViews.getActiveView().equals("ViewWait")) {
                    // Solo resetear si ya estuvimos en ViewPlay (segunda partida o posterior)
                    boolean wasInGame = UtilsViews.getActiveView().equals("ViewPlay") ||
                            UtilsViews.getActiveView().equals("ViewResult");

                    if (wasInGame && ctrlPlay != null) {
                        // Resetear solo si es una segunda partida
                        ctrlPlay.stop();
                        ctrlPlay.resetBoard();
                    }

                    // Sincronizar mi color
                    myColor = "";
                    for (ClientData client : clients) {
                        if (client.name.equals(clientName)) {
                            myColor = client.color;
                            break;
                        }
                    }

                    // Rechazar peticiones pendientes
                    ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection")).rejectAllPetitions();
                    UtilsViews.setView("ViewWait");
                }

                if (value == 0) {
                    // Asegurar que myColor esté sincronizado justo antes de iniciar
                    if (myColor.isEmpty()) {
                        for (ClientData client : clients) {
                            if (client.name.equals(clientName)) {
                                myColor = client.color;
                                break;
                            }
                        }
                    }

                    UtilsViews.setViewAnimating("ViewPlay");

                    // Iniciar el timer de animación
                    if (ctrlPlay != null) {
                        ctrlPlay.start();
                    }

                    txt = "GO";
                }
                ctrlWait.txtTitle.setText(txt);
                break;

            case "playAccepted":
                String pieceId = msgObj.getString("pieceId");
                int col = msgObj.getInt("column");
                int row = msgObj.getInt("row");
                boolean gameEnded = msgObj.getBoolean("gameEnded");
                String winner = msgObj.optString("winner", null);

                // Procesar coordenadas de línea ganadora si existen
                int[] winningLineCoords = null;
                if (msgObj.has("winningLineCoords") && !msgObj.isNull("winningLineCoords")) {
                    JSONArray coordsArray = msgObj.getJSONArray("winningLineCoords");
                    winningLineCoords = new int[coordsArray.length()];
                    for (int i = 0; i < coordsArray.length(); i++) {
                        winningLineCoords[i] = coordsArray.getInt(i);
                    }
                }

                if (ctrlPlay != null) {
                    ctrlPlay.handlePlayAccepted(pieceId, col, row, winner, winningLineCoords);
                }

                // Si el juego terminó
                if (gameEnded && winner != null) {
                    pauseDuring(1500, () -> {
                        String result = "";
                        if (winner.equals("DRAW")) {
                            result = "DRAW";
                        } else if (winner.equals(myColor)) {
                            result = "WIN";
                        } else {
                            result = "LOSE";
                        }

                        CtrlResult ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");
                        ctrlResult.setResultData(result, myColor, winner, ctrlPlay.boardState);
                        UtilsViews.setViewAnimating("ViewResult");
                    });
                }
                break;

            // AÑADIDO DE PRUEBA
            case "playRejected":
                String rejectedPieceId = msgObj.getString("pieceId");
                String reason = msgObj.optString("reason", "Invalid move");
                System.out.println("Play rejected: " + reason);
                if (ctrlPlay != null) {
                    ctrlPlay.handlePlayRejected(rejectedPieceId);
                }
                break;

            case "clientPieceMoving":
                // Recibir información de otro cliente moviendo una pieza
                if (ctrlPlay != null && msgObj.has("clientName")) {
                    String movingClientName = msgObj.getString("clientName");

                    // Solo procesar si no soy yo
                    if (!movingClientName.equals(clientName)) {
                        if (msgObj.has("hoveredColumn")) {
                            int otherClientHoverCol = msgObj.getInt("hoveredColumn");
                            if (otherClientHoverCol == -1) {
                                // El otro cliente soltó la pieza, limpiar su hover
                                ctrlPlay.clearOtherClientHover(movingClientName);
                            } else {
                                ctrlPlay.setOtherClientHover(movingClientName, otherClientHoverCol);
                            }
                        }
                    }
                }
                break;

            case "clientsList":
                JSONArray arr = msgObj.getJSONArray("clientsList");
                clients.clear();

                System.out.println("📋 Actualizando lista de clientes:");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject object = arr.getJSONObject(i);
                    System.out.println("  " + object);
                    String name = object.getString("name");
                    String color = object.getString("color");
                    boolean isPlaying = object.getBoolean("play");

                    ClientData cd = new ClientData(name, color);
                    cd.SetIsPlaying(isPlaying);

                    clients.add(cd);
                    System.out.println("  Cliente: " + name + " - isPlaying: " + isPlaying);
                }

                System.out.println("🔄 Recargando lista de envío...");
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection")).loadSendList();

                break;

            case "clientSendInvitation":
                String username = msgObj.getString("sendFrom");
                System.out.println("📧 clientSendInvitation recibido de: " + username);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .addFromReceiveList(username);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .addToSendInvitation(username);
                break;

            case "disableButtonFor":
                // El servidor me confirma que debo desactivar el botón de este usuario
                // porque le acabo de enviar una invitación
                String userToDisable = msgObj.getString("userName");
                System.out.println("🔒 disableButtonFor recibido para: " + userToDisable);
                System.out.println("   Yo soy: " + Main.clientName);
                System.out.println("   Debo desactivar botón de: " + userToDisable);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .addToSendInvitation(userToDisable);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .removeFromSendList(userToDisable);
                break;

            case "clientAnswerInvitation":
                System.out.println("📨 clientAnswerInvitation recibido:");
                System.out.println(msgObj);
                System.out.println("   Yo soy: " + Main.clientName);
                // Cuando alguien rechaza mi invitación:
                // - sendFrom: la persona que rechazó (ej: f)
                // - sendTo: yo, quien envió la invitación (ej: v)
                // Debo reactivar el botón de "sendFrom" (f) en MI lista (v)
                String whoRejected = msgObj.getString("sendFrom");
                System.out.println("👤 Reactivando botón de quien rechazó: " + whoRejected);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .removeFromSendInvitation(whoRejected);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .reactivateFromSendList(whoRejected);

                break;

            case "invitationRejectedByMe":
                System.out.println("🚫 invitationRejectedByMe recibido:");
                System.out.println(msgObj);
                // Cuando yo rechazo una invitación, el servidor me notifica
                // para reactivar el botón del usuario que me envió la invitación
                String userToReactivate = msgObj.getString("userName");
                System.out.println("👤 Reactivando botón de quien me envió invitación: " + userToReactivate);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .removeFromSendInvitation(userToReactivate);
                ((CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection"))
                        .reactivateFromSendList(userToReactivate);
                break;
        }
    }

    private static void wsError(String response) {
        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
