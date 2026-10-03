package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.client.Minecraft;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarRuntime;
import seashyne.shynecore.client.avatar.AvatarState;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import seashyne.shynecore.avatar.AvatarChannelConstants;

/**
 * Sandboxed network packet channel bridge.
 * Allows avatar runtimes to communicate over explicit, typed Shyne channels
 * rather than arbitrary or unbounded raw network packets.
 */
public final class AvatarChannelBridge {
    public static final int MAX_PAYLOAD_CHARS = AvatarChannelConstants.MAX_PAYLOAD_CHARS;
    public static final int MAX_PACKETS_PER_SEC = AvatarChannelConstants.MAX_PACKETS_PER_SEC;

    public record ChannelPacket(String senderId, String channel, String payloadJson) {}

    public static volatile Consumer<ChannelPacket> PACKET_SENDER;

    private final AvatarState state;
    private final Set<String> sessionAllowedChannels = ConcurrentHashMap.newKeySet();

    private volatile long windowStartMs = System.currentTimeMillis();
    private final AtomicInteger packetsInWindow = new AtomicInteger();

    public AvatarChannelBridge(AvatarState state) {
        this.state = state;
    }

    public void register(Globals globals) {
        globals.set("_avatar_net_send", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue channelArg, LuaValue jsonArg) {
                if (!state.permissionAllowed(AvatarPermission.NETWORK)) {
                    return LuaValue.FALSE;
                }
                String rawChannel = channelArg.optjstring("").trim().toLowerCase(Locale.ROOT);
                if (!isValidChannel(rawChannel)) {
                    return LuaValue.FALSE;
                }
                if (!isChannelAllowed(rawChannel)) {
                    return LuaValue.FALSE;
                }
                String payloadJson = jsonArg.optjstring("{}");
                if (payloadJson.length() > MAX_PAYLOAD_CHARS) {
                    return LuaValue.FALSE;
                }
                if (!tryRateLimit()) {
                    return LuaValue.FALSE;
                }

                Consumer<ChannelPacket> sender = PACKET_SENDER;
                if (sender != null) {
                    sender.accept(new ChannelPacket(state.avatarId(), rawChannel, payloadJson));
                } else {
                    // Offline / test loopback
                    AvatarRuntime.channelPacket("local", rawChannel, payloadJson);
                }
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_net_is_allowed", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue channelArg) {
                String rawChannel = channelArg.optjstring("").trim().toLowerCase(Locale.ROOT);
                return LuaValue.valueOf(isValidChannel(rawChannel) && isChannelAllowed(rawChannel));
            }
        });

        globals.set("_avatar_net_allow_channel", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue channelArg) {
                String rawChannel = channelArg.optjstring("").trim().toLowerCase(Locale.ROOT);
                if (isValidChannel(rawChannel)) {
                    sessionAllowedChannels.add(rawChannel);
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });

        globals.set("_avatar_net_connected", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                try {
                    Minecraft client = Minecraft.getInstance();
                    return LuaValue.valueOf(client != null && (client.getConnection() != null || client.level != null));
                } catch (Throwable ignored) {
                    return LuaValue.FALSE;
                }
            }
        });
    }

    public boolean isChannelAllowed(String channel) {
        if (channel == null || channel.isBlank()) return false;
        if (channel.startsWith("shyne:") || channel.startsWith("avatar:")) return true;
        return sessionAllowedChannels.contains(channel);
    }

    public static boolean isValidChannel(String channel) {
        return AvatarChannelConstants.isValidChannel(channel);
    }

    private boolean tryRateLimit() {
        long now = System.currentTimeMillis();
        if (now - windowStartMs >= 1000L) {
            synchronized (this) {
                if (now - windowStartMs >= 1000L) {
                    packetsInWindow.set(0);
                    windowStartMs = now;
                }
            }
        }
        if (packetsInWindow.get() >= MAX_PACKETS_PER_SEC) {
            return false;
        }
        packetsInWindow.incrementAndGet();
        return true;
    }

    public static void onPacketReceived(String senderId, String channel, String payloadJson) {
        if (!isValidChannel(channel)) return;
        AvatarRuntime.channelPacket(senderId, channel, payloadJson);
    }
}
