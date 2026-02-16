package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityCrossbowFirework;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.concurrent.ThreadLocalRandom;

public class ItemCrossbow extends ItemBow {

    public ItemCrossbow() {
        this(0, 1);
    }

    public ItemCrossbow(Integer meta) {
        this(meta, 1);
    }

    public ItemCrossbow(Integer meta, int count) {
        super(CROSSBOW, meta, count, "Crossbow");
    }

    @Override
    public int getEnchantAbility() {
        return 1;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_CROSSBOW;
    }

    public boolean isLoaded() {
        Tag itemInfo = this.getNamedTagEntry("chargedItem");
        if (itemInfo != null) {
            CompoundTag tag = (CompoundTag) itemInfo;
            return tag.getByte("Count") > 0 && !tag.getString("Name").isEmpty();
        }

        return false;
    }

    @Override
    public boolean isSupportedOn(int protocol) {
        return protocol >= ProtocolInfo.v1_8_0;
    }

    /**
     * Launch the crossbow. Assumes that isLoaded() == true.
     *
     * @param player player
     * @return launched successfully
     */
    public boolean launchArrow(Player player) {
        CompoundTag chargedItem = ((CompoundTag) this.getNamedTagEntry("chargedItem"));
        if (chargedItem == null) {
            throw new IllegalArgumentException("A crossbow without charged item cannot be launched!");
        }
        int arrowData = chargedItem.getShort("Damage");
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", player.x))
                        .add(new DoubleTag("", player.y + player.getEyeHeight()))
                        .add(new DoubleTag("", player.z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", -Math.sin(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI)))
                        .add(new DoubleTag("", -Math.sin(player.pitch / 180 * Math.PI)))
                        .add(new DoubleTag("", Math.cos(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI))))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", (player.yaw > 180 ? 360 : 0) - (float) player.yaw))
                        .add(new FloatTag("", (float) -player.pitch)));
        EntityProjectile arrow;
        Enchantment piercingEnchant = this.getEnchantment(Enchantment.ID_CROSSBOW_PIERCING);
        boolean isFirework = "minecraft:firework_rocket".equals(chargedItem.getString("Name"));
        if (isFirework) {
            arrow = new EntityCrossbowFirework(player.chunk, nbt, player);
            Item firework = Item.get(Item.FIREWORKS, arrowData, 1);
            if (chargedItem.contains("tag")) {
                firework.setCompoundTag(chargedItem.getCompound("tag"));
            }
            ((EntityCrossbowFirework) arrow).setFirework(firework);
        } else {
            arrow = (EntityArrow) Entity.createEntity(EntityArrow.NETWORK_ID, player.chunk, nbt, player, true, true);
            if (arrowData > 0) {
                ((EntityArrow) arrow).setData(arrowData);
            }
            if (piercingEnchant != null) {
                arrow.piercing = piercingEnchant.getLevel();
            }
        }
        EntityShootBowEvent entityShootBowEvent = new EntityShootBowEvent(player, this, arrow, isFirework ? 1 : 3.5);
        Server.getInstance().getPluginManager().callEvent(entityShootBowEvent);
        if (entityShootBowEvent.isCancelled()) {
            entityShootBowEvent.getProjectile().close();
            return false;
        } else {
            entityShootBowEvent.getProjectile().setMotion(entityShootBowEvent.getProjectile().getMotion().multiply(entityShootBowEvent.getForce()));
            if (entityShootBowEvent.getProjectile() != null) {
                EntityProjectile proj = entityShootBowEvent.getProjectile();
                ProjectileLaunchEvent projectev = new ProjectileLaunchEvent(proj);
                Server.getInstance().getPluginManager().callEvent(projectev);
                if (projectev.isCancelled()) {
                    proj.close();
                    return false;
                } else {
                    proj.spawnToAll();
                    if (!isFirework && this.hasEnchantment(Enchantment.ID_CROSSBOW_MULTISHOT)) {
                        double force = entityShootBowEvent.getForce();
                        double baseMotionX = -Math.sin(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI);
                        double baseMotionY = -Math.sin(player.pitch / 180 * Math.PI);
                        double baseMotionZ = Math.cos(player.yaw / 180 * Math.PI) * Math.cos(player.pitch / 180 * Math.PI);
                        for (double spreadAngle : new double[]{-10.0, 10.0}) {
                            double rad = Math.toRadians(spreadAngle);
                            double cos = Math.cos(rad);
                            double sin = Math.sin(rad);
                            double rotatedX = baseMotionX * cos - baseMotionZ * sin;
                            double rotatedZ = baseMotionX * sin + baseMotionZ * cos;
                            CompoundTag sideNbt = new CompoundTag()
                                    .putList(new ListTag<DoubleTag>("Pos")
                                            .add(new DoubleTag("", player.x))
                                            .add(new DoubleTag("", player.y + player.getEyeHeight()))
                                            .add(new DoubleTag("", player.z)))
                                    .putList(new ListTag<DoubleTag>("Motion")
                                            .add(new DoubleTag("", rotatedX))
                                            .add(new DoubleTag("", baseMotionY))
                                            .add(new DoubleTag("", rotatedZ)))
                                    .putList(new ListTag<FloatTag>("Rotation")
                                            .add(new FloatTag("", (player.yaw > 180 ? 360 : 0) - (float) player.yaw + (float) spreadAngle))
                                            .add(new FloatTag("", (float) -player.pitch)));
                            EntityArrow sideArrow = (EntityArrow) Entity.createEntity(EntityArrow.NETWORK_ID, player.chunk, sideNbt, player, true, true);
                            sideArrow.setPickupMode(EntityProjectile.PICKUP_NONE_REMOVE);
                            if (arrowData > 0) {
                                sideArrow.setData(arrowData);
                            }
                            if (piercingEnchant != null) { // Illegal enchantment (Multishot + Piercing)
                                sideArrow.piercing = piercingEnchant.getLevel();
                            }
                            sideArrow.setMotion(sideArrow.getMotion().multiply(force));
                            sideArrow.spawnToAll();
                        }
                    }
                    player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_CROSSBOW_SHOOT);

                    // Durability on fire: 1 arrow, 3 firework, 3 multishot arrows, 9 multishot fireworks
                    if (!player.isCreative() && !this.isUnbreakable()) {
                        boolean hasMultishot = this.hasEnchantment(Enchantment.ID_CROSSBOW_MULTISHOT);
                        int durabilityCost;
                        if (isFirework) {
                            durabilityCost = hasMultishot ? 9 : 3;
                        } else {
                            durabilityCost = hasMultishot ? 3 : 1;
                        }
                        Enchantment durability = this.getEnchantment(Enchantment.ID_DURABILITY);
                        if (!(durability != null && durability.getLevel() > 0 && (100 / (durability.getLevel() + 1)) <= ThreadLocalRandom.current().nextInt(100))) {
                            this.setDamage(this.getDamage() + durabilityCost);
                            if (this.getDamage() >= DURABILITY_CROSSBOW) {
                                this.count--;
                            }
                        }
                    }

                    this.setCompoundTag(this.getNamedTag().putBoolean("Charged", false).remove("chargedItem"));
                    player.getInventory().setItemInHand(this);
                }
            }
        }
        return true;
    }

    public Item loadArrow(Item arrow) {
        if (arrow == null) {
            throw new IllegalArgumentException("null cannot be loaded into a crossbow!");
        }
        if (arrow.getId() != Item.ARROW && arrow.getId() != Item.FIREWORKS) {
            throw new IllegalArgumentException(arrow + " cannot be loaded into a crossbow!");
        }
        if (arrow.getCount() != 1) {
            throw new IllegalArgumentException("Only one arrow per crossbow is supported!");
        }
        CompoundTag tag = this.getNamedTag() == null ? new CompoundTag() : this.getNamedTag();
        boolean isFirework = arrow.getId() == Item.FIREWORKS;
        CompoundTag chargedItem = new CompoundTag("chargedItem")
                .putByte("Count", arrow.getCount())
                .putShort("Damage", arrow.getDamage())
                .putString("Name", isFirework ? "minecraft:firework_rocket" : "minecraft:arrow");
        CompoundTag cTag;
        if (isFirework && (cTag = arrow.getNamedTag()) != null) {
            chargedItem.putCompound("tag", cTag);
        }
        tag.putBoolean("Charged", true).putCompound("chargedItem", chargedItem);
        this.setCompoundTag(tag);
        return this;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return false; // Handled directly from Player
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        return true;
    }

    @Override
    public boolean onUse(Player player, int ticksUsed) {
        // Quick Charge: 25/19/13/6 ticks for level 0/I/II/III
        int needTickUsed = 25;
        Enchantment enchantment = this.getEnchantment(Enchantment.ID_CROSSBOW_QUICK_CHARGE);
        if (enchantment != null) {
            int level = enchantment.getLevel();
            switch (level) {
                case 1: needTickUsed = 19; break;
                case 2: needTickUsed = 13; break;
                default: needTickUsed = 6; break; // QC III+
            }
        }

        if (ticksUsed < needTickUsed) {
            return true;
        }

        Item itemArrow = null;
        boolean offhand = false;
        Item offhandItem = player.getOffhandInventory().getItemFast(0);
        if (offhandItem.getId() == ItemID.ARROW || offhandItem.getId() == ItemID.FIREWORKS) {
            itemArrow = offhandItem.clone();
            itemArrow.setCount(1);
            offhand = true;
        } else {
            for (Item i : player.getInventory().getContents().values()) {
                if (i.getId() == ItemID.ARROW || i.getId() == ItemID.FIREWORKS) {
                    itemArrow = i.clone();
                    itemArrow.setCount(1);
                    break;
                }
            }
        }

        if (itemArrow == null) {
            if (player.isCreative()) {
                itemArrow = Item.get(Item.ARROW, 0, 1);
            } else {
                return true;
            }
        }

        if (!this.isLoaded()) {
            if (!player.isCreative()) {
                if (offhand) {
                    player.getOffhandInventory().removeItem(itemArrow);
                } else {
                    player.getInventory().removeItem(itemArrow);
                }
            }

            player.getInventory().setItemInHand(this.loadArrow(itemArrow));
            boolean hasQuickCharge = enchantment != null;
            player.getLevel().addLevelSoundEvent(player, hasQuickCharge
                    ? LevelSoundEventPacket.SOUND_CROSSBOW_QUICK_CHARGE_END
                    : LevelSoundEventPacket.SOUND_CROSSBOW_LOADING_END);
        }

        return true;
    }
}
