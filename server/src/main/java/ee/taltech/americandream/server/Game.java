package ee.taltech.americandream.server;

import com.esotericsoftware.kryonet.Connection;
import helper.BulletData;
import helper.PlayerState;
import helper.packet.GameStateMessage;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static helper.Constants.*;

public class Game extends Thread {

    private float gameTime;
    private boolean running = true;
    private Player[] players;
    private boolean bothJoinedMultiplayer = false;
    private final Lobby lobby;

    public Game(Connection[] connections, Lobby lobby) {
        // set game duration
        this.gameTime = GAME_DURATION;
        this.lobby = lobby;

        players = new Player[connections.length];
        // start game with connections
        // make players from connections
        for (int i = 0; i < connections.length; i++) {
            players[i] = new Player(connections[i], this, connections[i].getID());
        }
    }

    public void run() {
        while (running) {
            try {
                // update players
                for (Player player : players) {
                    player.update(1000f / TICK_RATE / 1000f);
                }
                // construct game state message
                GameStateMessage gameStateMessage = new GameStateMessage();

                gameStateMessage.gameTime = Math.round(gameTime);
                gameStateMessage.playerStates = new PlayerState[players.length];
                gameStateMessage.bulletData = new ArrayList<>();
                for (int i = 0; i < players.length; i++) {
                    // add player states to the game state message (like position)
                    gameStateMessage.playerStates[i] = players[i].getState();
                    // add bullets to the game state message
                    gameStateMessage.bulletData.addAll(players[i].getPlayerBullets());
                }
                
                // handle bullets hitting players
                handleBulletHits(gameStateMessage);

                // send game state message to all players
                for (Player player : players) {
                    player.sendGameState(gameStateMessage);
                }

                // Start decrementing time when both players have joined the level
                // Fixes countdown starting too early while in title screen
                if (bothJoinedMultiplayer) {
                    gameTime -= 1f / TICK_RATE;
                } else if (!Arrays.stream(gameStateMessage.playerStates).map(x -> x.direction).toList().contains(null)) {
                    bothJoinedMultiplayer = true;  // true when both players start sending non-null position data
                }

                // end game when      time ends  ||  one player has 0 lives
                if (gameTime <= 0
                        || Arrays.stream(gameStateMessage.playerStates).map(x -> x.livesCount).toList().contains(0)) {
                    this.end();
                }

                Thread.sleep(1000 / TICK_RATE);

            } catch (InterruptedException e) {
                running = false;
            }
        }
    }

    private void handleBulletHits(GameStateMessage gameStateMessage) {
        List<BulletData> bullets = gameStateMessage.bulletData;
        PlayerState[] playerStates = gameStateMessage.playerStates;

        // construct rectangles for players
        Rectangle[] playerHitboxes = new Rectangle[playerStates.length];
        for (int i = 0; i < playerStates.length; i++) {
            playerHitboxes[i] = new Rectangle((int) playerStates[i].x - PLAYER_WIDTH / 2, (int) playerStates[i].y - PLAYER_HEIGHT / 2, PLAYER_WIDTH, PLAYER_HEIGHT);
        }

        // check if bullets hit players
        for (BulletData bullet: bullets) {
            // construct bullet hitbox
            Rectangle bulletHitbox = new Rectangle((int) bullet.x - BULLET_HITBOX / 2, (int) bullet.y - BULLET_HITBOX / 2, BULLET_HITBOX, BULLET_HITBOX);
            // check if bullet hit any player
            for (int i = 0; i < playerHitboxes.length; i++) {
                if (playerHitboxes[i].intersects(bulletHitbox) // hitboxes hit
                                && !bullet.isDisabled // has already hit
                        && bullet.id != playerStates[i].id // is not the player who shot the bullet
                ) {
                    // remove bullet
                    bullet.isDisabled = true;
                    // find player with corresponding id

                    for (Player player : players) {
                        if (player.getId() == playerStates[i].id) {
                            // register being hit, increment damage and calculate force
                            // apply force to player (state)
                            playerStates[i].applyForce = player.handleBeingHit(bullet);  // returns force
                        }
                    }
                }
            }
        }
    }

    public void end() {
        running = false;
        lobby.clearLobby();
    }
}
