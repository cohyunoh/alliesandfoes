package net.cnn_r.alliesandfoes;

//Default Imports for Fabric
import net.cnn_r.alliesandfoes.network.packet.MenuScreenS2CPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.cnn_r.alliesandfoes.command.*;

public class Alliesandfoes implements ModInitializer {
	public static final String MOD_ID = "alliesandfoes";

	@Override
	public void onInitialize() {
		MenuScreenS2CPayload.register();
		CommandRegistrationCallback.EVENT.register(MenuCommand::register);
		CommandRegistrationCallback.EVENT.register(TeamsCommand::register);
	}
}