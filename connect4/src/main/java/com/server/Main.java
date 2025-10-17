package com.server;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.ClientData;
import com.shared.GameObject;

public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;

    private static final List<String> PLAYER_NAMES = Arrays.asList("Alejandro", "Victor");
    private static final List<String> PLAYER_COLORS = Arrays.asList("RED", "YELLOW");
    private static final int REQUIRED_CLIENTS = 2;

    // Claves JSON
    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_CLIENTS_LIST = "clientsList";
    private static final String K_OBJECTS_LIST = "objectsList";
    private static final String K_CURRENT_TURN = "currentTurn";
    private static final String K_BOARD_STATE = "boardState";

    
    private static final String T_CLIENT_REQUEST_PLAY = "clientRequestPlay";
    private static final String T_PLAY_ACCEPTED = "playAccepted";
    private static final String T_PLAY_REJECTED = "playRejected";
    private static final String T_GAME_STATE = "gameState";

    // Tipus de missatge nous i (alguns) heretats
    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving";            // client -> server
    private static final String T_CLIENT_PIECE_MOVING = "clientPieceMoving";            // client -> server
    private static final String T_CLIENT_PLAY = "clientPlay";                           // client -> server
    private static final String T_CLIENT_SEND_INVITATION = "clientSendInvitation";      // client -> server
    private static final String T_CLIENT_ANSWER_INVITATION = "clientAnswerInvitation";  // client -> server
    private static final String T_SERVER_DATA = "serverData";                           // server -> clients
    private static final String T_SERVER_CLIENTS_LIST = "clientsList";                           // server -> clients
    private static final String T_COUNTDOWN = "countdown";                              // server -> clients

    /** Registre de clients i assignació de noms (pool integrat). */
    private final ClientRegistry clients;
    private final Map<String, ClientData> clientsData = new HashMap<>();

    /*+ Llista amb els jugadors que estaran a la partida */
    private final List<String> playersNames = new ArrayList<>();

    /** Mapa d'objectes seleccionables compartits. */
    private final Map<String, GameObject> gameObjects = new HashMap<>();

    private String[][] boardState = new String[6][7];
    private String currentTurn = "RED";
    private boolean gameStarted = false;
    private volatile boolean countdownRunning = false;

    private static final int SEND_FPS = 30;
    private final ScheduledExecutorService ticker;

    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(PLAYER_NAMES);
        initializeBoard();
        initializegameObjects();

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(tf);
    }

    private void initializeBoard() {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 7; j++) {
                boardState[i][j] = null;
            }
        }
    }

    private void initializegameObjects() {
        double poolX = 610;
        double poolY = 130;
        double poolWidth = 250;
        double pieceRadius = 80.0 * 0.15;
        double pieceDiameter = pieceRadius * 2;

        int piecesPerRow = 7;
        int numRows = 6;

        double spacingX = (poolWidth - (piecesPerRow * pieceDiameter - 10)) / (piecesPerRow + 1);
        double spacingY = pieceDiameter + 10;

        int yellowCount = 0;
        int redCount = 0;

        for (int fila = 0; fila < numRows; fila++) {
            String colorPiece = (fila % 2 == 0) ? "YELLOW" : "RED";
            String prefix = (fila % 2 == 0) ? "Y_" : "R_";
            double startY = poolY + (fila * spacingY) + pieceRadius;

            for (int col = 0; col < piecesPerRow; col++) {
                if (colorPiece.equals("YELLOW") && yellowCount >= 21) {
                    continue;
                }
                if (colorPiece.equals("RED") && redCount >= 21) {
                    continue;
                }

                int index = colorPiece.equals("YELLOW") ? yellowCount : redCount;
                String id = prefix + index;

                double startX = poolX + spacingX + (col * (pieceDiameter + spacingX));
                double centerX = startX + pieceRadius;
                double centerY = startY;

                GameObject obj = new GameObject(id, centerX, centerY, pieceRadius, -1, -1);
                obj.color = colorPiece;
                gameObjects.put(obj.id, obj);

                if (colorPiece.equals("YELLOW")) {
                    yellowCount++;
                } else {
                    redCount++;
                }
            }
        }
    }

    private int getLowestAvailableRow(int col) {
        for (int row = 5; row >= 0; row--) {
            if (boardState[row][col] == null) {
                return row;
            }
        }
        return -1;
    }

    private boolean isValidPlay(String pieceId, int col) {
        if (!pieceId.startsWith(currentTurn.charAt(0) + "_")) {
            return false;
        }
        return getLowestAvailableRow(col) != -1;
    }

    private void switchTurn() {
        currentTurn = currentTurn.equals("RED") ? "YELLOW" : "RED";
    }

    private void sendCountdown() {
        synchronized (this) {
            if (countdownRunning) return;
            if (playersNames.size() != REQUIRED_CLIENTS) return;
            countdownRunning = true;
        }

        new Thread(() -> {
            try {
                for (int i = 3; i >= 0; i--) {
                    // Si durant el compte enrere ja no hi ha els clients requerits, cancel·la
                    if (playersNames.size() < REQUIRED_CLIENTS) {
                      gameStarted = false;
                        break;
                    }

                    sendCountdownToAll(i);

                    if (i == 0) {
                        synchronized (this) {
                            gameStarted = true;
                            currentTurn = "RED";
                        }
                        System.out.println("Game started! Turn: " + currentTurn);
                    }

                    if (i > 0)
                        Thread.sleep(750);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                countdownRunning = false;
            }
        }, "CountdownThread").start();
    }

    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    private void sendSafe(WebSocket to, String payload) {
        if (to == null)
            return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            clientsData.remove(name);
            System.out.println("Client desconnectat durant send: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!clientsData.containsKey(e.getValue()))
                continue;
            if (!Objects.equals(conn, sender))
                sendSafe(conn, payload);
        }
    }

    /** Envia un missatge a tots els jugadors. */
    private void broadcastExcept(String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();

            if (!playersNames.contains((e.getValue()))) continue;
            sendSafe(conn, payload);
        }
    }

    private void broadcastStatus() {
        JSONArray arrClients = new JSONArray();
        for (ClientData c : clientsData.values()) {
            arrClients.put(c.toJSON());
        }

        JSONArray arrObjects = new JSONArray();
        for (GameObject obj : gameObjects.values()) {
            arrObjects.put(obj.toJSON());
        }

        JSONArray arrBoard = new JSONArray();
        for (int i = 0; i < 6; i++) {
            JSONArray row = new JSONArray();
            for (int j = 0; j < 7; j++) {
                row.put(boardState[i][j] != null ? boardState[i][j] : JSONObject.NULL);
            }
            arrBoard.put(row);
        }

        JSONObject rst = msg(T_SERVER_DATA)
                .put(K_CLIENTS_LIST, arrClients)
                .put(K_OBJECTS_LIST, arrObjects)
                .put(K_CURRENT_TURN, currentTurn)
                .put(K_BOARD_STATE, arrBoard);

        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            String name = clients.nameBySocket(conn);
            rst.put(K_CLIENT_NAME, name);
            sendSafe(conn, rst.toString());
        }
    }

    private void sendCountdownToAll(int n) {
        JSONObject rst = msg(T_COUNTDOWN).put(K_VALUE, n);
        broadcastExcept(rst.toString());
    }

    private String sendAllClients() {
        JSONObject response = msg(T_SERVER_CLIENTS_LIST);
        JSONArray clientsDataArray = new JSONArray();

        for (ClientData cd : clientsData.values()) {
            JSONObject clientData = new JSONObject();
            clientData.put("name", cd.name);
            clientData.put("play", cd.isPlaying);
            clientsDataArray.put(clientData);
        }

        response.put(K_CLIENTS_LIST, clientsDataArray);

        return response.toString();
    }

    private void sendClientName(WebSocket conn, String name) {
        JSONObject response = msg(K_CLIENT_NAME);
        response.put(K_VALUE, name);
        sendSafe(conn, response.toString());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // CRÍTICO: El índice debe calcularse ANTES de añadir el cliente
        int clientIndex = clientsData.size();

        // Añadir cliente al registro (obtiene nombre)
        String name = clients.add(conn);

        // Asignar color según el índice
        String color;
        if (clientIndex == 0) {
            color = "RED";
        } else if (clientIndex == 1) {
            color = "YELLOW";
        } else {
            color = "GRAY";
        }

        // IMPORTANTE: Crear ClientData con el color correcto
        ClientData clientData = new ClientData(name, color);
        clientsData.put(name, clientData);

        System.out.println("==============================================");
        System.out.println("WebSocket client connected!");
        System.out.println("  Name: " + name);
        System.out.println("  Index: " + clientIndex);
        System.out.println("  Assigned Color: " + color);
        System.out.println("  Total clients: " + clientsData.size());
        System.out.println("==============================================");

        sendClientName(conn, name);
        broadcastExcept(null, sendAllClients());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        clientsData.remove(name);
        playersNames.remove(name);
        broadcastExcept(null, sendAllClients());

        synchronized (this) {
            gameStarted = false;
            initializeBoard();
            currentTurn = "RED";
        }

        System.out.println("WebSocket client disconnected: " + name);
        System.out.println("Game reset due to player disconnection");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return;
        }

        String type = obj.optString(K_TYPE, "");
        switch (type) {
            case T_CLIENT_MOUSE_MOVING -> {
                String clientName = clients.nameBySocket(conn);
                ClientData updatedData = ClientData.fromJSON(obj.getJSONObject(K_VALUE));
                // MANTENER EL COLOR ORIGINAL
                ClientData existingData = clientsData.get(clientName);
                if (existingData != null) {
                    updatedData.color = existingData.color;
                }
                clientsData.put(clientName, updatedData);
            }

            case T_CLIENT_PIECE_MOVING -> {
                GameObject objData = GameObject.fromJSON(obj.getJSONObject(K_VALUE));
                gameObjects.put(objData.id, objData);
            }

            case T_CLIENT_PLAY -> {
                // Gestionar la jugada

                sendCountdown();
            }

            case T_CLIENT_REQUEST_PLAY -> {
                if (!gameStarted) {
                    JSONObject response = msg(T_PLAY_REJECTED)
                            .put("pieceId", obj.optString("pieceId", ""))
                            .put("reason", "Game not started yet");
                    sendSafe(conn, response.toString());
                    return;
                }

                String pieceId = obj.getString("pieceId");
                int col = obj.getInt("column");

                String playerName = clients.nameBySocket(conn);
                ClientData playerData = clientsData.get(playerName);

                System.out.println("Play request: Player=" + playerName + ", Color=" + playerData.color +
                        ", Piece=" + pieceId + ", CurrentTurn=" + currentTurn);

                if (playerData == null || !playerData.color.equals(currentTurn)) {
                    JSONObject response = msg(T_PLAY_REJECTED)
                            .put("pieceId", pieceId)
                            .put("reason", "Not your turn! Current turn: " + currentTurn);
                    sendSafe(conn, response.toString());
                    return;
                }
              
            case T_CLIENT_SEND_INVITATION -> {
                // Rebem una petició amb el nom de l'usuari i destinatari a enviar la petició
                // Rebem l'usuari a qui hem d'enviar la petició
                String receiver = obj.getString("sendTo");

                // Enviem a l'usuari rebut, la petició d'invitació
                sendSafe(clients.socketByName(receiver), obj.toString());
            }

            case T_CLIENT_ANSWER_INVITATION -> {
                // Rebem una petició amb el nom de l'usuari i destinatari a enviar la petició i un boolà amb la resposta
                System.out.println(obj);
                if (obj.getBoolean(K_VALUE)) {
                    // SI ACCEPTA
                    // Comencen countdown per a la partida
                    String pRed = obj.getString("sendFrom");
                    String pYellow = obj.getString("sendTo");

                    playersNames.add(pRed);
                    playersNames.add(pYellow);
                    clientsData.get(pRed).SetIsPlaying(true);
                    clientsData.get(pYellow).SetIsPlaying(true);

                    broadcastExcept(null, sendAllClients());
                    sendCountdown();
                }
              
                else {
                    // SI NO ACCEPTA
                    // Enviem a l'usuari que ha fet la peticiól a resposta de l'invitació
                    String sender = obj.getString("sendFrom");
                    sendSafe(clients.socketByName(sender), obj.toString());
                }
            }

                if (isValidPlay(pieceId, col)) {
                    int row = getLowestAvailableRow(col);

                    if (row != -1) {
                        boardState[row][col] = pieceId;
                        GameObject piece = gameObjects.get(pieceId);
                        if (piece != null) {
                            piece.row = row;
                            piece.col = col;
                        }

                        switchTurn();

                        System.out.println("Play accepted: " + pieceId + " at [" + row + "," + col + "]. Next turn: "
                                + currentTurn);

                        JSONObject response = msg(T_PLAY_ACCEPTED)
                                .put("pieceId", pieceId)
                                .put("column", col)
                                .put("row", row);
                        sendSafe(conn, response.toString());
                    } else {
                        JSONObject response = msg(T_PLAY_REJECTED)
                                .put("pieceId", pieceId)
                                .put("reason", "Column is full");
                        sendSafe(conn, response.toString());
                    }
                } else {
                    JSONObject response = msg(T_PLAY_REJECTED)
                            .put("pieceId", pieceId)
                            .put("reason", "Invalid piece for current turn");
                    sendSafe(conn, response.toString());
                }
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100);
        startTicker();
    }

    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stopTicker();
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void startTicker() {
        long periodMs = Math.max(1, 1000 / SEND_FPS);
        ticker.scheduleAtFixedRate(() -> {
            try {
                if (!clients.snapshot().isEmpty()) {
                    broadcastStatus();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stopTicker() {
        try {
            ticker.shutdownNow();
            ticker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop it.");
        awaitForever();
    }
}