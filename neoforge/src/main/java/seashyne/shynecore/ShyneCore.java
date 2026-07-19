package seashyne.shynecore;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seashyne.shynecore.animation.AnimationRuntime;
import seashyne.shynecore.attachment.AttachmentRuntime;
import seashyne.shynecore.bridge.ActionDispatcher;
import seashyne.shynecore.bridge.ActionBus;
import seashyne.shynecore.command.ShyneCommand;
import seashyne.shynecore.diagnostics.ContentDiagnostics;
import seashyne.shynecore.equipment.EquipmentRuntime;
import seashyne.shynecore.events.ShyneEventBus;
import seashyne.shynecore.loader.ShyneModLoader;
import seashyne.shynecore.item.ShyneItemRuntime;
import seashyne.shynecore.item.ShyneItems;
import seashyne.shynecore.model.BbModelRegistry;
import seashyne.shynecore.network.ShyneNetwork;
import seashyne.shynecore.power.PowerStateMachine;
import seashyne.shynecore.power.CombatStatManager;
import seashyne.shynecore.profile.PlayerProfileRuntime;
import seashyne.shynecore.projectile.ProjectileRuntime;
import seashyne.shynecore.skill.SkillRegistry;
import seashyne.shynecore.skill.SkillExecutor;
import seashyne.shynecore.summon.SummonRuntime;
import seashyne.shynecore.targeting.TargetingRuntime;
import seashyne.shynecore.team.TeamRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(ShyneCore.MOD_ID)
public class ShyneCore {
    public static final String MOD_ID = "shyne_creator";
    public static final String VERSION = "2.7.29-alpha-blockbench-5.1-26.2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ShyneCore INSTANCE;

    public ShyneCore(IEventBus modEventBus) {
        INSTANCE = this;
        ShyneItems.register(modEventBus);
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("shyne-creator");
        Path shyneModsDir = gameDir.resolve("shyne-mods");
        ActionBus actionBus = new ActionBus();
        ContentDiagnostics diagnostics = new ContentDiagnostics(configDir.resolve("reports"));
        try {
            Files.deleteIfExists(configDir.resolve("bridge_server.py"));
        } catch (IOException e) {
            LOGGER.warn("[ShyneCreator] Could not remove unused legacy bridge_server.py: {}", e.getMessage());
        }

        BbModelRegistry bbModelRegistry = new BbModelRegistry(diagnostics);
        SkillRegistry skillRegistry = new SkillRegistry(diagnostics);
        EquipmentRuntime equipmentRuntime = new EquipmentRuntime(diagnostics);
        AnimationRuntime animationRuntime = new AnimationRuntime(bbModelRegistry);
        AttachmentRuntime attachmentRuntime = new AttachmentRuntime(bbModelRegistry);
        TeamRuntime teamRuntime = new TeamRuntime(configDir.resolve("teams.json"));
        teamRuntime.load();
        PlayerProfileRuntime profileRuntime = new PlayerProfileRuntime(configDir.resolve("profiles.json"), skillRegistry, diagnostics);
        try {
            profileRuntime.load();
        } catch (IOException e) {
            LOGGER.warn("[ShyneCreator] Could not load profiles: {}", e.getMessage());
        }

        SummonRuntime summonRuntime = new SummonRuntime(attachmentRuntime, animationRuntime);
        TargetingRuntime targetingRuntime = new TargetingRuntime(summonRuntime, teamRuntime);
        ProjectileRuntime projectileRuntime = new ProjectileRuntime(attachmentRuntime, animationRuntime, teamRuntime);
        CombatStatManager combatStatManager = new CombatStatManager();
        PowerStateMachine powerStateMachine = new PowerStateMachine(animationRuntime, combatStatManager);

        ShyneModLoader modLoader = new ShyneModLoader(actionBus, gameDir, bbModelRegistry, diagnostics);
        SkillExecutor skillExecutor = new SkillExecutor(skillRegistry, profileRuntime, equipmentRuntime, combatStatManager, powerStateMachine, modLoader);
        ShyneItemRuntime itemRuntime = new ShyneItemRuntime(skillExecutor, equipmentRuntime, modLoader, diagnostics);
        ShyneItems.bindRuntime(itemRuntime);
        ShyneNetwork.registerPayloads(modEventBus);
        ShyneNetwork network = new ShyneNetwork(bbModelRegistry, animationRuntime, attachmentRuntime, powerStateMachine, skillExecutor, skillRegistry, profileRuntime, equipmentRuntime);
        ActionDispatcher dispatcher = new ActionDispatcher(actionBus, bbModelRegistry, animationRuntime, attachmentRuntime, summonRuntime, targetingRuntime, projectileRuntime, powerStateMachine, teamRuntime, skillRegistry, skillExecutor, profileRuntime, equipmentRuntime, itemRuntime, modLoader);

        modLoader.discoverAndLoad();
        skillRegistry.discover(shyneModsDir);
        equipmentRuntime.discover(shyneModsDir);
        itemRuntime.discover(shyneModsDir);
        profileRuntime.validateProfiles();
        ShyneEventBus eventBus = new ShyneEventBus();
        eventBus.bind(modLoader);
        ShyneCommand.register(modLoader, bbModelRegistry, animationRuntime, attachmentRuntime, summonRuntime, projectileRuntime, powerStateMachine, teamRuntime, skillRegistry, skillExecutor, profileRuntime, equipmentRuntime, itemRuntime, diagnostics);

        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> {
            var server = event.getServer();
            dispatcher.bindServer(server);
            network.bindServer(server);
            bbModelRegistry.discover(shyneModsDir);
            skillRegistry.discover(shyneModsDir);
            equipmentRuntime.discover(shyneModsDir);
            itemRuntime.discover(shyneModsDir);
            profileRuntime.validateProfiles();
            LOGGER.info("[ShyneCreator] Server ready. mods={} models={} skills={} weapons={} items={} diagnostics={}",
                modLoader.getLoadedCount(), bbModelRegistry.all().size(), skillRegistry.all().size(), equipmentRuntime.allWeapons().size(), itemRuntime.all().size(), diagnostics.summary());
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
            teamRuntime.save();
            try {
                profileRuntime.save();
            } catch (IOException e) {
                LOGGER.warn("[ShyneCreator] Could not save profiles: {}", e.getMessage());
            }
        });
    }
}
