package dev.pipi.facilities;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

@Mod("pipifacilities")
public final class PipiFacilities {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("pipifacilities");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("pipifacilities");
    public PipiFacilities(IEventBus bus) { PipiPets.init(bus);
        for (String id : new String[]{"craft","farm","carrot_farm","potato_farm","lumber","mine","gold_mine","diamond_mine","feeder","pet_storage","research","pet_market"}) {
            BLOCKS.register(id, () -> new FacilityBlock(BlockBehaviour.Properties.of().strength(1.5f).noOcclusion()));
            ITEMS.registerSimpleItem(id + "_blueprint", new Item.Properties().stacksTo(64));
        }
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private static final class FacilityBlock extends Block {
        FacilityBlock(BlockBehaviour.Properties properties) { super(properties); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    }
}
