package cn.nukkit.entity.data.property;

import cn.nukkit.network.protocol.SyncEntityPropertyPacket;
import cn.nukkit.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Global singleton registry mapping entity type identifiers to their ordered
 * list of property definitions. Indices are assigned in first-use order and
 * never change for the server lifetime (transient — resets on restart).
 */
public class EntityPropertySchemaRegistry {

    private static final EntityPropertySchemaRegistry INSTANCE = new EntityPropertySchemaRegistry();

    /** entity type string → ordered property definitions (index = list position) */
    private final Map<String, List<EntityPropertyDefinition>> schemas = new ConcurrentHashMap<>();

    private EntityPropertySchemaRegistry() {}

    public static EntityPropertySchemaRegistry get() {
        return INSTANCE;
    }

    /**
     * Looks up or registers a property for an entity type.
     *
     * @return true if the schema changed (new property OR new enum value added),
     *         meaning callers should re-broadcast SyncEntityPropertyPacket.
     */
    public boolean registerOrUpdate(String entityTypeId, String key, Object value) {
        if (entityTypeId == null || entityTypeId.isEmpty()) return false;

        List<EntityPropertyDefinition> schema =
                schemas.computeIfAbsent(entityTypeId, k -> new ArrayList<>());

        // Find existing definition
        for (EntityPropertyDefinition def : schema) {
            if (def.getName().equals(key)) {
                // Property already registered. For ENUM type, check if value is new.
                if (def.getType() == EntityPropertyDefinition.TYPE_ENUM
                        && value instanceof String
                        && !def.getEnumValues().contains((String) value)) {
                    def.getEnumValues().add((String) value);
                    return true; // schema changed — new enum value
                }
                return false; // no change
            }
        }

        // New property — assign next index
        int newIndex = schema.size();
        int type = EntityPropertyDefinition.inferType(value);
        EntityPropertyDefinition def = new EntityPropertyDefinition(key, type, newIndex);
        if (type == EntityPropertyDefinition.TYPE_ENUM && value instanceof String) {
            def.getEnumValues().add((String) value);
        }
        schema.add(def);
        return true; // schema changed — new property
    }

    /** Returns the schema for an entity type, or an empty list if none registered. */
    public List<EntityPropertyDefinition> getSchema(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) return Collections.emptyList();
        return schemas.getOrDefault(entityTypeId, Collections.emptyList());
    }

    /** Returns true if any properties are registered for this entity type. */
    public boolean hasSchema(String entityTypeId) {
        List<EntityPropertyDefinition> s = schemas.get(entityTypeId);
        return s != null && !s.isEmpty();
    }

    /**
     * Finds the definition for a specific property name, or null if not registered.
     */
    public EntityPropertyDefinition getDefinition(String entityTypeId, String key) {
        for (EntityPropertyDefinition def : getSchema(entityTypeId)) {
            if (def.getName().equals(key)) return def;
        }
        return null;
    }

    /** Returns all entity type IDs that have at least one registered property. */
    public Set<String> getAllEntityTypeIds() {
        return schemas.keySet();
    }

    /**
     * Builds and sends a SyncEntityPropertyPacket for the given entity type to
     * the given player.
     */
    public void sendSchema(String entityTypeId, Player player) {
        List<EntityPropertyDefinition> schema = getSchema(entityTypeId);
        if (schema.isEmpty()) return;
        SyncEntityPropertyPacket pk = new SyncEntityPropertyPacket();
        pk.entityTypeId = entityTypeId;
        pk.properties = schema;
        player.dataPacket(pk);
    }

    /**
     * Broadcasts an updated schema for an entity type to all online players.
     * Call this whenever the schema changes (new property or new enum value).
     */
    public void broadcastSchema(String entityTypeId,
                                Collection<? extends Player> onlinePlayers) {
        List<EntityPropertyDefinition> schema = getSchema(entityTypeId);
        if (schema.isEmpty()) return;
        SyncEntityPropertyPacket pk = new SyncEntityPropertyPacket();
        pk.entityTypeId = entityTypeId;
        pk.properties = schema;
        for (Player player : onlinePlayers) {
            player.dataPacket(pk);
        }
    }
}
