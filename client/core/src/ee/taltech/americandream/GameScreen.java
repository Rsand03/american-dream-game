package ee.taltech.americandream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import helper.CollisionHandler;
import helper.Audio;
import helper.TileMapHelper;
import helper.packet.GameLeaveMessage;
import helper.packet.GunPickupMessage;
import indicators.OffScreenIndicator;
import indicators.hud.Hud;
import objects.RemoteManager;
import objects.player.AIPlayer;
import objects.gun.GunBox;
import objects.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static helper.Constants.*;

public class GameScreen extends ScreenAdapter {
    private boolean AIGame = false;
    private final RemoteManager remoteManager;
    private final Hud hud;
    private final OffScreenIndicator offScreenIndicator;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private World world;
    private Box2DDebugRenderer debugRenderer;
    private OrthogonalTiledMapRenderer orthogonalTiledMapRenderer;
    private TileMapHelper tileMapHelper;
    private Player player;  // local client player
    private AIPlayer AIPlayer;
    private Vector2 mapCenterPoint;
    private final List<GunBox> gunBoxList = new ArrayList<>();
    private CollisionHandler collisionHandler;

    /**
     * Initialize new game screen with its camera, spriteBatch (for object rendering), tileMap and other content.
     *
     * @param camera used for creating the image that the player will see on the screen
     */
    public GameScreen(Camera camera, String selectedCharacter, String selectedMap) {
        AIGame = selectedCharacter.equals("AIGame");
        this.camera = (OrthographicCamera) camera;
        // fix #81. bug related to previous screen input processing working on this screen.
        Gdx.input.setInputProcessor(new Stage());

        this.batch = new SpriteBatch();
        // creating a new world, vector contains the gravity constants
        // x - horizontal gravity, y - vertical gravity
        this.world = new World(new Vector2(0, GRAVITY), false);
        this.debugRenderer = new Box2DDebugRenderer();

        // setting up the map
        this.tileMapHelper = new TileMapHelper(this, selectedCharacter);
        switch (selectedMap) {
            case "Swamp":
                this.orthogonalTiledMapRenderer = tileMapHelper.setupMap("first_level.tmx", AIGame);
                Audio.getInstance().playMusic(Audio.MusicType.SWAMP);
                break;
            case "Desert":
                this.orthogonalTiledMapRenderer = tileMapHelper.setupMap("Desert.tmx", AIGame);
                Audio.getInstance().playMusic(Audio.MusicType.DESERT);
                break;
            case "AiMap":
                this.orthogonalTiledMapRenderer = tileMapHelper.setupMap("AiMap.tmx", AIGame);
                Audio.getInstance().playMusic(Audio.MusicType.DESERT);
                break;
            default:
                this.orthogonalTiledMapRenderer = tileMapHelper.setupMap("City.tmx", AIGame);
                Audio.getInstance().playMusic(Audio.MusicType.CITY);
                break;
        }
        // remote player(s) manager
        this.remoteManager = new RemoteManager();

        // visual info for player
        this.hud = new Hud(this.batch);
        this.offScreenIndicator = new OffScreenIndicator(player.getDimensions());
        this.collisionHandler = new CollisionHandler();
        this.world.setContactFilter(collisionHandler);
        hud.setGunPickupText(player.getName());
    }

    public World getWorld() {
        return world;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void setAIPlayer(AIPlayer player) {
        this.AIPlayer = player;
    }
    public void addGunBox(GunBox gunBox) {
        gunBoxList.add(gunBox);
    }

    public void setMapCenterPoint(Vector2 vector2) {
        this.mapCenterPoint = vector2;
    }

    /**
     * Render a new frame.
     *
     * @param delta time passed since the rendering of the previous frame
     */
    @Override
    public void render(float delta) {
        this.update(delta);

        // clear the screen (black screen)
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(Gdx.gl.GL_COLOR_BUFFER_BIT);
        // render map before the actual game objects
        orthogonalTiledMapRenderer.render();

        // object rendering
        batch.begin();

        player.render(batch);
        if (AIGame) AIPlayer.render(batch);
        remoteManager.renderPlayers(batch, player.getDimensions(), delta);
        remoteManager.renderBullets(batch);
        remoteManager.renderUFO(batch);
        offScreenIndicator.renderIndicators(batch, camera, remoteManager.getAllPlayerStates());
        player.render(batch);
        // render gunboxes, remove if they are null (they are null if the remove method is called in the gunbox class)
        for (int i = 0; i < gunBoxList.size(); i++) {
            if (gunBoxList.get(i).getBody() == null) {
                gunBoxList.remove(gunBoxList.get(i));
            } else {
                gunBoxList.get(i).update();
                gunBoxList.get(i).render(batch);
            }
        }
        batch.end();

        // for debugging
        if (GAMEPLAY_DEBUG) {
            debugRenderer.render(world, camera.combined.scl(PPM));
        }
        // create hud and add it to the GameScreen
        this.batch.setProjectionMatrix(hud.stage.getCamera().combined);
        // to
        hud.stage.draw();
    }

    /**
     * Update all game objects and data before the rendering of a new frame.
     */
    private void update(float delta) {
        // updates objects in the world
        world.step(1 / FPS, 6, 2);

        // update the camera position
        cameraUpdate();

        batch.setProjectionMatrix(camera.combined);
        // set the view of the map to the camera
        orthogonalTiledMapRenderer.setView(camera);
        if (remoteManager.getGameTime().isPresent()) {
            tileMapHelper.update(remoteManager.getGameTime().get());
        }
        player.update(delta, mapCenterPoint, remoteManager.getLocalPlayerState());
        if (AIGame) AIPlayer.update(delta, mapCenterPoint, remoteManager.getAIPlayerState(), remoteManager.getBulletData(), player);
        hud.update(remoteManager.getGameTime(), player, remoteManager.getRemotePlayers(), Optional.ofNullable(AIPlayer), delta);

        // if escape is pressed, the game will close
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
            AmericanDream.instance.setScreen(new TitleScreen(camera));

            // send message to server to remove player from lobby
            AmericanDream.client.sendTCP(new GameLeaveMessage());
        }
        if (Gdx.input.isKeyPressed(Input.Keys.J)) {
            GunPickupMessage gunPickupMessage = collisionHandler.removeGunBoxTouchingPlayer(player.getBody().getFixtureList(), gunBoxList);
            if (!gunPickupMessage.ids.isEmpty()) {
                gunPickupMessage.character = player.getName();
                AmericanDream.client.sendTCP(gunPickupMessage);
                Audio.getInstance().playSound(Audio.SoundType.GUN_PICKUP);
                hud.showGunPickupLabel();
            }
        }
        collisionHandler.removeGunBoxTaken(gunBoxList, player.getName());
    }

    /**
     * Updates the camera position relative to the players and the center point of the world.
     * New camera position will be the center point of the triangle created by players and the center point.
     */
    private void cameraUpdate() {
        // if player is out of bounds then set the camera to the center
        if (
                player.getPosition().y > mapCenterPoint.y + BOUNDS
                        || player.getPosition().y < mapCenterPoint.y - BOUNDS
                        || player.getPosition().x > mapCenterPoint.x + BOUNDS
                        || player.getPosition().x < mapCenterPoint.x - BOUNDS
        ) {
            // "lerp" makes the camera move smoothly back to the center point.
            camera.position.lerp(new Vector3(mapCenterPoint.x, mapCenterPoint.y, 0), 0.1f);
            camera.update();
            return;
        }
        // make camera follow the player slowly
        // vector from center to player
        Vector2 vector = new Vector2(player.getPosition().x - mapCenterPoint.x, player.getPosition().y - mapCenterPoint.y);
        camera.position.x = mapCenterPoint.x + vector.x / CAMERA_SPEED;
        camera.position.y = mapCenterPoint.y + vector.y / CAMERA_SPEED;
        // update the camera
        camera.update();
    }

    /**
     * This method is called when the game is closed.
     */
    @Override
    public void dispose() {
        super.dispose();
        world.dispose();
        batch.dispose();
        Audio.getInstance().dispose();
        debugRenderer.dispose();
    }
}
