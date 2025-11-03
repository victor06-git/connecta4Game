package com.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
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

    private static final String T_CLIENT_SEND_INVITATION = "clientSendInvitation";
    private static final String T_CLIENT_ANSWER_INVITATION = "clientAnswerInvitation";
    private static final String T_CLIENT_REQUEST_PLAY = "clientRequestPlay";

    private static final String T_SERVER_DATA = "serverData";
    // server -> clients
    private static final String T_SERVER_CLIENTS_LIST = "clientsList";
    private static final String T_COUNTDOWN = "countdown";

    private static final String T_SET_PLAYER_NAME = "setPlayerName"; // aconsegueix el nom del jugador

    private static final String T_PLAY_ACCEPTED = "playAccepted";
    private static final String T_PLAY_REJECTED = "playRejected";

    private final ClientRegistry clients;
    private final List<String> playersNames = new ArrayList<>();
    private final Map<String, ClientData> clientsData = new HashMap<>();
    private final Map<String, GameObject> gameObjects = new HashMap<>();
    private final Map<String, GameObject> originalPoolPositions = new HashMap<>(); // Store original positions

    private String[][] boardState = new String[6][7];
    private String currentTurn = "";
    private boolean gameStarted = false;
    private volatile boolean countdownRunning = false;

    // Variables para controlar el estado del ganador
    private boolean gameEnded = false;
    private String winnerColor = null;
    private int[] winningLineCoords = null;

    private static final int SEND_FPS = 30;
    private final ScheduledExecutorService ticker;

    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(new ArrayList<>()); // Inicializa sin nombres predefinidos
        resetBoard();
        initializeGameObjects();

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(tf);
    }

    /**
     * Initialize the board state
     */
    public void resetBoard() {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 7; j++) {
                boardState[i][j] = null;
            }
        }
    }

    /**
     * Initialize game pieces in the pool
     */
    private void initializeGameObjects() {
        double poolX = 650;
        double poolY = 125;
        double poolWidth = 200;
        double pieceRadius = 80.0 * 0.15; // Radius of each piece

        int piecesPerRow = 7;
        int numRows = 6;

        double marginX = 50;
        double marginY = 12;
        double availableWidth = poolWidth - (2 * marginX);
        double spacingX = availableWidth / piecesPerRow + 6;
        double spacingY = 83; // vertical spacing between rows

        int yellowCount = 0; // piece counter yellow
        int redCount = 0; // piece counter red
        for (int fila = 0; fila < numRows; fila++) {
            for (int col = 0; col < piecesPerRow; col++) {
                double centerX = poolX + marginX + (col + 0.5) * spacingX;
                double centerY = poolY + marginY + fila * spacingY;

                String color = (fila % 2 == 0) ? "RED" : "YELLOW";
                String id = (color.equals("RED") ? "R_" : "Y_") + (color.equals("RED") ? redCount++ : yellowCount++);

                GameObject piece = new GameObject(id, centerX, centerY, pieceRadius, -1, -1);
                piece.color = color;
                gameObjects.put(id, piece);

                // Store original position for reset purposes
                originalPoolPositions.put(id,
                        new GameObject(id, centerX, centerY, pieceRadius, -1, -1));
            }
        }
    }

    /**
     * Get the lowest available row in a column
     * 
     * @param col
     * @return row index or -1 if full
     */
    private int getLowestAvailableRow(int col) {
        for (int row = 5; row >= 0; row--) {
            if (boardState[row][col] == null) {
                return row;
            }
        }
        return -1;
    }

    /**
     * Check if the play is valid
     * 
     * @param pieceId
     * @param col
     * @return true if valid play
     */
    private boolean isValidPlay(String pieceId, int col) {
        if (!pieceId.startsWith(currentTurn.charAt(0) + "_")) {
            return false;
        }
        return getLowestAvailableRow(col) != -1;
    }

    /**
     * Switch turn to the next player
     */
    private void switchTurn() {
        currentTurn = currentTurn.equals("RED") ? "YELLOW" : "RED";
    }

    /**
     * Returns a piece to its original position in the pool
     * 
     * @param piece the piece to return
     */
    private void returnPieceToOriginalPosition(GameObject piece) {
        if (piece == null || !originalPoolPositions.containsKey(piece.id)) {
            return;
        }

        GameObject original = originalPoolPositions.get(piece.id); // Get original position
        piece.center_x = original.center_x;
        piece.center_y = original.center_y;
        piece.col = -1;
        piece.row = -1;
    }

    /**
     * Send countdown to all clients and start the game
     */
    private void sendCountdown() {
        synchronized (this) {
            if (countdownRunning) {
                System.out.println("Countdown already running, skipping...");
                return;
            }
            if (clientsData.size() != REQUIRED_CLIENTS) {
                System.out.println("Not enough players for countdown: " + clientsData.size() + "/" + REQUIRED_CLIENTS);
                return;
            }

            // Resetear el estado del juego antes de comenzar
            System.out.println("🔄 Resetting game state for new match...");
            gameStarted = false;
            gameEnded = false;
            winnerColor = null;
            winningLineCoords = null;
            resetBoard();
            initializeGameObjects(); // Reinicializar las piezas en el pool
            currentTurn = null;

            countdownRunning = true;
            System.out.println("Starting countdown sequence...");
        }

        new Thread(() -> {
            try {
                for (int i = 3; i >= 0; i--) {
                    if (clientsData.size() < REQUIRED_CLIENTS) {
                        synchronized (this) {
                            gameStarted = false;
                            countdownRunning = false;
                        }
                        System.out.println("Countdown cancelled: player disconnected");
                        break;
                    }

                    System.out.println("Countdown: " + i);
                    sendCountdownToAll(i);

                    if (i == 0) {
                        synchronized (this) {
                            gameStarted = true;
                            currentTurn = "RED";
                        }
                        System.out.println("Game started! Turn: " + currentTurn);
                        broadcastStatus(); // Enviamos el estado inicial del juego
                    }

                    if (i > 0)
                        Thread.sleep(1000); // Aumentado a 1 segundo para mejor visibilidad
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                countdownRunning = false;
            }
        }, "CountdownThread").start();
    }

    /**
     * Create a basic message JSON object
     * 
     * @param type
     * @return JSON object
     */
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    /**
     * Send a message safely to a client
     * 
     * @param to
     * @param payload
     */
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

    /**
     * Broadcast a message to all clients except the sender.
     * 
     * @param sender
     * @param payload
     */
    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!clientsData.containsKey(e.getValue()))
                continue;
            if (!Objects.equals(conn, sender))
                sendSafe(conn, payload);
        }
    }

    /**
     * Broadcast the current game status to all clients.
     * 
     */
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
            sendSafe(conn, rst.toString()); // Send personalized message to each client
        }
    }

    /**
     * Send countdown number to all clients
     * 
     * @param n countdown number
     */
    private void sendCountdownToAll(int n) {
        JSONObject rst = msg(T_COUNTDOWN).put(K_VALUE, n);
        broadcastExcept(null, rst.toString());
    }

    /**
     * Handles new client connection when opened.
     */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("==============================================");
        System.out.println("New client connected! Waiting for player name...");
        System.out.println("==============================================");

        JSONObject welcome = new JSONObject();
        welcome.put("type", "welcome");
        welcome.put("message", "Conectado al servidor. Por favor, envía tu nombre.");
        sendSafe(conn, welcome.toString());
    }

    /**
     * Send a list of all connected clients to the requester.
     * 
     * @return JSON string with clients list
     */
    private String sendAllClients() {
        JSONObject response = msg(T_SERVER_CLIENTS_LIST);
        JSONArray clientsDataArray = new JSONArray();

        for (ClientData cd : clientsData.values()) {
            JSONObject clientData = new JSONObject();
            clientData.put("name", cd.name);
            clientData.put("play", cd.isPlaying);
            clientData.put("color", cd.color);
            clientsDataArray.put(clientData);
        }

        response.put(K_CLIENTS_LIST, clientsDataArray);

        return response.toString();
    }

    /**
     * Send client name to the connected client.
     * 
     * @param conn
     * @param name
     */
    private void sendClientName(WebSocket conn, String name) {
        JSONObject response = msg(K_CLIENT_NAME);
        response.put(K_VALUE, name);
        sendSafe(conn, response.toString());
    }

    /**
     * Handles client disconnection and resets the game state.
     */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.nameBySocket(conn);

        if (playersNames.contains(name)) { 
            String toSendName;
            
            if (playersNames.get(0).equals(name)) {
                toSendName = playersNames.get(1);
            } else {
                toSendName = playersNames.get(0);
            }

            JSONObject response = msg("clientDisconnected");
            response.put("winner", toSendName  + " by disconnection");
            sendSafe(clients.socketByName(toSendName), response.toString());

            System.out.println(response);
        }

        clients.remove(conn);
        clientsData.remove(name);
        playersNames.remove(name);
        broadcastExcept(null, sendAllClients());

        synchronized (this) {
            gameStarted = false;
            gameEnded = false;
            winnerColor = null;
            resetBoard(); // Reset board
            currentTurn = null;
        }

        System.out.println("WebSocket client disconnected: " + name);
        System.out.println("Game reset due to player disconnection");
    }

    /**
     * Handles incoming messages from clients.
     * 
     * @param conn
     * @param message
     */
    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return;
        }

        String type = obj.optString(K_TYPE, "");
        System.out.println("Mensaje recibido del cliente tipo: " + type);

        switch (type) {
            case T_SET_PLAYER_NAME: {
                String playerName = obj.getString("name");

                // Asignar color según el orden de conexión
                String color = (clientsData.isEmpty()) ? "YELLOW" : (clientsData.size() == 1) ? "RED" : "YELLOW";

                // Registrar el jugador con su color
                ClientData clientData = new ClientData(playerName, color);
                clientsData.put(playerName, clientData);

                // Registrar el nombre del cliente
                clients.setName(conn, playerName);

                sendClientName(conn, playerName);
                broadcastExcept(null, sendAllClients());

                break;
            }

            case T_CLIENT_MOUSE_MOVING: {
                String clientName = clients.nameBySocket(conn);
                ClientData updatedData = ClientData.fromJSON(obj.getJSONObject(K_VALUE));
                // MANTENER EL COLOR ORIGINAL
                ClientData existingData = clientsData.get(clientName);
                if (existingData != null) {
                    updatedData.color = existingData.color;
                }
                clientsData.put(clientName, updatedData);
                break;
            }

            case T_CLIENT_PIECE_MOVING: {
                GameObject objData = GameObject.fromJSON(obj.getJSONObject(K_VALUE));
                gameObjects.put(objData.id, objData);

                // Reenviar el mensaje a todos los clientes incluyendo hoveredColumn si existe
                JSONObject broadcastMsg = new JSONObject();
                broadcastMsg.put("type", T_CLIENT_PIECE_MOVING);
                broadcastMsg.put("value", objData.toJSON());

                // Añadir información del cliente que está moviendo la pieza
                String clientName = clients.nameBySocket(conn);
                broadcastMsg.put("clientName", clientName);

                // Añadir columna hover si existe
                if (obj.has("hoveredColumn")) {
                    broadcastMsg.put("hoveredColumn", obj.getInt("hoveredColumn"));
                }

                // Obtener el color del cliente
                ClientData clientData = clientsData.get(clientName);
                if (clientData != null) {
                    broadcastMsg.put("clientColor", clientData.color);
                }

                broadcastExcept(conn, broadcastMsg.toString());
                break;
            }

            case T_CLIENT_SEND_INVITATION: {
                // Rebem una petició amb el nom de l'usuari i destinatari a enviar la petició
                String receiver = obj.getString("sendTo");

                // 1. Enviem la invitació al receptor
                sendSafe(clients.socketByName(receiver), obj.toString());

                // 2. Notificar al remitent que debe desactivar el botón del receptor
                JSONObject confirmToSender = new JSONObject();
                confirmToSender.put("type", "disableButtonFor");
                confirmToSender.put("userName", receiver);
                sendSafe(conn, confirmToSender.toString());

                break;
            }

            case T_CLIENT_ANSWER_INVITATION: {
                // Rebem una petició amb el nom de l'usuari i destinatari a enviar la petició i
                // un boolà amb la resposta
                System.out.println(obj);
                if (obj.getBoolean(K_VALUE)) {
                    // SI ACCEPTA
                    // Comencen countdown per a la partida
                    String p1 = obj.getString("sendFrom");
                    String p2 = obj.getString("sendTo");

                    playersNames.add(p1);
                    playersNames.add(p2);
                    clientsData.get(p1).SetIsPlaying(true);
                    clientsData.get(p2).SetIsPlaying(true);

                    sendSafe(clients.socketByName(p2), obj.toString());

                    broadcastExcept(null, sendAllClients());
                    sendCountdown();
                }

                else {
                    // SI NO ACCEPTA
                    // Enviem a l'usuari que ha fet la petició original la resposta de l'invitació
                    // String whoRejected = obj.getString("sendFrom"); // quien rechazó
                    String originalSender = obj.getString("sendTo"); // quien envió originalmente

                    // Enviar resposta al remitent original (quien envió la invitación)
                    sendSafe(clients.socketByName(originalSender), obj.toString());

                    // També notificar al receptor (qui va rebutjar) per reactivar el seu botó
                    JSONObject reactivateMsg = new JSONObject();
                    reactivateMsg.put("type", "invitationRejectedByMe");
                    reactivateMsg.put("userName", originalSender);
                    sendSafe(conn, reactivateMsg.toString());
                }
                break;
            }

            case T_CLIENT_REQUEST_PLAY: {
                if (!gameStarted) {
                    JSONObject response = msg(T_PLAY_REJECTED)
                            .put("pieceId", obj.optString("pieceId", ""))
                            .put("reason", "Game not started yet");
                    sendSafe(conn, response.toString());
                    return;
                }

                if (gameEnded) {
                    JSONObject response = msg(T_PLAY_REJECTED)
                            .put("pieceId", obj.optString("pieceId", ""))
                            .put("reason", "Game has ended");
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

                        checkWinner();

                        switchTurn();

                        System.out.println("Play accepted: " + pieceId + " at [" + row + "," + col + "]. Next turn: "
                                + currentTurn);

                        JSONObject response = msg(T_PLAY_ACCEPTED)
                                .put("pieceId", pieceId)
                                .put("column", col)
                                .put("row", row)
                                .put("gameEnded", gameEnded)
                                .put("winner", winnerColor != null ? winnerColor : JSONObject.NULL);

                        // Si hay ganador, enviar las coordenadas de la línea ganadora
                        if (winnerColor != null && !winnerColor.equals("DRAW") && winningLineCoords != null) {
                            JSONArray winningCoords = new JSONArray();
                            for (int coord : winningLineCoords) {
                                winningCoords.put(coord);
                            }
                            response.put("winningLineCoords", winningCoords);
                        }

                        // Enviar a TODOS los clientes, no solo al que jugó
                        broadcastExcept(null, response.toString());
                    } else {
                        // Column is full: return piece to pool
                        returnPieceToOriginalPosition(gameObjects.get(pieceId));
                        JSONObject response = msg(T_PLAY_REJECTED)
                                .put("pieceId", pieceId)
                                .put("reason", "Column is full");
                        sendSafe(conn, response.toString());
                    }
                } else {
                    // Invalid play: return piece to original position in pool
                    returnPieceToOriginalPosition(gameObjects.get(pieceId));
                    JSONObject response = msg(T_PLAY_REJECTED)
                            .put("pieceId", pieceId)
                            .put("reason", "Invalid piece for current turn");
                    sendSafe(conn, response.toString());
                }
                break;
            }

            case "gameEnded": {
                // Un cliente notifica que ha terminado el juego y vuelve a la selección
                String clientName = obj.optString("clientName", "");

                if (!clientName.isEmpty() && clientsData.containsKey(clientName)) {
                    System.out.println("Client " + clientName + " ended the game");

                    // Poner isPlaying a false para TODOS los jugadores que estaban jugando
                    for (String playerName : playersNames) {
                        if (clientsData.containsKey(playerName)) {
                            System.out.println("Setting isPlaying=false for: " + playerName);
                            clientsData.get(playerName).SetIsPlaying(false);
                        }
                    }

                    // Limpiar la lista de jugadores activos
                    playersNames.clear();

                    // Resetear el estado del juego
                    synchronized (this) {
                        gameStarted = false;
                        gameEnded = false;
                        winnerColor = null;
                        winningLineCoords = null;
                        resetBoard();
                        currentTurn = null;
                    }

                    // Notificar a TODOS los clientes con la lista actualizada
                    System.out.println("Broadcasting updated client list");
                    broadcastExcept(null, sendAllClients());

                    System.out.println("Game ended, all players reset to not playing");
                }
                break;
            }
        }

    }

    /**
     * Handles errors that occur on the WebSocket connection.
     * 
     * @param conn
     * @param ex   exception
     */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    /**
     * Called when the server starts.
     */
    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100); // Set high timeout to avoid disconnections
        startTicker(); // Start the ticker to broadcast game status
    }

    /**
     * Registers a shutdown hook for the server.
     * 
     * @param server
     */
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

    /**
     * Awaits indefinitely to keep the server running.
     */
    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts the ticker to broadcast game status at fixed intervals.
     */
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

    /**
     * Stops the ticker.
     */
    private void stopTicker() {
        try {
            ticker.shutdownNow();
            ticker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Function that checks the winner
     * 
     */
    private void checkWinner() {
        // Horizontal
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 4; col++) {
                String piece = getColorPiece(boardState[row][col]);
                if (piece != null && piece.equals(getColorPiece(boardState[row][col + 1]))
                        && piece.equals(getColorPiece(boardState[row][col + 2]))
                        && piece.equals(getColorPiece(boardState[row][col + 3]))) {
                    winningLineCoords = new int[] { row, col, row, col + 3 };
                    winnerColor = piece;
                    gameEnded = true;
                    return;
                }
            }
        }

        // Vertical
        for (int col = 0; col < 7; col++) {
            for (int row = 0; row < 3; row++) { // Solo hasta row 2 (0,1,2 -> verifica hasta row 5)
                String piece = getColorPiece(boardState[row][col]);
                if (piece != null &&
                        piece.equals(getColorPiece(boardState[row + 1][col])) &&
                        piece.equals(getColorPiece(boardState[row + 2][col])) &&
                        piece.equals(getColorPiece(boardState[row + 3][col]))) {

                    winningLineCoords = new int[] { row, col, row + 3, col };
                    winnerColor = piece;
                    gameEnded = true;
                    return;
                }
            }
        }

        // Diagonal a la izquierda (\)
        for (int row = 0; row <= 2; row++) {
            for (int col = 0; col <= 3; col++) {
                String piece = getColorPiece(boardState[row][col]);
                if (piece != null &&
                        piece.equals(getColorPiece(boardState[row + 1][col + 1])) &&
                        piece.equals(getColorPiece(boardState[row + 2][col + 2])) &&
                        piece.equals(getColorPiece(boardState[row + 3][col + 3]))) {

                    winningLineCoords = new int[] { row, col, row + 3, col + 3 };
                    winnerColor = piece;
                    gameEnded = true;
                    return;
                }
            }
        }

        // Diagonal a la derecha (/)
        for (int row = 3; row <= 5; row++) { // Empezar desde row 3 hacia abajo
            for (int col = 0; col <= 3; col++) {
                String piece = getColorPiece(boardState[row][col]);
                if (piece != null &&
                        piece.equals(getColorPiece(boardState[row - 1][col + 1])) &&
                        piece.equals(getColorPiece(boardState[row - 2][col + 2])) &&
                        piece.equals(getColorPiece(boardState[row - 3][col + 3]))) {

                    winningLineCoords = new int[] { row, col, row - 3, col + 3 };
                    winnerColor = piece;
                    gameEnded = true;
                    return;
                }
            }
        }

        boolean isBoardFull = true;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                if (boardState[row][col] == null) {
                    isBoardFull = false;
                    break;
                }
            }
        }
        if (isBoardFull) {
            gameEnded = true;
            winnerColor = "DRAW"; // Indicate a draw
            return;
        }
    }

    /**
     * Get the color of the piece for checking winner
     * 
     * @param piece
     * @return
     */
    private String getColorPiece(String piece) {
        if (piece != null) {
            // Extraer el color completo de la pieza (R_0 -> RED, Y_0 -> YELLOW)
            if (piece.startsWith("R")) {
                return "RED";
            } else if (piece.startsWith("Y")) {
                return "YELLOW";
            }
        }

        return null;
    }

    /**
     * Main entry point for the Connect 4 server.
     *
     * @param args
     */
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop it.");
        awaitForever();
    }
}