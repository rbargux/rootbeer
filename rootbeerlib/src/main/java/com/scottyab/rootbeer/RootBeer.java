package com.scottyab.rootbeer;

import android.content.Context;
import android.content.pm.PackageManager;

import com.scottyab.rootbeer.util.QLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static com.scottyab.rootbeer.Const.BINARY_BUSYBOX;
import static com.scottyab.rootbeer.Const.BINARY_SU;

class CustomException extends Exception {
    CustomException(String errorMessage) {
        super(errorMessage);
    }
}

/**
 * A simple root checker that gives an *indication* if the device is rooted or
 * not.
 * Disclaimer: **root==god**, so there's no 100% way to check for root.
 */
public class RootBeer {

    private final Context mContext;
    private boolean loggingEnabled = true;

    public RootBeer(Context context) {
        mContext = context;
    }

    /**
     * Run all the root detection checks.
     *
     * @return true, we think there's a good *indication* of root | false good
     *         *indication* of no root (could still be cloaked)
     */
    public boolean isRooted() {

        return detectRootManagementApps() || detectPotentiallyDangerousApps() || checkForBinary(BINARY_SU)
                || checkForDangerousProps() || checkForRWPaths()
                || detectTestKeys() || checkSuExists() || checkForRootNative() || checkForMagiskBinary();
    }

    /**
     * @deprecated This method is deprecated as checking without the busybox binary
     *             is now the
     *             default. This is because many manufacturers leave this binary on
     *             production devices.
     */
    @Deprecated
    public boolean isRootedWithoutBusyBoxCheck() {
        return isRooted();
    }

    /**
     * Run all the checks including checking for the busybox binary.
     * Warning: Busybox binary is not always an indication of root, many
     * manufacturers leave this
     * binary on production devices
     * 
     * @return true, we think there's a good *indication* of root | false good
     *         *indication* of no root (could still be cloaked)
     */
    public boolean isRootedWithBusyBoxCheck() {

        return detectRootManagementApps() || detectPotentiallyDangerousApps() || checkForBinary(BINARY_SU)
                || checkForBinary(BINARY_BUSYBOX) || checkForDangerousProps() || checkForRWPaths()
                || detectTestKeys() || checkSuExists() || checkForRootNative() || checkForMagiskBinary();
    }

    /**
     * Release-Keys and Test-Keys has to do with how the kernel is signed when it is
     * compiled.
     * Test-Keys means it was signed with a custom key generated by a third-party
     * developer.
     * 
     * @return true if signed with Test-keys
     */
    public boolean detectTestKeys() {
        String buildTags = android.os.Build.TAGS;

        return buildTags != null && buildTags.contains("test-keys");
    }

    /**
     * Using the PackageManager, check for a list of well known root apps. @link
     * {Const.knownRootAppsPackages}
     * 
     * @return true if one of the apps it's installed
     */
    public boolean detectRootManagementApps() {
        return detectRootManagementApps(null);
    }

    /**
     * Using the PackageManager, check for a list of well known root apps. @link
     * {Const.knownRootAppsPackages}
     * 
     * @param additionalRootManagementApps - array of additional packagenames to
     *                                     search for
     * @return true if one of the apps it's installed
     */
    public boolean detectRootManagementApps(String[] additionalRootManagementApps) {

        // Create a list of package names to iterate over from constants any others
        // provided
        ArrayList<String> packages = new ArrayList<>(Arrays.asList(Const.knownRootAppsPackages));
        if (additionalRootManagementApps != null && additionalRootManagementApps.length > 0) {
            packages.addAll(Arrays.asList(additionalRootManagementApps));
        }

        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Using the PackageManager, check for a list of well known apps that require
     * root. @link {Const.knownRootAppsPackages}
     * 
     * @return true if one of the apps it's installed
     */
    public boolean detectPotentiallyDangerousApps() {
        return detectPotentiallyDangerousApps(null);
    }

    /**
     * Using the PackageManager, check for a list of well known apps that require
     * root. @link {Const.knownRootAppsPackages}
     * 
     * @param additionalDangerousApps - array of additional packagenames to search
     *                                for
     * @return true if one of the apps it's installed
     */
    public boolean detectPotentiallyDangerousApps(String[] additionalDangerousApps) {

        // Create a list of package names to iterate over from constants any others
        // provided
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(Const.knownDangerousAppsPackages));
        if (additionalDangerousApps != null && additionalDangerousApps.length > 0) {
            packages.addAll(Arrays.asList(additionalDangerousApps));
        }

        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Using the PackageManager, check for a list of well known root cloak
     * apps. @link {Const.knownRootAppsPackages}
     * and checks for native library read access
     * 
     * @return true if one of the apps it's installed
     */
    public boolean detectRootCloakingApps() {
        return detectRootCloakingApps(null) || canLoadNativeLibrary() && !checkForNativeLibraryReadAccess();
    }

    /**
     * Using the PackageManager, check for a list of well known root cloak
     * apps. @link {Const.knownRootAppsPackages}
     * 
     * @param additionalRootCloakingApps - array of additional packagenames to
     *                                   search for
     * @return true if one of the apps it's installed
     */
    public boolean detectRootCloakingApps(String[] additionalRootCloakingApps) {

        // Create a list of package names to iterate over from constants any others
        // provided
        ArrayList<String> packages = new ArrayList<>(Arrays.asList(Const.knownRootCloakingPackages));
        if (additionalRootCloakingApps != null && additionalRootCloakingApps.length > 0) {
            packages.addAll(Arrays.asList(additionalRootCloakingApps));
        }
        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Checks various (Const.suPaths) common locations for the SU binary
     * 
     * @return true if found
     */
    public boolean checkForSuBinary() {
        return checkForBinary(BINARY_SU);
    }

    /**
     * Checks various (Const.suPaths) common locations for the magisk binary (a well
     * know root level program)
     * 
     * @return true if found
     */
    public boolean checkForMagiskBinary() {
        return checkForBinary("magisk");
    }

    /**
     * Checks various (Const.suPaths) common locations for the busybox binary (a
     * well know root level program)
     * 
     * @return true if found
     */
    public boolean checkForBusyBoxBinary() {
        return checkForBinary(BINARY_BUSYBOX);
    }

    /**
     *
     * @param filename - check for this existence of this file
     * @return true if found
     */
    public boolean checkForBinary(String filename) {

        String[] pathsArray = Const.getPaths();

        boolean result = false;

        for (String path : pathsArray) {
            String completePath = path + filename;
            File f = new File(path, filename);
            boolean fileExists = f.exists();
            if (fileExists) {
                QLog.v(completePath + " binary detected!");
                result = true;
            }
        }

        return result;
    }

    /**
     *
     * @param logging - set to true for logging
     */
    public void setLogging(boolean logging) {
        loggingEnabled = logging;
        QLog.LOGGING_LEVEL = logging ? QLog.ALL : QLog.NONE;
    }

    private String[] propsReader() {
        try {
            InputStream inputstream = Runtime.getRuntime().exec("getprop").getInputStream();
            if (inputstream == null)
                return null;
            Scanner scanner = new Scanner(inputstream);
            System.out.println("Soy inputstream = " + inputstream);
            scanner.useDelimiter("\\A");
            String propVal = scanner.next();
            System.out.println("propVal = " + propVal);
            return propVal.split("\n");
        } catch (IOException | NoSuchElementException e) {
            QLog.e(e);
            System.out.println("propsReader Efectivamente me toteo");
            throw new CustomException("propsReader error");
        }
    }

    private String[] mountReader() {
        try {
            InputStream inputstream = Runtime.getRuntime().exec("mount").getInputStream();
            if (inputstream == null)
                return null;
            String propVal = new Scanner(inputstream).useDelimiter("\\A").next();
            return propVal.split("\n");
        } catch (IOException | NoSuchElementException e) {
            QLog.e(e);
            return null;
        }
    }

    /**
     * Check if any package in the list is installed
     * 
     * @param packages - list of packages to search for
     * @return true if any of the packages are installed
     */
    private boolean isAnyPackageFromListInstalled(List<String> packages) {
        boolean result = false;

        PackageManager pm = mContext.getPackageManager();

        for (String packageName : packages) {
            try {
                // Root app detected
                pm.getPackageInfo(packageName, 0);
                QLog.e(packageName + " ROOT management app detected!");
                result = true;
            } catch (PackageManager.NameNotFoundException e) {
                // Exception thrown, package is not installed into the system
            }
        }

        return result;
    }

    /**
     * Checks for several system properties for
     * 
     * @return - true if dangerous props are found
     */
    public boolean checkForDangerousProps() {
        try {
            final Map<String, String> dangerousProps = new HashMap<>();
            dangerousProps.put("ro.debuggable", "1");
            dangerousProps.put("ro.secure", "0");

            boolean result = false;

            String[] lines = propsReader();
            System.out.println("Antes dle lines");
            System.out.println(lines);

            for (String line : lines) {
                for (String key : dangerousProps.keySet()) {
                    if (line.contains(key)) {
                        String badValue = dangerousProps.get(key);
                        badValue = "[" + badValue + "]";
                        if (line.contains(badValue)) {
                            QLog.v(key + " = " + badValue + " detected!");
                            result = true;
                        }
                    }
                }
            }
            return result;
        } catch (Exception e) {
            System.out.println("Exception checkForDangerousProps");
            return true;
        }
    }

    /**
     * When you're root you can change the permissions on common system directories,
     * this method checks if any of these patha Const.pathsThatShouldNotBeWritable
     * are writable.
     * 
     * @return true if one of the dir is writable
     */
    public boolean checkForRWPaths() {

        boolean result = false;

        // Run the command "mount" to retrieve all mounted directories
        String[] lines = mountReader();

        if (lines == null) {
            // Could not read, assume false;
            return false;
        }

        // The SDK version of the software currently running on this hardware device.
        int sdkVersion = android.os.Build.VERSION.SDK_INT;

        /**
         *
         * In devices that are running Android 6 and less, the mount command line has an
         * output as follow:
         *
         * <fs_spec_path> <fs_file> <fs_spec> <fs_mntopts>
         *
         * where :
         * - fs_spec_path: describes the path of the device or remote filesystem to be
         * mounted.
         * - fs_file: describes the mount point for the filesystem.
         * - fs_spec describes the block device or remote filesystem to be mounted.
         * - fs_mntopts: describes the mount options associated with the filesystem.
         * (E.g. "rw,nosuid,nodev" )
         *
         */

        /**
         * In devices running Android which is greater than Marshmallow, the mount
         * command output is as follow:
         *
         * <fs_spec> <ON> <fs_file> <TYPE> <fs_vfs_type> <(fs_mntopts)>
         *
         * where :
         * - fs_spec describes the block device or remote filesystem to be mounted.
         * - fs_file: describes the mount point for the filesystem.
         * - fs_vfs_type: describes the type of the filesystem.
         * - fs_mntopts: describes the mount options associated with the filesystem.
         * (E.g. "(rw,seclabel,nosuid,nodev,relatime)" )
         */

        for (String line : lines) {

            // Split lines into parts
            String[] args = line.split(" ");

            if ((sdkVersion <= android.os.Build.VERSION_CODES.M && args.length < 4)
                    || (sdkVersion > android.os.Build.VERSION_CODES.M && args.length < 6)) {
                // If we don't have enough options per line, skip this and log an error
                QLog.e("Error formatting mount line: " + line);
                continue;
            }

            String mountPoint;
            String mountOptions;

            /**
             * To check if the device is running Android version higher than Marshmallow or
             * not
             */
            if (sdkVersion > android.os.Build.VERSION_CODES.M) {
                mountPoint = args[2];
                mountOptions = args[5];
            } else {
                mountPoint = args[1];
                mountOptions = args[3];
            }

            for (String pathToCheck : Const.pathsThatShouldNotBeWritable) {
                if (mountPoint.equalsIgnoreCase(pathToCheck)) {

                    /**
                     * If the device is running an Android version above Marshmallow,
                     * need to remove parentheses from options parameter;
                     */
                    if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M) {
                        mountOptions = mountOptions.replace("(", "");
                        mountOptions = mountOptions.replace(")", "");

                    }

                    // Split options out and compare against "rw" to avoid false positives
                    for (String option : mountOptions.split(",")) {

                        if (option.equalsIgnoreCase("rw")) {
                            QLog.v(pathToCheck + " path is mounted with rw permissions! " + line);
                            result = true;
                            break;
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * A variation on the checking for SU, this attempts a 'which su'
     * 
     * @return true if su found
     */
    public boolean checkSuExists() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "which", BINARY_SU });
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return in.readLine() != null;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null)
                process.destroy();
        }
    }

    /**
     * Checks if device has ReadAccess to the Native Library
     * Precondition: canLoadNativeLibrary() ran before this and returned true
     *
     * Description: RootCloak automatically blocks read access to the Native
     * Libraries, however
     * allows for them to be loaded into memory. This check is an indication that
     * RootCloak is
     * installed onto the device.
     *
     * @return true if device has Read Access | false if UnsatisfiedLinkError Occurs
     */
    public boolean checkForNativeLibraryReadAccess() {
        RootBeerNative rootBeerNative = new RootBeerNative();
        try {
            rootBeerNative.setLogDebugMessages(loggingEnabled);
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Checks if it is possible to load our native library
     * 
     * @return true if we can | false if not
     */
    public boolean canLoadNativeLibrary() {
        return new RootBeerNative().wasNativeLibraryLoaded();
    }

    /**
     * Native checks are often harder to cloak/trick so here we call through to our
     * native root checker
     * 
     * @return true if we found su | false if not, or the native library could not
     *         be loaded / accessed
     */
    public boolean checkForRootNative() {

        if (!canLoadNativeLibrary()) {
            QLog.e("We could not load the native library to test for root");
            return false;
        }

        String[] paths = Const.getPaths();

        String[] checkPaths = new String[paths.length];
        for (int i = 0; i < checkPaths.length; i++) {
            checkPaths[i] = paths[i] + BINARY_SU;
        }

        RootBeerNative rootBeerNative = new RootBeerNative();
        try {
            rootBeerNative.setLogDebugMessages(loggingEnabled);
            return rootBeerNative.checkForRoot(checkPaths) > 0;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

}
