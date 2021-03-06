package io.github.cadiboo.nocubes.util;

import io.github.cadiboo.nocubes.NoCubes;
import io.github.cadiboo.nocubes.client.ExtendLiquidRange;
import io.github.cadiboo.nocubes.config.ModConfig;
import io.github.cadiboo.nocubes.util.pooled.Vec3;
import io.github.cadiboo.nocubes.util.reflect.ReflectionUtil;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;

import static io.github.cadiboo.nocubes.NoCubes.NO_CUBES_LOG;
import static io.github.cadiboo.nocubes.util.ModReference.CONFIG_VERSION;
import static io.github.cadiboo.nocubes.util.ModReference.MOD_ID;
import static io.github.cadiboo.nocubes.util.ModReference.MOD_NAME;
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

	private static final Field configuration_definedConfigVersion = ReflectionUtil.getFieldOrCrash(Configuration.class, "definedConfigVersion");

	private static final Field configManager_CONFIGS = ReflectionUtil.getFieldOrCrash(ConfigManager.class, "CONFIGS");

	public static final IIsSmoothable TERRAIN_SMOOTHABLE = ModUtil::shouldSmoothTerrain;
	public static final IIsSmoothable LEAVES_SMOOTHABLE = ModUtil::shouldSmoothLeaves;

	/**
	 * If the state should be smoothed
	 *
	 * @param state the state
	 * @return If the state should be smoothed
	 */
	public static boolean shouldSmoothTerrain(final IBlockState state) {
		return ModConfig.getTerrainSmoothableBlockStatesCache().contains(state);
	}

	/**
	 * If the state should be smoothed
	 *
	 * @param state the state
	 * @return If the state should be smoothed
	 */
	public static boolean shouldSmoothLeaves(final IBlockState state) {
		return ModConfig.getLeavesSmoothableBlockStatesCache().contains(state);
	}

	/**
	 * @return negative density if the block is smoothable (inside the isosurface), positive if it isn't
	 */
	public static float getIndividualBlockDensity(final boolean shouldSmooth, final IBlockState state, final IBlockAccess cache, final BlockPos pos) {
		if (state.getBlock() == SNOW_LAYER && shouldSmooth) {
			final int value = state.getValue(BlockSnow.LAYERS);
			if (value == 1) { // zero-height snow layer
				return 1;
			} else { // snow height between 0-8 to between -0.25F and -1
				return -((value - 1) * 0.125F);
			}
		} else if (shouldSmooth) {
			return state.getBlock() == BEDROCK ? -1.0005F : -1;
		} else if (state.isNormalCube() || state.isBlockNormalCube()) {
			return (float) ModConfig.smoothOtherBlocksAmount;
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

	@Deprecated
	public static double average(double... values) {
		if (values.length == 0) return 0;

		double total = 0L;

		for (double value : values) {
			total += value;
		}

		return total / values.length;
	}

	/**
	 * Ew
	 *
	 * @param modContainer the {@link ModContainer} for {@link NoCubes}
	 */
	public static void launchUpdateDaemon(ModContainer modContainer) {

		new Thread(() -> {
			WHILE:
			while (true) {

				final ForgeVersion.CheckResult checkResult = ForgeVersion.getResult(modContainer);

				switch (checkResult.status) {
					default:
					case PENDING:
						break;
					case OUTDATED:
						try {
							BadAutoUpdater.update(modContainer, checkResult.target.toString(), "Cadiboo");
						} catch (Exception e) {
							throw new RuntimeException("Unable to update Mod", e);
						}
						break WHILE;
					case FAILED:
					case UP_TO_DATE:
					case AHEAD:
					case BETA:
					case BETA_OUTDATED:
						break WHILE;
				}

				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

		}, MOD_NAME + " Update Daemon").start();

	}

	public static void fixConfig(final File configFile) {

// 		//Fix config file versioning while still using @Config
// 		final Map<String, Configuration> CONFIGS;
// 		try {
// 			//Map of full file path -> configuration
// 			CONFIGS = (Map<String, Configuration>) configManager_CONFIGS.get(null);
// 		} catch (IllegalAccessException e) {
// 			CrashReport crashReport = new CrashReport("Error getting field for ConfigManager.CONFIGS!", e);
// 			crashReport.makeCategory("Reflectively Accessing ConfigManager.CONFIGS");
// 			throw new ReportedException(crashReport);
// 		}

// 		//copied from ConfigManager
// 		Configuration config = CONFIGS.get(configFile.getAbsolutePath());
// 		if (config == null) {
// 			config = new Configuration(configFile, CONFIG_VERSION);
// 			config.load();
// 			CONFIGS.put(configFile.getAbsolutePath(), config);
// 		}

// 		try {
// 			configuration_definedConfigVersion.set(config, CONFIG_VERSION);
// //			config.save();
// //			config.load();
// 		} catch (IllegalAccessException | IllegalArgumentException e) {
// 			CrashReport crashReport = new CrashReport("Error setting value of field Configuration.definedConfigVersion!", e);
// 			crashReport.makeCategory("Reflectively Accessing Configuration.definedConfigVersion");
// 			throw new ReportedException(crashReport);
// 		}

// 		NO_CUBES_LOG.debug("fixing Config with version " + config.getDefinedConfigVersion() + ", current version is " + CONFIG_VERSION);
// //		config.load();

// 		// reset config if old version
// 		if (!CONFIG_VERSION.equals(config.getLoadedConfigVersion())) {
// 			NO_CUBES_LOG.info("Resetting config file " + configFile.getName());
// 			//copied from Configuration
// 			File backupFile = new File(configFile.getAbsolutePath() + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".version-" + config.getLoadedConfigVersion());
// 			try {
// 				FileUtils.copyFile(configFile, backupFile, true);
// 			} catch (IOException e) {
// 				NO_CUBES_LOG.error("We don't really care about this error", e);
// 			}
// 			configFile.delete();
// 			//refresh
// 			config.load();
// 			//save version
// 			config.save();
// 			//save default config
// 			ConfigManager.sync(MOD_ID, Config.Type.INSTANCE);
// 		}

// 		//remove Isosurface level (for mod version 0.2.0 onwards, implemented after 0.2.2 and before 0.2.3)
// 		{
// 			config.getCategory(Configuration.CATEGORY_GENERAL).remove("isosurfaceLevel");
// 		}

// 		//fix extendLiquids (implemented after 0.2.2 and before 0.2.3)
// 		{
// 			Property extendLiquids = config.get(Configuration.CATEGORY_GENERAL, "extendLiquids", ExtendLiquidRange.OneBlock.name());
// 			if (extendLiquids.isBooleanValue())
// 				config.getCategory(Configuration.CATEGORY_GENERAL).remove("extendLiquids");
// 		}

// 		if (false) {
// 			// fix Isosurface level (mod version 0.1.2?)
// 			{
// 				final double oldDefaultValue = 0.001D;
// 				Property isosurfaceLevel = config.get(Configuration.CATEGORY_GENERAL, "isosurfaceLevel", oldDefaultValue);
// 				if (isosurfaceLevel.isDefault())
// 					//edit in version 0.1.6: set to 1
// //				isosurfaceLevel.set(0.0D);
// 					isosurfaceLevel.set(1.0D);
// 			}

// 			// fix Isosurface level (mod version 0.1.5?)
// 			{
// 				final double oldDefaultValue = 0.0D;
// 				Property isosurfaceLevel = config.get(Configuration.CATEGORY_GENERAL, "isosurfaceLevel", oldDefaultValue);
// 				if (isosurfaceLevel.isDefault())
// 					isosurfaceLevel.set(1.0D);
// 			}
// 		}

// 		//save (Unnecessary?)
// 		config.save();
// 		//save
// 		ConfigManager.sync(MOD_ID, Config.Type.INSTANCE);
	}

	public static void crashIfNotDev(final Exception e) {
		if (FMLLaunchHandler.isDeobfuscatedEnvironment()) {
			NO_CUBES_LOG.error("FIX THIS ERROR NOW!", e);
			return;
		}
		final CrashReport crashReport = new CrashReport("Error in mod " + MOD_ID, e);
		throw new ReportedException(crashReport);
	}

	public static int max(int... ints) {
		int max = 0;
		for (final int anInt : ints) {
			if (max < anInt) max = anInt;
		}
		return max;
	}

	public static boolean isLiquidSource(final IBlockState state) {
		return state.getBlock() instanceof BlockLiquid && state.getValue(BlockLiquid.LEVEL) == 0;
	}

}
