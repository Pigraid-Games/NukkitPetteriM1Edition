package cn.nukkit.entity.data.property;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable definition of one entity property for a specific entity type.
 * The index is the position in the entity type's schema list — used as the
 * key in the index-based AddEntityPacket/SetEntityDataPacket property arrays.
 */
public class EntityPropertyDefinition {

    public static final int TYPE_INT   = 0;
    public static final int TYPE_FLOAT = 1;
    public static final int TYPE_BOOL  = 2;
    public static final int TYPE_ENUM  = 3;

    private final String name;
    private final int type;
    private final int index;
    /** Valid values, used only when type == TYPE_ENUM. Grows dynamically. */
    private final List<String> enumValues;

    public EntityPropertyDefinition(String name, int type, int index) {
        this.name = name;
        this.type = type;
        this.index = index;
        this.enumValues = (type == TYPE_ENUM) ? new ArrayList<>() : null;
    }

    public String getName()  { return name; }
    public int    getType()  { return type; }
    public int    getIndex() { return index; }

    /** Returns the live enum values list (mutate to add new values). */
    public List<String> getEnumValues() { return enumValues; }

    /**
     * Infers the property type from a Java value.
     * Boolean → BOOL, Integer → INT, Float → FLOAT, String → ENUM.
     */
    public static int inferType(Object value) {
        if (value instanceof Boolean) return TYPE_BOOL;
        if (value instanceof Integer) return TYPE_INT;
        if (value instanceof Float)   return TYPE_FLOAT;
        return TYPE_ENUM;
    }
}
