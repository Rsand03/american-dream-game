package objects.player;

import animation.PlayerAnimations;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import ee.taltech.americandream.AmericanDream;
import helper.Audio;
import helper.Direction;
import helper.PlayerState;
import helper.packet.AddUfoMessage;
import helper.packet.BulletMessage;
import helper.packet.PlayerPositionMessage;

import java.util.Objects;
import java.util.Optional;

import static helper.Constants.*;
import static helper.Textures.PLAYER_INDICATOR_TEXTURE;

public class Player extends GameEntity {

    protected final float speed;
    private final TextureAtlas textureAtlas;
    private final PlayerAnimations playerAnimations;
    protected final String name;
    protected Direction direction;
    protected int jumpCounter;
    protected float keyDownTime = 0;
    protected float timeTillRespawn = 0;
    protected Integer livesCount = LIVES_COUNT;
    protected Integer damage = 0;
    protected Integer ammoCount = 0;
    protected int isShooting;
    protected float jumpCounterResetTime = 0;
    protected float bulletHitForce = 0f;
    private boolean onGround = false;
    private boolean walkSoundStarted = false;

    /**
     * Initialize Player.
     *
     * @param width  width of the player object/body
     * @param height height
     * @param body   object that moves around in the world and collides with other bodies
     */
    public Player(float width, float height, Body body, String selectedCharacter) {
        super(width, height, body);
        this.speed = PLAYER_SPEED;
        this.jumpCounter = 0;
        this.direction = Direction.RIGHT;
        this.isShooting = 0;
        this.textureAtlas = new TextureAtlas(Gdx.files.internal("spriteatlas/SoldierSprites.atlas"));
        this.playerAnimations = new PlayerAnimations(textureAtlas);

        body.setTransform(new Vector2(body.getPosition().x, body.getPosition().y + 30), 0);
        // assign player a randomly generated name + id
        this.name = selectedCharacter + "_" + AmericanDream.id;
        if (selectedCharacter.contains("Obama")) {
            playerAnimations.generateObama();
        } else if (selectedCharacter.contains("Trump")) {
            playerAnimations.generateTrump();
        } else {  // ai is biden at the moment
            playerAnimations.generateBiden();
        }
    }

    public Direction getDirection() {
        return direction;
    }

    public float getVelX() {
        return velX;
    }

    public float getVelY() {
        return velY;
    }

    public int isShooting() {
        return isShooting;
    }

    public String getName() {
        return name;
    }

    public Integer getLivesCount() {
        if (livesCount < 0) return 0;  // fixes negative lives in hud
        return livesCount;
    }

    public Integer getDamage() {
        return damage;
    }

    public Integer getAmmoCount() {
        return ammoCount;
    }

    public Vector2 getPosition() {
        return body.getPosition().scl(PPM);
    }

    /**
     * Get the dimensions of the player (width, height).
     */
    public Vector2 getDimensions() {
        return new Vector2(width, height);
    }

    /**
     * Update player data according to input, collisions (platforms) and respawning.
     * Construct and send new playerPositionMessage.
     *
     * @param delta  delta time
     * @param center point of the map/world
     */
    @Override
    public void update(float delta, Vector2 center, Optional<PlayerState> playerState) {
        if (playerState.isPresent()) {
            PlayerState ps = playerState.get();
            damage = ps.getDamage();
            ammoCount = ps.getAmmoCount();
            if (ps.getApplyForce() != 0) bulletHitForce = ps.getApplyForce();
            // update server-sided lives here in the future
        }
        thisX = body.getPosition().x * PPM;
        thisY = body.getPosition().y * PPM;
        if (livesCount > 0) {  // let the dead player spectate, but ignore its input
            handleInput(delta);
        }
        applyBulletHitForce();
        handlePlatform();
        handleOutOfBounds(delta, center);  // respawning and decrementing lives
        direction = velX > 0 ? Direction.RIGHT : Direction.LEFT;

        // construct player position message to be sent to the server
        PlayerPositionMessage positionMessage = new PlayerPositionMessage();
        positionMessage.x = thisX;
        positionMessage.y = thisY;
        positionMessage.direction = Direction.LEFT;
        positionMessage.livesCount = livesCount;
        positionMessage.name = name;
        positionMessage.velX = velX;
        positionMessage.velY = velY;
        positionMessage.isShooting = isShooting;

        // send player position message to the server
        AmericanDream.client.sendUDP(positionMessage);

        playerAnimations.update(delta, this);
    }

    /**
     * Render player and find the correct animation frame.
     */
    @Override
    public void render(SpriteBatch batch) {
        if (livesCount > 0) {
            TextureRegion currentFrame = playerAnimations.getFrame(Gdx.graphics.getDeltaTime(), this);
            batch.draw(currentFrame, getPosition().x - getDimensions().x / 2 - 15, getPosition().y - getDimensions().y / 2, FRAME_WIDTH, FRAME_HEIGHT);
            batch.draw(PLAYER_INDICATOR_TEXTURE, getPosition().x - getDimensions().x / 2, getPosition().y - getDimensions().y / 2 + 80, 35, 35);
        }
    }

    /**
     * Handle mouse and keyboard input.
     * Update the speed of the player body according to user input.
     */
    protected void handleInput(float delta) {
        Controller controller = Controllers.getCurrent();
        velX = 0;
        // Moving right
        if (Gdx.input.isKeyPressed(Input.Keys.D) || (controller != null &&
                (controller.getButton(controller.getMapping().buttonDpadRight) ||
                        Objects.requireNonNull(controller).getAxis(controller.getMapping().axisLeftX) > 0.5f
                ))) {
            velX = 1;
        }
        // Moving left
        if (Gdx.input.isKeyPressed(Input.Keys.A) || (controller != null &&
                (controller.getButton(controller.getMapping().buttonDpadLeft) ||
                        Objects.requireNonNull(controller).getAxis(controller.getMapping().axisLeftX) < -0.5f))) {
            velX = -1;
        }

        // Jumping
        if (jumpCounter < JUMP_COUNT && Gdx.input.isKeyJustPressed(Input.Keys.W) || (controller != null &&
                controller.getButton(controller.getMapping().buttonA))) {
            float force = body.getMass() * JUMP_FORCE;
            body.setLinearVelocity(body.getLinearVelocity().x, 0);
            body.applyLinearImpulse(new Vector2(0, force), body.getWorldCenter(), true);
            jumpCounter++;
            Audio.getInstance().playSound(Audio.SoundType.JUMP);
        }

        // key down on platform
        if (Gdx.input.isKeyPressed(Input.Keys.S) || (controller != null &&
                (controller.getButton(controller.getMapping().buttonDpadDown) ||
                        Objects.requireNonNull(controller).getAxis(controller.getMapping().axisLeftY) > 0.5f))) {
            keyDownTime += delta;
        } else {
            keyDownTime = 0;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            // spawn UFO
            AmericanDream.client.sendTCP(new AddUfoMessage());
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.J)) {
            // pickup
        }

        // reset jump counter if landed (sometimes stopping in midair works as well)
        if (body.getLinearVelocity().y == 0) {
            onGround = true;
            // body y velocity must main 0 for some time to reset jump counter
            if (jumpCounterResetTime > 0.1f) {
                jumpCounter = 0;
                jumpCounterResetTime = 0;
            }
            jumpCounterResetTime += delta;
        } else {
            onGround = false;
        }

        body.setLinearVelocity(velX * speed, body.getLinearVelocity().y);

        // check for shooting input
        shootingInput();

        // walking sound
        if (onGround && !walkSoundStarted && velX != 0) {
            Audio.getInstance().startWalkSound();
            walkSoundStarted = true;
        } else if (!onGround || velX == 0) {
            Audio.getInstance().stopWalkSound();
            walkSoundStarted = false;
        }
    }

    /**
     * Check for shooting input.
     * Create and send new BulletMessage if the player is shooting.
     */
    public void shootingInput() {
        isShooting = 0;
        BulletMessage bulletMessage = new BulletMessage();
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) ||
                (Controllers.getCurrent() != null &&
                        Controllers.getCurrent().getAxis(Controllers.getCurrent().getMapping().axisRightX) > 0.5f)) {
            bulletMessage.direction = Direction.RIGHT;
            isShooting = 1;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) ||
                (Controllers.getCurrent() != null &&
                        Controllers.getCurrent().getAxis(Controllers.getCurrent().getMapping().axisRightX) < -0.5f)) {
            bulletMessage.direction = Direction.LEFT;
            isShooting = -1;
        }
        bulletMessage.name = name;
        AmericanDream.client.sendTCP(bulletMessage);
    }

    /**
     * Apply bullet hit knockback to the player if the player has been hit.
     * Float representing the force is received form the server only once, after that it's saved into the player object.
     * Exponentially decrement the applied force every game tick.
     * Stop applying knockback when the force becomes too small.
     */
    protected void applyBulletHitForce() {
        if (bulletHitForce != 0) {
            body.applyForceToCenter(new Vector2(bulletHitForce, 0), true);
            bulletHitForce *= 0.9f;  // exponentially decrement force
        }
        // set force to zero if its small enough
        if (Math.abs(bulletHitForce) < Math.abs(bulletHitForce / 10f)) {
            bulletHitForce = 0;
        }
    }

    /**
     * Handle the platform.
     * If player is below the platform, move it some random distance to the right
     * If player is above the platform, move it back to the original position
     * TODO: Make the logic less hacky
     */
    protected void handlePlatform() {
        Array<Body> bodies = new Array<Body>();
        body.getWorld().getBodies(bodies);

        for (Body b : bodies) {
            if (b.getUserData() != null && b.getUserData().toString().contains("platform")) {
                float height = Float.parseFloat(b.getUserData().toString().split(":")[1]);
                height = height / PPM;
                if (body.getPosition().y - this.height / PPM >= height && b.getPosition().x >= 2000 && (keyDownTime == 0 || keyDownTime > PLATFORM_DESCENT * 1.5)) {
                    // bring back platform
                    b.setTransform(b.getPosition().x - 2000, b.getPosition().y, 0);
                    keyDownTime = 0;
                } else if ((body.getPosition().y - this.height / PPM < height || (keyDownTime >= PLATFORM_DESCENT && keyDownTime <= PLATFORM_DESCENT * 1.5)) && b.getPosition().x <= 2000) {
                    // remove platform
                    b.setTransform(b.getPosition().x + 2000, b.getPosition().y, 0);
                }
            }
        }
    }

    /**
     * Decrement lives and respawn the player if it's out of bounds.
     */
    protected void handleOutOfBounds(float delta, Vector2 center) {
        if (thisY < -BOUNDS) {
            if (timeTillRespawn <= RESPAWN_TIME) {  // delay the respawning if necessary
                timeTillRespawn += delta;
            } else {
                livesCount--;
                // move the player back to the initial spawning position (far above platforms)
                body.setTransform(center.x / PPM, center.y / PPM + 30, 0);
                body.setLinearVelocity(0, 0);
                timeTillRespawn = 0;
                Audio.getInstance().playSound(Audio.SoundType.DEATH);
            }
        }
    }

    public enum State {WALKING, IDLE, JUMPING, SHOOTING}
}
