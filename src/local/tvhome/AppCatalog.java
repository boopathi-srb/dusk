package local.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads the installed apps that can be opened, with their names and pictures. */
final class AppCatalog {

    static final class LaunchableApp {
        final String packageName;
        final String label;
        /** The app's 16:9 TV banner, or null when it has none. */
        final Drawable banner;
        final Drawable icon;
        final Intent launchIntent;

        LaunchableApp(String packageName, String label, Drawable banner, Drawable icon, Intent launchIntent) {
            this.packageName = packageName;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.launchIntent = launchIntent;
        }
    }

    private AppCatalog() {
    }

    /** Every app with a launch entry, minus hidden system pieces, sorted by name. */
    static List<LaunchableApp> loadAll(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<LaunchableApp> apps = new ArrayList<>();
        for (String packageName : launchablePackages(packageManager)) {
            if (AppRules.isHidden(packageName, context.getPackageName())) {
                continue;
            }
            LaunchableApp app = load(packageManager, packageName);
            if (app != null) {
                apps.add(app);
            }
        }
        apps.sort((first, second) -> first.label.toLowerCase(Locale.ROOT).compareTo(second.label.toLowerCase(Locale.ROOT)));
        return apps;
    }

    /** One app by package; null when it is not installed or has no launch entry. */
    static LaunchableApp load(PackageManager packageManager, String packageName) {
        Intent launchIntent = launchIntentFor(packageManager, packageName);
        if (launchIntent == null) {
            Log.w(HomeState.LOG_TAG, "AppCatalog.load: no launch entry for package=" + packageName);
            return null;
        }
        ApplicationInfo info = applicationInfo(packageManager, packageName);
        if (info == null) {
            return null;
        }
        String label = String.valueOf(info.loadLabel(packageManager));
        return new LaunchableApp(packageName, label, bannerFor(packageManager, launchIntent, info),
                info.loadIcon(packageManager), launchIntent);
    }

    /** The app's display name, or its package name when it is not installed. */
    static String label(PackageManager packageManager, String packageName) {
        ApplicationInfo info = packageName == null ? null : applicationInfo(packageManager, packageName);
        return info == null ? String.valueOf(packageName) : String.valueOf(info.loadLabel(packageManager));
    }

    static List<String> packageNames(List<LaunchableApp> apps) {
        List<String> names = new ArrayList<>();
        for (LaunchableApp app : apps) {
            names.add(app.packageName);
        }
        return names;
    }

    private static List<String> launchablePackages(PackageManager packageManager) {
        List<String> packages = new ArrayList<>();
        addPackages(packages, packageManager, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addPackages(packages, packageManager, Intent.CATEGORY_LAUNCHER);
        return packages;
    }

    private static void addPackages(List<String> packages, PackageManager packageManager, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo resolved : packageManager.queryIntentActivities(query, 0)) {
            String packageName = resolved.activityInfo.packageName;
            if (!packages.contains(packageName)) {
                packages.add(packageName);
            }
        }
    }

    private static Intent launchIntentFor(PackageManager packageManager, String packageName) {
        Intent tvIntent = packageManager.getLeanbackLaunchIntentForPackage(packageName);
        return tvIntent != null ? tvIntent : packageManager.getLaunchIntentForPackage(packageName);
    }

    /** PackageManager reports a missing package only by throwing, so the catch is unavoidable here. */
    private static ApplicationInfo applicationInfo(PackageManager packageManager, String packageName) {
        try {
            return packageManager.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException missing) {
            Log.w(HomeState.LOG_TAG, "AppCatalog.applicationInfo: package not installed, package=" + packageName);
            return null;
        }
    }

    private static Drawable bannerFor(PackageManager packageManager, Intent launchIntent, ApplicationInfo info) {
        Drawable activityBanner = activityBanner(packageManager, launchIntent);
        return activityBanner != null ? activityBanner : info.loadBanner(packageManager);
    }

    private static Drawable activityBanner(PackageManager packageManager, Intent launchIntent) {
        try {
            return packageManager.getActivityBanner(launchIntent);
        } catch (PackageManager.NameNotFoundException missing) {
            Log.w(HomeState.LOG_TAG, "AppCatalog.activityBanner: activity not found, intent=" + launchIntent);
            return null;
        }
    }
}
