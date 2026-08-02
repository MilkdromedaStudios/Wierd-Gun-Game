package com.milkdromeda.ledger.gun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The art is generated, which means it can silently fall behind the code: add a
 * part, forget to rerun {@code tools/GenerateAssets.java}, and the only symptom
 * is a purple-and-black cube in the menu that nothing in the build complains
 * about. These checks turn that into a failing test.
 * <p>
 * Every part and every barrel is walked back through the same chain the game
 * follows — item definition, model, texture — and each link has to exist.
 */
class AssetCoverageTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/ledger");
    private static final Pattern MODEL_REFERENCE = Pattern.compile("\"model\"\\s*:\\s*\"ledger:item/([a-z0-9_]+)\"");
    private static final Pattern LAYER_REFERENCE = Pattern.compile("\"layer0\"\\s*:\\s*\"ledger:item/([a-z0-9_]+)\"");

    @Test
    @DisplayName("every part has a sprite and a model")
    void everyPartIsDrawn() {
        List<String> missing = new ArrayList<>();
        for (PartSection section : PartSection.values()) {
            for (GunPart part : PartRegistry.of(section)) {
                check("part_" + part.id(), missing);
            }
        }
        assertTrue(missing.isEmpty(), "no art for: " + missing);
    }

    @Test
    @DisplayName("every barrel has a gun sprite, because the barrel picks the model")
    void everyBarrelHasAGun() {
        List<String> missing = new ArrayList<>();
        for (GunPart barrel : PartRegistry.of(PartSection.BARREL)) {
            check("gun_" + barrel.id(), missing);
        }
        assertTrue(missing.isEmpty(), "no art for: " + missing);
    }

    @Test
    @DisplayName("every preset resolves to a gun model that exists")
    void everyPresetIsDrawn() {
        List<String> missing = new ArrayList<>();
        for (GunPreset preset : GunPreset.all()) {
            check(GunItem.modelFor(preset.toBlueprint()), missing);
        }
        assertTrue(missing.isEmpty(), "no art for: " + missing);
    }

    @Test
    @DisplayName("every case in an item definition points at a model that exists")
    void itemDefinitionsResolve() {
        List<String> missing = new ArrayList<>();
        for (String definition : List.of("gun", "icon", "camera")) {
            Path path = ASSETS.resolve("items/" + definition + ".json");
            assertTrue(Files.exists(path), "missing item definition: " + path);
            Matcher matcher = MODEL_REFERENCE.matcher(read(path));
            Set<String> seen = new HashSet<>();
            while (matcher.find()) {
                seen.add(matcher.group(1));
            }
            assertFalse(seen.isEmpty(), definition + ".json names no models at all");
            for (String model : seen) {
                if (!Files.exists(ASSETS.resolve("models/item/" + model + ".json"))) {
                    missing.add(definition + ".json -> " + model);
                }
            }
        }
        assertTrue(missing.isEmpty(), "dangling model references: " + missing);
    }

    @Test
    @DisplayName("every flat model points at a texture that exists")
    void modelsResolveToTextures() {
        List<String> missing = new ArrayList<>();
        try (var models = Files.list(ASSETS.resolve("models/item"))) {
            models.forEach(model -> {
                Matcher matcher = LAYER_REFERENCE.matcher(read(model));
                while (matcher.find()) {
                    if (!Files.exists(ASSETS.resolve("textures/item/" + matcher.group(1) + ".png"))) {
                        missing.add(model.getFileName() + " -> " + matcher.group(1) + ".png");
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(missing.isEmpty(), "dangling texture references: " + missing);
    }

    private static void check(String variant, List<String> missing) {
        if (!Files.exists(ASSETS.resolve("textures/item/" + variant + ".png"))) {
            missing.add(variant + ".png");
        }
        if (!Files.exists(ASSETS.resolve("models/item/" + variant + ".json"))) {
            missing.add(variant + ".json");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
