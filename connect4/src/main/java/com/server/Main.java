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

    // Tipos de mensaje
    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving";
    private static final String T_CLIENT_PIECE_MOVING = "clientPieceMoving";
    private static final String T_CLIENT_PLAY = "clientPlay";
    private static final String T_CLIENT_SEND_INVITATION = "clientSendInvitation";
    private static final String T_CLIENT_ANSWER_INVITATION = "clientAnswerInvitation";
    private static final String T_CLIENT_REQUEST_PLAY = "clientRequestPlay";
    private static final String T_SERVER_DATA = "serverData";
    private static final String T_COUNTDOWN = "countdown";
    private static final String T_PLAY_ACCEPTED = "playAccepted";
    private static final String T_PLAY_REJECTED = "playRejected";
    private static final String T_GAME_STATE = "gameState";

    private final ClientRegistry clients;
    private final Map<String, ClientData> clientsData = new HashMap<>();
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

        double spacingX = (poolWidth - (piecesPerRow * pieceDiameter)) / (piecesPerRow + 1);
        double spacingY = pieceDiameter + 5;

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
            if (countdownRunning)
                return;
            if (clientsData.size() != REQUIRED_CLIENTS)
                return;
            countdownRunning = true;
        }

        new Thread(() -> {
            try {
                for (int i = 3; i >= 0; i--) {
                    if (clientsData.size() < REQUIRED_CLIENTS) {
                        synchronized (this) {
                            gameStarted = false;
                        }
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
        broadcastExcept(null, rst.toString());
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

        if (clientsData.size() == REQUIRED_CLIENTS) {
            sendCountdown();
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        clientsData.remove(name);

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