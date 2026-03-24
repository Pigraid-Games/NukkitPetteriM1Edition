package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.property.EntityPropertyDefinition;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import lombok.ToString;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Declares the entity property SCHEMA for a specific entity type.
 * Sent to a player before/when an entity of that type spawns, and whenever
 * the schema changes (new property or new enum value).
 *
 * This packet tells the client what properties EXIST for a type — not the
 * per-instance values. Values are sent via ChangeMobPropertyPacket.
 */
@ToString
public class SyncEntityPropertyPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SYNC_ENTITY_PROPERTY_PACKET;

    /** Entity type identifier, e.g. "minecraft:zombie" or "example:my_entity" */
    public String entityTypeId;
    /** Ordered property definitions (order = index used in AddEntityPacket) */
    public List<EntityPropertyDefinition> properties;

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();

        CompoundTag root = new CompoundTag("");
        root.putString("type", this.entityTypeId);

        ListTag<CompoundTag> propList = new ListTag<>("properties");
        for (EntityPropertyDefinition def : this.properties) {
            CompoundTag propTag = new CompoundTag("");
            propTag.putString("name", def.getName());
            propTag.putInt("type", def.getType());
            // clientSync always true — our API always syncs
            propTag.putByte("clientSync", (byte) 1);

            switch (def.getType()) {
                case EntityPropertyDefinition.TYPE_INT:
                    propTag.putInt("default", 0);
                    propTag.putInt("min", Integer.MIN_VALUE);
                    propTag.putInt("max", Integer.MAX_VALUE);
                    break;
                case EntityPropertyDefinition.TYPE_FLOAT:
                    propTag.putFloat("default", 0f);
                    propTag.putFloat("min", -Float.MAX_VALUE);
                    propTag.putFloat("max", Float.MAX_VALUE);
                    break;
                case EntityPropertyDefinition.TYPE_BOOL:
                    // no extra fields for bool
                    break;
                case EntityPropertyDefinition.TYPE_ENUM:
                    ListTag<StringTag> enumTag = new ListTag<>("enum");
                    for (String v : def.getEnumValues()) {
                        enumTag.add(new StringTag("", v));
                    }
                    propTag.putList(enumTag);
                    break;
            }
            propList.add(propTag);
        }
        root.putList(propList);

        try {
            this.put(NBTIO.write(root, ByteOrder.BIG_ENDIAN, true));
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode SyncEntityPropertyPacket NBT", e);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
