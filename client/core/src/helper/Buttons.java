package helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import static helper.Constants.FONT_SCALING_FACTOR;

public class Buttons {
    /**
     * Disable a button by setting it to be not clickable
     * Changes the button style to be disabled
     *
     * @param button The button to be disabled
     */
    public static void disableButton(TextButton button) {
        button.setDisabled(true);
        button.getStyle().fontColor = Color.GRAY;
        button.getStyle().overFontColor = Color.GRAY;
    }

    /**
     * Create a text button with the given text
     *
     * @param text The text to be displayed on the button
     * @return A new TextButton with the given text
     */
    public static TextButton createButton(String text) {
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = new BitmapFont();
        buttonStyle.fontColor = Color.WHITE;
        TextButton button = new TextButton(text, buttonStyle);
        button.padLeft(Gdx.graphics.getWidth() / 20f);
        button.padRight(Gdx.graphics.getWidth() / 20f);
        button.padTop(Gdx.graphics.getHeight() / 30f);
        button.padBottom(Gdx.graphics.getHeight() / 30f);
        button.getLabel().setFontScale(Gdx.graphics.getWidth() / FONT_SCALING_FACTOR, Gdx.graphics.getHeight() / FONT_SCALING_FACTOR);
        button.getStyle().up = new TextureRegionDrawable(new TextureRegion(new Texture("pixel.jpg")));
        button.getStyle().over = new TextureRegionDrawable(new TextureRegion(new Texture("pixel.jpg"))).tint(Color.BLACK);
        return button;
    }
}
