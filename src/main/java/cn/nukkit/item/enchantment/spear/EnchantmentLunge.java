package cn.nukkit.item.enchantment.spear;

import cn.nukkit.item.enchantment.Enchantment;

public class EnchantmentLunge extends EnchantmentSpear {

    public EnchantmentLunge() {
        super(Enchantment.ID_LUNGE, "lunge", Rarity.UNCOMMON);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }
}
