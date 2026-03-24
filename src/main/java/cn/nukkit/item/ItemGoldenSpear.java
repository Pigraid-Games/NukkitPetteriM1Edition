package cn.nukkit.item;

/**
 * Ported from PowerNukkitX (author: Buddelbubi).
 */
public class ItemGoldenSpear extends ItemSpear {

    public ItemGoldenSpear() {
        this(0, 1);
    }

    public ItemGoldenSpear(Integer meta) {
        this(meta, 1);
    }

    public ItemGoldenSpear(Integer meta, int count) {
        super(GOLDEN_SPEAR, meta, count, "Golden Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_GOLD;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_GOLD;
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
        return 950L; // 19 game ticks
    }

    @Override public long getActivationDelayMs()  { return 700L;  }
    @Override public long getStage1DurationMs()    { return 3500L; }
    @Override public long getStage2DurationMs()    { return 5000L; }
    @Override public long getStage3DurationMs()    { return 5250L; }
    @Override public float getDismountSpeedBps()   { return 13f;   }
}
