package io.github.cadiboo.nocubes.util;

import io.github.cadiboo.nocubes.NoCubes;
import io.github.cadiboo.nocubes.mesh.MeshGenerator;
import io.github.cadiboo.nocubes.util.pooled.Vec3;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

import javax.annotation.Nonnull;
import java.util.Random;

import static net.minecraft.init.Blocks.BEDROCK;
import static net.minecraft.init.Blocks.SNOW_LAYER;

/**
 * Util that is used on BOTH physical sides
 *
 * @author Cadiboo
 */
@SuppressWarnings("WeakerAccess")
public final class ModUtil {

	private static final Random RANDOM = new Random();

	/**
	 * @return negative density if the block is smoothable (inside the isosurface), positive if it isn't
	 */
	public static float getIndividualBlockDensity(final boolean shouldSmooth, final IBlockState state) {
		if (shouldSmooth) {
			if (state.getBlock() == SNOW_LAYER) {
				final int value = state.getValue(BlockSnow.LAYERS);
				if (value == 1) { // zero-height snow layer
					return 1;
				} else { // snow height between 0-8 to between -0.25F and -1
					return -((value - 1) * 0.125F);
				}
			} else {
				return state.getBlock() == BEDROCK ? -1.0005F : -1;
			}
		} else if (state.isNormalCube() || state.isBlockNormalCube()) {
			return 0F;
		} else {
			return 1;
		}
	}

	/**
	 * Give the vec3 some (pseudo) random offset based on its location.
	 * This code is from {link MathHelper#getCoordinateRandom} and Block#getOffset
	 *
	 * @param vec3 the vec3
	 */
	public static Vec3 offsetVertex(Vec3 vec3) {
		long rand = (long) (vec3.x * 3129871.0D) ^ (long) vec3.z * 116129781L ^ (long) vec3.y;
		rand = rand * rand * 42317861L + rand * 11;
		vec3.x += ((double) ((float) (rand >> 16 & 15L) / 15.0F) - 0.5D) * 0.5D;
		vec3.y += ((double) ((float) (rand >> 20 & 15L) / 15.0F) - 1.0D) * 0.2D;
		vec3.z += ((double) ((float) (rand >> 24 & 15L) / 15.0F) - 0.5D) * 0.5D;
		return vec3;
	}

	/**
	 * Ew
	 *
	 * @param modContainer the {@link ModContainer} for {@link NoCubes}
	 */
	public static void launchUpdateDaemon(@Nonnull final ModContainer modContainer) {

		new Thread(() -> {
			while (true) {

				final ForgeVersion.CheckResult checkResult = ForgeVersion.getResult(modContainer);
				switch (checkResult.status) {
					default:
					case PENDING:
						try {
							Thread.sleep(500L);
						} catch (InterruptedException var4) {
							Thread.currentThread().interrupt();
						}
						break;
					case OUTDATED:
						try {
							BadAutoUpdater.update(modContainer, checkResult.target.toString(), "Cadiboo");
						} catch (Exception var3) {
							throw new RuntimeException(var3);
						}
					case FAILED:
					case UP_TO_DATE:
					case AHEAD:
					case BETA:
					case BETA_OUTDATED:
						return;
				}
			}

		}, modContainer.getName() + " Update Daemon").start();

	}

	public static boolean isDeveloperWorkspace() {
		return FMLLaunchHandler.isDeobfuscatedEnvironment();
	}

	/**
	 * We add 1 because idk (it fixes seams in between chunks)
	 * and then surface nets needs another +1 because reasons
	 */
	public static byte getMeshSizeX(final int initialSize, final MeshGenerator meshGenerator) {
		return (byte) (initialSize + meshGenerator.getSizeXExtension());
	}

	/**
	 * We add 1 because idk (it fixes seams in between chunks)
	 * and then surface nets needs another +1 because reasons
	 */
	public static byte getMeshSizeY(final int initialSize, final MeshGenerator meshGenerator) {
		return (byte) (initialSize + meshGenerator.getSizeYExtension());
	}

	/**
	 * We add 1 because idk (it fixes seams in between chunks)
	 * and then surface nets needs another +1 because reasons
	 */
	public static byte getMeshSizeZ(final int initialSize, final MeshGenerator meshGenerator) {
		return (byte) (initialSize + meshGenerator.getSizeZExtension());
	}

}
