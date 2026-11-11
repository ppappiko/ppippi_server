package dev.pipi.facilities;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

@Mod("pipifacilities")
public final class PipiFacilities {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("pipifacilities");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("pipifacilities");
    /** Plain stackable custom materials (no vanilla behaviour; the plugin owns their logic). */
    private static final String[] MATERIALS = {
        "plank", "steel", "reinforced_plank", "coated_plank", "blue_diamond", "refined_diamond",
        "iron_core", "diamond_core", "gold_core", "blueprint_paper", "repair_kit",
        "basic_trap", "iron_trap", "diamond_trap", "fish_feed", "river_fish", "straw", "pet_carrot", "raw_salmon", "amber_core", "advanced_blueprint_paper",
        "warden_soul_stone", "amber", "amber_ingot", "redwood_log", "redwood_plank", "cabbage", "vitality_crystal", "amber_trap",
        "salt", "sugar", "pepper", "soy_sauce", "hearty_salmon_stew", "hearty_carrot_soup", "hearty_veggie_salad", "failed_dish",
        "station_farm", "station_carrot_farm", "station_potato_farm", "station_cabbage_farm", "station_mine", "station_diamond_mine",
        "station_amber_quarry", "station_gold_mine", "station_lumber", "station_redwood_lumber"
    };
    public PipiFacilities(IEventBus bus) { PipiPets.init(bus);
        for (String id : new String[]{"craft","farm","carrot_farm","potato_farm","lumber","mine","gold_mine","diamond_mine","feeder","pet_storage","research","pet_market","amber_quarry","cabbage_farm","redwood_lumber","cooking_table","ranch"}) {
            BLOCKS.register(id, () -> new FacilityBlock(BlockBehaviour.Properties.of().strength(1.5f).noOcclusion()));
            ITEMS.registerSimpleItem(id + "_blueprint", new Item.Properties().stacksTo(64));
        }
        for (String id : MATERIALS)
            ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(64));
        ITEMS.registerSimpleItem("club", new Item.Properties().stacksTo(1).durability(30));
        ITEMS.registerSimpleItem("iron_club", new Item.Properties().stacksTo(1).durability(30));
        ITEMS.registerSimpleItem("diamond_club", new Item.Properties().stacksTo(1).durability(30));
        ITEMS.registerSimpleItem("amber_club", new Item.Properties().stacksTo(1).durability(30));
        ITEMS.registerSimpleItem("warp_stone_blueprint", new Item.Properties().stacksTo(16));
        ITEMS.registerSimpleItem("hammer", new Item.Properties().stacksTo(1).durability(250));
        // 3D skill-effect models for ItemDisplay (not obtainable, no recipes).
        for (String fx : new String[]{"slash_arc","slash_red","whirl_ring","shockwave","giant_sword","arrow_big","wind_spiral","leaf","orb","ice_lance","meteor","heal_cross","crystal"})
            ITEMS.registerSimpleItem("fx_" + fx, new Item.Properties().stacksTo(1));
        // Tier-4 region blocks: placeable by builders, no loot (the plugin handles drops/respawn).
        regionBlock("amber_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE).noLootTable()));
        regionBlock("rock_pile", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f).sound(SoundType.STONE).noLootTable()));
        regionBlock("redwood_tree_log", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_STEM).strength(2.0f).sound(SoundType.WOOD).noLootTable()));
        regionBlock("redwood_tree_leaves", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_HYPHAE).strength(0.2f).sound(SoundType.GRASS)
            .noOcclusion().isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).noLootTable()));
        regionBlock("cabbage_crop", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.CROP)
            .pushReaction(PushReaction.DESTROY).noOcclusion().noLootTable()));
        regionBlock("warp_stone", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(35.0f, 1200.0f).sound(SoundType.AMETHYST)
            .lightLevel(s -> 7).noOcclusion().isViewBlocking((s, l, p) -> false).noLootTable()));
        ITEMS.registerSimpleItem("cabbage_seed",new Item.Properties().stacksTo(64));
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private static void regionBlock(String id, java.util.function.Supplier<Block> sup) {
        var holder = BLOCKS.register(id, sup);
        ITEMS.registerSimpleBlockItem(id, holder, new Item.Properties().stacksTo(64));
    }

    private static final class FacilityBlock extends Block {
        FacilityBlock(BlockBehaviour.Properties properties) { super(properties); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    }
}
