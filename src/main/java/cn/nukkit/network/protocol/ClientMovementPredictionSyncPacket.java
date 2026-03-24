package cn.nukkit.network.protocol;

import cn.nukkit.math.Vector3f;
import lombok.ToString;

/**
 * Sent by the client to sync movement prediction state with the server.
 * Vanilla packet ID: 0x142 (322), internal ID: 222 (322 - 100).
 *
 * Contains entity flags, bounding box, movement attributes, runtime ID, and flying state.
 * The server accepts and ignores this packet — it is used only by the client-side
 * prediction system introduced in newer Bedrock versions.
 */
@ToString
public class ClientMovementPredictionSyncPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.__INTERNAL__MOVEMENT_PREDICTION_SYNC_PACKET;

    // Decoded fields (informational — server does not act on them)
    public Vector3f actorBoundingBox;
    public float movementSpeed;
    public float underwaterMovementSpeed;
    public float lavaMovementSpeed;
    public float jumpStrength;
    public float health;
    public float hunger;
    public long actorRuntimeId;
    public boolean actorFlyingState;

    @Override
    public void decode() {
        // Entity flags are encoded as a BigVarInt (continuation-bit VarInt > 64 bits).
        // Read and discard: the server does not process prediction flags.
        skipBigVarInt();

        actorBoundingBox = this.getVector3f();

        // MovementAttributesComponent — 6 little-endian floats
        movementSpeed = this.getLFloat();
        underwaterMovementSpeed = this.getLFloat();
        lavaMovementSpeed = this.getLFloat();
        jumpStrength = this.getLFloat();
        health = this.getLFloat();
        hunger = this.getLFloat();

        actorRuntimeId = this.getEntityRuntimeId();
        actorFlyingState = this.getBoolean();
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    /**
     * Advances the stream past a variable-width BigVarInt (standard 7-bit-per-byte
     * encoding, MSB = continuation bit). Used to skip the entity-flags field whose
     * bit-width exceeds 64 and cannot be held in a {@code long}.
     */
    private void skipBigVarInt() {
        while ((this.getByte() & 0x80) != 0) {
            // keep reading while the continuation bit is set
        }
    }
}
