package cn.nukkit.item;

/**
 * Ported from PowerNukkitX (author: Buddelbubi).
 */
public class ItemWoodenSpear extends ItemSpear {

    public ItemWoodenSpear() {
        this(0, 1);
    }

    public ItemWoodenSpear(Integer meta) {
        this(meta, 1);
    }

    public ItemWoodenSpear(Integer meta, int count) {
        super(WOODEN_SPEAR, meta, count, "Wooden Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_WOODEN;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public int getAttackDamage() {
        return 2;
    }

    @Override
    public float getChargeDamageMultiplier() {
        return 0.716f;
    }

    @Override
    public long getJabCooldownMs() {
        return 650L; // 13 game ticks
    }

    @Override public long getActivationDelayMs()  { return 750L;  }
    @Override public long getStage1DurationMs()    { return 5000L; }
    @Override public long getStage2DurationMs()    { return 5000L; }
    @Override public long getStage3DurationMs()    { return 5000L; }
    @Override public float getDismountSpeedBps()   { return 14f;   }
}
