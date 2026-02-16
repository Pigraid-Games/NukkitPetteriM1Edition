package cn.nukkit.item.enchantment.mace;

import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

public class EnchantmentBreach extends Enchantment {

    public EnchantmentBreach() {
        super(ID_BREACH, "breach", Rarity.RARE, EnchantmentType.MACE);
    }

    @Override
    public int getMaxLevel() {
        return 4;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 1 + (level - 1) * 11;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 20;
    }

    @Override
    protected boolean checkCompatibility(Enchantment enchantment) {
        return !(enchantment instanceof EnchantmentDensity)
                && !(enchantment instanceof cn.nukkit.item.enchantment.damage.EnchantmentDamageSmite)
                && !(enchantment instanceof cn.nukkit.item.enchantment.damage.EnchantmentDamageArthropods)
                && super.checkCompatibility(enchantment);
    }

    @Override
    public boolean isMajor() {
        return true;
    }

    /**
     * Returns the armor reduction factor for this level.
     * Each level reduces armor effectiveness by 15%.
     */
    public float getArmorBypassFactor() {
        return this.getLevel() * 0.15f;
    }
}
