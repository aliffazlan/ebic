package com.walnutt.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Loads design_ideas/units/**\/*.json and design_ideas/abilities/<unit>/*.json
 * directly from the filesystem (not copied into src/main/resources) - keeps one
 * source of truth while those JSON files are still under active design iteration.
 *
 * Units are organized into subdirectories (units/champion/, units/elite/, plus a
 * few top-level summon-prototype files like zenith_pylon.json) - loadAllUnits()
 * walks the whole units/ tree recursively rather than assuming a flat directory,
 * so the loader doesn't care how they're organized on disk.
 *
 * Tolerant of the JSON being a hand-edited draft: blank files (an empty unit file)
 * are skipped rather than failing the whole load.
 */
public class JsonDataLoader {
    private final Gson gson = new Gson();
    private final Path unitsDir;
    private final Path abilitiesDir;

    public JsonDataLoader(Path designIdeasRoot) {
        this.unitsDir = designIdeasRoot.resolve("units");
        this.abilitiesDir = designIdeasRoot.resolve("abilities");
    }

    /** Walks up from the current working directory looking for a design_ideas/ folder. */
    public static Path locateDesignIdeasRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("design_ideas");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "Could not locate a design_ideas/ directory above " + Paths.get("").toAbsolutePath());
    }

    public Map<String, UnitDefinition> loadAllUnits() {
        Map<String, UnitDefinition> result = new LinkedHashMap<>();
        if (!Files.isDirectory(unitsDir)) {
            return result;
        }
        try (Stream<Path> files = Files.walk(unitsDir)) {
            for (Path file : files.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                UnitDefinition def = readJson(file, UnitDefinition.class);
                if (def == null || def.name() == null) {
                    continue;
                }
                result.put(stripExtension(file.getFileName().toString()), def);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load unit definitions from " + unitsDir, e);
        }
        return result;
    }

    public Map<String, AbilityDefinition> loadAllAbilities() {
        Map<String, AbilityDefinition> result = new LinkedHashMap<>();
        if (!Files.isDirectory(abilitiesDir)) {
            return result;
        }
        try (Stream<Path> unitDirs = Files.list(abilitiesDir)) {
            for (Path unitDir : unitDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(unitDir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                        AbilityDefinition def = readJson(file, AbilityDefinition.class);
                        if (def == null || def.name() == null) {
                            continue;
                        }
                        result.put(stripExtension(file.getFileName().toString()), def);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ability definitions from " + abilitiesDir, e);
        }
        return result;
    }

    private <T> T readJson(Path file, Class<T> type) {
        try {
            String content = Files.readString(file);
            if (content.isBlank()) {
                return null;
            }
            return gson.fromJson(content, type);
        } catch (IOException | JsonSyntaxException e) {
            throw new RuntimeException("Failed to parse " + file, e);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }
}
