package com.lechucksoftware.proxy.proxysettings.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.lechucksoftware.proxy.proxysettings.ApplicationGlobals;
import com.lechucksoftware.proxy.proxysettings.R;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import com.lechucksoftware.proxy.proxysettings.utils.ChangeLogDialog;
import com.lechucksoftware.proxy.proxysettings.utils.UIUtils;
import com.lechucksoftware.proxy.proxysettings.utils.Utils;

public class HelpPrefsFragment extends PreferenceFragment
{
    public static HelpPrefsFragment instance;
    private Preference whatsNewPref;
    private Preference changeLogPref;
    private Preference aboutPref;
    private Preference sendFeedbackPref;
    private Preference betaTestPref;
    private Preference appRatePref;
    private Preference shareApp;
//    private Preference aboutPref;

    public static HelpPrefsFragment getInstance()
    {
        if (instance == null)
            instance = new HelpPrefsFragment();

        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.help_preferences);

        instance = this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        changeLogPref = findPreference("pref_full_changelog");
        changeLogPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {

                ChangeLogDialog cld = new ChangeLogDialog(getActivity());
                cld.show();
                return true;
            }
        });

        final String appVersionName = Utils.getAppVersionName(getActivity());
        aboutPref = findPreference("pref_about");
        aboutPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                UIUtils.showHTMLAssetsAlertDialog(getActivity(), getResources().getString(R.string.about), "about.html", getResources().getString(R.string.close), null);
                return true;
            }
        });
        aboutPref.setSummary(appVersionName);

        appRatePref = findPreference("pref_issues");
        appRatePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shouldit/proxy-settings/issues/new")));
                return true;
            }
        });

        betaTestPref = findPreference("pref_betatest");
        betaTestPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                showBetaTestDialog();
                return true;
            }
        });

        appRatePref = findPreference("pref_rate_app");
        appRatePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.lechucksoftware.proxy.proxysettings")));
                return true;
            }
        });

//        shareApp = findPreference("pref_share_app");
//        shareApp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
//        {
//            @Override
//            public boolean onPreferenceClick(Preference preference)
//            {
//
//
//            }
//        });





//        sendFeedbackPref = findPreference("pref_send_feedback");
//        sendFeedbackPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
//        {
//            @Override
//            public boolean onPreferenceClick(Preference preference)
//            {
//                Intent i = new Intent(Intent.ACTION_SEND);
////i.setType("text/plain"); //use this line for testing in the emulator
//                i.setType("message/rfc822"); // use from live device
//                i.putExtra(Intent.EXTRA_EMAIL, new String[]{"info@shouldit.net"});
//                i.putExtra(Intent.EXTRA_SUBJECT, "User feedback for Proxy Settings" + appVersionName);
//                startActivity(i);
//                return true;
//            }
//        });


        return v;
    }

    private void showBetaTestDialog()
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.beta_testing);
        builder.setMessage(R.string.beta_testing_instructions);
        builder.setPositiveButton(R.string.cont, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://plus.google.com/u/0/communities/104290788068260973104"));
                startActivity(browserIntent);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                dialogInterface.dismiss();
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener()
        {
            @Override
            public void onCancel(DialogInterface dialog)
            {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // Reset selected configuration
        ApplicationGlobals.setSelectedConfiguration(null);

        ActionBar actionBar = getActivity().getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
    }

}
