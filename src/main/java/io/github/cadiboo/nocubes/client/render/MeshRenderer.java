package io.github.cadiboo.nocubes.client.render;

import io.github.cadiboo.nocubes.NoCubes;
import io.github.cadiboo.nocubes.client.ClientUtil;
import io.github.cadiboo.nocubes.client.LightmapInfo;
import io.github.cadiboo.nocubes.client.ModelHelper;
import io.github.cadiboo.nocubes.client.PackedLightCache;
import io.github.cadiboo.nocubes.client.optifine.OptiFineCompatibility;
import io.github.cadiboo.nocubes.config.ModConfig;
import io.github.cadiboo.nocubes.util.IIsSmoothable;
import io.github.cadiboo.nocubes.util.ModProfiler;
import io.github.cadiboo.nocubes.util.pooled.Face;
import io.github.cadiboo.nocubes.util.pooled.FaceList;
import io.github.cadiboo.nocubes.util.pooled.Vec3;
import io.github.cadiboo.nocubes.util.pooled.Vec3b;
import io.github.cadiboo.nocubes.util.pooled.cache.StateCache;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nonnull;
import javax.vecmath.Vector3d;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

import static io.github.cadiboo.nocubes.client.ClientUtil.getCorrectRenderLayer;
import static io.github.cadiboo.nocubes.config.ModConfig.smoothBiomeColorTransitions;
import static io.github.cadiboo.nocubes.util.ModUtil.LEAVES_SMOOTHABLE;
import static io.github.cadiboo.nocubes.util.ModUtil.TERRAIN_SMOOTHABLE;
import static net.minecraft.util.math.MathHelper.getPositionRandom;

/**
 * @author Cadiboo
 */
public class MeshRenderer {

	public static void renderChunkMeshes(
			@Nonnull final RenderChunk renderChunk,
			@Nonnull final ChunkCompileTaskGenerator generator,
			@Nonnull final CompiledChunk compiledChunk,
			@Nonnull final BlockPos renderChunkPosition,
			final int renderChunkPositionX, final int renderChunkPositionY, final int renderChunkPositionZ,
			@Nonnull final IBlockAccess blockAccess,
			@Nonnull final StateCache stateCache,
			@Nonnull final PooledMutableBlockPos pooledMutableBlockPos,
			@Nonnull final boolean[] usedBlockRenderLayers,
			@Nonnull final BlockRendererDispatcher blockRendererDispatcher,
			@Nonnull final PackedLightCache pooledPackedLightCache
	) {
//		try (ModProfiler ignored = NoCubes.getProfiler().start("renderChunkMeshes"))
		{
			//normal terrain
			{
//				try (ModProfiler ignored1 = NoCubes.getProfiler().start("renderNormalTerrain"))
				{
					renderMesh(
							renderChunk,
							generator,
							compiledChunk,
							renderChunkPosition,
							renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
							blockAccess,
							stateCache,
							blockRendererDispatcher,
							pooledPackedLightCache,
							NoCubes.MESH_DISPATCHER.generateChunkMeshOffset(renderChunkPosition, blockAccess, TERRAIN_SMOOTHABLE, ModConfig.terrainMeshGenerator),
							TERRAIN_SMOOTHABLE,
							pooledMutableBlockPos, usedBlockRenderLayers, false
					);
				}
			}

			switch (ModConfig.smoothLeavesLevel) {
				case SEPARATE:
					try {
						for (final IBlockState smoothableState : ModConfig.getLeavesSmoothableBlockStatesCache()) {
//							try (ModProfiler ignored2 = NoCubes.getProfiler().start("renderLeaves" + smoothableState))
							{
								final IIsSmoothable isSmoothable = (checkState) -> checkState == smoothableState;
								renderMesh(
										renderChunk,
										generator,
										compiledChunk,
										renderChunkPosition,
										renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
										blockAccess,
										stateCache,
										blockRendererDispatcher,
										pooledPackedLightCache,
										NoCubes.MESH_DISPATCHER.generateChunkMeshOffset(renderChunkPosition, blockAccess, isSmoothable, ModConfig.leavesMeshGenerator),
										isSmoothable,
										pooledMutableBlockPos, usedBlockRenderLayers, true
								);
							}
						}
					} catch (ConcurrentModificationException e) {
						//REEE I don't want to synchronise because performance tho
						e.printStackTrace();
					}
					break;
				case TOGETHER:
//					try (ModProfiler ignored2 = NoCubes.getProfiler().start("renderLeavesTogether"))
				{
					renderMesh(
							renderChunk,
							generator,
							compiledChunk,
							renderChunkPosition,
							renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ,
							blockAccess,
							stateCache,
							blockRendererDispatcher,
							pooledPackedLightCache,
							NoCubes.MESH_DISPATCHER.generateChunkMeshOffset(renderChunkPosition, blockAccess, LEAVES_SMOOTHABLE, ModConfig.leavesMeshGenerator),
							LEAVES_SMOOTHABLE,
							pooledMutableBlockPos, usedBlockRenderLayers, true
					);
				}
				break;
				case OFF:
					break;
			}
		}

	}

	private static void renderMesh(
			@Nonnull final RenderChunk renderChunk,
			@Nonnull final ChunkCompileTaskGenerator generator,
			@Nonnull final CompiledChunk compiledChunk,
			@Nonnull final BlockPos renderChunkPosition,
			final int renderChunkPositionX, final int renderChunkPositionY, final int renderChunkPositionZ,
			@Nonnull final IBlockAccess blockAccess,
			@Nonnull final StateCache stateCache,
			@Nonnull final BlockRendererDispatcher blockRendererDispatcher,
			@Nonnull final PackedLightCache pooledPackedLightCache,
			@Nonnull final Map<Vec3b, FaceList> chunkData,
			@Nonnull final IIsSmoothable isStateSmoothable,
			@Nonnull final PooledMutableBlockPos pooledMutableBlockPos,
			@Nonnull final boolean[] usedBlockRenderLayers,
			final boolean renderOppositeSides
	) {

//		try (final ModProfiler profiler = NoCubes.getProfiler().start("renderMesh"))
		{

//			final Random random = new Random();

			FOR:
			for (Map.Entry<Vec3b, FaceList> entry : chunkData.entrySet()) {
				try (final Vec3b pos = entry.getKey()) {
					try (final FaceList faces = entry.getValue()) {

						if (faces.isEmpty()) {
							continue;
						}

						NoCubes.getProfiler().start("prepareRenderFaces");

						final int initialPosX = renderChunkPositionX + pos.x;
						final int initialPosY = renderChunkPositionY + pos.y;
						final int initialPosZ = renderChunkPositionZ + pos.z;

						pooledMutableBlockPos.setPos(
								initialPosX,
								initialPosY,
								initialPosZ
						);

						//TODO use pos?
						final byte relativePosX = ClientUtil.getRelativePos(renderChunkPositionX, initialPosX);
						final byte relativePosY = ClientUtil.getRelativePos(renderChunkPositionY, initialPosY);
						final byte relativePosZ = ClientUtil.getRelativePos(renderChunkPositionZ, initialPosZ);

//					    final IBlockState realState = blockAccess.getBlockState(pooledMutableBlockPos);
						final IBlockState realState = stateCache.getStateCache()[stateCache.getIndex(relativePosX + 1, relativePosY + 1, relativePosZ + 1)];

//					    final Tuple<BlockPos, IBlockState> texturePosAndState = ClientUtil.getTexturePosAndState(stateCache, blockAccess, pooledMutableBlockPos, realState, isStateSmoothable, (byte) (relativePosX+ 1), (byte) (relativePosY+ 1), (byte) (relativePosZ+ 1));
						final Tuple<BlockPos, IBlockState> texturePosAndState = ClientUtil.getTexturePosAndState(stateCache, pooledMutableBlockPos, realState, isStateSmoothable, relativePosX, relativePosY, relativePosZ);
						final BlockPos texturePos = texturePosAndState.getFirst();
						final IBlockState textureState = texturePosAndState.getSecond();

						NoCubes.getProfiler().end();

						try {
							renderFaces(renderChunk, generator, compiledChunk, renderChunkPosition, renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ, blockAccess, blockRendererDispatcher, pooledPackedLightCache, usedBlockRenderLayers, renderOppositeSides, pos, faces, texturePos, textureState);
						} catch (Exception e) {
							final CrashReport crashReport = new CrashReport("Rendering faces for smooth block in world", e);

							CrashReportCategory realBlockCrashReportCategory = crashReport.makeCategory("Block being rendered");
							final BlockPos blockPos = new BlockPos(renderChunkPositionX + pos.x, renderChunkPositionX + pos.y, renderChunkPositionX + pos.z);
							CrashReportCategory.addBlockInfo(realBlockCrashReportCategory, blockPos, realState);

							CrashReportCategory textureBlockCrashReportCategory = crashReport.makeCategory("TextureBlock of Block being rendered");
							CrashReportCategory.addBlockInfo(textureBlockCrashReportCategory, texturePos, textureState);

							throw new ReportedException(crashReport);
						}
					}
				}

			}

		}

	}

	public static void renderFaces(
			@Nonnull final RenderChunk renderChunk,
			@Nonnull final ChunkCompileTaskGenerator generator,
			@Nonnull final CompiledChunk compiledChunk,
			@Nonnull final BlockPos renderChunkPosition,
			final int renderChunkPositionX, final int renderChunkPositionY, final int renderChunkPositionZ,
			@Nonnull final IBlockAccess blockAccess,
			@Nonnull final BlockRendererDispatcher blockRendererDispatcher,
			@Nonnull final PackedLightCache pooledPackedLightCache,
			@Nonnull final boolean[] usedBlockRenderLayers,
			final boolean renderOppositeSides,
			@Nonnull final Vec3b pos,
			@Nonnull final FaceList faces,
			@Nonnull final BlockPos texturePos,
			@Nonnull final IBlockState textureState
	) {

//		try (final ModProfiler ignored = NoCubes.getProfiler().start("renderFaces"))
		{

			//TODO: use Event?
			final BlockRenderLayer blockRenderLayer = getCorrectRenderLayer(textureState);
			final int blockRenderLayerOrdinal = blockRenderLayer.ordinal();
			final BufferBuilder bufferBuilder = ClientUtil.startOrContinueBufferBuilder(generator, blockRenderLayerOrdinal, compiledChunk, blockRenderLayer, renderChunk, renderChunkPosition);
			usedBlockRenderLayers[blockRenderLayerOrdinal] = true;

//			NoCubes.getProfiler().start("getQuad");
//			BakedQuad quad = ClientUtil.getQuadFromFacingsOrdered(textureState.getActualState(blockAccess, texturePos), texturePos, blockRendererDispatcher);
			BakedQuad quad = ModelHelper.getQuad(textureState, texturePos, bufferBuilder, blockAccess, blockRendererDispatcher, blockRenderLayer);
			if (quad == null) {
				NoCubes.NO_CUBES_LOG.warn("Got null quad for " + textureState.getBlock() + " at " + texturePos);
				quad = blockRendererDispatcher.getBlockModelShapes().getModelManager().getMissingModel().getQuads(null, EnumFacing.DOWN, 0L).get(0);
			}
//			NoCubes.getProfiler().end();

			// Quads are packed xyz|argb|u|v|ts
			NoCubes.getProfiler().start("getUVs");

			final int formatSize = quad.getFormat().getIntegerSize();

			final float v0u = Float.intBitsToFloat(quad.getVertexData()[4]);
			final float v0v = Float.intBitsToFloat(quad.getVertexData()[5]);
			final float v1u = Float.intBitsToFloat(quad.getVertexData()[formatSize + 4]);
			final float v1v = Float.intBitsToFloat(quad.getVertexData()[formatSize + 5]);
			final float v2u = Float.intBitsToFloat(quad.getVertexData()[formatSize * 2 + 4]);
			final float v2v = Float.intBitsToFloat(quad.getVertexData()[formatSize * 2 + 5]);
			final float v3u = Float.intBitsToFloat(quad.getVertexData()[formatSize * 3 + 4]);
			final float v3v = Float.intBitsToFloat(quad.getVertexData()[formatSize * 3 + 5]);
			NoCubes.getProfiler().end();

			final int color0 = ClientUtil.getColor(quad, textureState, blockAccess, texturePos.south().east());
			final float alpha = 1F;

			final float red0 = ((color0 >> 16) & 255) / 255F;
			final float green0 = ((color0 >> 8) & 255) / 255F;
			final float blue0 = ((color0) & 255) / 255F;
			final float red1;
			final float green1;
			final float blue1;
			final float red2;
			final float green2;
			final float blue2;
			final float red3;
			final float green3;
			final float blue3;
			if (smoothBiomeColorTransitions) {
				final int color1 = ClientUtil.getColor(quad, textureState, blockAccess, texturePos.east());
				red1 = ((color1 >> 16) & 255) / 255F;
				green1 = ((color1 >> 8) & 255) / 255F;
				blue1 = ((color1) & 255) / 255F;
				final int color2 = ClientUtil.getColor(quad, textureState, blockAccess, texturePos);
				red2 = ((color2 >> 16) & 255) / 255F;
				green2 = ((color2 >> 8) & 255) / 255F;
				blue2 = ((color2) & 255) / 255F;
				final int color3 = ClientUtil.getColor(quad, textureState, blockAccess, texturePos.south());
				red3 = ((color3 >> 16) & 255) / 255F;
				green3 = ((color3 >> 8) & 255) / 255F;
				blue3 = ((color3) & 255) / 255F;
			} else {
				red1 = red0;
				green1 = green0;
				blue1 = blue0;
				red2 = red0;
				green2 = green0;
				blue2 = blue0;
				red3 = red0;
				green3 = green0;
				blue3 = blue0;
			}

			if (ModConfig.shortGrassEnabled) {
				renderShortGrass(blockAccess, blockRendererDispatcher, faces, texturePos, textureState, bufferBuilder, formatSize, red0, green0, blue0);
			}

			OptiFineCompatibility.pushShaderThing(textureState, texturePos, blockAccess, bufferBuilder);

			for (final Face face : faces) {
				try {
					//0 3
					//1 2
					//south east when looking down onto up face
					final Vec3 v0 = face.getVertex0();
					//north east when looking down onto up face
					final Vec3 v1 = face.getVertex1();
					//north west when looking down onto up face
					final Vec3 v2 = face.getVertex2();
					//south west when looking down onto up face
					final Vec3 v3 = face.getVertex3();

					float diffuse0;
					float diffuse1;
					float diffuse2;
					float diffuse3;
					if (!ModConfig.applyDiffuseLighting || !quad.shouldApplyDiffuseLighting()) {
						diffuse0 = diffuse1 = diffuse2 = diffuse3 = 1;
					} else {
						diffuse0 = diffuseLight(toSide(
								v0.x - renderChunkPositionX - pos.x,
								v0.y - renderChunkPositionY - pos.y,
								v0.z - renderChunkPositionZ - pos.z
						));
						diffuse1 = diffuseLight(toSide(
								v1.x - renderChunkPositionX - pos.x,
								v1.y - renderChunkPositionY - pos.y,
								v1.z - renderChunkPositionZ - pos.z
						));
						diffuse2 = diffuseLight(toSide(
								v2.x - renderChunkPositionX - pos.x,
								v2.y - renderChunkPositionY - pos.y,
								v2.z - renderChunkPositionZ - pos.z
						));
						diffuse3 = diffuseLight(toSide(
								v3.x - renderChunkPositionX - pos.x,
								v3.y - renderChunkPositionY - pos.y,
								v3.z - renderChunkPositionZ - pos.z
						));

					}

					final int lightmapSkyLight0;
					final int lightmapSkyLight1;
					final int lightmapSkyLight2;
					final int lightmapSkyLight3;

					final int lightmapBlockLight0;
					final int lightmapBlockLight1;
					final int lightmapBlockLight2;
					final int lightmapBlockLight3;

					if (ModConfig.approximateLighting) {
						try (final LightmapInfo lightmapInfo = LightmapInfo.generateLightmapInfo(pooledPackedLightCache, v0, v1, v2, v3, renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ)) {

							lightmapSkyLight0 = lightmapInfo.skylight0;
							lightmapSkyLight1 = lightmapInfo.skylight1;
							lightmapSkyLight2 = lightmapInfo.skylight2;
							lightmapSkyLight3 = lightmapInfo.skylight3;

							lightmapBlockLight0 = lightmapInfo.blocklight0;
							lightmapBlockLight1 = lightmapInfo.blocklight1;
							lightmapBlockLight2 = lightmapInfo.blocklight2;
							lightmapBlockLight3 = lightmapInfo.blocklight3;

						}
					} else {
						lightmapSkyLight0 = lightmapSkyLight1 = lightmapSkyLight2 = lightmapSkyLight3 = 240;
						lightmapBlockLight0 = lightmapBlockLight1 = lightmapBlockLight2 = lightmapBlockLight3 = 0;
					}

					try {
						try (final ModProfiler ignored1 = NoCubes.getProfiler().start("renderSide")) {
							// TODO use raw puts?
							bufferBuilder.pos(v0.x, v0.y, v0.z).color(red0 * diffuse0, green0 * diffuse0, blue0 * diffuse0, alpha).tex(v0u, v0v).lightmap(lightmapSkyLight0, lightmapBlockLight0).endVertex();
							bufferBuilder.pos(v1.x, v1.y, v1.z).color(red1 * diffuse1, green1 * diffuse1, blue1 * diffuse1, alpha).tex(v1u, v1v).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
							bufferBuilder.pos(v2.x, v2.y, v2.z).color(red2 * diffuse2, green2 * diffuse2, blue2 * diffuse2, alpha).tex(v2u, v2v).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
							bufferBuilder.pos(v3.x, v3.y, v3.z).color(red3 * diffuse3, green3 * diffuse3, blue3 * diffuse3, alpha).tex(v3u, v3v).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();
						}
						if (renderOppositeSides) {
							// TODO use raw puts?
							try (final ModProfiler ignored1 = NoCubes.getProfiler().start("renderOppositeSide")) {
								bufferBuilder.pos(v3.x, v3.y, v3.z).color(red3 * diffuse3, green3 * diffuse3, blue3 * diffuse3, alpha).tex(v0u, v0v).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();
								bufferBuilder.pos(v2.x, v2.y, v2.z).color(red2 * diffuse2, green2 * diffuse2, blue2 * diffuse2, alpha).tex(v1u, v1v).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
								bufferBuilder.pos(v1.x, v1.y, v1.z).color(red1 * diffuse1, green1 * diffuse1, blue1 * diffuse1, alpha).tex(v2u, v2v).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
								bufferBuilder.pos(v0.x, v0.y, v0.z).color(red0 * diffuse0, green0 * diffuse0, blue0 * diffuse0, alpha).tex(v3u, v3v).lightmap(lightmapSkyLight0, lightmapBlockLight0).endVertex();
							}
						}
					} finally {
						v0.close();
						v1.close();
						v2.close();
						v3.close();
					}
				} finally {
					face.close();
				}

			}

			OptiFineCompatibility.popShaderThing(bufferBuilder);
		}
	}

	public static void renderShortGrass(@Nonnull final IBlockAccess blockAccess, @Nonnull final BlockRendererDispatcher blockRendererDispatcher, @Nonnull final FaceList faces, @Nonnull final BlockPos texturePos, @Nonnull final IBlockState textureState, final BufferBuilder bufferBuilder, final int formatSize, final float red0, final float green0, final float blue0) {
		if (textureState != Blocks.GRASS.getDefaultState()) {
			return;
		}

		final IBlockState state = Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS);
		final IBlockState blockStateUp = blockAccess.getBlockState(texturePos.up());
		if (blockStateUp == state) {
			return;
		}
		if (blockStateUp.isOpaqueCube()) {
			return;
		}

		final Face face = faces.get(0);

		//0 3
		//1 2
		//south east when looking down onto up face
		final Vec3 v0 = face.getVertex0();
		//north east when looking down onto up face
		final Vec3 v1 = face.getVertex1();
		//north west when looking down onto up face
		final Vec3 v2 = face.getVertex2();
		//south west when looking down onto up face
		final Vec3 v3 = face.getVertex3();

//			    final Vec3d v0Normal = CrossProduct((v1-v0), (v2-v0));
		final Vector3d v1mv0 = new Vector3d(v1.x, v1.y, v1.z);
		v1mv0.sub(new Vector3d(v0.x, v0.y, v0.z));
		final Vector3d v2mv0 = new Vector3d(v2.x, v2.y, v2.z);
		v2mv0.sub(new Vector3d(v0.x, v0.y, v0.z));
		final Vector3d v0Normal = new Vector3d();
		v0Normal.cross((v1mv0), (v2mv0));

		if ((v0Normal.x < 0.1 && v0Normal.x > -0.1) && (v0Normal.z < 0.1 && v0Normal.z > -0.1)) {

			final BlockPos pos1 = texturePos.up();

			final int lightmapSkyLight0;
			final int lightmapSkyLight1;
			final int lightmapSkyLight2;
			final int lightmapSkyLight3;

			final int lightmapBlockLight0;
			final int lightmapBlockLight1;
			final int lightmapBlockLight2;
			final int lightmapBlockLight3;

//					if (ModConfig.approximateLighting) {
//						try (final LightmapInfo lightmapInfo = LightmapInfo.generateLightmapInfo(pooledPackedLightCache, v0, v1, v2, v3, renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ)) {
//
//							lightmapSkyLight0 = lightmapInfo.skylight0;
//							lightmapSkyLight1 = lightmapInfo.skylight1;
//							lightmapSkyLight2 = lightmapInfo.skylight2;
//							lightmapSkyLight3 = lightmapInfo.skylight3;
//
//							lightmapBlockLight0 = lightmapInfo.blocklight0;
//							lightmapBlockLight1 = lightmapInfo.blocklight1;
//							lightmapBlockLight2 = lightmapInfo.blocklight2;
//							lightmapBlockLight3 = lightmapInfo.blocklight3;
//
//						}
//					} else
			{
				lightmapSkyLight0 = lightmapSkyLight1 = lightmapSkyLight2 = lightmapSkyLight3 = 240;
//						lightmapBlockLight0 = lightmapBlockLight1 = lightmapBlockLight2 = lightmapBlockLight3 = 0;
				lightmapBlockLight0 = lightmapBlockLight1 = lightmapBlockLight2 = lightmapBlockLight3 = 240;
			}

//					final Vec3d offset = state.getOffset(blockAccess, pos1);
			final Vec3d offset = Vec3d.ZERO;
			final int posY = pos1.getY();

			OptiFineCompatibility.pushShaderThing(state, pos1, blockAccess, bufferBuilder);
			{

				@Nonnull
				IBakedModel model = ModelHelper.getModel(state, blockRendererDispatcher);

				final long posRand = getPositionRandom(pos1);

				for (EnumFacing facing : ModelHelper.ENUMFACING_QUADS_ORDERED) {
					List<BakedQuad> quads = model.getQuads(state, facing, posRand);
					if (quads.isEmpty()) {
						continue;
					}

					for (BakedQuad quad2 : quads) {
						// Quads are packed xyz|argb|u|v|ts
						NoCubes.getProfiler().start("getUVs");

						final int qformatSize = quad2.getFormat().getIntegerSize();

						final int[] vertexData = quad2.getVertexData();
						final float qv0u = Float.intBitsToFloat(vertexData[4]);
						final float qv0v = Float.intBitsToFloat(vertexData[5]);
						final float qv1u = Float.intBitsToFloat(vertexData[formatSize + 4]);
						final float qv1v = Float.intBitsToFloat(vertexData[formatSize + 5]);
						final float qv2u = Float.intBitsToFloat(vertexData[formatSize * 2 + 4]);
						final float qv2v = Float.intBitsToFloat(vertexData[formatSize * 2 + 5]);
						final float qv3u = Float.intBitsToFloat(vertexData[formatSize * 3 + 4]);
						final float qv3v = Float.intBitsToFloat(vertexData[formatSize * 3 + 5]);
						NoCubes.getProfiler().end();

						final int qcolor0 = ClientUtil.getColor(quad2, textureState, blockAccess, pos1.south().east());
						final float qalpha = 1F;

						final float qred0 = ((qcolor0 >> 16) & 255) / 255F;
						final float qgreen0 = ((qcolor0 >> 8) & 255) / 255F;
						final float qblue0 = ((qcolor0) & 255) / 255F;
						final float qred1;
						final float qgreen1;
						final float qblue1;
						final float qred2;
						final float qgreen2;
						final float qblue2;
						final float qred3;
						final float qgreen3;
						final float qblue3;
						if (smoothBiomeColorTransitions) {
							final int color1 = ClientUtil.getColor(quad2, textureState, blockAccess, pos1.east());
							qred1 = ((color1 >> 16) & 255) / 255F;
							qgreen1 = ((color1 >> 8) & 255) / 255F;
							qblue1 = ((color1) & 255) / 255F;
							final int color2 = ClientUtil.getColor(quad2, textureState, blockAccess, pos1);
							qred2 = ((color2 >> 16) & 255) / 255F;
							qgreen2 = ((color2 >> 8) & 255) / 255F;
							qblue2 = ((color2) & 255) / 255F;
							final int color3 = ClientUtil.getColor(quad2, textureState, blockAccess, pos1.south());
							qred3 = ((color3 >> 16) & 255) / 255F;
							qgreen3 = ((color3 >> 8) & 255) / 255F;
							qblue3 = ((color3) & 255) / 255F;
						} else {
							qred1 = red0;
							qgreen1 = green0;
							qblue1 = blue0;
							qred2 = red0;
							qgreen2 = green0;
							qblue2 = blue0;
							qred3 = red0;
							qgreen3 = green0;
							qblue3 = blue0;
						}

						final float qv0x = Float.intBitsToFloat(vertexData[0]);
						final float qv0y = Float.intBitsToFloat(vertexData[1]);
						final float qv0z = Float.intBitsToFloat(vertexData[2]);
						final float qv1x = Float.intBitsToFloat(vertexData[qformatSize]);
						final float qv1y = Float.intBitsToFloat(vertexData[qformatSize + 1]);
						final float qv1z = Float.intBitsToFloat(vertexData[qformatSize + 2]);
						final float qv2x = Float.intBitsToFloat(vertexData[qformatSize + qformatSize]);
						final float qv2y = Float.intBitsToFloat(vertexData[qformatSize + qformatSize + 1]);
						final float qv2z = Float.intBitsToFloat(vertexData[qformatSize + qformatSize + 2]);
						final float qv3x = Float.intBitsToFloat(vertexData[qformatSize + qformatSize + qformatSize]);
						final float qv3y = Float.intBitsToFloat(vertexData[qformatSize + qformatSize + qformatSize + 1]);
						final float qv3z = Float.intBitsToFloat(vertexData[qformatSize + qformatSize + qformatSize + 2]);

//						if (qv0 < 0.5F && ) {
//
//						}

						bufferBuilder.pos(v2.x + qv0x, v0.y + 1, v2.z + qv0z).color(qred0, qgreen0, qblue0, qalpha).tex(qv0u, qv0v).lightmap(lightmapSkyLight0, lightmapBlockLight0).endVertex();
						bufferBuilder.pos(v2.x + qv1x, v1.y + 0, v2.z + qv1z).color(qred0, qgreen0, qblue0, qalpha).tex(qv1u, qv1v).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
						bufferBuilder.pos(v2.x + qv2x, v2.y + 0, v2.z + qv2z).color(qred0, qgreen0, qblue0, qalpha).tex(qv2u, qv2v).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
						bufferBuilder.pos(v2.x + qv3x, v3.y + 1, v2.z + qv3z).color(qred0, qgreen0, qblue0, qalpha).tex(qv3u, qv3v).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();
//						bufferBuilder.pos(v1.x, v1.y, v1.z).color(qred1, qgreen1, qblue1, qalpha).tex(qv1u, qv1v).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
//						bufferBuilder.pos(v2.x, v2.y, v2.z).color(qred2, qgreen2, qblue2, qalpha).tex(qv2u, qv2v).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
//						bufferBuilder.pos(v3.x, v3.y, v3.z).color(qred3, qgreen3, qblue3, qalpha).tex(qv3u, qv3v).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();

//						bufferBuilder.pos(offset.x + v0.x - 1 + Float.intBitsToFloat(quad2.getVertexData()[0]), offset.y + v0.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1]), 0.35), offset.z + v0.z - 1 + Float.intBitsToFloat(quad2.getVertexData()[2])).color(qred0, qgreen0, qblue0, qalpha).tex(qv0u, qv0v).lightmap(lightmapSkyLight0, lightmapBlockLight0).endVertex();
//						bufferBuilder.pos(offset.x + v2.x + Float.intBitsToFloat(quad2.getVertexData()[qformatSize]), offset.y + v2.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1 + qformatSize]), 0.35), offset.z + v2.z + Float.intBitsToFloat(quad2.getVertexData()[2 + qformatSize])).color(qred1, qgreen1, qblue1, qalpha).tex(qv1u, qv1v).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
//						bufferBuilder.pos(offset.x + v2.x + Float.intBitsToFloat(quad2.getVertexData()[qformatSize << 1]), offset.y + v2.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1 + (qformatSize << 1)]), 0.35), offset.z + v2.z + Float.intBitsToFloat(quad2.getVertexData()[2 + (qformatSize << 1)])).color(qred2, qgreen2, qblue2, qalpha).tex(qv2u, qv2v).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
//						bufferBuilder.pos(offset.x + v2.x + Float.intBitsToFloat(quad2.getVertexData()[(qformatSize << 1) + qformatSize]), offset.y + v2.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1 + (qformatSize << 1) + qformatSize]), 0.35), offset.z + v2.z + Float.intBitsToFloat(quad2.getVertexData()[2 + (qformatSize << 1) + qformatSize])).color(qred3, qgreen3, qblue3, qalpha).tex(qv3u, qv3v).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();
//
//						bufferBuilder.pos(offset.x + v0.x + Float.intBitsToFloat(quad2.getVertexData()[0]), offset.y + v0.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1]), 0.4), offset.z + v0.z + Float.intBitsToFloat(quad2.getVertexData()[2])).color(qred0, qgreen0, qblue0, qalpha).tex(qv0u, qv0v).lightmap(lightmapSkyLight0, lightmapBlockLight0).endVertex();
//						bufferBuilder.pos(offset.x + v1.x + Float.intBitsToFloat(quad2.getVertexData()[qformatSize]), offset.y + v1.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1 + qformatSize]), 0.4), offset.z + v1.z + Float.intBitsToFloat(quad2.getVertexData()[2 + qformatSize])).color(qred1, qgreen1, qblue1, qalpha).tex(qv1u, qv1v).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
//						bufferBuilder.pos(offset.x + v2.x + Float.intBitsToFloat(quad2.getVertexData()[qformatSize << 1]), offset.y + v2.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1 + (qformatSize << 1)]), 0.4), offset.z + v2.z + Float.intBitsToFloat(quad2.getVertexData()[2 + (qformatSize << 1)])).color(qred2, qgreen2, qblue2, qalpha).tex(qv2u, qv2v).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
//						bufferBuilder.pos(offset.x + v3.x + Float.intBitsToFloat(quad2.getVertexData()[(qformatSize << 1) + qformatSize]), offset.y + v3.y + Math.min(Float.intBitsToFloat(quad2.getVertexData()[1 + (qformatSize << 1) + qformatSize]), 0.4), offset.z + v3.z + Float.intBitsToFloat(quad2.getVertexData()[2 + (qformatSize << 1) + qformatSize])).color(qred3, qgreen3, qblue3, qalpha).tex(qv3u, qv3v).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();

					}
				}

//					ModelHelper.
//					blockRendererDispatcher.renderBlock(Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS), texturePos.up(), blockAccess, bufferBuilder);
			}
			OptiFineCompatibility.popShaderThing(bufferBuilder);

		}
	}

	private static EnumFacing toSide(final double x, final double y, final double z) {
		if (((x <= 0) ? -x : x) > ((y <= 0) ? -y : y)) {
			if (((x <= 0) ? -x : x) > ((z <= 0) ? -z : z)) {
				if (x < 0) return EnumFacing.WEST;
				return EnumFacing.EAST;
			} else {
				if (z < 0) return EnumFacing.NORTH;
				return EnumFacing.SOUTH;
			}
		} else {
			if (((y <= 0) ? -y : y) > ((z <= 0) ? -z : z)) {
				if (y < 0) return EnumFacing.DOWN;
				return EnumFacing.UP;
			} else {
				if (z < 0) return EnumFacing.NORTH;
				return EnumFacing.SOUTH;
			}
		}
	}

	private static float diffuseLight(final EnumFacing side) {
		if (side == EnumFacing.UP) {
			return 1f;
		} else {
			return .97f;
		}
	}

	private static double map(final double input, final double inputStart, final double inputEnd, final double outputStart, final double outputEnd) {
		return (((input - inputStart) * (outputEnd - outputStart)) / (inputEnd - inputStart)) + outputStart;
	}

}
