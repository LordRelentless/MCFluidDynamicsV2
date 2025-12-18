// Port each gen: Eagle(), Cat() etc as static void generate(ServerCommandSource source, BlockPos origin)
// Use world.setBlockState(worldPos, getBlockFor(color, type))
// getBlockFor: if color==GLASS Blocks.GLASS, SAND Blocks.SAND, else COLORED_SOLID.with(COLOR, HEX_TO_DYE.get(color))
// For 'water' : FLUID_VOXEL.with(TYPE, WATER)
private static void generateSphere(ServerLevel world, BlockPos center, int r, int col, String typeStr) { ... loop dx dy dz <=r2 }
private static void setBlock(ServerLevel world, BlockPos p, int col, String typeStr) { ... }