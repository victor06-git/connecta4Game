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

        final int windowWidth = 850;
        final int windowHeight = 700;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");

        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewOpponentSelection", "/assets/opponent_selection.fxml");
        // UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlOpponentSelection = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
        // ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

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

    public static <T> List<T> jsonArrayToList(JSONArray array, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            T value = clazz.cast(array.get(i));
            list.add(value);
        }
        return list;
    }

    /**
     * Function to connect to the WebSocket server
     * 
     */
    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");

        pauseDuring(1500, () -> { // Give time to show connecting message ...

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            playerName = ctrlConfig.txtPlayerName.getText(); // Nombre elegido por el jugador

            System.out.println(playerName); // DEBUG

            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);

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

                // Actualizar mi color basado en el cliente actual
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

                // AÑADIDO DE PRUEBA
                if (ctrlPlay != null) {
                    ctrlPlay.updateGameState(msgObj);
                }

                if (clients.size() == 1) {
                    ctrlWait.txtPlayer0.setText(clients.get(0).name);
                } else if (clients.size() > 1) {
                    ctrlWait.txtPlayer0.setText(clients.get(0).name);
                    ctrlWait.txtPlayer1.setText(clients.get(1).name);
                    ctrlPlay.title.setText(clients.get(0).name + " vs " + clients.get(1).name);
                }

                if (UtilsViews.getActiveView().equals("ViewConfig")) {
                    UtilsViews.setViewAnimating("ViewWait");
                }

                break;

            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);
                if (value == 0) {
                    UtilsViews.setViewAnimating("ViewPlay");
                    txt = "GO";
                }
                ctrlWait.txtTitle.setText(txt);
                break;

            // AÑADIDO DE PRUEBA
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
