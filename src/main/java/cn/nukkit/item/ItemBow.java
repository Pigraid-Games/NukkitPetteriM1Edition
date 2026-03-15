package cn.nukkit.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.bow.EnchantmentBow;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemBow extends ItemTool {

    public ItemBow() {
        this(0, 1);
    }

    public ItemBow(Integer meta) {
        this(meta, 1);
    }

    public ItemBow(Integer meta, int count) {
        this(BOW, meta, count, "Bow");
    }

    public ItemBow(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getEnchantAbility() {
        return 1;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_BOW;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        return player.isCreative() || playerHasArrow(player);
    }

    @Override
    public boolean onRelease(Player player, int ticksUsed) {
        Optional<Map.Entry<Integer, Item>> inventoryOptional = player.getInventory().getContents().entrySet().stream()
                .filter(e -> e.getValue().getId() == ItemID.ARROW).findFirst();
        Optional<Map.Entry<Integer, Item>> offhandOptional = player.getOffhandInventory().getContents().entrySet().stream()
                .filter(e -> e.getValue().getId() == ItemID.ARROW).findFirst();

        if (offhandOptional.isEmpty() && inventoryOptional.isEmpty() && !player.isCreative()) {
            return false;
        }

        Item itemArrow = offhandOptional.isPresent()
                ? offhandOptional.get().getValue()
                : inventoryOptional.map(Map.Entry::getValue).orElse(null);

        if (itemArrow == null) {
            if (player.isCreative()) {
                itemArrow = Item.get(Item.ARROW, 0, 1);
            } else {
                return false;
            }
        }

        double damage = 2;
        Enchantment bowDamage = this.getEnchantment(Enchantment.ID_BOW_POWER);
        if (bowDamage != null && bowDamage.getLevel() > 0) {
            damage += (double) bowDamage.getLevel() * 0.5 + 0.5;
        }

        Enchantment flameEnchant = this.getEnchantment(Enchantment.ID_BOW_FLAME);
        boolean flame = flameEnchant != null && flameEnchant.getLevel() > 0;

        // Petter's per-arrow knockback — stored in NBT so EntityProjectile can apply it on hit
        float knockBack = 0.3f;
        Enchantment knockBackEnchantment = this.getEnchantment(Enchantment.ID_BOW_KNOCKBACK);
        if (knockBackEnchantment != null) {
            knockBack += knockBackEnchantment.getLevel() * 0.1f;
        }

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
                        .add(new FloatTag("", (float) -player.pitch)))
                .putShort("Fire", flame ? 2700 : 0)
                .putDouble("damage", damage)
                .putFloat("knockback", knockBack);

        double p = (double) ticksUsed / 20;
        final double maxForce = 3.5;
        double f = Math.min((p * p + p * 2) / 3, 1) * maxForce;

        EntityArrow arrow = (EntityArrow) Entity.createEntity(EntityArrow.NETWORK_ID, player.chunk, nbt, player, f == maxForce);
        if (arrow == null) {
            return false;
        }

        if (itemArrow.getDamage() > 0) {
            arrow.setData(itemArrow.getDamage());
        }

        EntityShootBowEvent entityShootBowEvent = new EntityShootBowEvent(player, this, arrow, f);

        if (f < 0.1 || ticksUsed < 3) {
            entityShootBowEvent.setCancelled();
        }

        Server.getInstance().getPluginManager().callEvent(entityShootBowEvent);
        if (entityShootBowEvent.isCancelled()) {
            entityShootBowEvent.getProjectile().close();
            return false;
        }

        entityShootBowEvent.getProjectile().setMotion(entityShootBowEvent.getProjectile().getMotion().multiply(entityShootBowEvent.getForce()));

        Enchantment infinityEnchant = this.getEnchantment(Enchantment.ID_BOW_INFINITY);
        boolean infinity = infinityEnchant != null && infinityEnchant.getLevel() > 0;
        EntityProjectile projectile;
        if (infinity && itemArrow.getDamage() == 0 && (projectile = entityShootBowEvent.getProjectile()) instanceof EntityArrow) {
            ((EntityArrow) projectile).setPickupMode(EntityArrow.PICKUP_CREATIVE);
        }

        // Dispatch onBowShoot callbacks to all bow enchantments
        for (Enchantment enc : this.getEnchantments()) {
            if (enc instanceof EnchantmentBow) {
                ((EnchantmentBow) enc).onBowShoot(player, arrow, this);
            }
        }

        if (!player.isCreative()) {
            if (!infinity || itemArrow.getDamage() != 0) {
                if (offhandOptional.isPresent()) {
                    player.getOffhandInventory().removeItem(Item.get(Item.ARROW, itemArrow.getDamage(), 1));
                } else {
                    player.getInventory().removeItem(Item.get(Item.ARROW, itemArrow.getDamage(), 1));
                }
            }

            if (!this.isUnbreakable()) {
                Enchantment durability = this.getEnchantment(Enchantment.ID_DURABILITY);
                if (!(durability != null && durability.getLevel() > 0 && (100 / (durability.getLevel() + 1)) <= ThreadLocalRandom.current().nextInt(100))) {
                    this.setDamage(this.getDamage() + 1);
                    if (this.getDamage() >= getMaxDurability()) {
                        this.count--;
                    }
                    player.getInventory().setItemInHand(this);
                }
            }
        }

        if (entityShootBowEvent.getProjectile() != null) {
            EntityProjectile proj = entityShootBowEvent.getProjectile();
            ProjectileLaunchEvent projectev = new ProjectileLaunchEvent(proj);
            Server.getInstance().getPluginManager().callEvent(projectev);
            if (projectev.isCancelled()) {
                proj.close();
            } else {
                proj.spawnToAll();
                player.getLevel().addLevelSoundEvent(player, LevelSoundEventPacket.SOUND_BOW);
            }
        }

        return true;
    }

    private boolean playerHasArrow(Player p) {
        if (p.getOffhandInventory().getItemFast(0).getId() == ItemID.ARROW) return true;
        for (Item i : p.getInventory().getContents().values()) {
            if (i.getId() == ItemID.ARROW) return true;
        }
        return false;
    }
}
