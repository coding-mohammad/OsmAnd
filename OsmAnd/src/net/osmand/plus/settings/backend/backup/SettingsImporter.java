package net.osmand.plus.settings.backend.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.util.Algorithms;

import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static net.osmand.IndexConstants.OSMAND_SETTINGS_FILE_EXT;

class SettingsImporter {

	private OsmandApplication app;

	SettingsImporter(@NonNull OsmandApplication app) {
		this.app = app;
	}

	List<SettingsItem> collectItems(@NonNull File file) throws IllegalArgumentException, IOException {
		return processItems(file, null);
	}

	void importItems(@NonNull File file, @NonNull List<SettingsItem> items) throws IllegalArgumentException, IOException {
		processItems(file, items);
	}

	private List<SettingsItem> getItemsFromJson(@NonNull File file) throws IOException {
		List<SettingsItem> items = new ArrayList<>();
		ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
		InputStream ois = new BufferedInputStream(zis);
		try {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String fileName = checkEntryName(entry.getName());
				if (fileName.equals("items.json")) {
					String itemsJson = null;
					try {
						itemsJson = Algorithms.readFromInputStream(ois, false).toString();
					} catch (IOException e) {
						SettingsHelper.LOG.error("Error reading items.json: " + itemsJson, e);
						throw new IllegalArgumentException("No items");
					} finally {
						zis.closeEntry();
					}
					try {
						SettingsItemsFactory itemsFactory = new SettingsItemsFactory(app, itemsJson);
						items.addAll(itemsFactory.getItems());
					} catch (IllegalArgumentException e) {
						SettingsHelper.LOG.error("Error parsing items: " + itemsJson, e);
						throw new IllegalArgumentException("No items");
					} catch (JSONException e) {
						SettingsHelper.LOG.error("Error parsing items: " + itemsJson, e);
						throw new IllegalArgumentException("No items");
					}
					break;
				}
			}
		} catch (IOException ex) {
			SettingsHelper.LOG.error("Failed to read next entry", ex);
		} finally {
			Algorithms.closeStream(ois);
			Algorithms.closeStream(zis);
		}
		return items;
	}

	private List<SettingsItem> processItems(@NonNull File file, @Nullable List<SettingsItem> items) throws IllegalArgumentException, IOException {
		boolean collecting = items == null;
		if (collecting) {
			items = getItemsFromJson(file);
		} else {
			if (items.size() == 0) {
				throw new IllegalArgumentException("No items");
			}
		}
		ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
		InputStream ois = new BufferedInputStream(zis);
		try {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String fileName = checkEntryName(entry.getName());
				SettingsItem item = null;
				for (SettingsItem settingsItem : items) {
					if (settingsItem != null && settingsItem.applyFileName(fileName)) {
						item = settingsItem;
						break;
					}
				}
				if (item != null && collecting && item.shouldReadOnCollecting()
						|| item != null && !collecting && !item.shouldReadOnCollecting()) {
					try {
						SettingsItemReader<? extends SettingsItem> reader = item.getReader();
						if (reader != null) {
							reader.readFromStream(ois);
						}
					} catch (IllegalArgumentException e) {
						item.warnings.add(app.getString(R.string.settings_item_read_error, item.getName()));
						SettingsHelper.LOG.error("Error reading item data: " + item.getName(), e);
					} catch (IOException e) {
						item.warnings.add(app.getString(R.string.settings_item_read_error, item.getName()));
						SettingsHelper.LOG.error("Error reading item data: " + item.getName(), e);
					} finally {
						zis.closeEntry();
					}
				}
			}
		} catch (IOException ex) {
			SettingsHelper.LOG.error("Failed to read next entry", ex);
		} finally {
			Algorithms.closeStream(ois);
			Algorithms.closeStream(zis);
		}
		return items;
	}

	private String checkEntryName(String entryName) {
		String fileExt = OSMAND_SETTINGS_FILE_EXT + "/";
		int index = entryName.indexOf(fileExt);
		if (index != -1) {
			entryName = entryName.substring(index + fileExt.length());
		}
		return entryName;
	}
}