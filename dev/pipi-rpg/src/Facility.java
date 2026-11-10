package dev.pipi.life;

import org.bukkit.Material;

enum Facility {
    FARM("밀 농장", Material.COMPOSTER, Material.HAY_BLOCK),
    CARROT_FARM("당근 농장", Material.BEEHIVE, Material.CARROT),
    POTATO_FARM("감자 농장", Material.BEE_NEST, Material.POTATO),
    LUMBER("벌목장", Material.LOOM, Material.OAK_LOG),
    MINE("철광석 채석장", Material.STONECUTTER, Material.RAW_IRON),
    GOLD_MINE("금광석 채석장", Material.BLAST_FURNACE, Material.RAW_GOLD),
    DIAMOND_MINE("다이아 채석장", Material.GRINDSTONE, Material.DIAMOND),
    FEEDER("식량 저장소", Material.BARREL, Material.OAK_PLANKS),
    PET_STORAGE("펫 창고", Material.CHEST, Material.OAK_PLANKS),
    RESEARCH("연구소", Material.LECTERN, Material.BOOK),
    PET_MARKET("펫 가판대", Material.FLETCHING_TABLE, Material.OAK_PLANKS),
    CRAFT("기본 작업대", Material.SMITHING_TABLE, Material.IRON_INGOT),
    TRANSPORT("운반", Material.OAK_PLANKS, Material.IRON_INGOT);

    final String label;
    final Material block;
    final Material ingredient;
    Facility(String label, Material block, Material ingredient) {
        this.label = label;
        this.block = block;
        this.ingredient = ingredient;
    }

    Material installedBlock() {
        if (this == TRANSPORT) return block;
        Material material = Material.matchMaterial("PIPIFACILITIES_" + name());
        if (material == null) throw new IllegalStateException("PipiFacilities 모드가 필요합니다: " + name());
        return material;
    }

    Material blueprintMaterial() {
        if (this == TRANSPORT) return block;
        Material material = Material.matchMaterial("PIPIFACILITIES_" + name() + "_BLUEPRINT");
        if (material == null) throw new IllegalStateException("PipiFacilities 청사진이 등록되지 않았습니다: " + name());
        return material;
    }

    int width() { return this==CRAFT || this==RESEARCH || this==LUMBER || this==PET_STORAGE || this==PET_MARKET ? 3 : 2; }
    int depth() { return this==CRAFT || this==RESEARCH ? 1 : this==LUMBER || this==PET_STORAGE ? 3 : 2; }

    boolean matches(Material material) { return this != TRANSPORT && (material == block || material == installedBlock()); }

    boolean isProducer() { return this == LUMBER || this == FARM || this == CARROT_FARM || this == POTATO_FARM || this == MINE || this == DIAMOND_MINE; }

    static long duration(long base, int level) {
        if (level < 1 || level > 3) throw new IllegalArgumentException("Invalid aptitude level");
        return base / level;
    }
}


