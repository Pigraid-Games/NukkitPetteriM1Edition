package cn.nukkit.item;

/**
 * Ported from PowerNukkitX (author: Buddelbubi).
 */
public class ItemNetheriteSpear extends ItemSpear {

    public ItemNetheriteSpear() {
        this(0, 1);
    }

    public ItemNetheriteSpear(Integer meta) {
        this(meta, 1);
    }

    public ItemNetheriteSpear(Integer meta, int count) {
        super(NETHERITE_SPEAR, meta, count, "Netherite Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_NETHERITE;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_NETHERITE;
    }

    @Override
    public int getAttackDamage() {
        return 6;
    }

    @Override
    public float getChargeDamageMultiplier() {
        return 1.2f;
    }

    @Override
    public long getJabCooldownMs() {
        return 1150L; // 23 game ticks
    }

    @Override public long getActivationDelayMs()  { return 400L;  }
    @Override public long getStage1DurationMs()    { return 2500L; }
    @Override public long getStage2DurationMs()    { return 3000L; }
    @Override public long getStage3DurationMs()    { return 3250L; }
    @Override public float getDismountSpeedBps()   { return 9f;    }
}
