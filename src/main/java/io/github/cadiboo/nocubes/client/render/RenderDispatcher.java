package io.github.cadiboo.nocubes.client.render;

import io.github.cadiboo.nocubes.client.ClientCacheUtil;
import io.github.cadiboo.nocubes.client.LazyBlockColorCache;
import io.github.cadiboo.nocubes.client.LazyPackedLightCache;
import io.github.cadiboo.nocubes.client.UVHelper;
import io.github.cadiboo.nocubes.config.Config;
import io.github.cadiboo.nocubes.mesh.MeshDispatcher;
import io.github.cadiboo.nocubes.mesh.MeshGenerator;
import io.github.cadiboo.nocubes.mesh.MeshGeneratorType;
import io.github.cadiboo.nocubes.util.CacheUtil;
import io.github.cadiboo.nocubes.util.IsSmoothable;
import io.github.cadiboo.nocubes.util.ModProfiler;
import io.github.cadiboo.nocubes.util.pooled.Face;
import io.github.cadiboo.nocubes.util.pooled.FaceList;
import io.github.cadiboo.nocubes.util.pooled.Vec3;
import io.github.cadiboo.nocubes.util.pooled.cache.SmoothableCache;
import io.github.cadiboo.nocubes.util.pooled.cache.StateCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.chunk.ChunkRender;
import net.minecraft.client.renderer.chunk.ChunkRenderTask;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.IEnviromentBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.BiomeColors;

import javax.annotation.Nonnull;
import java.util.Random;

import static io.github.cadiboo.nocubes.util.IsSmoothable.LEAVES_SMOOTHABLE;
import static io.github.cadiboo.nocubes.util.IsSmoothable.TERRAIN_SMOOTHABLE;

/**
 * @author Cadiboo
 */
public final class RenderDispatcher {

	public static void renderChunk(
			@Nonnull final ChunkRender renderChunk,
			@Nonnull final BlockPos renderChunkPosition,
			@Nonnull final ChunkRenderTask generator,
			@Nonnull final CompiledChunk compiledChunk,
			// Use World for eagerly generated caches
			@Nonnull final IWorld world,
			// Use RenderChunkCache for lazily generated caches
			@Nonnull final IEnviromentBlockReader blockAccess,
			@Nonnull final boolean[] usedBlockRenderLayers,
			@Nonnull final Random random,
			@Nonnull final BlockRendererDispatcher blockRendererDispatcher
	) {

		if (!Config.renderSmoothTerrain && !Config.renderSmoothLeaves) {
			return;
		}

		final MeshGenerator terrainMeshGenerator = Config.terrainMeshGenerator.getMeshGenerator();

		// A chunk is 0-15 so 16, we add one because idk and then surface nets needs another +1 because reasons
		//TODO what if leaves mesh generator is SurfaceNets but terrain is not?
		final byte meshSizeX = (byte) (17 + terrainMeshGenerator.getSizeXExtension());
		final byte meshSizeY = (byte) (17 + terrainMeshGenerator.getSizeYExtension());
		final byte meshSizeZ = (byte) (17 + terrainMeshGenerator.getSizeZExtension());

		final int renderChunkPositionX = renderChunkPosition.getX();
		final int renderChunkPositionY = renderChunkPosition.getY();
		final int renderChunkPositionZ = renderChunkPosition.getZ();

//		* Density Cache    | -1, n     | n + 1
//		* Vertices         | -1, 16    | 17
//		* Texture Cache    | -2, 17    | 20
//		* Light Cache      | -2, 17    | 20
//		* Color Cache      | -2, 17    | 20
//		* Fluids Cache     | 0,15x0,16y| 16, 17

		final int cacheSizeX = Math.max(
				// -2 for Textures + 15 for 0-15 chunk +1 because there are 16 blocks in 0-15 + 2 for Textures
				2 + 15 + 1 + 2,
				// -1 for Density + meshSizeX +1 because there are n+1 blocks in 0-n + 1 for calculating density
				1 + meshSizeX + 1 + 1
		);
		final int cacheSizeY = Math.max(
				// -2 for Textures + 15 for 0-15 chunk +1 because there are 16 blocks in 0-15 + 2 for Textures
				2 + 15 + 1 + 2,
				// -1 for Density + meshSizeX +1 because there are n+1 blocks in 0-n + 1 for calculating density
				1 + meshSizeY + 1 + 1
		);
		final int cacheSizeZ = Math.max(
				// -2 for Textures + 15 for 0-15 chunk +1 because there are 16 blocks in 0-15 + 2 for Textures
				2 + 15 + 1 + 2,
				// -1 for Density + meshSizeX +1 because there are n+1 blocks in 0-n + 1 for calculating density
				1 + meshSizeZ + 1 + 1
		);

		try (
				PooledMutableBlockPos pooledMutableBlockPos = PooledMutableBlockPos.retain();
				StateCache stateCache = CacheUtil.generateStateCache(
						// -2 for Textures
						renderChunkPositionX - 2,
						renderChunkPositionY - 2,
						renderChunkPositionZ - 2,
						cacheSizeX, cacheSizeY, cacheSizeZ,
						world, pooledMutableBlockPos
				)
		) {
			renderChunk(
					renderChunk,
					generator,
					compiledChunk,
					renderChunkPosition,
					renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
					world, blockAccess,
					pooledMutableBlockPos,
					usedBlockRenderLayers,
					random,
					blockRendererDispatcher,
					stateCache
			);
		}
	}

	private static void renderChunk(
			@Nonnull final ChunkRender renderChunk,
			@Nonnull final ChunkRenderTask generator,
			@Nonnull final CompiledChunk compiledChunk,
			@Nonnull final BlockPos renderChunkPosition,
			final int renderChunkPositionX, final int renderChunkPositionY, final int renderChunkPositionZ,
			// Use World for eagerly generated caches
			@Nonnull final IWorld world,
			// Use RenderChunkCache for lazily generated caches
			@Nonnull final IEnviromentBlockReader blockAccess,
			@Nonnull final PooledMutableBlockPos pooledMutableBlockPos,
			@Nonnull final boolean[] usedBlockRenderLayers,
			@Nonnull final Random random,
			@Nonnull final BlockRendererDispatcher blockRendererDispatcher,
			@Nonnull final StateCache stateCache
	) {
		if (!Config.renderSmoothTerrain && !Config.renderSmoothLeaves) {
			return;
		}
		try (LazyPackedLightCache packedLightCache = ClientCacheUtil.generatePackedLightCache(renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ, stateCache, blockAccess)) {

//			if (Config.extendFluidsRange.getRange() > 0) {
//				try (
//						final ModProfiler ignored = ModProfiler.get().start("extendFluids");
//						SmoothableCache smoothableCache = CacheUtil.generateSmoothableCache(
//								stateCache,
//								(state) ->
//										(Config.renderSmoothTerrain && TERRAIN_SMOOTHABLE.apply(state)) || (Config.renderSmoothLeaves && LEAVES_SMOOTHABLE.apply(state))
//						)
//				) {
//					try {
//						ExtendedFluidChunkRenderer.renderChunk(
//								renderChunk,
//								generator,
//								compiledChunk,
//								renderChunkPosition,
//								renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
//								blockAccess,
//								pooledMutableBlockPos,
//								usedBlockRenderLayers,
//								stateCache, smoothableCache,
//								packedLightCache
//						);
//					} catch (ReportedException e) {
//						throw e;
//					} catch (Exception e) {
//						CrashReport crashReport = new CrashReport("Error rendering extended fluids!", e);
//						crashReport.makeCategory("Rendering extended fluids");
//						throw new ReportedException(crashReport);
//					}
//				}
//			}

			try {
				if (Config.renderSmoothTerrain) {
					renderTerrain(renderChunk, generator, compiledChunk, renderChunkPosition, renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ, blockAccess, stateCache, pooledMutableBlockPos, usedBlockRenderLayers, blockRendererDispatcher, random, packedLightCache);
				}
				if (Config.renderSmoothLeaves) {
					renderLeaves(renderChunk, generator, compiledChunk, renderChunkPosition, renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ, blockAccess, stateCache, pooledMutableBlockPos, usedBlockRenderLayers, blockRendererDispatcher, random, packedLightCache);
				}
			} catch (ReportedException e) {
				throw e;
			} catch (Exception e) {
				CrashReport crashReport = new CrashReport("Error rendering mesh!", e);
				crashReport.makeCategory("Rendering mesh");
				throw new ReportedException(crashReport);
			}

		}
	}

	private static void renderLeaves(
			@Nonnull final ChunkRender renderChunk,
			@Nonnull final ChunkRenderTask generator,
			@Nonnull final CompiledChunk compiledChunk,
			@Nonnull final BlockPos renderChunkPosition,
			final int renderChunkPositionX, final int renderChunkPositionY, final int renderChunkPositionZ,
			@Nonnull final IEnviromentBlockReader blockAccess,
			@Nonnull final StateCache stateCache,
			@Nonnull final PooledMutableBlockPos pooledMutableBlockPos,
			@Nonnull final boolean[] usedBlockRenderLayers,
			@Nonnull final BlockRendererDispatcher blockRendererDispatcher,
			@Nonnull final Random random,
			@Nonnull final LazyPackedLightCache pooledPackedLightCache
	) {
		if (Config.getLeavesSmoothableBlocks().isEmpty()) {
			return;
		}
		try (
				LazyBlockColorCache blockColorsCache = ClientCacheUtil.generateLazyBlockColorCache(renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ, blockAccess, BiomeColors.field_180289_b);
				PooledMutableBlockPos texturePooledMutableBlockPos = PooledMutableBlockPos.retain()
		) {
			switch (Config.smoothLeavesType) {
				case SEPARATE:
					for (final Block smoothableBlock : Config.getLeavesSmoothableBlocks()) {
						final IsSmoothable isSmoothable = (checkState) -> (LEAVES_SMOOTHABLE.apply(checkState) && checkState.getBlock() == smoothableBlock);
						try (
								ModProfiler ignored = ModProfiler.get().start("renderLeaves" + smoothableBlock.getRegistryName());
								SmoothableCache smoothableCache = CacheUtil.generateSmoothableCache(stateCache, isSmoothable)
						) {
							MeshRenderer.renderMesh(
									renderChunk,
									generator,
									compiledChunk,
									renderChunkPosition,
									renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
									blockAccess,
									stateCache,
									blockRendererDispatcher,
									random,
									pooledPackedLightCache,
									blockColorsCache,
									MeshDispatcher.generateChunkMeshOffset(
											renderChunkPosition, blockAccess, pooledMutableBlockPos,
											stateCache, smoothableCache, null,
											// state cache begins at -2 and density cache expects -1
											1, 1, 1,
											isSmoothable,
											Config.leavesMeshGenerator
									),
									smoothableCache,
									// state cache begins at -2 and density cache expects 0
									2, 2, 2,
									pooledMutableBlockPos, texturePooledMutableBlockPos, usedBlockRenderLayers,
									true,
									true, false
							);
						}
					}
					break;
				case TOGETHER:
					final IsSmoothable isSmoothable = LEAVES_SMOOTHABLE;
					try (
//							ModProfiler ignored = ModProfiler.get().start("renderLeavesTogether");
							SmoothableCache smoothableCache = CacheUtil.generateSmoothableCache(stateCache, isSmoothable)
					) {
						MeshRenderer.renderMesh(
								renderChunk,
								generator,
								compiledChunk,
								renderChunkPosition,
								renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
								blockAccess,
								stateCache,
								blockRendererDispatcher,
								random,
								pooledPackedLightCache,
								blockColorsCache,
								MeshDispatcher.generateChunkMeshOffset(
										renderChunkPosition, blockAccess, pooledMutableBlockPos,
										stateCache, smoothableCache, null,
										// state cache begins at -2 and density cache expects -1
										1, 1, 1,
										isSmoothable,
										Config.leavesMeshGenerator
								),
								smoothableCache,
								// state cache begins at -2 and density cache expects 0
								2, 2, 2,
								pooledMutableBlockPos, texturePooledMutableBlockPos, usedBlockRenderLayers,
								true,
								true, false
						);
					}
					break;
			}
		}
	}

	private static void renderTerrain(
			@Nonnull final ChunkRender renderChunk,
			@Nonnull final ChunkRenderTask generator,
			@Nonnull final CompiledChunk compiledChunk,
			@Nonnull final BlockPos renderChunkPosition,
			final int renderChunkPositionX, final int renderChunkPositionY, final int renderChunkPositionZ,
			@Nonnull final IEnviromentBlockReader blockAccess,
			@Nonnull final StateCache stateCache,
			@Nonnull final PooledMutableBlockPos pooledMutableBlockPos,
			@Nonnull final boolean[] usedBlockRenderLayers,
			@Nonnull final BlockRendererDispatcher blockRendererDispatcher,
			@Nonnull final Random random,
			@Nonnull final LazyPackedLightCache pooledPackedLightCache
	) {
		try (
				SmoothableCache smoothableCache = CacheUtil.generateSmoothableCache(stateCache, IsSmoothable.TERRAIN_SMOOTHABLE);
				LazyBlockColorCache blockColorsCache = ClientCacheUtil.generateLazyBlockColorCache(renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ, blockAccess, BiomeColors.field_180291_a);
				PooledMutableBlockPos texturePooledMutableBlockPos = PooledMutableBlockPos.retain()
		) {
			MeshRenderer.renderMesh(
					renderChunk,
					generator,
					compiledChunk,
					renderChunkPosition,
					renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
					blockAccess,
					stateCache,
					blockRendererDispatcher,
					random,
					pooledPackedLightCache,
					blockColorsCache,
					MeshDispatcher.generateChunkMeshOffset(
							renderChunkPosition, blockAccess, pooledMutableBlockPos, stateCache, smoothableCache, null,
							// state cache begins at -2 and density cache expects -1
							1, 1, 1,
							TERRAIN_SMOOTHABLE,
							Config.terrainMeshGenerator
					),
					smoothableCache,
					// state cache begins at -2 and density cache expects 0
					2, 2, 2,
					pooledMutableBlockPos, texturePooledMutableBlockPos, usedBlockRenderLayers,
					false,
					true, true
			);
		}
	}

	public static void renderSmoothBlockDamage(final Tessellator tessellatorIn, final BufferBuilder bufferBuilderIn, final BlockPos blockpos, final BlockState iblockstate, final IEnviromentBlockReader world, final TextureAtlasSprite textureatlassprite) {
		if (iblockstate.getRenderType() == BlockRenderType.MODEL) {
			tessellatorIn.draw();
			bufferBuilderIn.begin(7, DefaultVertexFormats.BLOCK);
			final IsSmoothable isSmoothable;
			final MeshGeneratorType meshGeneratorType;
			if (Config.renderSmoothTerrain && iblockstate.nocubes_isTerrainSmoothable()) {
				isSmoothable = TERRAIN_SMOOTHABLE;
				meshGeneratorType = Config.terrainMeshGenerator;
			} else if (Config.renderSmoothLeaves && iblockstate.nocubes_isLeavesSmoothable()) {
				isSmoothable = LEAVES_SMOOTHABLE;
				meshGeneratorType = Config.leavesMeshGenerator;
			} else {
				return;
			}

			try (FaceList faces = MeshDispatcher.generateBlockMeshOffset(blockpos, world, isSmoothable, meshGeneratorType)) {
				float minU = UVHelper.getMinU(textureatlassprite);
				float maxU = UVHelper.getMaxU(textureatlassprite);
				float minV = UVHelper.getMinV(textureatlassprite);
				float maxV = UVHelper.getMaxV(textureatlassprite);
//				int color = Minecraft.getInstance().getBlockColors().getColor(iblockstate, world, blockpos, -1);
//				float red = (float) (color >> 16 & 255) / 255.0F;
//				float green = (float) (color >> 8 & 255) / 255.0F;
//				float blue = (float) (color & 255) / 255.0F;
				float red = 1.0F;
				float green = 1.0F;
				float blue = 1.0F;
				float alpha = 1.0F;
				final int packed = iblockstate.func_215684_a(world, blockpos);
				int lightmapSkyLight = (packed >> 16) & 0xFFFF;
				int lightmapBlockLight = packed & 0xFFFF;
				for (int faceIndex = 0, facesSize = faces.size(); faceIndex < facesSize; ++faceIndex) {
					try (Face face = faces.get(faceIndex)) {
						try (
								Vec3 v0 = face.getVertex0();
								Vec3 v1 = face.getVertex1();
								Vec3 v2 = face.getVertex2();
								Vec3 v3 = face.getVertex3()
						) {
							bufferBuilderIn.pos(v0.x, v0.y, v0.z).color(red, green, blue, alpha).tex(minU, minV).lightmap(lightmapSkyLight, lightmapBlockLight).endVertex();
							bufferBuilderIn.pos(v1.x, v1.y, v1.z).color(red, green, blue, alpha).tex(minU, maxV).lightmap(lightmapSkyLight, lightmapBlockLight).endVertex();
							bufferBuilderIn.pos(v2.x, v2.y, v2.z).color(red, green, blue, alpha).tex(maxU, maxV).lightmap(lightmapSkyLight, lightmapBlockLight).endVertex();
							bufferBuilderIn.pos(v3.x, v3.y, v3.z).color(red, green, blue, alpha).tex(maxU, minV).lightmap(lightmapSkyLight, lightmapBlockLight).endVertex();
						}
					}
				}
			}
			tessellatorIn.draw();
			bufferBuilderIn.begin(7, DefaultVertexFormats.BLOCK);
			bufferBuilderIn.noColor();
		}
	}

}
