package io.github.cadiboo.nocubes.client;

import io.github.cadiboo.nocubes.util.pooled.cache.XYZCache;

import javax.annotation.Nonnull;

/**
 * @author Cadiboo
 */

public class VecLightCache extends XYZCache implements AutoCloseable {

	private static int instances = 0;

	@Nonnull
	private int[] cache;

	private static final ThreadLocal<VecLightCache> POOL = ThreadLocal.withInitial(() -> new VecLightCache(0, 0, 0));

	private VecLightCache(final int sizeX, final int sizeY, final int sizeZ) {
		super(sizeX, sizeY, sizeZ);
		cache = new int[sizeX * sizeY * sizeZ];
		++instances;
	}

	@Nonnull
	public int[] getVecLightCache() {
		return cache;
	}

	@Nonnull
	public static VecLightCache retain(final int sizeX, final int sizeY, final int sizeZ) {

		final VecLightCache pooled = POOL.get();

		if (pooled.sizeX == sizeX && pooled.sizeY == sizeY && pooled.sizeZ == sizeZ) {
			return pooled;
		}

		pooled.sizeX = sizeX;
		pooled.sizeY = sizeY;
		pooled.sizeZ = sizeZ;

		final int size = sizeX * sizeY * sizeZ;

		if (pooled.cache.length < size || pooled.cache.length > size * 1.25F) {
			pooled.cache = new int[size];
		}

		return pooled;
	}

	@Override
	public void close() {
	}

	public static int getInstances() {
		return instances;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		--instances;
	}

}


