package cn.nukkit.item.enchantment.bow;

import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.item.ItemBow;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class EnchantmentBow extends Enchantment {

    protected EnchantmentBow(int id, String name, Rarity rarity) {
        super(id, name, rarity, EnchantmentType.BOW);
    }

    /**
     * Called when a bow with this enchantment shoots an arrow.
     *
     * @param user       the entity using the bow
     * @param projectile the arrow entity that was shot
     * @param bow        the bow item
     */
    public void onBowShoot(EntityLiving user, EntityProjectile projectile, ItemBow bow) {

    }
}
