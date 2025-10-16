package com.server;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
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
    private String currentTurn = "RED"; // Comienza RED
    private boolean gameStarted = false;
    private volatile boolean countdownRunning = false;
    private static final int SEND_FPS = 30;
    private final ScheduledExecutorService ticker;

    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(PLAYER_NAMES);
        initializeBoard();
        initializeGameObjects();

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

    private void initializeGameObjects() {
        double poolX = 610;
        double poolY = 130;
        double poolWidth = 250;
        double pieceRadius = 80.0 * 0.40;
        double pieceDiameter = pieceRadius * 2;

        int piecesPerRow = 7;
        int numRows = 6;

        // Espaciado más compacto
        double spacingX = (poolWidth - (piecesPerRow * pieceDiameter)) / (piecesPerRow + 1);
        double spacingY = pieceDiameter + 5; // Reducido de 10 a 5

        int yellowCount = 0;
        int redCount = 0;

        for (int fila = 0; fila < numRows; fila++) {
            String colorPiece = (fila % 2 == 0) ? "YELLOW" : "RED";
            String prefix = (fila % 2 == 0) ? "Y_" : "R_";
            double startY = poolY + (fila * spacingY) + pieceRadius;

            for (int col = 0; col < piecesPerRow; col++) {
                // Solo crear hasta 21 por color
                if (colorPiece.equals("YELLOW") && yellowCount >= 21)
                    continue;
                if (colorPiece.equals("RED") && redCount >= 21)
                    continue;

                int index = colorPiece.equals("YELLOW") ? yellowCount : redCount;
                String id = prefix + index;

                double startX = poolX + spacingX + (col * (pieceDiameter + spacingX));
                double centerX = startX + pieceRadius;
                double centerY = startY;

                GameObject obj = new GameObject(id, centerX, centerY, pieceRadius, -1, -1);
                obj.color = colorPiece;
                gameObjects.put(obj.id, obj);

                if (colorPiece.equals("YELLOW"))
                    yellowCount++;
                else
                    redCount++;
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
        // Verificar que es el turno correcto
        if (!pieceId.startsWith(currentTurn.charAt(0) + "_")) {
            return false;
        }
        // Verificar que la columna tiene espacio
        return getLowestAvailableRow(col) != -1;
    }

    private void switchTurn() {
        currentTurn = currentTurn.equals("RED") ? "YELLOW" : "RED";
    }

    private synchronized String getColor() {
        return clients.snapshot().size() == 1 ? PLAYER_COLORS.get(0) : PLAYER_COLORS.get(1);
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
                    if (clientsData.size() < REQUIRED_CLIENTS)
                        break;
                    sendCountdownToAll(i);
                    if (i > 0)
                        Thread.sleep(750);
                }
                gameStarted = true;
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
            System.out.println("Client desconectado durante send: " + name);
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
        String name = clients.add(conn);
        String color = getColor();
        clientsData.put(name, new ClientData(name, color));
        System.out.println("WebSocket client connected: " + name + " (" + color + ")");

        if (clientsData.size() == REQUIRED_CLIENTS) {
            sendCountdown();
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        clientsData.remove(name);
        gameStarted = false;
        System.out.println("WebSocket client disconnected: " + name);
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
                clientsData.put(clientName, ClientData.fromJSON(obj.getJSONObject(K_VALUE)));
            }

            case T_CLIENT_PIECE_MOVING -> {
                GameObject objData = GameObject.fromJSON(obj.getJSONObject(K_VALUE));
                gameObjects.put(objData.id, objData);
            }

            case T_CLIENT_REQUEST_PLAY -> {
                if (!gameStarted)
                    return;

                String pieceId = obj.getString("pieceId");
                int col = obj.getInt("column");

                if (isValidPlay(pieceId, col)) {
                    // Calcular la fila más baja disponible
                    int row = getLowestAvailableRow(col);

                    if (row != -1) {
                        // Aceptar jugada
                        boardState[row][col] = pieceId;
                        GameObject piece = gameObjects.get(pieceId);
                        if (piece != null) {
                            piece.row = row;
                            piece.col = col;
                        }

                        JSONObject response = msg(T_PLAY_ACCEPTED)
                                .put("pieceId", pieceId)
                                .put("column", col)
                                .put("row", row);
                        sendSafe(conn, response.toString());

                        switchTurn();
                    } else {
                        // Columna llena
                        JSONObject response = msg(T_PLAY_REJECTED)
                                .put("pieceId", pieceId)
                                .put("reason", "Column is full");
                        sendSafe(conn, response.toString());
                    }
                } else {
                    // Rechazar jugada
                    JSONObject response = msg(T_PLAY_REJECTED)
                            .put("pieceId", pieceId)
                            .put("reason", "Not your turn or invalid move");
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
            System.out.println("Deteniendo servidor...");
            try {
                server.stopTicker();
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor detenido.");
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