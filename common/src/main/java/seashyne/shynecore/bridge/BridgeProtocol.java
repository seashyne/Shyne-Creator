package seashyne.shynecore.bridge;

public final class BridgeProtocol {
    public static final String RES_ACTION   = "action";
    public static final String RES_SCHEDULE = "schedule";

    public static final String ACT_SEND_MESSAGE   = "send_message";
    public static final String ACT_GIVE_ITEM      = "give_item";
    public static final String ACT_GIVE_SHYNE_ITEM = "give_shyne_item";
    public static final String ACT_RUN_COMMAND    = "run_command";
    public static final String ACT_SPAWN_ENTITY   = "spawn_entity";
    public static final String ACT_SET_BLOCK      = "set_block";
    public static final String ACT_PLAY_SOUND     = "play_sound";
    public static final String ACT_LOG            = "log";
    public static final String ACT_SCHEDULE       = "schedule";
    public static final String ACT_LOAD_BBMODEL   = "load_bbmodel";
    public static final String ACT_ATTACH_MODEL   = "attach_model";
    public static final String ACT_DETACH_MODEL   = "detach_model";
    public static final String ACT_PLAY_ANIMATION = "play_animation";
    public static final String ACT_PLAY_ANIMATION_ENTITY = "play_animation_entity";
    public static final String ACT_STOP_ANIMATION = "stop_animation";
    public static final String ACT_STOP_ANIMATION_ENTITY = "stop_animation_entity";
    public static final String ACT_SUMMON_ENTITY  = "summon_entity";
    public static final String ACT_DESPAWN_ENTITY = "despawn_entity";
    public static final String ACT_DESPAWN_OWNER_SUMMONS = "despawn_owner_summons";
    public static final String ACT_LAUNCH_PROJECTILE = "launch_projectile";
    public static final String ACT_ADVANCE_COMBO = "advance_combo";
    public static final String ACT_RESET_COMBO = "reset_combo";
    public static final String ACT_SET_TEAM = "set_team";
    public static final String ACT_CONFIGURE_TEAM = "configure_team";
    public static final String ACT_SET_MANA = "set_mana";
    public static final String ACT_GRANT_XP = "grant_xp";
    public static final String ACT_UNLOCK_SKILL = "unlock_skill";
    public static final String ACT_EQUIP_SKILL = "equip_skill";
    public static final String ACT_EQUIP_WEAPON = "equip_weapon";
    public static final String ACT_CAST_SKILL = "cast_skill";

    public static final String F_TYPE       = "type";
    public static final String F_MOD_ID     = "mod_id";
    public static final String F_HOOK       = "hook";
    public static final String F_ARGS       = "args";
    public static final String F_ACTION     = "action";
    public static final String F_DELAY      = "delay_ticks";

    private BridgeProtocol() {}
}
