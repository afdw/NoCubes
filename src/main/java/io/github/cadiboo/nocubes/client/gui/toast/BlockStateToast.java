package io.github.cadiboo.nocubes.client.gui.toast;

import io.github.cadiboo.nocubes.client.optifine.OptiFineCompatibility;
import io.github.cadiboo.nocubes.client.render.BufferBuilderCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.model.TRSRTransformation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static io.github.cadiboo.nocubes.util.ModReference.MOD_ID;

/**
 * @author Cadiboo
 */
public abstract class BlockStateToast implements IToast {

	// These buffers are large enough for an entire chunk, consider using smaller buffers
	@Nonnull
	private final BufferBuilderCache bufferCache = new BufferBuilderCache(0x200, 0x200, 0x200, 0x200);
	@Nonnull
	private final boolean[] usedBlockRenderLayers = new boolean[BlockRenderLayer.values().length];
	@Nonnull
	private final boolean[] startedBufferBuilders = new boolean[BlockRenderLayer.values().length];
	@Nonnull
	private final String name;

	BlockStateToast(@Nonnull final IBlockState state, @Nonnull final BlockPos pos, @Nonnull final RayTraceResult result) {
		final Minecraft minecraft = Minecraft.getMinecraft();
		name = state.getBlock().getPickBlock(state, result, minecraft.world, pos, minecraft.player).getDisplayName();

		// Reset values
		Arrays.fill(usedBlockRenderLayers, false);
		Arrays.fill(startedBufferBuilders, false);

		final IBlockAccess blockAccess = minecraft.world;
		final BlockRendererDispatcher blockRendererDispatcher = minecraft.getBlockRendererDispatcher();

		{
			for (BlockRenderLayer blockRenderLayer : BlockRenderLayer.values()) {
				if (!state.getBlock().canRenderInLayer(state, blockRenderLayer)) {
					continue;
				}
				ForgeHooksClient.setRenderLayer(blockRenderLayer);
				final int blockRenderLayerId = blockRenderLayer.ordinal();
				final BufferBuilder bufferBuilder = bufferCache.get(blockRenderLayerId);
				if (!startedBufferBuilders[blockRenderLayerId]) {
					startedBufferBuilders[blockRenderLayerId] = true;
					// Copied from RenderChunk
					{
						bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
						bufferBuilder.setTranslation((-pos.getX()), (-pos.getY()), (-pos.getZ()));
					}
				}
				// OptiFine Shaders compatibility
				OptiFineCompatibility.pushShaderThing(state, pos, blockAccess, bufferBuilder);
				usedBlockRenderLayers[blockRenderLayerId] |= blockRendererDispatcher.renderBlock(state, pos, blockAccess, bufferBuilder);
				OptiFineCompatibility.popShaderThing(bufferBuilder);
			}
			ForgeHooksClient.setRenderLayer(null);
		}

		// finishDrawing
		for (int blockRenderLayerId = 0; blockRenderLayerId < usedBlockRenderLayers.length; blockRenderLayerId++) {
			if (!startedBufferBuilders[blockRenderLayerId]) {
				continue;
			}
			bufferCache.get(blockRenderLayerId).finishDrawing();
		}

	}

	// Copied from the Tessellator's vboUploader - Draw everything but don't reset the buffer
	private static void drawBuffer(final BufferBuilder bufferBuilderIn) {
		if (bufferBuilderIn.getVertexCount() > 0) {
			VertexFormat vertexformat = bufferBuilderIn.getVertexFormat();
			int i = vertexformat.getSize();
			ByteBuffer bytebuffer = bufferBuilderIn.getByteBuffer();
			List<VertexFormatElement> list = vertexformat.getElements();

			for (int j = 0; j < list.size(); ++j) {
				VertexFormatElement vertexformatelement = list.get(j);
//				VertexFormatElement.EnumUsage vertexformatelement$enumusage = vertexformatelement.getUsage();
//				int k = vertexformatelement.getType().getGlConstant();
//				int l = vertexformatelement.getIndex();
				bytebuffer.position(vertexformat.getOffset(j));

				// moved to VertexFormatElement.preDraw
				vertexformatelement.getUsage().preDraw(vertexformat, j, i, bytebuffer);
			}

			GlStateManager.glDrawArrays(bufferBuilderIn.getDrawMode(), 0, bufferBuilderIn.getVertexCount());
			int i1 = 0;

			for (int j1 = list.size(); i1 < j1; ++i1) {
				VertexFormatElement vertexformatelement1 = list.get(i1);
//				VertexFormatElement.EnumUsage vertexformatelement$enumusage1 = vertexformatelement1.getUsage();
//				int k1 = vertexformatelement1.getIndex();

				// moved to VertexFormatElement.postDraw
				vertexformatelement1.getUsage().postDraw(vertexformat, i1, i, bytebuffer);
			}
		}

		//do not reset buffer
//		bufferBuilderIn.reset();
	}

	public abstract String getUpdateType();

	@Nonnull
	@Override
	public Visibility draw(@Nonnull final GuiToast toastGui, final long delta) {
		final Minecraft minecraft = toastGui.getMinecraft();
		minecraft.getTextureManager().bindTexture(TEXTURE_TOASTS);
		GlStateManager.color(1.0F, 1.0F, 1.0F);
		toastGui.drawTexturedModalRect(0, 0, 0, 0, 160, 32);

		minecraft.fontRenderer.drawString(I18n.format(getUpdateType()) + ":", 30, 7, 0xFFFFFFFF);
		minecraft.fontRenderer.drawString(name, 30, 18, 0xFFFFFFFF);

		// Code to draw the buffer
		RENDER:
		{
			final Entity entity = minecraft.getRenderViewEntity();
			if (entity == null) {
				break RENDER;
			}
			{
				minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
				minecraft.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);

				GlStateManager.pushMatrix();
				GlStateManager.translate(0, 0, 100);
				GlStateManager.enableRescaleNormal();
				GlStateManager.enableAlpha();
				GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
				GlStateManager.enableBlend();
				GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
				GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
				{
					GlStateManager.translate(7, 21, 0);
					GlStateManager.scale(-1, -1, 1);
					GlStateManager.scale(20, 20, 20);
				}
				{
					GlStateManager.disableLighting();
					OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
					GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
				}
				{
					ForgeHooksClient.multiplyCurrentGlMatrix(
							new TRSRTransformation(
									new ItemTransformVec3f(new Vector3f(-30, 225, 0), new Vector3f(), new Vector3f(0.625F, 0.625F, 0.625F))
							).getMatrix()
					);
				}
			}
			for (int blockRenderLayerId = 0; blockRenderLayerId < usedBlockRenderLayers.length; blockRenderLayerId++) {
				if (!usedBlockRenderLayers[blockRenderLayerId]) {
					continue;
				}
				drawBuffer(bufferCache.get(blockRenderLayerId));
			}
			{
				GlStateManager.disableAlpha();
				GlStateManager.disableRescaleNormal();
				GlStateManager.popMatrix();

				minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
				minecraft.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
			}
		}

		return delta >= 10000L ? Visibility.HIDE : Visibility.SHOW;
	}

	public static class Add extends BlockStateToast {

		public Add(@Nonnull final IBlockState state, @Nonnull final BlockPos pos, final RayTraceResult result) {
			super(state, pos, result);
		}

		@Override
		public String getUpdateType() {
			return MOD_ID + ".addedSmoothableBlockState";
		}

	}

	public static class Remove extends BlockStateToast {

		public Remove(@Nonnull final IBlockState state, @Nonnull final BlockPos pos, final RayTraceResult result) {
			super(state, pos, result);
		}

		@Override
		public String getUpdateType() {
			return MOD_ID + ".removedSmoothableBlockState";
		}

	}

}
