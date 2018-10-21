package top.trumeet.mipushframework.register;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.drakeet.multitype.Items;
import me.drakeet.multitype.MultiTypeAdapter;
import top.trumeet.common.db.EventDb;
import top.trumeet.common.db.RegisteredApplicationDb;
import top.trumeet.common.register.RegisteredApplication;
import top.trumeet.mipush.BuildConfig;
import top.trumeet.mipush.R;
import top.trumeet.mipushframework.utils.MiPushManifestChecker;
import top.trumeet.mipushframework.utils.ThreadUtils;
import top.trumeet.mipushframework.widgets.Footer;
import top.trumeet.mipushframework.widgets.FooterItemBinder;

import static top.trumeet.common.Constants.SERVICE_APP_NAME;
import static top.trumeet.common.Constants.TAG;

/**
 * Created by Trumeet on 2017/8/26.
 *
 * @author Trumeet
 */

public class RegisteredApplicationFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private MultiTypeAdapter mAdapter;
    private LoadTask mLoadTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new MultiTypeAdapter();
        mAdapter.register(RegisteredApplication.class, new RegisteredApplicationBinder());
        mAdapter.register(Footer.class, new FooterItemBinder());
    }

    SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        RecyclerView view = new RecyclerView(getActivity());
        view.setLayoutManager(new LinearLayoutManager(getActivity(),
                LinearLayoutManager.VERTICAL, false));
        view.setAdapter(mAdapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(view.getContext(),
                LinearLayoutManager.VERTICAL);
        view.addItemDecoration(dividerItemDecoration);


        swipeRefreshLayout = new SwipeRefreshLayout(getActivity());
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.addView(view);

        loadPage();
        return swipeRefreshLayout;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadPage();
    }

    private void loadPage() {
        Log.d(TAG, "loadPage");
        if (mLoadTask != null && !mLoadTask.isCancelled()) {
            return;
        }
        mLoadTask = new LoadTask(getActivity());
        mLoadTask.execute();
    }

    @Override
    public void onDetach() {
        if (mLoadTask != null && !mLoadTask.isCancelled()) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
        super.onDetach();
    }

    @Override
    public void onRefresh() {
        loadPage();

    }

    private class LoadTask extends AsyncTask<Integer, Void, LoadTask.Result> {
        private CancellationSignal mSignal;

        public LoadTask(Context context) {
            this.context = context;
        }

        private Context context;

        private MiPushManifestChecker checker = null;


        private class Result {
            private final int notUseMiPushCount;
            private final List<RegisteredApplication> list;

            public Result(int notUseMiPushCount, List<RegisteredApplication> list) {
                this.notUseMiPushCount = notUseMiPushCount;
                this.list = list;
            }
        }

        @Override
        protected Result doInBackground(Integer... integers) {
            mSignal = new CancellationSignal();

            Map<String /* pkg */, RegisteredApplication> registeredPkgs = new HashMap<>();
            for (RegisteredApplication application : RegisteredApplicationDb.getList(context, null, mSignal)) {
                registeredPkgs.put(application.getPackageName(), application);
            }
            Set<String> actuallyRegisteredPkgs = EventDb.queryRegistered(context, mSignal);

            try {
                checker = MiPushManifestChecker.create(context);
            } catch (PackageManager.NameNotFoundException | ClassNotFoundException e) {
                Log.e(RegisteredApplicationFragment.class.getSimpleName(), "Create mi push checker", e);
            }

            List<RegisteredApplication> res = new Vector<>();


            ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

            final AtomicInteger notUseMiPushCount = new AtomicInteger(0);
            for (PackageInfo tinfo : context.getPackageManager().getInstalledPackages(PackageManager.GET_DISABLED_COMPONENTS|
                    PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS)) {

                final PackageInfo info = tinfo;
                executorService.submit(() -> {
                    if (info.services == null) {
                        info.services = new ServiceInfo[]{};
                    }
                    if (info.packageName.equals(SERVICE_APP_NAME) || info.packageName.equals(BuildConfig.APPLICATION_ID)) {
                        return;
                    }
                    System.out.println("Not Registered:: " + info.packageName);
                    RegisteredApplication application;
                    if (registeredPkgs.containsKey(info.packageName)) {
                        System.out.println("Reg Pkg Con " + info.packageName);
                        application = registeredPkgs.get(info.packageName);
                        if (actuallyRegisteredPkgs.contains(info.packageName)) {
                            application.setRegisteredType(1);
                        } else {
                            application.setRegisteredType(2);
                        }
                    } else {
                        System.out.println("Reg Pkg NoCon " + info.packageName);
                        if (checker != null) {
                            // checkReceivers will use Class#forName, but we can't change our classloader to target app's.
                            if (!checker.checkServices(info)) {
                                notUseMiPushCount.incrementAndGet();
                                System.out.println("Not MiPush " + info.packageName);
                                return;
                            }
                        } else {
                            notUseMiPushCount.incrementAndGet();
                            return;
                        }
                        application = new RegisteredApplication();
                        application.setPackageName(info.packageName);
                        application.setRegisteredType(0);
                    }
                    System.out.println("Not registered: " + application.getPackageName() + ": " + application.getPackageName());
                    res.add(application);
                });
            }


            ThreadUtils.shutdownAndAwaitTermination(executorService, 2, TimeUnit.MINUTES);


            return new Result(notUseMiPushCount.get(), res);
        }

        @Override
        protected void onPostExecute(Result result) {
            mAdapter.notifyItemRangeRemoved(0, mAdapter.getItemCount());
            mAdapter.getItems().clear();

            int start = mAdapter.getItemCount();
            Items items = new Items(mAdapter.getItems());
            items.addAll(result.list);
            if (result.notUseMiPushCount > 0) {
                items.add(new Footer(getString(R.string.footer_app_ignored_not_registered, Integer.toString(result.notUseMiPushCount))));
            }
            mAdapter.setItems(items);
            mAdapter.notifyItemRangeInserted(start, result.notUseMiPushCount > 0 ? result.list.size() + 1 : result.list.size());

            swipeRefreshLayout.setRefreshing(false);
            mLoadTask = null;
        }

        @Override
        protected void onCancelled() {
            if (mSignal != null) {
                if (!mSignal.isCanceled()) {
                    mSignal.cancel();
                }
                mSignal = null;
            }

            swipeRefreshLayout.setRefreshing(false);
            mLoadTask = null;
        }
    }
}
