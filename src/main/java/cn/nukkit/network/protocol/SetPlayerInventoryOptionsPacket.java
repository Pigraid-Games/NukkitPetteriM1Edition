package cn.nukkit.network.protocol;

/**
 * Sent by the client to inform the server about the player's inventory UI preferences.
 * This is a client-to-server packet (vanilla ID 0x133 / internal ID 207).
 * The server does not need to act on this data — it is decoded to avoid kicking the player.
 */
public class SetPlayerInventoryOptionsPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.__INTERNAL__SET_PLAYER_INVENTORY_OPTIONS_PACKET;

    /** Left tab selection (0=NONE, 1=RECIPE_CONSTRUCTION, 2=RECIPE_EQUIPMENT, 3=RECIPE_ITEMS, 4=RECIPE_NATURE, 5=RECIPE_SEARCH, 6=SURVIVAL) */
    public int leftTab;
    /** Right tab selection (0=NONE, 1=FULL_SCREEN, 2=CRAFTING, 3=ARMOR) */
    public int rightTab;
    /** Whether recipe filtering is enabled */
    public boolean filtering;
    /** Inventory layout (0=NONE, 1=INVENTORY_ONLY, 2=DEFAULT, 3=RECIPE_BOOK_ONLY) */
    public int layout;
    /** Crafting layout (same enum as layout) */
    public int craftingLayout;

    @Override
    public void decode() {
        this.leftTab = (int) this.getUnsignedVarInt();
        this.rightTab = (int) this.getUnsignedVarInt();
        this.filtering = this.getBoolean();
        this.layout = (int) this.getUnsignedVarInt();
        this.craftingLayout = (int) this.getUnsignedVarInt();
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.leftTab);
        this.putUnsignedVarInt(this.rightTab);
        this.putBoolean(this.filtering);
        this.putUnsignedVarInt(this.layout);
        this.putUnsignedVarInt(this.craftingLayout);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
