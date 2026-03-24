package cn.nukkit.item;

/**
 * Ported from PowerNukkitX (author: Buddelbubi).
 */
public class ItemStoneSpear extends ItemSpear {

    public ItemStoneSpear() {
        this(0, 1);
    }

    public ItemStoneSpear(Integer meta) {
        this(meta, 1);
    }

    public ItemStoneSpear(Integer meta, int count) {
        super(STONE_SPEAR, meta, count, "Stone Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_STONE;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_STONE;
    }

    @Override
    public int getAttackDamage() {
        return 3;
    }

    @Override
    public float getChargeDamageMultiplier() {
        return 0.836f;
    }

    @Override
    public long getJabCooldownMs() {
        return 750L; // 15 game ticks
    }

    @Override public long getActivationDelayMs()  { return 700L;  }
    @Override public long getStage1DurationMs()    { return 4500L; }
    @Override public long getStage2DurationMs()    { return 4500L; }
    @Override public long getStage3DurationMs()    { return 4750L; }
    @Override public float getDismountSpeedBps()   { return 13f;   }
}
