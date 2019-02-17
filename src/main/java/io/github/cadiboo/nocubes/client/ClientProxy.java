package io.github.cadiboo.nocubes.client;

import io.github.cadiboo.nocubes.NoCubes;
import io.github.cadiboo.nocubes.util.IProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static io.github.cadiboo.nocubes.util.ModReference.MOD_ID;

/**
 * The version of IProxy that gets injected into {@link NoCubes#proxy} on a PHYSICAL CLIENT
 *
 * @author Cadiboo
 */
public final class ClientProxy implements IProxy {

	private static final int KEY_CODE_N = 49;

	public static final KeyBinding toggleSmoothableBlockstate = new KeyBinding(MOD_ID + ".key.toggleSmoothableBlockstate", KEY_CODE_N, "key.categories.nocubes");

	static {
		ClientRegistry.registerKeyBinding(toggleSmoothableBlockstate);
	}
	private static final MethodHandle RenderGlobal_markBlocksForUpdate;
	static {
		try {
			RenderGlobal_markBlocksForUpdate = MethodHandles.publicLookup().unreflect(
					ObfuscationReflectionHelper.findMethod(WorldRenderer.class, "func_184385_a",
							void.class,
							int.class, int.class, int.class, int.class, int.class, int.class, boolean.class
					)
			);
		} catch (IllegalAccessException e) {
			final CrashReport crashReport = new CrashReport("Unable to find method RenderGlobal.markBlocksForUpdate. Method does not exist!", e);
			crashReport.makeCategory("Finding Method");
			throw new ReportedException(crashReport);
		}
	}

	@Override
	public void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean updateImmediately) {

//		final WorldRenderer renderGlobal = Minecraft.getInstance().renderGlobal;
//
//		if (renderGlobal.world == null || renderGlobal.viewFrustum == null) {
//			return;
//		}
//
//		try {
//			RenderGlobal_markBlocksForUpdate.invokeExact(renderGlobal, minX, minY, minZ, maxX, maxY, maxZ, updateImmediately);
//		} catch (ReportedException e) {
//			throw e;
//		} catch (Throwable throwable) {
//			final CrashReport crashReport = new CrashReport("Exception invoking method RenderGlobal.markBlocksForUpdate", throwable);
//			crashReport.makeCategory("Reflectively Invoking Method");
//			throw new ReportedException(crashReport);
//		}
	}

}
