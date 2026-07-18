package seashyne.shynecore.model;

import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.diagnostics.ContentDiagnostics;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class BbModelRegistry {
    public interface Listener {
        void onRegistryChanged(Collection<BbModelDefinition> allModels);
    }

    private final Map<String, BbModelDefinition> models = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ContentDiagnostics diagnostics;

    public BbModelRegistry() {
        this(null);
    }

    public BbModelRegistry(ContentDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void clear() {
        models.clear();
        notifyListeners();
    }

    public void discover(Path shyneModsDir) {
        models.clear();
        if (diagnostics != null) diagnostics.clearSource("model");
        if (!Files.isDirectory(shyneModsDir)) {
            notifyListeners();
            return;
        }
        try (DirectoryStream<Path> modDirs = Files.newDirectoryStream(shyneModsDir)) {
            for (Path modDir : modDirs) {
                if (!Files.isDirectory(modDir)) continue;
                String modId = modDir.getFileName().toString();
                scanMod(modDir, modId);
            }
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[BbModelRegistry] Failed to scan {}: {}", shyneModsDir, e.getMessage());
            if (diagnostics != null) {
                diagnostics.error("model", "registry", shyneModsDir, "Failed to scan model directories: " + e.getMessage(), "Check shyne-mods folder permissions and path names.");
            }
        }
        notifyListeners();
    }

    public Optional<BbModelDefinition> register(Path bbmodelPath, String modId) {
        String modelKey = modId == null || modId.isBlank() ? bbmodelPath.getFileName().toString() : modId;
        try {
            BbModelDefinition definition = BbModelParser.parse(bbmodelPath, modId);
            BbModelDefinition existing = models.put(definition.modelId(), definition);
            if (existing != null && diagnostics != null) {
                diagnostics.warn("model", definition.modelId(), bbmodelPath, "Duplicate model id replaced previous definition.", "Use unique model_id values per mod to avoid accidental overrides.");
            }
            notifyListeners();
            return Optional.of(definition);
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[BbModelRegistry] Could not parse {}: {}", bbmodelPath, e.getMessage());
            if (diagnostics != null) {
                diagnostics.error("model", modelKey, bbmodelPath, "Could not parse bbmodel: " + e.getMessage(), "Open the .bbmodel in Blockbench and export again if the format is broken.");
            }
            return Optional.empty();
        }
    }

    public Collection<BbModelDefinition> all() {
        return Collections.unmodifiableCollection(models.values());
    }

    public BbModelDefinition get(String modelId) {
        return models.get(modelId);
    }

    public boolean contains(String modelId) {
        return models.containsKey(modelId);
    }

    private void notifyListeners() {
        Collection<BbModelDefinition> snapshot = List.copyOf(models.values());
        listeners.forEach(listener -> listener.onRegistryChanged(snapshot));
    }

    private void scanMod(Path modDir, String modId) {
        List<Path> roots = List.of(
            modDir,
            modDir.resolve("bbmodels"),
            modDir.resolve("assets"),
            modDir.resolve("assets/models"),
            modDir.resolve("assets/bbmodels")
        );
        Set<Path> seen = new LinkedHashSet<>();
        Set<Path> scannedFiles = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.exists(root) || !seen.add(root)) continue;
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".bbmodel"))
                    .filter(path -> scannedFiles.add(path.toAbsolutePath().normalize()))
                    .forEach(path -> register(path, modId));
            } catch (IOException e) {
                ShyneCore.LOGGER.warn("[BbModelRegistry] Failed walking {}: {}", root, e.getMessage());
                if (diagnostics != null) {
                    diagnostics.warn("model", modId, root, "Failed while scanning model files: " + e.getMessage(), "Check that the folder exists and is readable.");
                }
            }
        }
    }
}
