package ee.taltech.americandream.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import helper.BulletData;
import helper.Direction;
import helper.PlayerListener;
import helper.PlayerState;
import helper.packet.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static helper.Constants.*;

public class Player {
    private final boolean thisIsAI;
    private final int id;
    private final Game game;
    private final Connection connection;
    private final List<BulletData> playerBullets;
    private float x;
    private float y;
    private Direction direction;
    private String name;
    private Integer livesCount;
    private int damage = 0;
    private Direction nextBulletDirection;
    private float bulletTimeout;
    private float velX, velY;
    private int isShooting;
    private int maxAmmo = 10;
    private int ammoCount = maxAmmo;  // different amount could cause bugs; additional 'has game started' checking required
    private float ammoDelta = 0;
    private boolean gunPickedUp = false;
    private float bulletForce = 1000;
    private float bulletSpeed = 5;
    private float ammoIncrementingTime = 0.75f;
    private float shootDelay = 0.3f;

    /**
     * Initialize server-side representation of a player based on the PlayerPositionMessages sent by a specific client.
     * Receives: PlayerPositionMessage - data regarding the player, including lives
     *           BulletMessage - indicates that the player has shot a new bullet
     *           AddAiMessage - Player has triggered the activation of AI player
     * @param connection connection to the client
     * @param game game instance
     * @param id player id
     */
    public Player(Connection connection, Game game, int id, boolean thisIsAI) {
        // create player
        this.thisIsAI = thisIsAI;
        this.id = id;
        this.game = game;
        this.connection = connection;
        this.playerBullets = new ArrayList<>();
        this.bulletTimeout = 0;

        // add listeners
        connection.addListener(new PlayerListener(this, game));
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public List<BulletData> getPlayerBullets() {
        return playerBullets;
    }

    public float getBulletForce() {
        return bulletForce;
    }

    public float getBulletSpeed() {
        return bulletSpeed;
    }

    public boolean isThisIsAI() {
        return thisIsAI;
    }

    public boolean isGunPickedUp() {
        return gunPickedUp;
    }

    public void setGunPickedUp(boolean gunPickedUp) {
        this.gunPickedUp = gunPickedUp;
    }

    public void setAmmoIncrementingTime(float ammoIncrementingTime) {
        this.ammoIncrementingTime = ammoIncrementingTime;
    }

    /**
     * Generate new PlayerState.
     */
    public PlayerState getState() {
        PlayerState state = new PlayerState();
        state.id = id;
        state.x = x;
        state.y = y;
        state.direction = direction;
        state.livesCount = livesCount;
        state.velX = velX;
        state.velY = velY;
        state.isShooting = isShooting;
        state.damage = damage;
        state.name = name;
        state.ammoCount = ammoCount;
        state.thisIsAI = thisIsAI;
        return state;
    }

    /**
     * Update player's position according to received PlayerPositionMessage.
     * Update existing bullets' positions and add new bullets that are shot by the player.
     * Remove bullets that are out of bounds.
     * @param delta tick rate
     */
    public void update(float delta) {
        // will shoot a bullet if the bulletTimeout is 0
        if (nextBulletDirection != null && bulletTimeout >= shootDelay && ammoCount > 0) {
            // construct the bullet to be shot
            BulletData bulletData = new BulletData();
            bulletData.x = x + (nextBulletDirection == Direction.LEFT ? -1 : 1) * 20;
            bulletData.id = id;
            bulletData.name = name;
            bulletData.y = y;
            bulletData.speedBullet = bulletSpeed * (nextBulletDirection == Direction.LEFT ? -1 : 1);
            bulletData.shotWithGun = gunPickedUp;
            playerBullets.add(bulletData);
            ammoCount--;
            // reset variables
            bulletTimeout = 0;
            nextBulletDirection = null;
        }
        bulletTimeout += delta;
        if (ammoIncrementingTime != 0) {
            ammoDelta += delta;
            if (ammoDelta >= ammoIncrementingTime && ammoCount < maxAmmo) {
                ammoDelta = ammoDelta % ammoIncrementingTime;
                ammoCount++;
            }
        }
        if (gunPickedUp && ammoCount == 0) {
            gunPickedUp = false;
            ammoIncrementingTime = 0.75f;
            changeGun(10, 500, 5, 0.3f);
        }

        // remove bullets that are out of bounds
        playerBullets.removeIf(bullet -> bullet.x < x - BOUNDS || bullet.x > x + BOUNDS);
        // move bullets
        for (BulletData bullet : playerBullets) {
            bullet.x += bullet.speedBullet;
        }
    }

    /**
     * Each time a player shoots a new bullet, a BulletMessage is sent. Rest of the bullet logic is server-sided.
     */
    public void handleNewBullet(BulletMessage bulletMessage) {
        // handle bullet message
        nextBulletDirection = bulletMessage.direction;
    }

    /**
     * Handle incoming PlayerPositionMessages.
     */
    public void handlePositionMessage(PlayerPositionMessage positionMessage) {
        // handle position message
        x = positionMessage.x;
        y = positionMessage.y;
        direction = positionMessage.direction;
        name = positionMessage.name;

        // reset damage after respawning
        if (livesCount != null && !Objects.equals(positionMessage.livesCount, livesCount)) {
            damage = 0;
        }
        livesCount = positionMessage.livesCount;
        velX = positionMessage.velX;
        velY = positionMessage.velY;
        isShooting = positionMessage.isShooting;
    }

    /**
     * Calculate the force of the bullet hit and increment player's damage.
     * @param bullet bullet shot by another player that hit 'this' player.
     */
    public float handleBeingHit(BulletData bullet, String characterName, boolean gunPickedUpEnemy) {
        if (gunPickedUpEnemy) {
            if (characterName.contains("Biden")) {
                // sniper damage
                this.damage += 8;
            } else if (characterName.contains("Trump")) {
                // smg damage
                this.damage += 2;
            } else if (characterName.contains("Obama")) {
                // ar damage
                this.damage += 3;
            }
        } else {
            this.damage += 2;
        }
        // calculate force to apply to player and bullet moving direction
        float force = bulletForce * (bullet.speedBullet > 0 ? 1 : -1);
        // damage increases force exponentially, at 100% damage the force is 4x stronger than at 0%
        // force *= 1 + (damage / x)
        force *= (1 + (float) damage / DAMAGE_INCREASES_PUSHBACK_COEFFICIENT);
        return force;
    }

    /**
     * Used for changing the qualities shooting and bullets when picking up a gun.
     */
    public void changeGun(int ammoCount, int bulletForce, int bulletSpeed, float shootDelay) {
        this.ammoCount = ammoCount;
        this.bulletForce = bulletForce;
        this.bulletSpeed = bulletSpeed;
        this.shootDelay = shootDelay;
    }

    /**
     * Send gameStateMessage to the client of 'this' player.
     * @param gameStateMessage contains info about all players, bullets and game time
     */
    public void sendGameState(GameStateMessage gameStateMessage) {
        connection.sendUDP(gameStateMessage);
    }

    /**
     * Assure that the final gameStateMessage reaches all clients before the game instance closes.
     */
    public void sendGameStateTCP(GameStateMessage gameStateMessage) {
        connection.sendTCP(gameStateMessage);
    }

    public void sendGunBoxTCP(GunBoxMessage gunBoxMessage) {
        connection.sendTCP(gunBoxMessage);
    }

    public void sendGunPickupMessage(GunPickupMessage gunPickupMessage) {
        connection.sendTCP(gunPickupMessage);
    }

    /**
     * End the game in case of a disconnect.
     */
    public void onDisconnect() {
        game.end();
    }

}
