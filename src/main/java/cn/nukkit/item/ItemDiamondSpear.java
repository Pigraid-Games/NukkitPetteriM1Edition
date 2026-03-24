package cn.nukkit.item;

/**
 * Ported from PowerNukkitX (author: Buddelbubi).
 */
public class ItemDiamondSpear extends ItemSpear {

    public ItemDiamondSpear() {
        this(0, 1);
    }

    public ItemDiamondSpear(Integer meta) {
        this(meta, 1);
    }

    public ItemDiamondSpear(Integer meta, int count) {
        super(DIAMOND_SPEAR, meta, count, "Diamond Spear");
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_DIAMOND;
    }

    @Override
    public int getTier() {
        return ItemTool.TIER_DIAMOND;
    }

    @Override
    public int getAttackDamage() {
        return 5;
    }

    @Override
    public float getChargeDamageMultiplier() {
        return 1.075f;
    }

    @Override
    public long getJabCooldownMs() {
        return 1050L; // 21 game ticks
    }

    @Override public long getActivationDelayMs()  { return 500L;  }
    @Override public long getStage1DurationMs()    { return 3000L; }
    @Override public long getStage2DurationMs()    { return 3500L; }
    @Override public long getStage3DurationMs()    { return 3500L; }
    @Override public float getDismountSpeedBps()   { return 10f;   }
}
