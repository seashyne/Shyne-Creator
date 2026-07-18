package seashyne.shynecore.client;

import net.fabricmc.api.ClientModInitializer;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.ShyneCloudClient;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.client.render.BbModelEntityRenderer;
import seashyne.shynecore.client.render.AvatarRenderTasks;
import seashyne.shynecore.client.ui.ShyneKeybinds;
import seashyne.shynecore.client.ui.ShynePauseMenu;

public class ShyneCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ShyneClientSettings.load();
        ShyneCloudClient.init();
        ShyneClientNetworking.init();
        AvatarRuntime.init();
        BbModelEntityRenderer.init();
        AvatarRenderTasks.init();
        ShyneKeybinds.init();
        ShynePauseMenu.init();
        ShyneCore.LOGGER.info("[ShyneCreator] Client initialized with renderer + animation sync + keybinds + avatar runtime.");
    }
}
