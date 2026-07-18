package seashyne.shynecore.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public class BehaviorContext {
    private final ServerLevel world;
    private final Entity self;
    private final Entity owner;
    private final Map<String, Object> memory = new HashMap<>();

    public BehaviorContext(ServerLevel world, Entity self, Entity owner) {
        this.world = world;
        this.self = self;
        this.owner = owner;
    }

    public ServerLevel world() { return world; }
    public Entity self() { return self; }
    public Entity owner() { return owner; }
    public Map<String, Object> memory() { return memory; }
}
