package cn.nukkit.item;

/**
 * Ported from PowerNukkitX (author: Buddelbubi).
 */
public class ItemCopperSpear extends ItemSpear {

    public ItemCopperSpear() {
        this(0, 1);
    }

    public ItemCopperSpear(Integer meta) {
        this(meta, 1);
    }

    public ItemCopperSpear(Integer meta, int count) {
        super(COPPER_SPEAR, meta, count, "Copper Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_COPPER;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_COPPER;
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
        return 850L; // 17 game ticks
    }

    @Override public long getActivationDelayMs()  { return 650L;  }
    @Override public long getStage1DurationMs()    { return 4000L; }
    @Override public long getStage2DurationMs()    { return 4250L; }
    @Override public long getStage3DurationMs()    { return 4250L; }
    @Override public float getDismountSpeedBps()   { return 12f;   }
}
