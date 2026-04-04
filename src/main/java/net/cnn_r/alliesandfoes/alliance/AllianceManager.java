package net.cnn_r.alliesandfoes.alliance;

import net.cnn_r.alliesandfoes.network.AllianceCreateResultPayload;
import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.AllianceStatePayload;
import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class AllianceManager {
    private static final Map<MinecraftServer, AllianceManager> INSTANCES = new WeakHashMap<>();

    private final Map<UUID, Alliance> alliancesById = new HashMap<>();
    private final Map<UUID, UUID> playerToAllianceId = new HashMap<>();
    private final Map<String, UUID> allianceNameToId = new HashMap<>();

    public static AllianceManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new AllianceManager());
    }

    public boolean isPlayerInAlliance(UUID playerUuid) {
        return playerToAllianceId.containsKey(playerUuid);
    }

    public Alliance getAllianceFor(UUID playerUuid) {
        UUID allianceId = playerToAllianceId.get(playerUuid);
        return allianceId == null ? null : alliancesById.get(allianceId);
    }

    public Alliance getAllianceById(UUID allianceId) {
        return alliancesById.get(allianceId);
    }

    public void syncPlayer(ServerPlayer player) {
        Alliance alliance = getAllianceFor(player.getUUID());
        if (alliance == null) {
            ServerPlayNetworking.send(player, new AllianceStatePayload(false, ""));
        } else {
            ServerPlayNetworking.send(player, new AllianceStatePayload(true, alliance.getName()));
        }
    }

    public void syncAllianceMembers(MinecraftServer server, Alliance alliance) {
        for (UUID memberUuid : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberUuid);
            if (player != null) {
                syncPlayer(player);
            }
        }
    }

    public void sendCreationScreen(MinecraftServer server, ServerPlayer requester) {
        Alliance existing = getAllianceFor(requester.getUUID());
        if (existing != null) {
            ServerPlayNetworking.send(requester, new AllianceCreationScreenPayload(
                    true,
                    existing.getName(),
                    List.of()
            ));
            return;
        }

        List<AllianceCreationScreenPayload.CandidateEntry> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(requester.getUUID())) {
                continue;
            }

            if (isPlayerInAlliance(player.getUUID())) {
                continue;
            }

            candidates.add(new AllianceCreationScreenPayload.CandidateEntry(
                    player.getUUID(),
                    player.getGameProfile().name()
            ));
        }

        ServerPlayNetworking.send(requester, new AllianceCreationScreenPayload(
                false,
                "",
                candidates
        ));
    }

    public void sendViewScreen(MinecraftServer server, ServerPlayer requester) {
        Alliance alliance = getAllianceFor(requester.getUUID());
        if (alliance == null) {
            ServerPlayNetworking.send(requester, new AllianceViewPayload(
                    false,
                    "",
                    requester.getUUID(),
                    requester.getGameProfile().name(),
                    List.of()
            ));
            return;
        }

        Map<UUID, String> knownNames = new LinkedHashMap<>();
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            knownNames.put(onlinePlayer.getUUID(), onlinePlayer.getGameProfile().name());
        }

        List<AllianceViewPayload.MemberEntry> members = new ArrayList<>();
        for (UUID memberUuid : alliance.getMemberUuids()) {
            String name = knownNames.getOrDefault(memberUuid, memberUuid.toString());
            members.add(new AllianceViewPayload.MemberEntry(
                    memberUuid,
                    name,
                    memberUuid.equals(alliance.getOwnerUuid())
            ));
        }

        List<AllianceViewPayload.PendingInviteEntry> pendingInvites = new ArrayList<>();
        for (UUID inviteUuid : alliance.getPendingInviteUuids()) {
            String name = knownNames.getOrDefault(inviteUuid, inviteUuid.toString());
            pendingInvites.add(new AllianceViewPayload.PendingInviteEntry(inviteUuid, name));
        }

        String ownerName = knownNames.getOrDefault(alliance.getOwnerUuid(), alliance.getOwnerUuid().toString());

        ServerPlayNetworking.send(requester, new AllianceViewPayload(
                true,
                alliance.getName(),
                alliance.getOwnerUuid(),
                ownerName,
                members,
                pendingInvites
        ));
    }

    public CreationResult createAlliance(MinecraftServer server, ServerPlayer owner, String rawName, Collection<UUID> invitedPlayers) {
        if (isPlayerInAlliance(owner.getUUID())) {
            return CreationResult.failure("You are already in an alliance.");
        }

        String allianceName = rawName == null ? "" : rawName.trim();
        if (allianceName.length() < 3) {
            return CreationResult.failure("Alliance name must be at least 3 characters.");
        }

        if (allianceName.length() > 24) {
            return CreationResult.failure("Alliance name must be 24 characters or fewer.");
        }

        String normalized = allianceName.toLowerCase(Locale.ROOT);
        if (allianceNameToId.containsKey(normalized)) {
            return CreationResult.failure("That alliance name is already taken.");
        }

        Alliance alliance = new Alliance(UUID.randomUUID(), allianceName, owner.getUUID());
        alliancesById.put(alliance.getId(), alliance);
        playerToAllianceId.put(owner.getUUID(), alliance.getId());
        allianceNameToId.put(normalized, alliance.getId());

        for (UUID invitedUuid : invitedPlayers) {
            if (invitedUuid.equals(owner.getUUID())) {
                continue;
            }
            if (isPlayerInAlliance(invitedUuid)) {
                continue;
            }

            ServerPlayer invitedPlayer = server.getPlayerList().getPlayer(invitedUuid);
            if (invitedPlayer == null) {
                continue;
            }

            alliance.addPendingInvite(invitedUuid);

            ServerPlayNetworking.send(invitedPlayer, new AllianceInvitePayload(
                    alliance.getId(),
                    alliance.getName(),
                    owner.getUUID(),
                    owner.getGameProfile().getName()
            ));
        }

        syncPlayer(owner);
        return CreationResult.success(alliance);
    }

    public ActionResult respondToInvite(MinecraftServer server, ServerPlayer player, UUID allianceId, boolean accept) {
        if (isPlayerInAlliance(player.getUUID())) {
            return ActionResult.failure("You are already in an alliance.");
        }

        Alliance alliance = getAllianceById(allianceId);
        if (alliance == null) {
            return ActionResult.failure("That alliance no longer exists.");
        }

        if (!alliance.hasPendingInvite(player.getUUID())) {
            return ActionResult.failure("You do not have a pending invite for that alliance.");
        }

        alliance.removePendingInvite(player.getUUID());

        if (accept) {
            alliance.addMember(player.getUUID());
            playerToAllianceId.put(player.getUUID(), alliance.getId());
            syncAllianceMembers(server, alliance);
            return ActionResult.success("You joined alliance: " + alliance.getName());
        }

        return ActionResult.success("Alliance invite declined.");
    }

    public ActionResult leaveAlliance(MinecraftServer server, ServerPlayer player) {
        Alliance alliance = getAllianceFor(player.getUUID());
        if (alliance == null) {
            return ActionResult.failure("You are not in an alliance.");
        }

        UUID playerUuid = player.getUUID();

        if (alliance.getOwnerUuid().equals(playerUuid)) {
            if (alliance.getMemberUuids().size() > 1) {
                return ActionResult.failure("Transfer ownership before leaving your alliance.");
            }

            playerToAllianceId.remove(playerUuid);
            alliancesById.remove(alliance.getId());
            allianceNameToId.remove(alliance.getName().toLowerCase(Locale.ROOT));
            syncPlayer(player);
            return ActionResult.success("Alliance disbanded.");
        }

        alliance.removeMember(playerUuid);
        playerToAllianceId.remove(playerUuid);
        syncPlayer(player);
        syncAllianceMembers(server, alliance);

        return ActionResult.success("You left the alliance.");
    }

    public ActionResult kickMember(MinecraftServer server, ServerPlayer actor, UUID targetUuid) {
        Alliance alliance = getAllianceFor(actor.getUUID());
        if (alliance == null) {
            return ActionResult.failure("You are not in an alliance.");
        }

        if (!alliance.getOwnerUuid().equals(actor.getUUID())) {
            return ActionResult.failure("Only the alliance owner can kick members.");
        }

        if (targetUuid.equals(actor.getUUID())) {
            return ActionResult.failure("You cannot kick yourself.");
        }

        if (!alliance.hasMember(targetUuid)) {
            return ActionResult.failure("That player is not a member of your alliance.");
        }

        alliance.removeMember(targetUuid);
        playerToAllianceId.remove(targetUuid);

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target != null) {
            syncPlayer(target);
        }
        syncAllianceMembers(server, alliance);

        return ActionResult.success("Member kicked from alliance.");
    }

    public ActionResult transferOwnership(MinecraftServer server, ServerPlayer actor, UUID newOwnerUuid) {
        Alliance alliance = getAllianceFor(actor.getUUID());
        if (alliance == null) {
            return ActionResult.failure("You are not in an alliance.");
        }

        if (!alliance.getOwnerUuid().equals(actor.getUUID())) {
            return ActionResult.failure("Only the current owner can transfer ownership.");
        }

        if (!alliance.hasMember(newOwnerUuid)) {
            return ActionResult.failure("New owner must already be in the alliance.");
        }

        alliance.setOwnerUuid(newOwnerUuid);
        syncAllianceMembers(server, alliance);

        return ActionResult.success("Alliance ownership transferred.");
    }

    public record CreationResult(boolean success, String message, Alliance alliance) {
        public static CreationResult success(Alliance alliance) {
            return new CreationResult(true, "Alliance created.", alliance);
        }

        public static CreationResult failure(String message) {
            return new CreationResult(false, message, null);
        }
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }
}