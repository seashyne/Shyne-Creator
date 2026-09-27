package seashyne.shynecore.client.avatar;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
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

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> AvatarRuntime.onJoin());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> AvatarRuntime.onDisconnect());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> AvatarRuntime.tick(Minecraft.getInstance()));

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, event -> {
            event.getDispatcher().register(net.minecraft.commands.Commands.literal("avatar")
                .then(net.minecraft.commands.Commands.literal("reload").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    long start = System.currentTimeMillis();
                    boolean ok = AvatarRuntime.reloadActive(mc);
                    long elapsed = System.currentTimeMillis() - start;
                    ctx.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(ok ? "§a[Shyne] Avatar reloaded in " + elapsed + " ms!§r" : "§c[Shyne] Failed to reload avatar! Check logs.§r"));
                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                }))
                .then(net.minecraft.commands.Commands.literal("eval")
                    .then(net.minecraft.commands.Commands.argument("code", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                        String code = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "code");
                        String result = AvatarRuntime.eval(code);
                        ctx.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[Lua] §f" + result));
                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                    })))
            );
        });
    }
}
