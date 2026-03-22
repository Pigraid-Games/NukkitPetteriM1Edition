package cn.nukkit.item.enchantment.spear;

import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

public abstract class EnchantmentSpear extends Enchantment {

    protected EnchantmentSpear(int id, String name, Rarity rarity) {
        super(id, name, rarity, EnchantmentType.SPEAR);
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return 50;
    }
}
