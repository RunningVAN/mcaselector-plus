package net.querz.mcaselector.ui;

import com.goog.mcaselector.plus.nbtstudio.NbtMatchMode;
import com.goog.mcaselector.plus.nbtstudio.NbtPattern;
import com.goog.mcaselector.plus.nbtstudio.NbtQuery;
import com.goog.mcaselector.plus.nbtstudio.ui.NbtStudioEditorDialog;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.StringTag;
import net.querz.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import com.goog.mcaselector.plus.nbtstudio.ui.NbtStudioEditorDialog;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The chunk editor loads region, poi and entities into three tabs. A search can hit any of them, so
 * the tab holding the hits has to be brought to front: opening the editor on region while the data
 * sits in entities leaves the user staring at "0 matches" (that is what happened with a search hit
 * on an item name inside Entities).
 */
public class NbtEditorTabSwitchTest {

	/** Region data of a chunk without the searched item, entities data that contains it. */
	private CompoundTag regionData() {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", 3465);
		ListTag sections = new ListTag(Tag.Type.COMPOUND);
		CompoundTag section = new CompoundTag();
		ListTag palette = new ListTag(Tag.Type.STRING);
		palette.add(StringTag.valueOf("minecraft:stone"));
		section.put("palette", palette);
		sections.add(section);
		root.put("sections", sections);
		return root;
	}

	private CompoundTag entitiesData() {
		CompoundTag root = new CompoundTag();
		CompoundTag item = new CompoundTag();
		item.putString("id", "minecraft:item");
		CompoundTag itemStack = new CompoundTag();
		itemStack.putString("id", "minecraft:netherite_pickaxe");
		CompoundTag components = new CompoundTag();
		CompoundTag itemName = new CompoundTag();
		itemName.putString("text", "科班丘电动扳手");
		components.put("minecraft:item_name", itemName);
		itemStack.put("components", components);
		item.put("Item", itemStack);
		ListTag entities = new ListTag(Tag.Type.COMPOUND);
		entities.add(item);
		root.put("Entities", entities);
		return root;
	}

	private NbtQuery itemNameQuery() {
		// what the find bar builds for a value-only search
		return NbtQuery.create(null, null,
				NbtPattern.of("电动扳手", NbtMatchMode.CONTAINS, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, false);
	}

	@Test
	public void entityHitIsCountedForTheEntitiesTabOnly() {
		NbtQuery query = itemNameQuery();
		assertEquals(0, NbtStudioEditorDialog.countHits(NbtQuery.ROOT_REGION, regionData(), query),
				"region does not contain the item name");
		assertEquals(0, NbtStudioEditorDialog.countHits(NbtQuery.ROOT_POI, null, query),
				"a tab that failed to load never counts as a hit");
		assertEquals(1, NbtStudioEditorDialog.countHits(NbtQuery.ROOT_ENTITIES, entitiesData(), query),
				"entities holds the item name");
	}

	@Test
	public void tabWithHitsWinsOverRegion() {
		Map<Integer, Integer> hits = new LinkedHashMap<>();
		hits.put(NbtQuery.ROOT_REGION, 0);
		hits.put(NbtQuery.ROOT_POI, 0);
		hits.put(NbtQuery.ROOT_ENTITIES, 1);
		assertEquals(NbtQuery.ROOT_ENTITIES, NbtStudioEditorDialog.pickTabWithHits(hits),
				"the editor has to switch to the tab that contains the hit");
	}

	@Test
	public void regionStaysWhenItHasHits() {
		Map<Integer, Integer> hits = new LinkedHashMap<>();
		hits.put(NbtQuery.ROOT_REGION, 3);
		hits.put(NbtQuery.ROOT_POI, 1);
		hits.put(NbtQuery.ROOT_ENTITIES, 9);
		assertEquals(NbtQuery.ROOT_REGION, NbtStudioEditorDialog.pickTabWithHits(hits),
				"region wins when it matches, the tab order must not change for plain searches");
	}

	@Test
	public void poiIsTakenWhenRegionIsEmpty() {
		Map<Integer, Integer> hits = new LinkedHashMap<>();
		hits.put(NbtQuery.ROOT_REGION, 0);
		hits.put(NbtQuery.ROOT_POI, 2);
		hits.put(NbtQuery.ROOT_ENTITIES, 0);
		assertEquals(NbtQuery.ROOT_POI, NbtStudioEditorDialog.pickTabWithHits(hits));
	}

	@Test
	public void noHitsAnywhereKeepsRegionSoZeroMatchesStayVisible() {
		Map<Integer, Integer> hits = new LinkedHashMap<>();
		hits.put(NbtQuery.ROOT_REGION, 0);
		hits.put(NbtQuery.ROOT_POI, 0);
		hits.put(NbtQuery.ROOT_ENTITIES, 0);
		assertEquals(NbtQuery.ROOT_REGION, NbtStudioEditorDialog.pickTabWithHits(hits));
	}

	@Test
	public void tabThatHasNotReportedYetDoesNotCountAsHit() {
		Map<Integer, Integer> hits = new LinkedHashMap<>();
		hits.put(NbtQuery.ROOT_REGION, 0);
		assertEquals(NbtQuery.ROOT_REGION, NbtStudioEditorDialog.pickTabWithHits(hits));
	}

	@Test
	public void nameAndValueOnDifferentTagsStillHitsInEntities() {
		// the find bar widens "name and value" to "name or value" inside one chunk, otherwise the
		// entities tab would report 0 and the editor would not switch although there is a hit
		NbtQuery sameTag = NbtQuery.create(null,
				NbtPattern.of("minecraft:item_name", NbtMatchMode.CONTAINS, true),
				NbtPattern.of("电动扳手", NbtMatchMode.CONTAINS, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, false);
		assertTrue(NbtStudioEditorDialog.countHits(NbtQuery.ROOT_ENTITIES, entitiesData(), sameTag) > 0,
				"a name pattern and a value pattern on different tags still produce a hit");
	}
}
