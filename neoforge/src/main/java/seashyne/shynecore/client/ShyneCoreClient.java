package seashyne.shynecore.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.ShyneCloudClient;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.render.BbModelEntityRenderer;
import seashyne.shynecore.client.render.AvatarRenderTasks;
import seashyne.shynecore.client.ui.ShyneKeybinds;
import seashyne.shynecore.client.ui.ShynePauseMenu;
import seashyne.shynecore.client.ui.ShyneSettingsScreen;

@Mod(value = ShyneCore.MOD_ID, dist = Dist.CLIENT)
public class ShyneCoreClient {
    public ShyneCoreClient(IEventBus modEventBus, ModContainer container) {
        ShyneClientSettings.load();
        ShyneCloudClient.init();
        ShyneClientNetworking.init();
        AvatarRuntime.init();
        BbModelEntityRenderer.init(modEventBus);
        AvatarRenderTasks.init();
        ShyneKeybinds.init(modEventBus);
        ShynePauseMenu.init();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ShyneSettingsScreen(parent));
        ShyneCore.LOGGER.info("[ShyneCreator] Client initialized with renderer + animation sync + keybinds + avatar runtime.");
    }
}
