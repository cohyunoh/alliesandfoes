package net.cnn_r.alliesandfoes;

//Default Imports for Fabric
import net.cnn_r.alliesandfoes.network.ANFNetworking;
import net.cnn_r.alliesandfoes.network.packet.ANFStartScreenS2CPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Imports from mod
import net.cnn_r.alliesandfoes.command.*;

public class Alliesandfoes implements ModInitializer {
	public static final String MOD_ID = "alliesandfoes";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(StartCommand::register);
		CommandRegistrationCallback.EVENT.register(TeamsCommand::register);
	}
}