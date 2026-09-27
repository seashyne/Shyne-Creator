package seashyne.shynecore.client.avatar;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import seashyne.shynecore.client.avatar.runtime.AvatarCloudDelegate;
import seashyne.shynecore.client.avatar.runtime.AvatarNetworkSender;
import seashyne.shynecore.client.network.ShyneClientNetworking;
import seashyne.shynecore.network.ShyneNetwork;

public final class AvatarRuntimeLoaderAdapter {
    private AvatarRuntimeLoaderAdapter() {}

    public static void init() {
        AvatarRuntime.setNetworkSender(new AvatarNetworkSender() {
            @Override
            public boolean sendAvatarSnapshot(ShyneNetwork.NetAvatarSnapshot snapshot, long transportRevision) {
                return ShyneClientNetworking.sendAvatarSnapshot(snapshot, transportRevision);
            }

            @Override
            public boolean sendAvatarClear(String playerId, long transportRevision) {
                return ShyneClientNetworking.sendAvatarClear(playerId, transportRevision);
            }
        });

        AvatarRuntime.setCloudDelegate(new AvatarCloudDelegate() {
            @Override
            public boolean restoreSelectedPublic(Minecraft client) {
                return ShyneCloudClient.restoreSelectedPublic(client);
            }

            @Override
            public void tick(Minecraft client) {
                ShyneCloudClient.tick(client);
            }

            @Override
            public void clearActivePublic() {
                ShyneCloudClient.clearActivePublic();
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> AvatarRuntime.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AvatarRuntime.onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(AvatarRuntime::tick);

        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("avatar")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("reload").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    long start = System.currentTimeMillis();
                    boolean ok = AvatarRuntime.reloadActive(mc);
                    long elapsed = System.currentTimeMillis() - start;
                    ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(ok ? "§a[Shyne] Avatar reloaded in " + elapsed + " ms!§r" : "§c[Shyne] Failed to reload avatar! Check logs.§r"));
                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                }))
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("eval")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("code", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                        String code = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "code");
                        String result = AvatarRuntime.eval(code);
                        ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("§d[Lua] §f" + result));
                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                    })))
            );
        });
    }
}
