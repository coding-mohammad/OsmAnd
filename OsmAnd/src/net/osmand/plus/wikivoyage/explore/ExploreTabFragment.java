package net.osmand.plus.wikivoyage.explore;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.osmand.data.LatLon;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.Version;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.download.DownloadActivityType;
import net.osmand.plus.download.DownloadIndexesThread;
import net.osmand.plus.download.DownloadResources;
import net.osmand.plus.download.DownloadValidationManager;
import net.osmand.plus.download.IndexItem;
import net.osmand.plus.wikivoyage.data.TravelArticle;
import net.osmand.plus.wikivoyage.data.TravelDbHelper;
import net.osmand.plus.wikivoyage.explore.travelcards.ArticleTravelCard;
import net.osmand.plus.wikivoyage.explore.travelcards.BaseTravelCard;
import net.osmand.plus.wikivoyage.explore.travelcards.HeaderTravelCard;
import net.osmand.plus.wikivoyage.explore.travelcards.OpenBetaTravelCard;
import net.osmand.plus.wikivoyage.explore.travelcards.StartEditingTravelCard;
import net.osmand.plus.wikivoyage.explore.travelcards.TravelDownloadUpdateCard;
import net.osmand.plus.wikivoyage.explore.travelcards.TravelNeededMapsCard;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ExploreTabFragment extends BaseOsmAndFragment implements DownloadIndexesThread.DownloadEvents {

	private static final String WORLD_WIKIVOYAGE_FILE_NAME = "World_wikivoyage.sqlite";

	private ExploreRvAdapter adapter = new ExploreRvAdapter();
	private boolean nightMode;

	private TravelDownloadUpdateCard downloadUpdateCard;
	private TravelNeededMapsCard neededMapsCard;

	private DownloadValidationManager downloadManager;
	private IndexItem currentDownloadingIndexItem;
	private IndexItem mainIndexItem;
	private List<IndexItem> neededIndexItems = new ArrayList<>();
	private boolean waitForIndexes;

	@SuppressWarnings("RedundantCast")
	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		downloadManager = new DownloadValidationManager(getMyApplication());
		nightMode = !getMyApplication().getSettings().isLightContent();

		final View mainView = inflater.inflate(R.layout.fragment_explore_tab, container, false);
		final RecyclerView rv = (RecyclerView) mainView.findViewById(R.id.recycler_view);

		rv.setLayoutManager(new LinearLayoutManager(getContext()));
		rv.setAdapter(adapter);

		return mainView;
	}

	@Override
	public void newDownloadIndexes() {
		if (waitForIndexes) {
			waitForIndexes = false;
			checkDownloadIndexes();
		}
	}

	@Override
	public void downloadInProgress() {
		IndexItem current = getMyApplication().getDownloadThread().getCurrentDownloadingItem();
		if (current != null && current != currentDownloadingIndexItem) {
			currentDownloadingIndexItem = current;
			removeRedundantCards();
		}
		adapter.updateDownloadUpdateCard();
		adapter.updateNeededMapsCard();
	}

	@Override
	public void downloadHasFinished() {
		TravelDbHelper travelDbHelper = getMyApplication().getTravelDbHelper();
		if (travelDbHelper.getSelectedTravelBook() == null) {
			getMyApplication().getTravelDbHelper().initTravelBooks();
			Fragment parent = getParentFragment();
			if (parent != null && parent instanceof WikivoyageExploreDialogFragment) {
				((WikivoyageExploreDialogFragment) parent).populateData();
			}
		} else {
			removeRedundantCards();
		}
	}

	public void invalidateAdapter() {
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	public void populateData() {
		final List<BaseTravelCard> items = new ArrayList<>();
		final OsmandApplication app = getMyApplication();

		if (!Version.isPaidVersion(app)) {
			items.add(new OpenBetaTravelCard(app, nightMode, getFragmentManager()));
		}
		if (app.getTravelDbHelper().getSelectedTravelBook() != null) {
			FragmentActivity activity = getActivity();
			if (activity != null) {
				items.add(new HeaderTravelCard(app, nightMode, getString(R.string.popular_destinations)));

				FragmentManager fm = activity.getSupportFragmentManager();
				List<TravelArticle> popularArticles = app.getTravelDbHelper().getPopularArticles();
				for (TravelArticle article : popularArticles) {
					items.add(new ArticleTravelCard(app, nightMode, article, fm));
				}
			}
		}
		items.add(new StartEditingTravelCard(app, nightMode));
		adapter.setItems(items);

		final DownloadIndexesThread downloadThread = app.getDownloadThread();
		if (!downloadThread.getIndexes().isDownloadedFromInternet) {
			waitForIndexes = true;
			downloadThread.runReloadIndexFilesSilent();
		} else {
			checkDownloadIndexes();
		}
	}

	private void removeRedundantCards() {
		if (mainIndexItem != null && mainIndexItem.isDownloaded() && !mainIndexItem.isOutdated()) {
			removeDownloadUpdateCard();
		}
		boolean allMapsDownloaded = true;
		for (IndexItem item : neededIndexItems) {
			if (!item.isDownloaded()) {
				allMapsDownloaded = false;
				break;
			}
		}
		if (allMapsDownloaded) {
			removeNeededMapsCard();
		}
	}

	private void checkDownloadIndexes() {
		new ProcessIndexItemsTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	private void addIndexItemCards(IndexItem mainIndexItem, List<IndexItem> neededIndexItems) {
		this.mainIndexItem = mainIndexItem;
		this.neededIndexItems.clear();
		this.neededIndexItems.addAll(neededIndexItems);
		addDownloadUpdateCard();
		addNeededMapsCard();
	}

	private void addDownloadUpdateCard() {
		final OsmandApplication app = getMyApplication();
		final DownloadIndexesThread downloadThread = app.getDownloadThread();

		boolean outdated = mainIndexItem != null && mainIndexItem.isOutdated();
		File selectedTravelBook = app.getTravelDbHelper().getSelectedTravelBook();

		if (selectedTravelBook == null || outdated) {
			boolean showOtherMaps = false;
			if (selectedTravelBook == null) {
				List<IndexItem> items = downloadThread.getIndexes().getWikivoyageItems();
				showOtherMaps = items != null && items.size() > 1;
			}

			downloadUpdateCard = new TravelDownloadUpdateCard(app, nightMode, !outdated);
			downloadUpdateCard.setShowOtherMapsBtn(showOtherMaps);
			downloadUpdateCard.setListener(new TravelDownloadUpdateCard.ClickListener() {
				@Override
				public void onPrimaryButtonClick() {
					if (mainIndexItem != null) {
						downloadManager.startDownload(getMyActivity(), mainIndexItem);
						adapter.updateDownloadUpdateCard();
					}
				}

				@Override
				public void onSecondaryButtonClick() {
					if (downloadUpdateCard.isLoading()) {
						downloadThread.cancelDownload(mainIndexItem);
						adapter.updateDownloadUpdateCard();
					} else if (!downloadUpdateCard.isDownload()) {
						removeDownloadUpdateCard();
					} else if (downloadUpdateCard.isShowOtherMapsBtn()) {
						Activity activity = getActivity();
						if (activity != null) {
							Intent newIntent = new Intent(activity,
									getMyApplication().getAppCustomization().getDownloadActivity());
							newIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
							activity.startActivity(newIntent);
						}
					}
				}
			});
			downloadUpdateCard.setIndexItem(mainIndexItem);
			adapter.setDownloadUpdateCard(downloadUpdateCard);
		}
	}

	private void addNeededMapsCard() {
		if (!neededIndexItems.isEmpty()) {
			final OsmandApplication app = getMyApplication();

			neededMapsCard = new TravelNeededMapsCard(app, nightMode, neededIndexItems);
			neededMapsCard.setListener(new TravelNeededMapsCard.CardListener() {
				@Override
				public void onPrimaryButtonClick() {
					IndexItem[] items = neededIndexItems.toArray(new IndexItem[neededIndexItems.size()]);
					downloadManager.startDownload(getMyActivity(), items);
					adapter.updateNeededMapsCard();
				}

				@Override
				public void onSecondaryButtonClick() {
					if (neededMapsCard.isDownloading()) {
						app.getDownloadThread().cancelDownload(neededIndexItems);
						adapter.updateNeededMapsCard();
					} else {
						removeNeededMapsCard();
					}
				}

				@Override
				public void onIndexItemClick(IndexItem item) {
					DownloadIndexesThread downloadThread = app.getDownloadThread();
					if (downloadThread.isDownloading(item)) {
						downloadThread.cancelDownload(item);
					} else {
						downloadManager.startDownload(getMyActivity(), item);
					}
					adapter.updateNeededMapsCard();
				}
			});
			adapter.setNeededMapsCard(neededMapsCard);
		}
	}

	@NonNull
	private String getWikivoyageFileName() {
		File selectedTravelBook = getMyApplication().getTravelDbHelper().getSelectedTravelBook();
		return selectedTravelBook == null ? WORLD_WIKIVOYAGE_FILE_NAME : selectedTravelBook.getName();
	}

	private void removeDownloadUpdateCard() {
		adapter.removeDownloadUpdateCard();
		downloadUpdateCard = null;
	}

	private void removeNeededMapsCard() {
		adapter.removeNeededMapsCard();
		neededMapsCard = null;
	}

	private static class ProcessIndexItemsTask extends AsyncTask<Void, Void, Pair<IndexItem, List<IndexItem>>> {

		private static DownloadActivityType[] types = new DownloadActivityType[]{
				DownloadActivityType.NORMAL_FILE,
				DownloadActivityType.WIKIPEDIA_FILE
		};

		private OsmandApplication app;
		private WeakReference<ExploreTabFragment> weakFragment;

		private String fileName;

		ProcessIndexItemsTask(ExploreTabFragment fragment) {
			app = fragment.getMyApplication();
			weakFragment = new WeakReference<>(fragment);
			fileName = fragment.getWikivoyageFileName();
		}

		@Override
		protected Pair<IndexItem, List<IndexItem>> doInBackground(Void... voids) {
			IndexItem mainItem = app.getDownloadThread().getIndexes().getWikivoyageItem(fileName);

			List<IndexItem> neededItems = new ArrayList<>();
			for (TravelArticle article : app.getTravelDbHelper().getLocalDataHelper().getSavedArticles()) {
				LatLon latLon = new LatLon(article.getLat(), article.getLon());
				try {
					for (DownloadActivityType type : types) {
						IndexItem item = DownloadResources.findSmallestIndexItemAt(app, latLon, type);
						if (item != null && !item.isDownloaded()) {
							neededItems.add(item);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			return new Pair<>(mainItem, neededItems);
		}

		@Override
		protected void onPostExecute(Pair<IndexItem, List<IndexItem>> res) {
			ExploreTabFragment fragment = weakFragment.get();
			if (fragment != null && fragment.isResumed()) {
				fragment.addIndexItemCards(res.first, res.second);
			}
		}
	}
}
