package seashyne.shynecore.client.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AvatarHumanoidRigValidationTest {
    @TempDir Path temp;

    @Test
    void fullBodyWithoutHumanoidRootsIsValidButWarned() throws Exception {
        writeAvatar(temp, "[]");

        AvatarManifest manifest = AvatarLoader.loadManifest(temp);
        var model = seashyne.shynecore.model.BbModelParser.parse(temp.resolve("model.bbmodel"), manifest.id());
        List<AvatarValidationReport.Issue> issues = new ArrayList<>();
        AvatarValidator.validateFullBodyHumanoid(model, manifest, issues);

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("full_body_humanoid_missing")));
    }

    @Test
    void standardRootsUseMinecraftPoseWithoutAuthoredAnimations() throws Exception {
        writeAvatar(temp, """
            [
              {"name":"Head","uuid":"head","origin":[0,24,0],"children":[]},
              {"name":"Body","uuid":"body","origin":[0,24,0],"children":[]},
              {"name":"LeftArm","uuid":"left-arm","origin":[5,22,0],"children":[]},
              {"name":"RightArm","uuid":"right-arm","origin":[-5,22,0],"children":[]},
              {"name":"LeftLeg","uuid":"left-leg","origin":[1.9,12,0],"children":[]},
              {"name":"RightLeg","uuid":"right-leg","origin":[-1.9,12,0],"children":[]}
            ]
            """);

        AvatarManifest manifest = AvatarLoader.loadManifest(temp);
        var model = seashyne.shynecore.model.BbModelParser.parse(temp.resolve("model.bbmodel"), manifest.id());
        List<AvatarValidationReport.Issue> issues = new ArrayList<>();
        AvatarValidator.validateFullBodyHumanoid(model, manifest, issues);

        assertEquals(0, model.animations().size());
        assertFalse(issues.stream().anyMatch(issue -> issue.code().startsWith("full_body_humanoid_")));
    }

    private static void writeAvatar(Path root, String outliner) throws Exception {
        Files.writeString(root.resolve("avatar.json"), """
            {"standard":"2.0","name":"Humanoid Test","profile":"full_body","model":"model.bbmodel"}
            """);
        Files.writeString(root.resolve("model.bbmodel"), """
            {
              "meta":{"format_version":"4.10","model_format":"free"},
              "resolution":{"width":64,"height":64},
              "elements":[],
              "outliner":%s,
              "textures":[],
              "animations":[]
            }
            """.formatted(outliner));
    }
}
