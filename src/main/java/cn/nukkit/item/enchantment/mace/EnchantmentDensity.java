package cn.nukkit.item.enchantment.mace;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

public class EnchantmentDensity extends Enchantment {

    public EnchantmentDensity() {
        super(ID_DENSITY, "density", Rarity.COMMON, EnchantmentType.MACE);
    }

    @Override
    public int getMaxLevel() {
        return 5;
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
        return !(enchantment instanceof EnchantmentBreach)
                && !(enchantment instanceof cn.nukkit.item.enchantment.damage.EnchantmentDamageSmite)
                && !(enchantment instanceof cn.nukkit.item.enchantment.damage.EnchantmentDamageArthropods)
                && super.checkCompatibility(enchantment);
    }

    @Override
    public double getDamageBonus(Entity target, Entity damager) {
        double height = Math.max(damager.fallDistance, damager.highestPosition - damager.y);
        if (height >= 1.5) {
            return height * 0.5 * this.level;
        }
        return 0;
    }

    @Override
    public boolean isMajor() {
        return true;
    }
}
