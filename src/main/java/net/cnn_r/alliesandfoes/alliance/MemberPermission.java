package net.cnn_r.alliesandfoes.alliance;

import com.mojang.serialization.Codec;

public enum MemberPermission {
    BUILD("Build"),
    BREAK_BLOCKS("Break Blocks"),
    PVP("PvP"),
    PVE("PvE");

    public static final Codec<MemberPermission> CODEC = Codec.STRING.xmap(
            MemberPermission::valueOf,
            MemberPermission::name
    );

    public final String displayName;

    MemberPermission(String d) { this.displayName = d; }
}
