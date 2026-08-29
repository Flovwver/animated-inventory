package ru.example.inventorygif;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import ru.example.inventorygif.client.InventoryGifConfig;
import ru.example.inventorygif.client.InventoryGifConfigScreen;

/**
 * Inventory GIF — client-only mod that draws an animated sprite sheet
 * on top of the vanilla player inventory screen.
 *
 * The value here must match the modId entry in META-INF/neoforge.mods.toml.
 */
@Mod(InventoryGif.MODID)
public class InventoryGif {

    public static final String MODID = "inventorygif";

    public static final Logger LOGGER = LogUtils.getLogger();

    public InventoryGif(ModContainer modContainer) {
        // Client config file: config/inventorygif-client.toml
        modContainer.registerConfig(ModConfig.Type.CLIENT, InventoryGifConfig.SPEC);

        // In-game settings screen: enables the "Config" button in the mod list.
        // Client-only, so guard the registration to keep the class loadable on servers.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parent) -> InventoryGifConfigScreen.create(parent));
        }

        // Client rendering is handled by ru.example.inventorygif.client.InventoryGifRenderer,
        // which is auto-registered via @EventBusSubscriber(Dist.CLIENT).
        LOGGER.info("Inventory GIF mod loaded");
    }
}
