package ee.taltech.americandream.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import helper.BulletData;
import helper.Direction;
import helper.PlayerState;
import helper.packet.BulletPositionMessage;
import helper.packet.GameStateMessage;
import helper.packet.PlayerPositionMessage;

import java.util.List;

public class Player {
    private final int id;
    private List<BulletData> playerBullets;
    private float x;
    private float y;
    private Direction direction;

    private Integer livesCount;

    private final Game game;
    private final Connection connection;
    public Player(Connection connection, Game game, int id) {
        // create player
        this.id = id;
        this.game = game;
        this.connection = connection;
        // send id to client
        connection.sendTCP("id:" + id);
        // add listeners
        connection.addListener(new Listener() {
            @Override
            public void disconnected(Connection connection) {
                super.disconnected(connection);
                onDisconnect();
            }
            public void received(Connection connection, Object object) {
                super.received(connection, object);
                // handle incoming data
                if (object instanceof PlayerPositionMessage positionMessage) {
                    // handle position message
                    handlePositionMessage(positionMessage);
                }

                // handle bullet position message
                if (object instanceof BulletPositionMessage bulletPositionMessage) {
                    // handle bullet position message
                    handleBullets(bulletPositionMessage);
                }
            }
        });
    }

    private void handleBullets(BulletPositionMessage bulletPositionMessage) {
        // handle bullet position message
        playerBullets = bulletPositionMessage.playerBullets;
    }

    private void onDisconnect() {
        // handle disconnect
        // end game
        game.end();
    }

    private void handlePositionMessage(PlayerPositionMessage positionMessage) {
        // handle position message
        x = positionMessage.x;
        y = positionMessage.y;
        direction = positionMessage.direction;
        livesCount = positionMessage.livesCount;
    }

    public PlayerState getState() {
        // get player state
        PlayerState state = new PlayerState();
        state.id = id;
        state.x = x;
        state.y = y;
        state.direction = direction;
        state.livesCount = livesCount;
        return state;
    }

    public List<BulletData> getPlayerBullets() {
        return playerBullets;
    }

    public void sendGameState(GameStateMessage gameStateMessage) {
        // send game state message
        connection.sendUDP(gameStateMessage);
    }
}
