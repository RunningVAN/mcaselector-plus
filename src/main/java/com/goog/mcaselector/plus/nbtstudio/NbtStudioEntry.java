package com.goog.mcaselector.plus.nbtstudio;


import java.util.List;
import java.util.Optional;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import net.querz.mcaselector.changer.Field;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.querz.mcaselector.tile.TileMap;
import com.goog.mcaselector.plus.nbtstudio.ui.NbtStudioDialog;
import com.goog.mcaselector.plus.nbtstudio.mixin.NbtStudioPreviewJob;
import com.goog.mcaselector.plus.nbtstudio.ui.NbtFileBrowserDialog;
import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.ui.dialog.ErrorDialog;
import net.querz.mcaselector.text.Translation;
import com.goog.mcaselector.plus.nbtstudio.ui.NbtStudioPreviewDialog;
import com.goog.mcaselector.plus.nbtstudio.mixin.NbtReplaceField;
import com.goog.mcaselector.plus.nbtstudio.ui.NbtStudioReplaceConfirmationDialog;
import net.querz.mcaselector.ui.dialog.CancellableProgressDialog;
import net.querz.mcaselector.io.job.ChangeFieldsJob;
import net.querz.mcaselector.io.CacheHelper;
import com.goog.mcaselector.plus.nbtstudio.ui.NbtStudioResultDialog;
import net.querz.mcaselector.selection.Selection;
/**
 * Entry points of the Plus NBT Studio, called from the upstream menu wiring.
 *
 * <p>The implementation used to live inside {@code net.querz.mcaselector.ui.DialogHelper}. It is
 * Plus behaviour end to end (find, replace, preview, data files), so it sits in the Plus package and
 * upstream only keeps the two one line menu hooks.</p>
 */
public final class NbtStudioEntry {

	private static final Logger LOGGER = LogManager.getLogger(NbtStudioEntry.class);

	private NbtStudioEntry() {
	}

	/**
	 * NBT Studio style find and replace: search NBT for names and values (plain, glob, regex and
	 * path restricted), select the matching chunks or apply a name/value replacement to them.
	 */
	private static final int PREVIEW_MAX_CHUNKS = 400;
	private static final int PREVIEW_MAX_CHANGES = 300;

	/**
	 * Opens the list of every editable data file of the world. Unlike the find results, which only
	 * hold the files a query matched, this lets the user walk the whole world and open any file in
	 * the editor without searching first.
	 */
	public static void openNbtFileBrowser(Stage primaryStage) {
		// the browser collects the files itself, so it can rescan after a save changed a file
		new NbtFileBrowserDialog(() -> NbtDataFiles.collect(ConfigProvider.WORLD.getWorldDirs()), primaryStage)
				.showAndWait();
	}

	public static void openNbtStudio(TileMap tileMap, Stage primaryStage) {
		Optional<NbtStudioDialog.Result> result = new NbtStudioDialog(primaryStage, tileMap).showAndWait();
		if (result.isEmpty()) {
			return;
		}
		NbtStudioDialog.Result r = result.get();
		NbtQuery query = r.parsed();
		if (query == null || !query.isValid()) {
			new ErrorDialog(primaryStage, Translation.DIALOG_NBT_STUDIO_INVALID_QUERY + ": "
					+ (query == null ? "" : query.getError()));
			return;
		}
		LOGGER.debug("nbt studio query: {} action: {} target: {} data files: {}",
				r.query(), r.action(), r.target(), r.includeDataFiles());

		// find and replace always cover everything the query can reach. there is no second button
		// left that could promise "really everything", so the current selection must not narrow the
		// run - the user has no way to tell the two apart anymore
		List<NbtDataFiles.FileRef> dataFiles = r.includeDataFiles()
				? NbtDataFiles.collect(ConfigProvider.WORLD.getWorldDirs())
				: List.of();

		boolean[] targets = resolveTargets(query, r.target());
		if (targets == null) {
			// only the path is restricted: replacing would overwrite every tag below that path
			new ErrorDialog(primaryStage, Translation.DIALOG_NBT_STUDIO_TARGET_REQUIRED.toString());
			return;
		}
		if (r.action() == NbtStudioDialog.Action.PREVIEW) {
			NbtStudioPreviewJob.PreviewAccumulator preview = previewNbtReplacement(
					tileMap, query, targets[0], targets[1], r.replacement(), false, primaryStage);
			if (preview == null) {
				return;
			}
			// the data files are previewed in the same window: the user asked to see what the
			// replacement does, not to close one window to find the second half in the next one
			NbtDataFiles.ReplaceOutcome filePreview = dataFiles.isEmpty()
					? null
					: previewDataFileReplacement(dataFiles, query, targets[0], targets[1], r.replacement(),
							primaryStage);
			new NbtStudioPreviewDialog(preview, primaryStage, tileMap, filePreview).showAndWait();
			return;
		}

		NbtReplaceField field = new NbtReplaceField();
		if (!field.parseNewValue(r.toFieldSpec())) {
			new ErrorDialog(primaryStage, Translation.DIALOG_NBT_STUDIO_INVALID_QUERY + ": " + r.toFieldSpec());
			return;
		}

		Optional<ButtonType> confRes = new NbtStudioReplaceConfirmationDialog(dataFiles.size(), primaryStage).showAndWait();
		if (confRes.isEmpty() || confRes.get() != ButtonType.OK) {
			return;
		}

		long replacedBefore = NbtReplaceField.getTotalReplaced();
		long skippedBefore = NbtReplaceField.getTotalSkipped();
		NbtDataFiles.ReplaceOutcome[] files = new NbtDataFiles.ReplaceOutcome[1];

		CancellableProgressDialog c = new CancellableProgressDialog(
				Translation.DIALOG_PROGRESS_TITLE_CHANGING_NBT_DATA, primaryStage);
		c.showProgressBar(t -> {
			ChangeFieldsJob.changeNBTFields(
					List.<Field<?>>of(field),
					false,
					null,
					t,
					false);
			// the data files are written after the chunk files: they are tiny compared to the
			// region run, and a cancelled chunk pass never leaves half written data files behind
			if (!dataFiles.isEmpty()) {
				files[0] = NbtDataFiles.replace(dataFiles, query, targets[0], targets[1], r.replacement(), false);
			}
		});

		CacheHelper.clearAllCache(tileMap);
		tileMap.draw();

		int replaced = (int) (NbtReplaceField.getTotalReplaced() - replacedBefore);
		int skipped = (int) (NbtReplaceField.getTotalSkipped() - skippedBefore);
		StringBuilder message = new StringBuilder(replaced == 0 && skipped == 0
				? Translation.DIALOG_NBT_STUDIO_RESULT_NONE.toString()
				: String.format(Translation.DIALOG_NBT_STUDIO_RESULT_REPLACED.toString(), replaced, skipped));
		if (files[0] != null) {
			message.append('\n').append(String.format(
					Translation.DIALOG_NBT_STUDIO_RESULT_FILES_REPLACED.toString(),
					files[0].filesUpdated(), files[0].replaced()));
			if (!files[0].errors().isEmpty()) {
				message.append('\n').append(String.format(
						Translation.DIALOG_NBT_STUDIO_FILE_RESULT_ERRORS.toString(), files[0].errors().size()));
			}
		}
		new NbtStudioResultDialog(message.toString(), primaryStage).showAndWait();
	}

	/**
	 * Shows what a replacement would change in the world data files without writing anything.
	 */
	private static NbtDataFiles.ReplaceOutcome previewDataFileReplacement(List<NbtDataFiles.FileRef> dataFiles,
			NbtQuery query, boolean names, boolean values, String replacement, Stage primaryStage) {
		CancellableProgressDialog c = new CancellableProgressDialog(
				Translation.DIALOG_PROGRESS_TITLE_CHANGING_NBT_DATA, primaryStage);
		NbtDataFiles.ReplaceOutcome[] holder = new NbtDataFiles.ReplaceOutcome[1];
		c.showProgressBarUntilDone(t -> holder[0] = NbtDataFiles.replace(
				dataFiles, query, names, values, replacement, true, t));
		return holder[0];
	}

	/**
	 * Decides whether names, values or both are rewritten.
	 *
	 * <p>{@code AUTO} follows NBT Studio: names are edited when the query restricts names, values are
	 * edited when the query restricts values, so a pure value query never renames anything.</p>
	 *
	 * @return {names, values}, or null if the target cannot be derived from the query
	 */
	private static boolean[] resolveTargets(NbtQuery query, NbtStudioDialog.Target target) {
		boolean hasName = query.getNamePattern() != null;
		boolean hasValue = query.getValuePattern() != null;
		return switch (target) {
			case NAME -> new boolean[]{true, false};
			case VALUE -> new boolean[]{false, true};
			case BOTH -> new boolean[]{true, true};
			case AUTO -> {
				if (!hasName && !hasValue) {
					yield null;
				}
				yield new boolean[]{hasName, hasValue};
			}
		};
	}

	private static NbtStudioPreviewJob.PreviewAccumulator previewNbtReplacement(TileMap tileMap, NbtQuery query,
			boolean names, boolean values, String replacement, boolean selectionOnly, Stage primaryStage) {
		Selection selection = selectionOnly ? tileMap.getSelection() : null;

		CancellableProgressDialog c = new CancellableProgressDialog(
				Translation.DIALOG_PROGRESS_TITLE_CHANGING_NBT_DATA, primaryStage);
		NbtStudioPreviewJob.PreviewAccumulator[] holder = new NbtStudioPreviewJob.PreviewAccumulator[1];
		c.showProgressBar(t -> holder[0] = NbtStudioPreviewJob.preview(
				query,
				names,
				values,
				replacement,
				selection,
				PREVIEW_MAX_CHUNKS,
				PREVIEW_MAX_CHANGES,
				t,
				false));

		return holder[0];
	}
}
