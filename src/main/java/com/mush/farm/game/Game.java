/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mush.farm.game;

import com.mush.farm.game.events.CharacterEvent;
import com.mush.farm.game.events.ControlEvent;
import com.mush.farm.game.events.GenericGameEvent;
import com.mush.farm.game.events.InteractionEvent;
import com.mush.farm.game.render.GameRenderer;
import com.mush.farm.game.model.Body;
import com.mush.farm.game.model.BodyType;
import com.mush.farm.game.model.GameCharacters;
import com.mush.farm.game.model.MovableCharacter;
import com.mush.farm.game.model.GameMap;
import com.mush.farm.game.model.GameMapGenerator;
import com.mush.farm.game.model.MapObject;
import com.mush.farm.game.model.MapObjectType;
import java.util.List;

/**
 *
 * @author mush
 */
public class Game {

    public GameControl control;
    public GameRenderer renderer;
    public GameMap gameMap;
    public GameKeyboardListener keyboardListener;
    public GameEventQueue eventQueue;
    public GameCharacters characters;

    private MovableCharacter playerCharacter;
    private boolean showStats;
    private boolean paused = false;

    public Game() {
        eventQueue = new GameEventQueue();
        control = new GameControl(this);
        gameMap = new GameMap(eventQueue);
        characters = new GameCharacters(gameMap, eventQueue);
        renderer = new GameRenderer(this);
        keyboardListener = new GameKeyboardListener(control);

        eventQueue.addListener(this);
        eventQueue.addListener(gameMap);

        showStats = false;

        new GameMapGenerator(gameMap).generate();
        setupBodies();
    }

    // todo: ugly, put all of this somewhere else
    private void setupBodies() {
        for (int i = 0; i < 3; i++) {
            gameMap.spawnBody(BodyType.BUCKET, Math.random() * 25 * 16, Math.random() * 25 * 16);
        }

        for (int i = 0; i < 3; i++) {
            characters.spawn((int) (100 + Math.random() * 200), (int) (20 + Math.random() * 200), BodyType.PERSON);
        }

        playerCharacter = characters.getCharacters().get(0);
    }

    public void update(double elapsedSeconds) {
        eventQueue.process();

        double seconds = paused ? 0 : elapsedSeconds;

        characters.update(seconds);
        gameMap.update(seconds);
    }

    public void togglePause() {
        paused = !paused;
    }

    public void changeCharacter() {
        List<MovableCharacter> all = characters.getCharacters();
        int ind = all.indexOf(playerCharacter);
        ind++;
        playerCharacter.velocity.setLocation(0, 0);
        playerCharacter = ind >= all.size() ? all.get(0) : all.get(ind);
    }

    public MovableCharacter getPlayer() {
        return playerCharacter;
    }

    public boolean getShowStats() {
        return showStats;
    }

    public void onEvent(ControlEvent event) {
        switch (event.action) {
            case APPLY_JOYSTICK:
                onJoystick();
                break;
            case PAUSE:
                paused = !paused;
                break;
            case TOGGLE_STATS:
                showStats = !showStats;
                break;
            case CHANGE_CHARACTER:
                changeCharacter();
                break;
        }
    }

    public void onEvent(GenericGameEvent event) {
        switch (event.eventName) {
            case "setTile":
                setTile((MapObjectType) event.eventPayload);
                break;
        }
    }

    public void onEvent(CharacterEvent.Interact event) {
        MovableCharacter character = characters.getCharacter(event.characterId);
        if (character == null) {
            return;
        }
        double shortest = Double.POSITIVE_INFINITY;
        Body nearest = null;
        for (Body body : gameMap.getBodies()) {
            if (body != character.body) {
                double dx = body.position.x - character.body.position.x;
                double dy = body.position.y - character.body.position.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < shortest) {
                    shortest = dist;
                    nearest = body;
                }
            }
        }

        Body tool = character.getEquipped();

        if (nearest != null && shortest < GameRenderer.TILE_SIZE) {
            if (tool != null) {
                eventQueue.add(new InteractionEvent.BodyOnBody(character, tool, nearest));
            } else {
                gameMap.getBodies().remove(nearest);
                character.addToInventory(nearest);
            }
        }
        if (tool != null) {
            int u = (int) ((playerCharacter.body.position.x) / GameRenderer.TILE_SIZE);
            int v = (int) ((playerCharacter.body.position.y + GameRenderer.TILE_SIZE) / GameRenderer.TILE_SIZE);

            MapObject mapObject = gameMap.getMapObject(u, v);
            eventQueue.add(new InteractionEvent.BodyOnMapObject(character, tool, mapObject));
        }
    }

    public void onEvent(CharacterEvent.Drop event) {
        MovableCharacter character = characters.getCharacter(event.characterId);
        if (character == null) {
            return;
        }
        Body item = character.removeLastFromInventory();
        if (item != null) {
            item.position.setLocation(character.body.position);
            // just a tiny bit in front of the character
            item.position.y += 1;
            gameMap.getBodies().add(item);
        }
    }

    public void onEvent(CharacterEvent.Equip event) {
        MovableCharacter character = characters.getCharacter(event.characterId);
        if (character == null) {
            return;
        }
        character.equipFromInventory(event.inventoryIndex);
    }

    public void onEvent(CharacterEvent.Unequip event) {
        MovableCharacter character = characters.getCharacter(event.characterId);
        if (character == null) {
            return;
        }
        character.unequipIntoInventory();
    }

    public void onEvent(CharacterEvent.CycleInventory event) {
        MovableCharacter character = characters.getCharacter(event.characterId);
        if (character == null) {
            return;
        }
        character.cycleInventory();
    }

    public void onEvent(InteractionEvent.BodyOnBody event) {
        System.out.println("interaction by c.id:" + event.character.characterId
                + " with tool.type:" + event.tool.type
                + " on target.type:" + event.target.type);
    }

    public void onEvent(InteractionEvent.BodyOnMapObject event) {
        System.out.println("interaction by c.id:" + event.character.characterId
                + " with tool.type:" + event.tool.type
                + " on target.type:" + event.target.type);
    }

    private void onJoystick() {
        playerCharacter.move(control.joystick.getXJoystick(), control.joystick.getYJoystick());
    }

    private void setTile(MapObjectType type) {
        int u = (int) ((playerCharacter.body.position.x) / GameRenderer.TILE_SIZE);
        int v = (int) ((playerCharacter.body.position.y + GameRenderer.TILE_SIZE) / GameRenderer.TILE_SIZE);

        gameMap.setTile(u, v, type);
    }

}
