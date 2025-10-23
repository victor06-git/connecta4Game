# Guía Completa del Sistema WebSocket - Connect 4

## Índice
1. [Introducción](#introducción)
2. [Arquitectura del Sistema](#arquitectura-del-sistema)
3. [Flujo de Conexión](#flujo-de-conexión)
4. [Tipos de Mensajes](#tipos-de-mensajes)
5. [Casos de Uso Detallados](#casos-de-uso-detallados)
6. [Ejemplos Prácticos](#ejemplos-prácticos)

---

## Introducción

El sistema utiliza **WebSockets** para permitir comunicación bidireccional en tiempo real entre el servidor y múltiples clientes. Esto significa que:

- El servidor puede enviar mensajes a los clientes en cualquier momento
- Los clientes pueden enviar mensajes al servidor en cualquier momento
- No es necesario hacer polling (consultas repetidas)
- La comunicación es instantánea

### ¿Qué es un WebSocket?

Un WebSocket es como un "tubo" de comunicación permanente entre el cliente y el servidor:

```
Cliente 1  ←→  Servidor  ←→  Cliente 2
   ↓                            ↓
  Juan                        María
```

---

## Arquitectura del Sistema

### Componentes Principales

#### 1. **Servidor (`com.server.Main`)**
- Escucha en el puerto 3000
- Gestiona múltiples clientes conectados
- Controla el estado del juego
- Distribuye mensajes entre clientes

#### 2. **Cliente (`com.connect4.Main` + `UtilsWS`)**
- Se conecta al servidor
- Envía acciones del jugador
- Recibe actualizaciones del juego

#### 3. **ClientRegistry**
- Mantiene un registro de qué WebSocket corresponde a qué jugador
- Mapea nombres de jugadores a conexiones

---

## Flujo de Conexión

### Paso 1: Cliente se Conecta

```java
// Cliente (UtilsWS.java)
wsClient = UtilsWS.getSharedInstance("ws://localhost:3000");
```

Esto crea una conexión WebSocket al servidor.

**En el servidor se ejecuta:**
```java
@Override
public void onOpen(WebSocket conn, ClientHandshake handshake) {
    System.out.println("New client connected! Waiting for player name...");
}
```

### Paso 2: Cliente Envía su Nombre

```java
// Cliente (Main.java - connectToServer)
wsClient.onOpen((response) -> {
    JSONObject msgObj = new JSONObject();
    msgObj.put("type", "setPlayerName");
    msgObj.put("name", playerName);
    wsClient.safeSend(msgObj.toString());
});
```

**Mensaje JSON enviado:**
```json
{
  "type": "setPlayerName",
  "name": "Juan"
}
```

### Paso 3: Servidor Registra al Jugador

```java
// Servidor (Main.java - onMessage)
case T_SET_PLAYER_NAME: {
    String playerName = obj.getString("name");
    
    // Asignar color según orden (primer jugador = RED, segundo = YELLOW)
    String color = (clientsData.isEmpty()) ? "RED" : 
                   (clientsData.size() == 1) ? "YELLOW" : "GRAY";
    
    ClientData clientData = new ClientData(playerName, color);
    clientsData.put(playerName, clientData);
    clients.setName(conn, playerName);
    
    // Confirmar al cliente su nombre
    sendClientName(conn, playerName);
    
    // Notificar a todos los clientes sobre la lista actualizada
    broadcastExcept(null, sendAllClients());
    
    // Si hay 2 jugadores, comenzar cuenta atrás
    if (clientsData.size() == REQUIRED_CLIENTS) {
        sendCountdown();
    }
}
```

### Paso 4: Servidor Confirma al Cliente

El servidor envía **dos mensajes** al cliente:

**Mensaje 1: Confirmación de nombre**
```json
{
  "type": "clientName",
  "value": "Juan"
}
```

**Mensaje 2: Lista de clientes conectados**
```json
{
  "type": "clientsList",
  "clientsList": [
    {
      "name": "Juan",
      "color": "RED",
      "play": false
    }
  ]
}
```

### Paso 5: Cliente Recibe y Procesa

```java
// Cliente (Main.java - wsMessage)
case "clientName":
    clientName = msgObj.getString("value");
    break;

case "serverData":
    // Actualizar lista de jugadores
    clientName = msgObj.getString("clientName");
    // ... procesar clientes, objetos, tablero
    
    // Cambiar a vista de espera
    if (UtilsViews.getActiveView().equals("ViewConfig")) {
        UtilsViews.setViewAnimating("ViewWait");
    }
    break;
```

---

## Tipos de Mensajes

### Del Cliente al Servidor

#### 1. **setPlayerName**
Establece el nombre del jugador.

```json
{
  "type": "setPlayerName",
  "name": "Juan"
}
```

#### 2. **clientRequestPlay**
El jugador intenta colocar una ficha.

```json
{
  "type": "clientRequestPlay",
  "pieceId": "R_5",
  "column": 3
}
```

#### 3. **clientSendInvitation**
Enviar invitación a otro jugador.

```json
{
  "type": "clientSendInvitation",
  "sendFrom": "Juan",
  "sendTo": "María"
}
```

#### 4. **clientAnswerInvitation**
Responder a una invitación.

```json
{
  "type": "clientAnswerInvitation",
  "sendFrom": "María",
  "sendTo": "Juan",
  "value": true
}
```

#### 5. **clientMouseMoving**
Actualizar posición del cursor del cliente.

```json
{
  "type": "clientMouseMoving",
  "value": {
    "name": "Juan",
    "color": "RED",
    "mouseX": 150.5,
    "mouseY": 200.3
  }
}
```

#### 6. **clientPieceMoving**
Actualizar posición de una ficha que se está arrastrando.

```json
{
  "type": "clientPieceMoving",
  "value": {
    "id": "R_5",
    "x": 150.5,
    "y": 200.3,
    "radius": 12.0,
    "row": -1,
    "col": -1
  }
}
```

### Del Servidor al Cliente

#### 1. **clientName**
Confirma el nombre asignado.

```json
{
  "type": "clientName",
  "value": "Juan"
}
```

#### 2. **clientsList**
Lista de clientes conectados.

```json
{
  "type": "clientsList",
  "clientsList": [
    {
      "name": "Juan",
      "color": "RED",
      "play": true
    },
    {
      "name": "María",
      "color": "YELLOW",
      "play": true
    }
  ]
}
```

#### 3. **serverData**
Estado completo del juego (enviado 30 veces por segundo).

```json
{
  "type": "serverData",
  "clientName": "Juan",
  "clientsList": [...],
  "objectsList": [
    {
      "id": "R_0",
      "x": 650.5,
      "y": 145.0,
      "radius": 12.0,
      "row": -1,
      "col": -1,
      "color": "RED"
    }
  ],
  "currentTurn": "RED",
  "boardState": [
    [null, null, null, null, null, null, null],
    [null, null, null, null, null, null, null],
    [null, null, null, null, null, null, null],
    [null, null, null, null, null, null, null],
    [null, null, null, "R_5", null, null, null],
    [null, null, null, "Y_3", null, null, null]
  ]
}
```

#### 4. **countdown**
Cuenta atrás antes de empezar el juego.

```json
{
  "type": "countdown",
  "value": 3
}
```

Los valores pueden ser: `3`, `2`, `1`, `0` (GO!)

#### 5. **playAccepted**
El movimiento fue válido y aceptado.

```json
{
  "type": "playAccepted",
  "pieceId": "R_5",
  "column": 3,
  "row": 4,
  "gameEnded": false,
  "winner": null
}
```

Si hay ganador:
```json
{
  "type": "playAccepted",
  "pieceId": "Y_8",
  "column": 2,
  "row": 3,
  "gameEnded": true,
  "winner": "YELLOW",
  "winningLineCoords": [3, 2, 0, 2]
}
```

#### 6. **playRejected**
El movimiento fue rechazado.

```json
{
  "type": "playRejected",
  "pieceId": "R_5",
  "reason": "Not your turn! Current turn: YELLOW"
}
```

Posibles razones:
- `"Game not started yet"`
- `"Game has ended"`
- `"Not your turn! Current turn: RED"`
- `"Column is full"`
- `"Invalid piece for current turn"`

---

## Casos de Uso Detallados

### Caso 1: Dos Jugadores se Conectan

**Secuencia completa:**

```
1. Juan abre el juego
   Cliente Juan → Servidor: Conexión WebSocket establecida
   Servidor: onOpen() ejecutado

2. Juan introduce su nombre "Juan" y presiona conectar
   Cliente Juan → Servidor: {"type":"setPlayerName","name":"Juan"}
   
3. Servidor procesa:
   - Crea ClientData("Juan", "RED")
   - Registra la conexión
   - Envía confirmación a Juan
   
   Servidor → Cliente Juan: {"type":"clientName","value":"Juan"}
   Servidor → Cliente Juan: {"type":"clientsList","clientsList":[...]}
   
4. Juan ve la pantalla de espera mostrando:
   - Jugador 1: Juan (esperando oponente...)

5. María abre el juego
   Cliente María → Servidor: Conexión WebSocket establecida

6. María introduce "María" y conecta
   Cliente María → Servidor: {"type":"setPlayerName","name":"María"}
   
7. Servidor procesa:
   - Crea ClientData("María", "YELLOW")
   - Detecta que hay 2 jugadores
   - Inicia cuenta atrás
   
   Servidor → Cliente María: {"type":"clientName","value":"María"}
   Servidor → Todos: {"type":"clientsList","clientsList":[...]}
   Servidor → Todos: {"type":"countdown","value":3}
   
8. Ambos jugadores ven:
   - Jugador 1: Juan
   - Jugador 2: María
   - Cuenta atrás: 3... 2... 1... GO!

9. El juego comienza
   - currentTurn = "RED"
   - Juan puede jugar primero
```

### Caso 2: Realizar una Jugada

**Juan quiere colocar una ficha roja en la columna 3:**

```
1. Juan arrastra la ficha R_5 sobre la columna 3

   Cliente Juan → Servidor: {
     "type": "clientPieceMoving",
     "value": {
       "id": "R_5",
       "x": 285.5,
       "y": 420.0,
       ...
     }
   }

2. Servidor recibe y actualiza gameObjects
   
3. Servidor envía estado actualizado a TODOS (30 veces/segundo):
   
   Servidor → Todos: {
     "type": "serverData",
     "objectsList": [
       {"id": "R_5", "x": 285.5, "y": 420.0, ...},
       ...
     ],
     ...
   }

4. María ve la ficha de Juan moviéndose en tiempo real

5. Juan suelta la ficha sobre la columna 3

   Cliente Juan → Servidor: {
     "type": "clientRequestPlay",
     "pieceId": "R_5",
     "column": 3
   }

6. Servidor valida:
   - ¿Juego iniciado? ✓
   - ¿Juego terminado? ✗
   - ¿Es turno de Juan (RED)? ✓
   - ¿Ficha correcta (R_5 es RED)? ✓
   - ¿Columna tiene espacio? ✓
   
7. Servidor actualiza:
   - boardState[4][3] = "R_5"
   - gameObjects["R_5"].row = 4
   - gameObjects["R_5"].col = 3
   - checkWinner() → No hay ganador
   - currentTurn = "YELLOW"

8. Servidor responde a Juan:
   
   Servidor → Cliente Juan: {
     "type": "playAccepted",
     "pieceId": "R_5",
     "column": 3,
     "row": 4,
     "gameEnded": false,
     "winner": null
   }

9. Juan procesa la respuesta:
   - Actualiza visualmente la ficha en la posición final
   - Desactiva la ficha (ya no se puede mover)
   
10. María recibe la actualización en el siguiente serverData
    - Ve la ficha de Juan colocada
    - Ve que es su turno (currentTurn = "YELLOW")
```

### Caso 3: Detectar Ganador

**María completa 4 en línea vertical:**

```
Estado del tablero antes de la jugada:
    0  1  2  3  4  5  6
  ┌──┬──┬──┬──┬──┬──┬──┐
0 │  │  │  │  │  │  │  │
  ├──┼──┼──┼──┼──┼──┼──┤
1 │  │  │  │  │  │  │  │
  ├──┼──┼──┼──┼──┼──┼──┤
2 │  │  │Y │  │  │  │  │
  ├──┼──┼──┼──┼──┼──┼──┤
3 │  │  │Y │R │  │  │  │
  ├──┼──┼──┼──┼──┼──┼──┤
4 │  │  │Y │R │  │  │  │
  ├──┼──┼──┼──┼──┼──┼──┤
5 │  │R │Y │R │  │  │  │
  └──┴──┴──┴──┴──┴──┴──┘

María coloca Y_10 en columna 2:

1. Cliente María → Servidor: {
     "type": "clientRequestPlay",
     "pieceId": "Y_10",
     "column": 2
   }

2. Servidor actualiza:
   - boardState[1][2] = "Y_10"
   - checkWinner() ejecuta:

   // Verificación Vertical en columna 2:
   for (int row = 0; row < 3; row++) {
     String piece = getColorPiece(boardState[row][2]);
     // row=1: piece = "Y"
     if (piece != null &&
         piece.equals(getColorPiece(boardState[row+1][2])) &&  // row=2: "Y" ✓
         piece.equals(getColorPiece(boardState[row+2][2])) &&  // row=3: "Y" ✓
         piece.equals(getColorPiece(boardState[row+3][2]))) {  // row=4: "Y" ✓
       
       winningLineCoords = [1, 2, 4, 2]  // [startRow, startCol, endRow, endCol]
       winnerColor = "YELLOW"
       gameEnded = true
       return;
     }
   }

3. Servidor → Cliente María: {
     "type": "playAccepted",
     "pieceId": "Y_10",
     "column": 2,
     "row": 1,
     "gameEnded": true,
     "winner": "YELLOW",
     "winningLineCoords": [1, 2, 4, 2]
   }

4. Cliente María procesa:
   - Dibuja la línea ganadora desde [1,2] hasta [4,2]
   - Espera 1.5 segundos
   - Muestra pantalla de victoria: "¡HAS GANADO!"

5. Cliente Juan recibe el mismo mensaje
   - Dibuja la línea ganadora
   - Muestra pantalla de derrota: "¡HAS PERDIDO!"
```

### Caso 4: Sistema de Invitaciones

**Juan invita a María a jugar:**

```
Escenario: 4 jugadores conectados (Juan, María, Pedro, Ana)

1. Juan ve la lista de jugadores disponibles
   - María (disponible)
   - Pedro (disponible)
   - Ana (jugando con otro)

2. Juan hace clic en "Invitar" junto a María
   
   Cliente Juan → Servidor: {
     "type": "clientSendInvitation",
     "sendFrom": "Juan",
     "sendTo": "María"
   }

3. Servidor reenvía a María:
   
   Servidor → Cliente María: {
     "type": "clientSendInvitation",
     "sendFrom": "Juan",
     "sendTo": "María"
   }

4. En el cliente de Juan:
   - Botón "Invitar" de María se desactiva
   - Muestra "Invitación enviada..."

5. En el cliente de María:
   - Aparece notificación: "Juan te ha invitado a jugar"
   - Botones: [Aceptar] [Rechazar]

Caso 4a: María ACEPTA
─────────────────────

6a. Cliente María → Servidor: {
      "type": "clientAnswerInvitation",
      "sendFrom": "María",
      "sendTo": "Juan",
      "value": true
    }

7a. Servidor procesa:
    - playersNames.add("Juan")
    - playersNames.add("María")
    - clientsData.get("Juan").SetIsPlaying(true)
    - clientsData.get("María").SetIsPlaying(true)

8a. Servidor → Todos: {
      "type": "clientsList",
      "clientsList": [
        {"name": "Juan", "play": true, ...},
        {"name": "María", "play": true, ...},
        {"name": "Pedro", "play": false, ...},
        {"name": "Ana", "play": true, ...}
      ]
    }

9a. Servidor inicia cuenta atrás para Juan y María
    Servidor → Juan y María: {"type": "countdown", "value": 3}

10a. Pedro y Ana ven que Juan y María están jugando
     (ya no pueden invitarlos)

Caso 4b: María RECHAZA
─────────────────────

6b. Cliente María → Servidor: {
      "type": "clientAnswerInvitation",
      "sendFrom": "María",
      "sendTo": "Juan",
      "value": false
    }

7b. Servidor → Cliente Juan: {
      "type": "clientAnswerInvitation",
      "sendFrom": "María",
      "sendTo": "Juan",
      "value": false
    }

8b. En el cliente de Juan:
    - Recibe notificación: "María rechazó la invitación"
    - Botón "Invitar" de María se reactiva
    - Juan puede invitar a María de nuevo o a otro jugador
```

---

## Ejemplos Prácticos

### Ejemplo 1: Seguimiento del Cursor en Tiempo Real

**Objetivo:** Los jugadores ven el cursor del oponente moviéndose en tiempo real.

```java
// Cliente: Cuando Juan mueve el mouse
canvas.setOnMouseMoved(e -> {
    // Crear objeto con datos del cliente
    ClientData myData = new ClientData(Main.clientName, Main.myColor);
    myData.mouseX = e.getX();
    myData.mouseY = e.getY();
    
    // Enviar al servidor
    JSONObject msgObj = new JSONObject();
    msgObj.put("type", "clientMouseMoving");
    msgObj.put("value", myData.toJSON());
    Main.wsClient.safeSend(msgObj.toString());
});

// Servidor: Recibe y actualiza
case T_CLIENT_MOUSE_MOVING: {
    String clientName = clients.nameBySocket(conn);
    ClientData updatedData = ClientData.fromJSON(obj.getJSONObject(K_VALUE));
    
    // Mantener el color original (no permitir que el cliente lo cambie)
    ClientData existingData = clientsData.get(clientName);
    if (existingData != null) {
        updatedData.color = existingData.color;
    }
    
    clientsData.put(clientName, updatedData);
    break;
}

// El ticker del servidor envía a todos automáticamente (30 FPS)
private void broadcastStatus() {
    JSONArray arrClients = new JSONArray();
    for (ClientData c : clientsData.values()) {
        arrClients.put(c.toJSON());  // Incluye mouseX, mouseY
    }
    
    JSONObject rst = msg(T_SERVER_DATA)
        .put(K_CLIENTS_LIST, arrClients)
        ...;
    
    // Enviar a cada cliente
    for (WebSocket conn : clients.snapshot().keySet()) {
        sendSafe(conn, rst.toString());
    }
}

// Cliente María: Recibe y dibuja el cursor de Juan
case "serverData":
    JSONArray arrClients = msgObj.getJSONArray("clientsList");
    for (ClientData client : clients) {
        if (!client.name.equals(Main.clientName)) {
            // Dibujar cursor del oponente
            gc.setFill(getColorForPlayer(client.color));
            gc.fillOval(client.mouseX - 5, client.mouseY - 5, 10, 10);
        }
    }
    break;
```

### Ejemplo 2: Validación de Jugadas

**Objetivo:** Asegurar que solo se acepten jugadas válidas.

```java
// Servidor valida múltiples condiciones
case T_CLIENT_REQUEST_PLAY: {
    String pieceId = obj.getString("pieceId");
    int col = obj.getInt("column");
    
    // Validación 1: ¿Juego iniciado?
    if (!gameStarted) {
        sendSafe(conn, rejectionMessage("Game not started yet"));
        return;
    }
    
    // Validación 2: ¿Juego terminado?
    if (gameEnded) {
        sendSafe(conn, rejectionMessage("Game has ended"));
        return;
    }
    
    String playerName = clients.nameBySocket(conn);
    ClientData playerData = clientsData.get(playerName);
    
    // Validación 3: ¿Es el turno del jugador?
    if (!playerData.color.equals(currentTurn)) {
        String msg = "Not your turn! Current turn: " + currentTurn;
        sendSafe(conn, rejectionMessage(msg));
        return;
    }
    
    // Validación 4: ¿La ficha es del color correcto?
    if (!pieceId.startsWith(currentTurn.charAt(0) + "_")) {
        sendSafe(conn, rejectionMessage("Invalid piece"));
        return;
    }
    
    // Validación 5: ¿La columna tiene espacio?
    int row = getLowestAvailableRow(col);
    if (row == -1) {
        sendSafe(conn, rejectionMessage("Column is full"));
        return;
    }
    
    // ✓ Todas las validaciones pasadas, aceptar jugada
    boardState[row][col] = pieceId;
    checkWinner();
    switchTurn();
    
    sendSafe(conn, acceptanceMessage(pieceId, col, row));
}
```

### Ejemplo 3: Broadcasting Inteligente

**Objetivo:** Enviar mensajes a todos excepto al remitente.

```java
// Método útil del servidor
private void broadcastExcept(WebSocket sender, String payload) {
    for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
        WebSocket conn = e.getKey();
        String name = e.getValue();
        
        // No enviar al que hizo la petición
        if (!Objects.equals(conn, sender)) {
            sendSafe(conn, payload);
        }
    }
}

// Uso práctico:
// Cuando Juan se registra, notificar a María (pero no a Juan)
sendClientName(conn, playerName);  // Solo a Juan
broadcastExcept(conn, sendAllClients());  // A todos menos Juan
```

---

## Sistema de Ticker (Actualizaciones Periódicas)

El servidor tiene un **ticker** que envía el estado del juego 30 veces por segundo:

```java
private void startTicker() {
    long periodMs = Math.max(1, 1000 / SEND_FPS);  // 33ms entre envíos
    
    ticker.scheduleAtFixedRate(() -> {
        try {
            if (!clients.snapshot().isEmpty()) {
                broadcastStatus();  // Envía serverData a todos
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }, 0, periodMs, TimeUnit.MILLISECONDS);
}
```

Esto permite que:
- Los cursores se vean en tiempo real
- Las fichas arrastrándose se sincronicen
- El tablero esté siempre actualizado
- No se pierdan actualizaciones

---

## Manejo de Errores

### Desconexión de Cliente

```java
@Override
public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    String name = clients.remove(conn);
    clientsData.remove(name);
    
    synchronized (this) {
        gameStarted = false;
        gameEnded = false;
        winnerColor = null;
        initializeBoard();
        currentTurn = "RED";
    }
    
    System.out.println("Client disconnected: " + name);
    System.out.println("Game reset due to player disconnection");
}
```

### Reconexión Automática

```java
// Cliente intenta reconectar automáticamente
private void scheduleReconnect() {
    if (!exitRequested.get()) {
        scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
    }
}

@Override
public void onError(Exception e) {
    if (e.getMessage().contains("Connection refused") || 
        e.getMessage().contains("Connection reset")) {
        scheduleReconnect();  // Intenta reconectar en 5 segundos
    }
}
```

---

## Diagrama de Secuencia Completo

```
Juan (Cliente 1)         Servidor            María (Cliente 2)
      |                     |                      |
      |-- Conectar -------->|                      |
      |                     |                      |
      |<-- onOpen -----------|                      |
      |                     |                      |
      |-- setPlayerName --->|                      |
      |   "Juan"            |                      |
      |                     |                      |
      |<-- clientName -------|                      |
      |   "Juan"            |                      |
      |                     |                      |
      |<-- clientsList ------|                      |
      |                     |                      |
      |  [Vista: Wait]      |                      |
      |                     |                      |
      |                     |<---- Conectar -------|
      |                     |                      |
      |                     |--- onOpen ---------->|
      |                     |                      |
      |                     |<-- setPlayerName ----|
      |                     |    "María"           |
      |                     |                      |
      |                     |--- clientName ------>|
      |                     |    "María"           |
      |                     |                      |
      |<-- clientsList ------|--- clientsList ---->|
      |                     |                      |
      |<-- countdown --------|--- countdown ------>|
      |    3                |    3                 |
      |                     |                      |
      |<-- countdown --------|--- countdown ------>|
      |    2                |    2                 |
      |                     |                      |
      |<-- countdown --------|--- countdown ------>|
      |    1                |    1                 |
      |                     |                      |
      |<-- countdown --------|--- countdown ------>|
      |    0 (GO!)          |    0 (GO!)           |
      |                     |                      |
      |  [Vista: Play]      |  currentTurn="RED"   |  [Vista: Play]
      |                     |                      |
      |-- requestPlay ----->|                      |
      |   R_5, col=3        |                      |
      |                     |                      |
      |                     |-- Validar jugada     |
      |                     |-- Actualizar board   |
      |                     |-- checkWinner()      |
      |                     |-- switchTurn()       |
      |                     |   currentTurn="YELLOW"|
      |                     |                      |
      |<-- playAccepted -----|                      |
      |                     |                      |
      |<-- serverData -------|--- serverData ------>|
      |  (30 FPS)           |   (30 FPS)           |
      |                     |                      |
      |                     |<-- requestPlay ------|
      |                     |    Y_3, col=3        |
      |                     |                      |
      |                     |-- Validar jugada     |
      |                     |-- Actualizar board   |
      |                     |-- checkWinner()      |
      |                     |   ¡GANADOR!          |
      |                     |                      |
      |<-- playAccepted -----|--- playAccepted ---->|
      |  winner="YELLOW"    |   winner="YELLOW"    |
      |  winningLine=[...]  |   winningLine=[...]  |
      |                     |                      |
      |  [Vista: Result]    |                      |  [Vista: Result]
      |  "HAS PERDIDO"      |                      |  "¡HAS GANADO!"
```

---

## Preguntas Frecuentes

### ¿Por qué usar WebSockets y no HTTP?

**HTTP tradicional:**
```
Cliente: "¿Hay actualizaciones?" (polling)
Servidor: "No"
[Espera 1 segundo]
Cliente: "¿Hay actualizaciones?"
Servidor: "No"
[Espera 1 segundo]
Cliente: "¿Hay actualizaciones?"
Servidor: "Sí, María movió ficha"
```

**WebSocket:**
```
[Conexión permanente establecida]
[María mueve ficha]
Servidor → Cliente: "María movió ficha" (instantáneo)
```

### ¿Qué pasa si un jugador tiene lag?

El servidor es la fuente de verdad. Si Juan tiene lag:
1. Juan envía jugada con retraso
2. Servidor valida con estado actual
3. Si ya no es válida (turno cambió), la rechaza

### ¿Por qué enviar 30 actualizaciones por segundo?

Para que los movimientos se vean suaves:
- Cursores en tiempo real
- Fichas arrastrándose
- Animaciones fluidas

Si fuera solo 1 por segundo, se vería entrecortado.

---

## Conclusión

El sistema WebSocket de Connect 4:

✅ **Comunicación en tiempo real** entre todos los jugadores
✅ **Servidor autoritario** que valida todas las jugadas
✅ **Sincronización automática** 30 veces por segundo
✅ **Sistema de invitaciones** para partidas específicas
✅ **Detección de ganadores** con coordenadas de línea
✅ **Reconexión automática** si se pierde conexión
✅ **Manejo de múltiples clientes** simultáneos

Todo esto permite una experiencia de juego multijugador fluida y sin trampas.
