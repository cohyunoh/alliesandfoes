package net.cnn_r.alliesandfoes.alliance.progression;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Temporary debug commands for alliance progression testing.
 *
 * These commands allow quick progression grants so territory founding and
 * expansion can be tested before gameplay-based progression gain exists.
 */
public final class AllianceProgressionCommands {
    private AllianceProgressionCommands() {
    }

    /**
     * Registers progression debug commands.
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register(AllianceProgressionCommands::registerCommands);
    }

    /**
     * Registers the Brigadier command tree for progression commands.
     *
     * Current commands:
     * - /allianceprogress add <amount>
     * - /allianceprogress balance
     */
    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context,
            Commands.CommandSelection selection
    ) {
        dispatcher.register(
                Commands.literal("allianceprogress")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(
                                Commands.literal("add")
                                        .then(
                                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(commandContext -> addProgression(
                                                                commandContext.getSource(),
                                                                IntegerArgumentType.getInteger(commandContext, "amount")
                                                        ))
                                        )
                        )
                        .then(
                                Commands.literal("balance")
                                        .executes(commandContext -> showBalance(commandContext.getSource()))
                        )
        );
    }

    /**
     * Adds progression to the executing player's alliance.
     *
     * @param source command source
     * @param amount progression amount
     * @return Brigadier command result
     */
    private static int addProgression(CommandSourceStack source, int amount) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        Alliance alliance = AllianceManager.get(player.level().getServer()).getAllianceFor(player.getUUID());
        if (alliance == null) {
            source.sendFailure(Component.literal("You are not in an alliance."));
            return 0;
        }

        AllianceProgressionService progressionService = AllianceProgressionService.get(player.level().getServer());
        progressionService.add(alliance.getId(), amount);

        int newBalance = progressionService.getBalance(alliance.getId());

        source.sendSuccess(
                () -> Component.literal(
                        "Added " + amount + " progression to alliance " + alliance.getName()
                                + ". New balance: " + newBalance
                ),
                false
        );

        return 1;
    }

    /**
     * Shows the executing player's alliance progression balance.
     *
     * @param source command source
     * @return Brigadier command result
     */
    private static int showBalance(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        Alliance alliance = AllianceManager.get(player.level().getServer()).getAllianceFor(player.getUUID());
        if (alliance == null) {
            source.sendFailure(Component.literal("You are not in an alliance."));
            return 0;
        }

        AllianceProgressionService progressionService = AllianceProgressionService.get(player.level().getServer());
        int balance = progressionService.getBalance(alliance.getId());

        source.sendSuccess(
                () -> Component.literal(
                        "Alliance progression balance for " + alliance.getName() + ": " + balance
                ),
                false
        );

        return 1;
    }
}