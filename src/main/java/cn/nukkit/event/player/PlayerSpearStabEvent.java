package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.Item;
import lombok.Getter;

/**
 * Fired before a player performs a spear stab attack.
 * Cancelling this event prevents the stab from dealing damage and applying lunge.
 *
 * Ported from PowerNukkitX by xRookieFight.
 */
public class PlayerSpearStabEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Getter
    private final Item item;

    @Getter
    private final float movementSpeed;

    public PlayerSpearStabEvent(Player player, Item item, float movementSpeed) {
        this.player = player;
        this.item = item;
        this.movementSpeed = movementSpeed;
    }
}
