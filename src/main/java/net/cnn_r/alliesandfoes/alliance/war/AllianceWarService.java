package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class AllianceWarService {
    private static final Map<MinecraftServer, AllianceWarService> INSTANCES = new WeakHashMap<>();

    static final int WAR_DECLARE_COST = 25;

    private final MinecraftServer server;
    private final List<AllianceWar> wars = new ArrayList<>();
    // "warId:proposerAllianceId"
    private final Set<String> peaceProposals = new HashSet<>();

    private AllianceWarService(MinecraftServer server) {
        this.server = server;
        this.wars.addAll(AllianceWarSavedData.get(server).createLiveWars());
    }

    public static AllianceWarService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, AllianceWarService::new);
    }

    // === Query ===

    public boolean areAtWar(UUID allianceA, UUID allianceB) {
        return getActiveWarBetween(allianceA, allianceB).isPresent();
    }

    public Optional<AllianceWar> getActiveWarBetween(UUID allianceA, UUID allianceB) {
        for (AllianceWar war : this.wars) {
            if (war.status() == WarStatus.ACTIVE && war.involves(allianceA) && war.involves(allianceB)) {
                return Optional.of(war);
            }
        }
        return Optional.empty();
    }

    public List<AllianceWar> getWarsForAlliance(UUID allianceId) {
        List<AllianceWar> result = new ArrayList<>();
        for (AllianceWar war : this.wars) {
            if (war.involves(allianceId)) result.add(war);
        }
        return result;
    }

    public List<AllianceWar> getEndedWarsFor(UUID allianceId) {
        List<AllianceWar> result = new ArrayList<>();
        for (AllianceWar war : this.wars) {
            if (war.status() == WarStatus.ENDED && war.involves(allianceId)) result.add(war);
        }
        return result;
    }

    // === Actions — all return null on success, error string on failure ===

    public String declareWar(ServerPlayer actor, UUID targetAllianceId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance to declare war.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can declare war.";

        UUID actorAllianceId = actorAlliance.getId();
        if (actorAllianceId.equals(targetAllianceId)) return "You cannot declare war on your own alliance.";

        Alliance targetAlliance = AllianceManager.get(server).getAllianceById(targetAllianceId);
        if (targetAlliance == null) return "Target alliance not found.";

        for (AllianceWar existing : this.wars) {
            if (existing.involves(actorAllianceId) && existing.involves(targetAllianceId)
                    && existing.status() != WarStatus.ENDED) {
                return "You already have an active or pending war with " + targetAlliance.getName() + ".";
            }
        }

        AllianceProgressionService progression = AllianceProgressionService.get(server);
        if (!progression.canAfford(actorAllianceId, WAR_DECLARE_COST)) {
            return "Your alliance needs " + WAR_DECLARE_COST + " influence to declare war (have "
                    + progression.getBalance(actorAllianceId) + ").";
        }
        progression.trySpend(actorAllianceId, WAR_DECLARE_COST);

        AllianceWar war = new AllianceWar(UUID.randomUUID(), actorAllianceId, targetAllianceId, WarStatus.PENDING);
        this.wars.add(war);
        this.save();

        notifyAlliance(actorAlliance, Component.literal(
                "[WAR] Your alliance declared war on " + targetAlliance.getName() + ".").withStyle(ChatFormatting.RED));
        notifyAlliance(targetAlliance, Component.literal(
                "[WAR] " + actorAlliance.getName() + " declared war on your alliance! "
                        + "Use /alliance war accept or /alliance war reject.").withStyle(ChatFormatting.RED));
        return null;
    }

    public String acceptWar(ServerPlayer actor, UUID attackerAllianceId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can accept war.";

        AllianceWar pending = findWar(attackerAllianceId, actorAlliance.getId(), WarStatus.PENDING);
        if (pending == null) return "No pending war declaration found from that alliance.";

        Alliance attackerAlliance = AllianceManager.get(server).getAllianceById(attackerAllianceId);
        updateWar(pending, pending.withStatus(WarStatus.ACTIVE));
        this.save();

        String attackerName = attackerAlliance != null ? attackerAlliance.getName() : "Unknown";
        notifyAlliance(actorAlliance, Component.literal(
                "[WAR] War with " + attackerName + " is now ACTIVE.").withStyle(ChatFormatting.RED));
        if (attackerAlliance != null) notifyAlliance(attackerAlliance, Component.literal(
                "[WAR] " + actorAlliance.getName() + " accepted your declaration. War is ACTIVE.").withStyle(ChatFormatting.RED));
        return null;
    }

    public String rejectWar(ServerPlayer actor, UUID attackerAllianceId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can reject war.";

        AllianceWar pending = findWar(attackerAllianceId, actorAlliance.getId(), WarStatus.PENDING);
        if (pending == null) return "No pending war declaration found from that alliance.";

        Alliance attackerAlliance = AllianceManager.get(server).getAllianceById(attackerAllianceId);
        this.wars.remove(pending);
        this.save();

        String attackerName = attackerAlliance != null ? attackerAlliance.getName() : "Unknown";
        notifyAlliance(actorAlliance, Component.literal(
                "[WAR] You rejected the war declaration from " + attackerName + ".").withStyle(ChatFormatting.GOLD));
        if (attackerAlliance != null) notifyAlliance(attackerAlliance, Component.literal(
                "[WAR] " + actorAlliance.getName() + " rejected your war declaration.").withStyle(ChatFormatting.GOLD));
        return null;
    }

    public String proposePeace(ServerPlayer actor, UUID enemyAllianceId) {
        Alliance actorAlliance = AllianceManager.get(server).getAllianceFor(actor.getUUID());
        if (actorAlliance == null) return "You must be in an alliance.";
        if (!actorAlliance.getOwnerUuid().equals(actor.getUUID())) return "Only the Founder can propose peace.";

        AllianceWar activeWar = getActiveWarBetween(actorAlliance.getId(), enemyAllianceId).orElse(null);
        if (activeWar == null) return "You are not at active war with that alliance.";

        Alliance enemyAlliance = AllianceManager.get(server).getAllianceById(enemyAllianceId);
        String enemyName = enemyAlliance != null ? enemyAlliance.getName() : "Unknown";
        String proposalKey = activeWar.id() + ":" + actorAlliance.getId();
        String enemyProposalKey = activeWar.id() + ":" + enemyAllianceId;

        if (this.peaceProposals.contains(enemyProposalKey)) {
            this.peaceProposals.remove(enemyProposalKey);
            updateWar(activeWar, activeWar.withStatus(WarStatus.ENDED));
            this.save();

            notifyAlliance(actorAlliance, Component.literal(
                    "[PEACE] Peace with " + enemyName + " agreed. War ended.").withStyle(ChatFormatting.GREEN));
            if (enemyAlliance != null) notifyAlliance(enemyAlliance, Component.literal(
                    "[PEACE] " + actorAlliance.getName() + " accepted your peace offer. War ended.").withStyle(ChatFormatting.GREEN));
            return null;
        }

        if (this.peaceProposals.contains(proposalKey)) {
            return "You already proposed peace to this alliance. Waiting for their response.";
        }

        this.peaceProposals.add(proposalKey);
        notifyAlliance(actorAlliance, Component.literal(
                "[PEACE] Peace offer sent to " + enemyName + ". Awaiting their response.").withStyle(ChatFormatting.GREEN));
        if (enemyAlliance != null) notifyAlliance(enemyAlliance, Component.literal(
                "[PEACE] " + actorAlliance.getName() + " proposes peace. Use /alliance war peace "
                        + actorAlliance.getName() + " to accept.").withStyle(ChatFormatting.GREEN));
        return null;
    }

    // === Helpers ===

    private AllianceWar findWar(UUID attackerId, UUID defenderId, WarStatus status) {
        for (AllianceWar war : this.wars) {
            if (war.status() == status
                    && war.attackerId().equals(attackerId)
                    && war.defenderId().equals(defenderId)) {
                return war;
            }
        }
        return null;
    }

    private void updateWar(AllianceWar old, AllianceWar updated) {
        int idx = this.wars.indexOf(old);
        if (idx >= 0) this.wars.set(idx, updated);
    }

    private void notifyAlliance(Alliance alliance, Component message) {
        for (UUID memberUuid : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberUuid);
            if (player != null) player.sendSystemMessage(message);
        }
    }

    public void save() {
        AllianceWarSavedData.get(server).saveFromLiveWars(new ArrayList<>(this.wars));
    }
}
