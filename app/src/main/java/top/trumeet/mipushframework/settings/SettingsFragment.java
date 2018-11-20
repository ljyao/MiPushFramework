package top.trumeet.mipushframework.settings;

import android.content.ComponentName;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import moe.shizuku.preference.Preference;
import moe.shizuku.preference.PreferenceFragment;
import moe.shizuku.preference.PreferenceGroup;
import moe.shizuku.preference.SwitchPreferenceCompat;
import moe.shizuku.preference.TwoStatePreference;
import top.trumeet.common.Constants;
import top.trumeet.common.utils.rom.RomUtils;
import top.trumeet.mipush.R;
import top.trumeet.mipushframework.MainActivity;
import top.trumeet.mipushframework.utils.ShellUtils;

import static top.trumeet.common.Constants.FAKE_CONFIGURATION_PATH;
import static top.trumeet.common.utils.rom.RomUtils.ROM_H2OS;
import static top.trumeet.common.utils.rom.RomUtils.ROM_MIUI;
import static top.trumeet.common.utils.rom.RomUtils.ROM_UNKNOWN;

/**
 * Created by Trumeet on 2017/8/27.
 * Main settings
 * @see MainActivity
 * @author Trumeet
 */

public class SettingsFragment extends PreferenceFragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();

    private CheckROMTask mTask;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings);
       setPreferenceOnclick("key_get_log", preference -> {
           startActivity(new Intent()
           .setComponent(new ComponentName(Constants.SERVICE_APP_NAME,
                   Constants.SHARE_LOG_COMPONENT_NAME)));
           return true;
       });

       setPreferenceOnclick("key_clear_log", preference -> {
           startActivity(new Intent()
           .setComponent(new ComponentName(Constants.SERVICE_APP_NAME,
                   Constants.CLEAR_LOG_COMPONENT_NAME)));
           return true;
       });


       setPreferenceOnclick("activity_keep_alive", preference -> {
           Intent intent = new Intent().setComponent(new ComponentName(Constants.SERVICE_APP_NAME,
                   Constants.KEEPLIVE_COMPONENT_NAME));
           intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           startActivity(intent);
           return true;
       });


       setPreferenceOnclick("activity_push_icon", preference -> {
           Intent intent = new Intent().setComponent(new ComponentName(Constants.SERVICE_APP_NAME,
                   Constants.KEEPLIVE_COMPONENT_NAME));
           intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           TwoStatePreference switchPreference = (TwoStatePreference) preference;
           intent.putExtra(Constants.ENABLE_LAUNCHER, switchPreference.isChecked() ? R.string.enable : R.string.disable);
           startActivity(intent);
           return true;
       });

        String globeFake = Constants.FAKE_CONFIGURATION_GLOBE;
        addItem(new File(globeFake).exists(), (preference, newValue) -> {
                    boolean enabled = (boolean) newValue;

                    List<String> commands = new ArrayList<>(3);
                    if (!new File(FAKE_CONFIGURATION_PATH).exists()) {
                        commands.add("mkdir -p " + FAKE_CONFIGURATION_PATH);
                    }

                    if (new File(FAKE_CONFIGURATION_PATH).isFile()) {
                        commands.add("rm -rf " + FAKE_CONFIGURATION_PATH);
                    }


                    if (enabled) {
                        if (!new File(globeFake).exists()) commands.add("touch " + globeFake);
                    } else {
                        commands.add("rm " + globeFake);
                    }

                    Log.i(TAG, "Final Commands: " + commands.toString());
                    // About permissions and groups: these commands below with root WILL make the file accessible (not editable) for all apps.
                    Log.d(TAG, "Exit: " + ShellUtils.execCmd(commands, true, true).toString());
                    return true;
                },
                "全局" + getString(R.string.fake_enable_title),
                getString(R.string.fake_enable_detail),
                getPreferenceScreen().findPreference("activity_push_icon").getParent());


       checkROM();
    }

    private void addItem(boolean value, Preference.OnPreferenceChangeListener listener,
                         CharSequence title, CharSequence summary, PreferenceGroup parent) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(getContext(),
                null, moe.shizuku.preference.R.attr.switchPreferenceStyle,
                R.style.Preference_SwitchPreferenceCompat);
        preference.setOnPreferenceChangeListener(listener);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setChecked(value);
        parent.addPreference(preference);
    }

    private void checkROM () {
        cancelTask();
        mTask = new CheckROMTask((result) -> {
            Preference preference = getPreferenceScreen().findPreference("activity_keep_alive");
            if (preference != null) {
                preference.setVisible(result == ROM_MIUI || result == ROM_H2OS ||
                result == ROM_UNKNOWN);
            }
        });
        mTask.execute();
    }

    private void cancelTask () {
        if (mTask != null) {
            if (!mTask.isCancelled())
                mTask.cancel(true);
            mTask = null;
        }
    }

    private void setPreferenceOnclick(String key, Preference.OnPreferenceClickListener onPreferenceClickListener) {
          getPreferenceScreen().findPreference(key).setOnPreferenceClickListener(onPreferenceClickListener);

    }

    @Override
    public void onStart () {
        super.onStart();
        long time = System.currentTimeMillis();
        Log.d(TAG, "rebuild UI took: " + String.valueOf(System.currentTimeMillis() -
                time));
    }

    @FunctionalInterface
    private interface CheckListener {
        void result (int value);
    }

    private class CheckROMTask extends AsyncTask<Void, Void, Integer> {
        private final CheckListener mListener;

        public CheckROMTask(CheckListener mListener) {
            this.mListener = mListener;
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            return RomUtils.getOs();
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
        }
    }
}
