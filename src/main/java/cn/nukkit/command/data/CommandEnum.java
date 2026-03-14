package cn.nukkit.command.data;

import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.item.ItemID;
import cn.nukkit.network.protocol.UpdateSoftEnumPacket;
import cn.nukkit.potion.Effect;
import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * @author CreeperFace
 */
@ToString
@EqualsAndHashCode
public class CommandEnum {

    public static final CommandEnum ENUM_BOOLEAN = new CommandEnum("Boolean", ImmutableList.of("true", "false"));
    public static final CommandEnum ENUM_GAMEMODE = new CommandEnum("GameMode", ImmutableList.of("default", "creative", "spectator", "survival", "adventure", "d", "c", "s", "a"));
    public static final CommandEnum ENUM_DIFFICULTY = new CommandEnum("Difficulty", ImmutableList.of("peaceful", "p", "easy", "e", "normal", "n", "hard", "h"));
    public static final CommandEnum ENUM_BLOCK;
    public static final CommandEnum ENUM_ITEM;
    public static final CommandEnum ENUM_EFFECTS;

    static {
        ImmutableList.Builder<String> blocks = ImmutableList.builder();
        for (Field field : BlockID.class.getDeclaredFields()) {
            blocks.add(field.getName().toLowerCase(Locale.ROOT));
        }
        ENUM_BLOCK = new CommandEnum("Block", blocks.build());

        ImmutableList.Builder<String> items = ImmutableList.builder();
        for (Field field : ItemID.class.getDeclaredFields()) {
            items.add(field.getName().toLowerCase(Locale.ROOT));
        }
        items.addAll(ENUM_BLOCK.getValues());
        ENUM_ITEM = new CommandEnum("Item", items.build());

        ImmutableList.Builder<String> effects = ImmutableList.builder();
        for (Field field : Effect.class.getDeclaredFields()) {
            if (field.getType() == int.class && field.getModifiers() == (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL)) {
                effects.add(field.getName().toLowerCase(Locale.ROOT));
            }
        }
        ENUM_EFFECTS = new CommandEnum("Effect", effects.build());
    }

    private final String name;
    private final List<String> values;
    private final boolean soft;
    private final Supplier<Collection<String>> supplier;

    public CommandEnum(String name, String... values) {
        this(name, Arrays.asList(values));
    }

    public CommandEnum(String name, List<String> values) {
        this(name, values, false);
    }

    public CommandEnum(String name, List<String> values, boolean soft) {
        this.name = name;
        this.values = values;
        this.soft = soft;
        this.supplier = null;
    }

    public CommandEnum(String name, Supplier<Collection<String>> supplier) {
        this.name = name;
        this.values = null;
        this.soft = true;
        this.supplier = supplier;
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        if (this.supplier == null) {
            return values;
        } else {
            return new ArrayList<>(supplier.get());
        }
    }

    public boolean isSoft() {
        return soft;
    }

    public void updateSoftEnum(UpdateSoftEnumPacket.Type mode, String... value) {
        if (!this.soft) return;
        UpdateSoftEnumPacket packet = new UpdateSoftEnumPacket();
        packet.name = this.getName();
        packet.values = Arrays.asList(value);
        packet.type = mode;
        Server.broadcastPacket(Server.getInstance().getOnlinePlayers().values(), packet);
    }

    public void updateSoftEnum() {
        if (!this.soft) return;
        UpdateSoftEnumPacket packet = new UpdateSoftEnumPacket();
        packet.name = this.getName();
        packet.values = this.getValues();
        packet.type = UpdateSoftEnumPacket.Type.SET;
        Server.broadcastPacket(Server.getInstance().getOnlinePlayers().values(), packet);
    }
}
