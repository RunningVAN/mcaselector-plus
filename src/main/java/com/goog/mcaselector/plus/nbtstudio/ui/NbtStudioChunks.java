package com.goog.mcaselector.plus.nbtstudio.ui;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.util.point.Point2i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chunk level helpers shared by the Plus find and replace flows.
 *
 * <p>These used to live in {@code net.querz.mcaselector.ui.DialogHelper}; they are pure functions
 * over a {@link Selection} and only need public upstream API, so they belong to the Plus package.
 */
public final class NbtStudioChunks {

	private NbtStudioChunks() {
	}

	/**
	 * Appends the chunk coordinates of one part of a selection to the hit list, so a find can hand
	 * the chunk editor every chunk that matched instead of only the first one.
	 */
	public static void collectChunks(Selection selection, List<Point2i> target) {
		if (selection == null || selection.isEmpty()) {
			return;
		}
		for (Long2ObjectMap.Entry<ChunkSet> entry : selection) {
			Point2i region = new Point2i(entry.getLongKey());
			ChunkSet chunks = entry.getValue();
			if (chunks == null) {
				// the whole region matched, so every chunk of it is a hit
				Point2i first = region.regionToChunk();
				for (int x = 0; x < 32; x++) {
					for (int z = 0; z < 32; z++) {
						target.add(new Point2i(first.getX() + x, first.getZ() + z));
					}
				}
				continue;
			}
			for (int index : chunks) {
				target.add(new Point2i(index).add(region.regionToChunk()));
			}
		}
	}

	/** Sorts hits left to right and top to bottom, so walking through them follows the map. */
	public static List<Point2i> sortChunks(List<Point2i> chunks) {
		List<Point2i> sorted;
		synchronized (chunks) {
			sorted = new ArrayList<>(chunks);
		}
		sorted.sort(Comparator.comparingInt(Point2i::getX).thenComparingInt(Point2i::getZ));
		return sorted;
	}

}
