package ee.taltech.americandream.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import helper.packet.GameLeaveMessage;
import helper.packet.MapSelectionMessage;

import java.util.ArrayList;
import java.util.List;

public class Lobby {
    private static int id = 1;
    private final int lobbySize;
    private final String name;
    private final int lobbyId;
    private final List<Connection> connections;
    private Game game;
    private String currentMap;

    /**
     * Initialize new Lobby.
     *
     * @param name      lobby name
     * @param lobbySize exact amount of players needed to start a new game
     */
    public Lobby(String name, int lobbySize) {
        this.name = name;
        this.lobbySize = lobbySize;
        this.lobbyId = id;
        this.connections = new ArrayList<>();

        // increment id
        id++;
    }

    /**
     * @return amount of players in the lobby
     */
    public int getPlayerCount() {
        return connections.size();
    }

    /**
     * @return maximum amount of players in the lobby
     */
    public int getMaxPlayerCount() {
        return lobbySize;
    }

    public String getCurrentMap() {
        return currentMap;
    }

    public void setCurrentMap(String currentMap) {
        this.currentMap = currentMap;
    }

    public int getId() {
        return lobbyId;
    }

    /**
     * @return lobby name
     */
    public String getName() {
        return name;
    }

    public void removeDisconnected() {
        connections.removeIf(connection -> !connection.isConnected());
    }

    /**
     * Check for and add new connections (clients).
     * Remove disconnected clients.
     * Receives: GameLeaveMessage - message indicating that a client has left the game instance
     *
     * @param connection connection with a specific client
     */
    public void addConnection(Connection connection) {
        removeDisconnected();

        // check for game leave message
        connection.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                super.received(connection, object);
                if (object instanceof GameLeaveMessage) {
                    // remove connection from lobby and restart game if needed
                    connections.remove(connection);
                    if (connections.size() <= 1 && game != null) {
                        game.end();
                    }
                }
                if (object instanceof MapSelectionMessage) {
                    currentMap = ((MapSelectionMessage) object).currentMap;
                    System.out.println("received MapSelectionMessage: " + currentMap);
                }

            }
        });

        // check if lobby is full
        if (connections.size() >= lobbySize) return;

        // check if connection is already in the lobby
        if (connections.contains(connection)) return;

        // add connection to the lobby
        connections.add(connection);
    }

    /**
     * Create a new game instance with the connections of a specific lobby.
     */
    public void startGame() {
        // create array of connections
        Connection[] connectionArray = new Connection[connections.size()];
        connectionArray = connections.toArray(connectionArray);

        // create a new game and start it
        game = new Game(connectionArray, this);
        game.start();
    }

    /**
     * Check if there are enough clients connected to start a new game.
     */
    public boolean canStartGame() {
        removeDisconnected();
        return connections.size() >= lobbySize && game == null;
    }

    /**
     * End ongoing game instance and clear lobby of players.
     */
    public void clearLobby() {
        game = null;
        connections.clear();
    }
}
