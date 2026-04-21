package net.cnn_r.alliesandfoes.alliance;

import net.cnn_r.alliesandfoes.network.AllianceCreationScreenPayload;
import net.cnn_r.alliesandfoes.network.AllianceInvitePayload;
import net.cnn_r.alliesandfoes.network.AllianceJoinRequestPayload;
import net.cnn_r.alliesandfoes.network.AllianceStatePayload;
import net.cnn_r.alliesandfoes.network.AllianceViewPayload;
import net.cnn_r.alliesandfoes.network.InviteAllianceManagementScreenPayload;
import net.cnn_r.alliesandfoes.network.JoinAllianceScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class AllianceManager {
    private static final Map<MinecraftServer, AllianceManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, Alliance> alliancesById = new HashMap<>();
    private final Map<UUID, UUID> playerToAllianceId = new HashMap<>();
    private final Map<String, UUID> allianceNameToId = new HashMap<>();

    private AllianceManager(MinecraftServer server) {
        this.server = server;
        this.loadFromSavedData();
    }

    public static AllianceManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceManager::new);
    }

    public boolean isPlayerInAlliance(UUID playerUuid) {
        return this.playerToAllianceId.containsKey(playerUuid);
    }

    public Alliance getAllianceFor(UUID playerUuid) {
        UUID allianceId = this.playerToAllianceId.get(playerUuid);
        return allianceId == null ? null : this.alliancesById.get(allianceId);
    }

    public Alliance getAllianceById(UUID allianceId) {
        return this.alliancesById.get(allianceId);
    }

    public Alliance getAllianceByName(String name) {
        if (name == null) return null;
        UUID id = this.allianceNameToId.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : this.alliancesById.get(id);
    }

    public void syncPlayer(ServerPlayer player) {
        Alliance alliance = this.getAllianceFor(player.getUUID());

        if (alliance == null) {
            ServerPlayNetworking.send(player, new AllianceStatePayload(false, "", "", null));
            return;
        }

        String memberRole = alliance.getRole(player.getUUID());

        ServerPlayNetworking.send(player, new AllianceStatePayload(
                true,
                alliance.getName(),
                memberRole == null ? "" : memberRole,
                alliance.getOwnerUuid()
        ));
    }

    public void syncAllianceMembers(MinecraftServer server, Alliance alliance) {
        for (UUID memberUuid : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberUuid);
            if (player != null) {
                this.syncPlayer(player);
            }
        }
    }

    public void sendCreationScreen(MinecraftServer server, ServerPlayer requester) {
        Alliance existing = this.getAllianceFor(requester.getUUID());
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

            if (this.isPlayerInAlliance(player.getUUID())) {
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

    public void sendJoinScreen(MinecraftServer server, ServerPlayer requester) {
        Alliance existing = this.getAllianceFor(requester.getUUID());
        if (existing != null) {
            ServerPlayNetworking.send(requester, new JoinAllianceScreenPayload(
                    true,
                    existing.getName(),
                    List.of()
            ));
            return;
        }

        Map<UUID, String> knownNames = new LinkedHashMap<>();
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            knownNames.put(onlinePlayer.getUUID(), onlinePlayer.getGameProfile().name());
        }

        List<JoinAllianceScreenPayload.Entry> entries = new ArrayList<>();
        for (Alliance alliance : this.alliancesById.values()) {
            String ownerName = knownNames.getOrDefault(alliance.getOwnerUuid(), alliance.getOwnerUuid().toString());

            entries.add(new JoinAllianceScreenPayload.Entry(
                    alliance.getId(),
                    alliance.getName(),
                    alliance.getOwnerUuid(),
                    ownerName,
                    alliance.getMemberUuids().size()
            ));
        }

        ServerPlayNetworking.send(requester, new JoinAllianceScreenPayload(
                false,
                "",
                entries
        ));
    }

    public void sendViewScreen(MinecraftServer server, ServerPlayer requester) {
        Alliance alliance = this.getAllianceFor(requester.getUUID());
        if (alliance == null) {
            ServerPlayNetworking.send(requester, new AllianceViewPayload(
                    false,
                    "",
                    requester.getUUID(),
                    requester.getGameProfile().name(),
                    List.of(),
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
            String role = alliance.getRole(memberUuid);

            members.add(new AllianceViewPayload.MemberEntry(
                    memberUuid,
                    name,
                    memberUuid.equals(alliance.getOwnerUuid()),
                    role
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
        if (this.isPlayerInAlliance(owner.getUUID())) {
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
        if (this.allianceNameToId.containsKey(normalized)) {
            return CreationResult.failure("That alliance name is already taken.");
        }

        Alliance alliance = new Alliance(UUID.randomUUID(), allianceName, owner.getUUID());
        this.alliancesById.put(alliance.getId(), alliance);
        this.playerToAllianceId.put(owner.getUUID(), alliance.getId());
        this.allianceNameToId.put(normalized, alliance.getId());

        int sentInvites = 0;

        for (UUID invitedUuid : invitedPlayers) {
            if (invitedUuid == null) {
                continue;
            }

            if (invitedUuid.equals(owner.getUUID())) {
                continue;
            }

            if (this.isPlayerInAlliance(invitedUuid)) {
                continue;
            }

            if (alliance.hasPendingInvite(invitedUuid)) {
                continue;
            }

            ServerPlayer invitedPlayer = server.getPlayerList().getPlayer(invitedUuid);
            if (invitedPlayer == null) {
                continue;
            }

            alliance.addPendingInvite(invitedUuid);
            sentInvites++;

            ServerPlayNetworking.send(invitedPlayer, new AllianceInvitePayload(
                    alliance.getId(),
                    alliance.getName(),
                    owner.getUUID(),
                    owner.getGameProfile().name()
            ));
        }

        this.save();
        this.syncPlayer(owner);

        String message = sentInvites > 0
                ? "Alliance created. Sent " + sentInvites + " invite(s)."
                : "Alliance created.";

        return CreationResult.success(alliance, message);
    }

    public ActionResult requestJoinAlliance(MinecraftServer server, ServerPlayer requester, UUID allianceId) {
        if (this.isPlayerInAlliance(requester.getUUID())) {
            return ActionResult.failure("You are already in an alliance.");
        }

        Alliance alliance = this.getAllianceById(allianceId);
        if (alliance == null) {
            return ActionResult.failure("That alliance no longer exists.");
        }

        if (requester.getUUID().equals(alliance.getOwnerUuid())) {
            return ActionResult.failure("You already lead that alliance.");
        }

        if (alliance.hasMember(requester.getUUID())) {
            return ActionResult.failure("You are already in that alliance.");
        }

        if (alliance.hasPendingJoinRequest(requester.getUUID())) {
            return ActionResult.failure("You already sent a join request to that alliance.");
        }

        ServerPlayer owner = server.getPlayerList().getPlayer(alliance.getOwnerUuid());
        if (owner == null) {
            return ActionResult.failure("That alliance founder is not online.");
        }

        alliance.addPendingJoinRequest(requester.getUUID());
        this.save();

        ServerPlayNetworking.send(owner, new AllianceJoinRequestPayload(
                alliance.getId(),
                alliance.getName(),
                requester.getUUID(),
                requester.getGameProfile().name()
        ));

        return ActionResult.success("Join request sent to " + alliance.getName() + ".");
    }

    public ActionResult respondToInvite(MinecraftServer server, ServerPlayer player, UUID allianceId, boolean accept) {
        if (this.isPlayerInAlliance(player.getUUID())) {
            return ActionResult.failure("You are already in an alliance.");
        }

        Alliance alliance = this.getAllianceById(allianceId);
        if (alliance == null) {
            return ActionResult.failure("That alliance no longer exists.");
        }

        if (!alliance.hasPendingInvite(player.getUUID())) {
            return ActionResult.failure("You do not have a pending invite for that alliance.");
        }

        alliance.removePendingInvite(player.getUUID());

        ServerPlayer owner = server.getPlayerList().getPlayer(alliance.getOwnerUuid());

        if (accept) {
            alliance.addMember(player.getUUID());
            this.playerToAllianceId.put(player.getUUID(), alliance.getId());

            this.save();
            this.refreshAllianceMembers(server, alliance);

            if (owner != null) {
                owner.sendSystemMessage(
                        Component.literal(player.getGameProfile().name() + " joined alliance: " + alliance.getName()), true);
            }

            return ActionResult.success("You joined alliance: " + alliance.getName());
        }

        this.save();

        if (owner != null) {
            owner.sendSystemMessage(
                    Component.literal(player.getGameProfile().name() + " declined your alliance invite."), true);
        }

        return ActionResult.success("Alliance invite declined.");
    }

    public ActionResult respondToJoinRequest(MinecraftServer server, ServerPlayer actor, UUID allianceId, UUID requesterUuid, boolean accept) {
        Alliance alliance = this.getAllianceFor(actor.getUUID());
        if (alliance == null) {
            return ActionResult.failure("You are not in an alliance.");
        }

        if (!alliance.getId().equals(allianceId)) {
            return ActionResult.failure("That join request does not belong to your alliance.");
        }

        if (!alliance.getOwnerUuid().equals(actor.getUUID())) {
            return ActionResult.failure("Only the alliance owner can respond to join requests.");
        }

        if (!alliance.hasPendingJoinRequest(requesterUuid)) {
            return ActionResult.failure("That join request is no longer pending.");
        }

        ServerPlayer requester = server.getPlayerList().getPlayer(requesterUuid);
        alliance.removePendingJoinRequest(requesterUuid);

        if (accept) {
            if (this.isPlayerInAlliance(requesterUuid)) {
                return ActionResult.failure("That player is already in an alliance.");
            }

            alliance.addMember(requesterUuid);
            this.playerToAllianceId.put(requesterUuid, alliance.getId());

            this.save();
            this.refreshAllianceMembers(server, alliance);

            if (requester != null) {
                requester.sendSystemMessage(
                        Component.literal("Your request to join " + alliance.getName() + " was accepted."), true);
            }

            return ActionResult.success("Join request accepted.");
        }

        this.save();

        if (requester != null) {
            requester.sendSystemMessage(
                    Component.literal("Your request to join " + alliance.getName() + " was declined."), true);
        }

        return ActionResult.success("Join request declined.");
    }

    public void sendInviteManagementScreen(MinecraftServer server, ServerPlayer requester) {
        Alliance alliance = this.getAllianceFor(requester.getUUID());
        if (alliance == null) {
            ServerPlayNetworking.send(requester, new InviteAllianceManagementScreenPayload(
                    false,
                    "",
                    List.of()
            ));
            return;
        }

        if (!alliance.getOwnerUuid().equals(requester.getUUID())) {
            ServerPlayNetworking.send(requester, new InviteAllianceManagementScreenPayload(
                    false,
                    alliance.getName(),
                    List.of()
            ));
            return;
        }

        List<InviteAllianceManagementScreenPayload.CandidateEntry> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUuid = player.getUUID();

            if (playerUuid.equals(requester.getUUID())) {
                continue;
            }

            if (this.isPlayerInAlliance(playerUuid)) {
                continue;
            }

            if (alliance.hasPendingInvite(playerUuid)) {
                continue;
            }

            candidates.add(new InviteAllianceManagementScreenPayload.CandidateEntry(
                    playerUuid,
                    player.getGameProfile().name()
            ));
        }

        ServerPlayNetworking.send(requester, new InviteAllianceManagementScreenPayload(
                true,
                alliance.getName(),
                candidates
        ));
    }

    public ActionResult sendAllianceInvites(MinecraftServer server, ServerPlayer actor, Collection<UUID> invitedPlayerUuids) {
        Alliance alliance = this.getAllianceFor(actor.getUUID());
        if (alliance == null) {
            return ActionResult.failure("You are not in an alliance.");
        }

        if (!alliance.getOwnerUuid().equals(actor.getUUID())) {
            return ActionResult.failure("Only the alliance founder can send invites.");
        }

        if (invitedPlayerUuids == null || invitedPlayerUuids.isEmpty()) {
            return ActionResult.failure("Select at least one player to invite.");
        }

        int sentInvites = 0;

        for (UUID invitedUuid : invitedPlayerUuids) {
            if (invitedUuid == null) {
                continue;
            }

            if (invitedUuid.equals(actor.getUUID())) {
                continue;
            }

            if (this.isPlayerInAlliance(invitedUuid)) {
                continue;
            }

            if (alliance.hasPendingInvite(invitedUuid)) {
                continue;
            }

            ServerPlayer invitedPlayer = server.getPlayerList().getPlayer(invitedUuid);
            if (invitedPlayer == null) {
                continue;
            }

            alliance.addPendingInvite(invitedUuid);
            sentInvites++;

            ServerPlayNetworking.send(invitedPlayer, new AllianceInvitePayload(
                    alliance.getId(),
                    alliance.getName(),
                    actor.getUUID(),
                    actor.getGameProfile().name()
            ));
        }

        if (sentInvites <= 0) {
            return ActionResult.failure("No eligible players were available to invite.");
        }

        this.save();
        this.sendViewScreen(server, actor);

        return ActionResult.success("Sent " + sentInvites + " alliance invite(s).");
    }

    public ActionResult leaveAlliance(MinecraftServer server, ServerPlayer player) {
        Alliance alliance = this.getAllianceFor(player.getUUID());
        if (alliance == null) {
            return ActionResult.failure("You are not in an alliance.");
        }

        UUID playerUuid = player.getUUID();

        if (alliance.getOwnerUuid().equals(playerUuid)) {
            if (alliance.getMemberUuids().size() > 1) {
                return ActionResult.failure("Transfer ownership before leaving your alliance.");
            }

            this.playerToAllianceId.remove(playerUuid);
            this.alliancesById.remove(alliance.getId());
            this.allianceNameToId.remove(alliance.getName().toLowerCase(Locale.ROOT));

            this.save();
            this.refreshRemovedPlayer(server, player);

            return ActionResult.success("Alliance disbanded.");
        }

        alliance.removeMember(playerUuid);
        this.playerToAllianceId.remove(playerUuid);

        this.save();
        this.refreshRemovedPlayer(server, player);
        this.refreshAllianceMembers(server, alliance);

        ServerPlayer owner = server.getPlayerList().getPlayer(alliance.getOwnerUuid());
        if (owner != null) {
            owner.sendSystemMessage(
                    Component.literal(player.getGameProfile().name() + " left the alliance."), true);
        }

        return ActionResult.success("You left the alliance.");
    }

    public ActionResult kickMember(MinecraftServer server, ServerPlayer actor, UUID targetUuid) {
        Alliance alliance = this.getAllianceFor(actor.getUUID());
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
        this.playerToAllianceId.remove(targetUuid);
        this.save();

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target != null) {
            this.refreshRemovedPlayer(server, target);
            target.sendSystemMessage(
                    Component.literal("You were removed from alliance: " + alliance.getName()), true);
        }

        this.refreshAllianceMembers(server, alliance);

        return ActionResult.success("Member kicked from alliance.");
    }

    public ActionResult transferOwnership(MinecraftServer server, ServerPlayer actor, UUID newOwnerUuid) {
        Alliance alliance = this.getAllianceFor(actor.getUUID());
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
        this.save();

        this.syncAllianceMembers(server, alliance);

        ServerPlayer newOwner = server.getPlayerList().getPlayer(newOwnerUuid);
        if (newOwner != null) {
            newOwner.sendSystemMessage(
                    Component.literal("You are now the owner of alliance: " + alliance.getName()), true);
        }

        this.sendViewScreen(server, actor);

        return ActionResult.success("Alliance ownership transferred.");
    }

    public ActionResult setMemberRole(MinecraftServer server, ServerPlayer actor, UUID targetUuid, String rawRole) {
        Alliance alliance = this.getAllianceFor(actor.getUUID());
        if (alliance == null) {
            return ActionResult.failure("You are not in an alliance.");
        }

        if (!alliance.getOwnerUuid().equals(actor.getUUID())) {
            return ActionResult.failure("Only the alliance owner can change member roles.");
        }

        if (!alliance.hasMember(targetUuid)) {
            return ActionResult.failure("That player is not a member of your alliance.");
        }

        if (targetUuid.equals(actor.getUUID())) {
            return ActionResult.failure("You cannot change your own founder role.");
        }

        String sanitizedRole = Alliance.sanitizeRole(rawRole);
        alliance.setRole(targetUuid, sanitizedRole);
        this.save();

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target != null) {
            target.sendSystemMessage(
                    Component.literal("Your alliance role is now: " + sanitizedRole), true);
        }

        this.sendViewScreen(server, actor);
        return ActionResult.success("Updated member role to: " + sanitizedRole);
    }

    public void sendViewScreenToAlliance(MinecraftServer server, Alliance alliance) {
        for (UUID memberUuid : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberUuid);
            if (player != null) {
                this.sendViewScreen(server, player);
            }
        }
    }

    private void refreshAllianceMembers(MinecraftServer server, Alliance alliance) {
        this.sendViewScreenToAlliance(server, alliance);
    }

    private void refreshRemovedPlayer(MinecraftServer server, ServerPlayer player) {
        this.sendViewScreen(server, player);
        this.syncPlayer(player);
    }

    private void loadFromSavedData() {
        this.alliancesById.clear();
        this.playerToAllianceId.clear();
        this.allianceNameToId.clear();

        AllianceSavedData savedData = AllianceSavedData.get(this.server);

        List<Alliance> loadedAlliances = new ArrayList<>(savedData.createLiveAlliances());
        loadedAlliances.sort(Comparator.comparing(Alliance::getName, String.CASE_INSENSITIVE_ORDER));

        for (Alliance alliance : loadedAlliances) {
            this.registerLoadedAlliance(alliance);
        }
    }

    private void registerLoadedAlliance(Alliance alliance) {
        this.alliancesById.put(alliance.getId(), alliance);
        this.allianceNameToId.put(alliance.getName().toLowerCase(Locale.ROOT), alliance.getId());

        for (UUID memberUuid : alliance.getMemberUuids()) {
            this.playerToAllianceId.put(memberUuid, alliance.getId());
        }
    }

    private void save() {
        AllianceSavedData.get(this.server).saveFromLiveAlliances(this.alliancesById.values());
    }

    public record CreationResult(boolean success, String message, Alliance alliance) {
        public static CreationResult success(Alliance alliance, String message) {
            return new CreationResult(true, message, alliance);
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