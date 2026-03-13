package alliesandfoes;

//Default Imports for Fabric
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.*;

//Imports from mod
import alliesandfoes.command.*;

public class Alliesandfoes implements ModInitializer {
	public static final String MOD_ID = "alliesandfoes";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution
		LOGGER.info("Hello Fabric world!");
		CommandRegistrationCallback.EVENT.register(StartCommand::register);
		CommandRegistrationCallback.EVENT.register(TeamsCommand::register);
	}
}