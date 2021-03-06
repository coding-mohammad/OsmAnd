package net.osmand.plus.openplacereviews;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.base.BaseOsmAndFragment;
import org.apache.commons.logging.Log;

public class OprStartFragment extends BaseOsmAndFragment {
	private static final String TAG = OprStartFragment.class.getSimpleName();
	private static final Log LOG = PlatformUtil.getLog(OprStartFragment.class);
	private static final String openPlaceReviewsUrl = "OpenPlaceReviews.org";
	private boolean nightMode;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		nightMode = getMyApplication().getDaynightHelper().isNightModeForMapControls();
		View v = UiUtilities.getInflater(requireMyActivity(), nightMode).inflate(R.layout.fragment_opr_login, container,
				false);
		View createAccount = v.findViewById(R.id.register_opr_create_account);
		v.findViewById(R.id.back_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				FragmentActivity activity = getActivity();
				if (activity != null) {
					activity.getSupportFragmentManager().popBackStack();
				}
			}
		});
		UiUtilities.setupDialogButton(nightMode, createAccount, UiUtilities.DialogButtonType.PRIMARY,
				R.string.register_opr_create_new_account);
		createAccount.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent i = new Intent(requireContext(), OPRWebviewActivity.class);
				i.putExtra(OPRWebviewActivity.KEY_TITLE, getString(R.string.register_opr_create_new_account));
				i.putExtra(OPRWebviewActivity.KEY_LOGIN, false);
				startActivity(i);
			}
		});
		View haveAccount = v.findViewById(R.id.register_opr_have_account);
		UiUtilities.setupDialogButton(nightMode, haveAccount, UiUtilities.DialogButtonType.SECONDARY,
				R.string.register_opr_have_account);
		haveAccount.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent i = new Intent(requireContext(), OPRWebviewActivity.class);
				i.putExtra(OPRWebviewActivity.KEY_TITLE, getString(R.string.user_login));
				i.putExtra(OPRWebviewActivity.KEY_LOGIN, true);
				startActivity(i);
			}
		});
		setURLSpan(v);
		return v;
	}

	private void setURLSpan(View v) {
		String desc = requireContext().getString(R.string.register_on_openplacereviews_desc);
		SpannableString ss = new SpannableString(desc);
		ss.setSpan(new URLSpanNoUnderline("https://" + openPlaceReviewsUrl), desc.indexOf(openPlaceReviewsUrl),
				desc.indexOf(openPlaceReviewsUrl) + openPlaceReviewsUrl.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
		v.<TextView>findViewById(R.id.start_opr_description).setText(ss);
		v.<TextView>findViewById(R.id.start_opr_description).setMovementMethod(LinkMovementMethod.getInstance());
	}

	@Override
	public int getStatusBarColorId() {
		View view = getView();
		if (view != null && Build.VERSION.SDK_INT >= 23 && !nightMode) {
			view.setSystemUiVisibility(view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		}
		return nightMode ? R.color.list_background_color_dark : R.color.list_background_color_light;
	}

	private class URLSpanNoUnderline extends URLSpan {
		public URLSpanNoUnderline(String url) {
			super(url);
		}

		@Override
		public void updateDrawState(@NonNull TextPaint ds) {
			super.updateDrawState(ds);
			ds.setUnderlineText(false);
			ds.setTypeface(Typeface.DEFAULT_BOLD);
		}
	}


	public static void showInstance(@NonNull FragmentManager fm) {
		try {
			if (fm.findFragmentByTag(OprStartFragment.TAG) == null) {
				OprStartFragment fragment = new OprStartFragment();
				fm.beginTransaction()
						.add(R.id.fragmentContainer, fragment, OprStartFragment.TAG)
						.addToBackStack(null).commit();
			}
		} catch (RuntimeException e) {
			LOG.error("showInstance", e);
		}
	}
}
