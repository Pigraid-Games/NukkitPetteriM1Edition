package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.ItemSpear;
import lombok.Getter;
import lombok.Setter;

/**
 * Called when a player performs a spear charge attack collision.
 * Can be cancelled to prevent the damage.
 */
public class PlayerSpearChargeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Getter
    private final ItemSpear item;

    @Getter
    private final Entity target;

    @Getter
    private final ItemSpear.ChargeStage stage;

    @Getter
    @Setter
    private float damage;

    public PlayerSpearChargeEvent(Player player, ItemSpear item, Entity target, ItemSpear.ChargeStage stage, float damage) {
        this.player = player;
        this.item = item;
        this.target = target;
        this.stage = stage;
        this.damage = damage;
    }
}
