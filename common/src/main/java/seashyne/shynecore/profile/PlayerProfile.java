package seashyne.shynecore.profile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlayerProfile(
    UUID playerId,
    String playerName,
    int level,
    long experience,
    int statPoints,
    int skillPoints,
    String playerClass,
    List<String> unlockedSkills,
    Map<String, String> equippedSkills,
    Map<String, Integer> attributes,
    String teamId,
    long updatedAtMillis
) {
    public PlayerProfile withProgress(int level, long experience, int statPoints, int skillPoints, long updatedAtMillis) {
        return new PlayerProfile(playerId, playerName, level, experience, statPoints, skillPoints, playerClass, unlockedSkills, equippedSkills, attributes, teamId, updatedAtMillis);
    }
}
