package cn.nukkit.item;

/**
 * Ported from PowerNukkitX (author: Buddelbubi).
 */
public class ItemIronSpear extends ItemSpear {

    public ItemIronSpear() {
        this(0, 1);
    }

    public ItemIronSpear(Integer meta) {
        this(meta, 1);
    }

    public ItemIronSpear(Integer meta, int count) {
        super(IRON_SPEAR, meta, count, "Iron Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_IRON;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_IRON;
    }

    @Override
    public int getAttackDamage() {
        return 4;
    }

    @Override
    public float getChargeDamageMultiplier() {
        return 0.966f;
    }

    @Override
    public long getJabCooldownMs() {
        return 950L; // 19 game ticks
    }

    @Override public long getActivationDelayMs()  { return 600L;  }
    @Override public long getStage1DurationMs()    { return 2500L; }
    @Override public long getStage2DurationMs()    { return 4250L; }
    @Override public long getStage3DurationMs()    { return 4500L; }
    @Override public float getDismountSpeedBps()   { return 11f;   }
}
