package com.server;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
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

/**
 * Servidor WebSocket que manté l'estat complet dels clients i objectes
 * seleccionables.
 *
 * Protocol simplificat:
 * - Client -> Server: { "type": "clientData", "data": { ...ClientData... } }
 * - Server -> Clients: { "type": "state", "clientId": <clientId>, "clients": [
 * ...ClientData... ], "gameObjects": { ... }, "countdown": n? }
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    /** Llista de noms disponibles per als clients connectats. */
    private static final List<String> PLAYER_NAMES = Arrays.asList(
            "Alejandro", "Victor");

    /** Llista de colors disponibles per als clients connectats. */
    private static final List<String> PLAYER_COLORS = Arrays.asList(
            "RED", "YELLOW");

    /** Nombre de clients necessaris per iniciar el compte enrere. */
    private static final int REQUIRED_CLIENTS = 2;

    // Claus JSON
    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_CLIENTS_LIST = "clientsList";
    private static final String K_OBJECTS_LIST = "objectsList";
    private static final String K_CURRENT_TURN = "currentTurn";
    private static final String K_BOARD_STATE = "boardState";

    // Tipus de missatge nous i (alguns) heretats
    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving"; // client -> server
    private static final String T_CLIENT_PIECE_MOVING = "clientPieceMoving"; // client -> server
    private static final String T_CLIENT_PLAY = "clientPlay"; // client -> server
    private static final String T_CLIENT_SEND_INVITATION = "clientSendInvitation"; // client -> server
    private static final String T_CLIENT_ANSWER_INVITATION = "clientAnswerInvitation"; // client -> server
    private static final String T_CLIENT_REQUEST_PLAY = "clientRequestPlay";
    private static final String T_SERVER_DATA = "serverData"; // server -> clients
    private static final String T_COUNTDOWN = "countdown"; // server -> clients
    private static final String T_PLAY_ACCEPTED = "playAccepted";
    private static final String T_PLAY_REJECTED = "playRejected";
    private static final String T_GAME_STATE = "gameState";

    /** Registre de clients i assignació de noms (pool integrat). */
    private final ClientRegistry clients;

    /** Mapa d’estat per client (source of truth del servidor). Clau = name/id. */
    private final Map<String, ClientData> clientsData = new HashMap<>();

    /** Mapa d'objectes seleccionables compartits. */
    private final Map<String, GameObject> gameObjects = new HashMap<>();

    private String[][] boardState = new String[6][7];
    private String currentTurn = "RED"; // Comienza RED
    private boolean gameStarted = false;

    private volatile boolean countdownRunning = false;

    /** Freqüència d’enviament de l’estat (frames per segon). */
    private static final int SEND_FPS = 30;
    private final ScheduledExecutorService ticker;

    /**
     * Crea un servidor WebSocket que escolta a l'adreça indicada.
     *
     * @param address adreça i port d'escolta del servidor
     */
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

    /**
     * Inicialitza els objectes seleccionables predefinits.
     */
    private void initializegameObjects() {

        double poolX = 610;
        double poolY = 130;
        double poolWidth = 250;
        double pieceRadius = 80.0 * 0.15;
        double pieceDiameter = pieceRadius * 2;

        int piecesPerRow = 7;
        int numRows = 6;

        double spacingX = (poolWidth - (piecesPerRow * pieceDiameter)) / (piecesPerRow + 1);
        double spacingY = pieceDiameter + 5; // Ajusta si necesitas más/menos espacio

        int yellowCount = 0; // count ids for yellow pieces
        int redCount = 0; // count ids for red pieces

        for (int fila = 0; fila < numRows; fila++) {
            // Alternar color por fila: filas 0,2,4 = YELLOW; 1,3,5 = RED
            String colorPiece = (fila % 2 == 0) ? "YELLOW" : "RED";
            String prefix = (fila % 2 == 0) ? "Y_" : "R_";
            double startY = poolY + (fila * spacingY) + pieceRadius; // Posición Y base para la fila

            for (int col = 0; col < piecesPerRow; col++) {
                // Solo crear hasta 21 por color (total 42)
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

                // Crea el objeto
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

    /**
     * Obté el color per un nom de client.
     *
     * @return color assignat
     */
    private synchronized String getColor() {
        return clients.snapshot().size() == 1 ? PLAYER_COLORS.get(0) : PLAYER_COLORS.get(1);
    }

    private synchronized String getColorForClient(int clientIndex) {
        if (clientIndex < PLAYER_COLORS.size()) {
            return PLAYER_COLORS.get(clientIndex);
        }
        return "GRAY"; // Color per defecte si hi ha més clients dels esperats
    }

    /**
     * Envia un compte enrere (3..0) com a part del mateix STATE.
     * Evita comptes simultanis i es cancel·la si baixa el nombre de clients.
     */
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

                    // Quan arriba a 0, iniciar el joc
                    if (i == 0) {
                        synchronized (this) {
                            gameStarted = true;
                            currentTurn = "RED"; // Sempre comença RED
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

    // ----------------- Helpers JSON -----------------

    /** Crea un objecte JSON amb el camp type inicialitzat. */
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    /**
     * Envia de forma segura un payload i, si el socket no està connectat, el neteja
     * del registre.
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

    /** Envia un missatge a tots els clients excepte l'emissor. */
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

    /** Envia a tots els clients el compte enrere. */
    private void sendCountdownToAll(int n) {
        JSONObject rst = msg(T_COUNTDOWN).put(K_VALUE, n);
        broadcastExcept(null, rst.toString());
    }

    // ----------------- WebSocketServer overrides -----------------

    /** Assigna un nom i color al client i envia l’STATE complet. */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {

        int clientIndex = clients.snapshot().size();

        String name = clients.add(conn);
        String color = getColorForClient(clientIndex);

        clientsData.put(name, new ClientData(name, color));

        System.out.println("WebSocket client connected: " + name);

        if (clientsData.size() == REQUIRED_CLIENTS) {
            sendCountdown();
        }
    }

    /** Elimina el client del registre i envia l’STATE complet. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        clientsData.remove(name);

        synchronized (this) {
            gameStarted = false;
            // Reiniciar el tauler si un jugador es desconnecta
            initializeBoard();
            currentTurn = "RED";
        }

        System.out.println("WebSocket client disconnected: " + name);
        System.out.println("Game reset due to player disconnection");
    }

    /** Processa els missatges rebuts. */
    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return; // JSON invàlid
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

            case T_CLIENT_PLAY -> {
                // Fer compte enrere

                sendCountdown();

                // Enviar dades als jugadors
            }

            case T_CLIENT_SEND_INVITATION -> {
                // Revem un value amb el nom de l'usuari a enviar la petició

                // Formatem la resposta

                // Enviem a l'usuari rebut, la petició d'invitació
            }

            case T_CLIENT_ANSWER_INVITATION -> {
                // Revem un value amb el nom de l'usuari respondre la petició i un valor booleà
                // amb la resposta

                // SI ACCEPTA
                // Comencen countdown per a la partida

                // SI NO ACCEPTA
                // Formatem la resposta
                // Enviem a l'usuari rebut, la resposta de la petició d'invitació
            }

            default -> {
                // Ignora altres tipus
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

                // Verificar que és el torn del jugador que fa la jugada
                String playerName = clients.nameBySocket(conn);
                ClientData playerData = clientsData.get(playerName);

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
                        // Acceptar jugada
                        boardState[row][col] = pieceId;
                        GameObject piece = gameObjects.get(pieceId);
                        if (piece != null) {
                            piece.row = row;
                            piece.col = col;
                        }

                        // Canviar torn
                        switchTurn();

                        System.out.println("Play accepted: " + pieceId + " at [" + row + "," + col + "]. Next turn: "
                                + currentTurn);

                        // Enviar acceptació només al jugador que ha jugat
                        JSONObject response = msg(T_PLAY_ACCEPTED)
                                .put("pieceId", pieceId)
                                .put("column", col)
                                .put("row", row);
                        sendSafe(conn, response.toString());

                        // El broadcastStatus() automàtic s'encarregarà d'enviar l'estat actualitzat
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

    /** Log d'error global o de socket concret. */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    /** Arrencada: log i configuració del timeout de connexió perduda. */
    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(100);
        startTicker();
    }

    // ----------------- Lifecycle util -----------------

    /**
     * Registra un shutdown hook per aturar netament el servidor en finalitzar el
     * procés.
     */
    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stopTicker(); // <- atura el bucle periòdic
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    /** Bloqueja el fil principal indefinidament fins que sigui interromput. */
    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ----------------- Ticker util -----------------

    private void startTicker() {
        long periodMs = Math.max(1, 1000 / SEND_FPS);
        ticker.scheduleAtFixedRate(() -> {
            try {
                // Opcional: si no hi ha clients, evita enviar
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

    /** Punt d'entrada. */
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Server running on port " + DEFAULT_PORT + ". Press Ctrl+C to stop it.");
        awaitForever();
    }
}
