package co.krypt.krypton.pairing;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.ArraySet;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.j256.ormlite.android.apptools.OpenHelperManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import co.krypt.krypton.analytics.Analytics;
import co.krypt.krypton.db.OpenDatabaseHelper;
import co.krypt.krypton.log.GitCommitSignatureLog;
import co.krypt.krypton.log.GitTagSignatureLog;
import co.krypt.krypton.log.Log;
import co.krypt.krypton.log.SSHSignatureLog;
import co.krypt.krypton.log.U2FSignatureLog;
import co.krypt.krypton.protocol.JSON;

/**
 * Created by Kevin King on 12/3/16.
 * Copyright 2016. KryptCo, Inc.
 */

public class Pairings {
    private static final String PAIRINGS_KEY = "PAIRINGS_2";
    private static final Object lock = new Object();
    private final SharedPreferences preferences;
    private final Context context;
    private final Analytics analytics;

    public static final String ON_DEVICE_LOG_ACTION = "co.krypt.krypton.action.ON_DEVICE_LOG";

    //  per-pairing settings
    private static final String REQUIRE_UNKNOWN_HOST_MANUAL_APPROVAL = ".REQUIRE_UNKNOWN_HOST_MANUAL_APPROVAL";

    public final OpenDatabaseHelper dbHelper;

    public Pairings(Context context) {
        this.context = context;
        this.analytics = new Analytics(context);
        preferences = context.getSharedPreferences("PAIRING_MANAGER_PREFERENCES", Context.MODE_PRIVATE);
        dbHelper = OpenHelperManager.getHelper(context, OpenDatabaseHelper.class);
    }

    public static String pairingApprovedKey(String pairingUUIDString) {
        return pairingUUIDString + ".APPROVED";
    }

    public static String pairingApprovedUntilKey(String pairingUUIDString) {
        return pairingUUIDString + ".APPROVED_UNTIL";
    }

    public static String pairingU2FZeroTouchKey(String pairingUUIDString) {
        return pairingUUIDString + ".U2F_ZERO_TOUCH_ALLOWED";
    }

    public void registerOnSharedPreferenceChangedListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        synchronized (lock) {
            preferences.registerOnSharedPreferenceChangeListener(listener);
        }
    }
    public void unregisterOnSharedPreferenceChangedListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        synchronized (lock) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private HashSet<Pairing> loadAllLocked() {
        HashSet<Pairing> pairings = new HashSet<>();
        Set<String> jsonPairings = new HashSet<>(preferences.getStringSet(PAIRINGS_KEY, new ArraySet<String>()));
        for (String jsonPairing : jsonPairings) {
            Pairing pairing = JSON.fromJson(jsonPairing, Pairing.class);
            //Update legacy pairings
            pairing.updateDeviceType();
            pairings.add(pairing);
        }
        return pairings;
    }

    private void setAllLocked(HashSet<Pairing> pairings) {
        Set<String> jsonPairings = new ArraySet<>();
        for (Pairing pairing : pairings) {
            jsonPairings.add(JSON.toJson(pairing));
        }
        preferences.edit().putStringSet(PAIRINGS_KEY, jsonPairings).apply();
    }

    public HashSet<Pairing> loadAll() {
        synchronized (lock) {
            return loadAllLocked();
        }
    }

    public Boolean getApproved(String pairingUUID) {
        synchronized (lock) {
            return preferences.getBoolean(pairingApprovedKey(pairingUUID), false);
        }
    }

    public Boolean getApproved(Pairing pairing) {
        return getApproved(pairing.getUUIDString());
    }

    public void setApproved(String pairingUUID, boolean approved) {
        synchronized (lock) {
            SharedPreferences.Editor editor = preferences.edit().putBoolean(pairingApprovedKey(pairingUUID), approved);
            if (!approved) {
                editor.putLong(pairingApprovedUntilKey(pairingUUID), -1);
            }
            editor.apply();
        }
    }

    public Boolean getU2FZeroTouchAllowed(String pairingUUID) {
        synchronized (lock) {
            return preferences.getBoolean(pairingU2FZeroTouchKey(pairingUUID), false);
        }
    }

    public Boolean getU2FZeroTouchAllowed(Pairing pairing) { return getU2FZeroTouchAllowed(pairing.getUUIDString()); }

    public void setU2FZeroTouchAllowed(String pairingUUID, boolean u2fZeroTouchAllowed) {
        synchronized (lock) {
            SharedPreferences.Editor editor = preferences.edit().putBoolean(pairingU2FZeroTouchKey(pairingUUID), u2fZeroTouchAllowed);
            editor.apply();
        }
    }

   public HashSet<Session> loadAllSessions() {
        synchronized (lock) {
            HashSet<Pairing> pairings = loadAllLocked();
            HashSet<Session> sessions = new HashSet<>();
            for (Pairing pairing: pairings) {
                List<Log> sortedLogs = getAllLogsTimeDescending(pairing);
                if (sortedLogs.size() > 0) {
                    sessions.add(new Session(pairing, sortedLogs.get(0), getApproved(pairing)));
                } else {
                    sessions.add(new Session(pairing, null, getApproved(pairing)));
                }
            }
            return sessions;
        }
    }

    public Pairing getPairing(String pairingUUID) {
        synchronized (lock) {
            HashSet<Pairing> pairings =  loadAllLocked();
            for (Pairing pairing: pairings) {
                if (pairing.getUUIDString().equals(pairingUUID)) {
                    return pairing;
                }
            }
            return null;
        }
    }

    public Pairing getPairing(UUID pairingUUID) {
        synchronized (lock) {
            HashSet<Pairing> pairings =  loadAllLocked();
            for (Pairing pairing: pairings) {
                if (pairing.uuid.equals(pairingUUID)) {
                    return pairing;
                }
            }
            return null;
        }
    }

    public void renamePairing(String pairingUUID, String newDisplayName) {
        synchronized (lock) {
            HashSet<Pairing> pairings =  loadAllLocked();
            for (Pairing pairing: pairings) {
                if (pairing.getUUIDString().equals(pairingUUID)) {
                    pairing.setDisplayName(newDisplayName);
                }
            }
            setAllLocked(pairings);
        }
    }

    public void unpair(Pairing pairing) {
        synchronized (lock) {
            HashSet<Pairing> currentPairings = loadAllLocked();
            currentPairings.remove(pairing);
            setAllLocked(currentPairings);
            SharedPreferences.Editor prefs = preferences.edit();
            prefs.remove(getSettingsKey(pairing, REQUIRE_UNKNOWN_HOST_MANUAL_APPROVAL))
                    .remove(pairingApprovedKey(pairing.getUUIDString()))
                    .remove(pairingApprovedUntilKey(pairing.getUUIDString()))
                    .apply();
        }
    }

    public void pair(Pairing pairing) {
        synchronized (lock) {
            HashSet<Pairing> currentPairings = loadAllLocked();
            currentPairings.add(pairing);
            setAllLocked(currentPairings);
        }
    }

    public void unpairAll() {
        synchronized (lock) {
            for (Pairing pairing: loadAllLocked()) {
                unpair(pairing);
            }
        }
    }

    public void appendToSSHLog(SSHSignatureLog log) {
        synchronized (lock) {
            try {
                dbHelper.getSSHSignatureLogDao().create(log);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        Intent onLog = new Intent(ON_DEVICE_LOG_ACTION);
        LocalBroadcastManager.getInstance(context).sendBroadcast(onLog);
    }

    public HashSet<SSHSignatureLog> getSSHLogs(String pairingUUID) {
        synchronized (lock) {
            try {
                List<SSHSignatureLog> logs = dbHelper.getSSHSignatureLogDao().queryForEq("pairing_uuid", pairingUUID);
                return new HashSet<>(logs);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new HashSet<>();
        }
    }

    public HashSet<SSHSignatureLog> getSSHLogs(Pairing pairing) {
        return getSSHLogs(pairing.getUUIDString());
    }

    public List<SSHSignatureLog> getAllSSHLogsRedacted() {
        synchronized (lock) {
            try {
                List<SSHSignatureLog> logs = dbHelper.getSSHSignatureLogDao().queryForAll();
                return SSHSignatureLog.sortByTimeDescending(new HashSet<SSHSignatureLog>(logs));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new ArrayList<>();
        }
    }

    public HashSet<GitCommitSignatureLog> getCommitLogs(String pairingUUID) {
        synchronized (lock) {
            try {
                List<GitCommitSignatureLog> logs = dbHelper.getGitCommitSignatureLogDao().queryForEq("pairing_uuid", pairingUUID);
                return new HashSet<>(logs);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new HashSet<>();
        }
    }

    public HashSet<GitCommitSignatureLog> getCommitLogs(Pairing pairing) {
        return getCommitLogs(pairing.getUUIDString());
    }

    public void appendToCommitLogs(GitCommitSignatureLog log) {
        synchronized (lock) {
            try {
                dbHelper.getGitCommitSignatureLogDao().create(log);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        Intent onLog = new Intent(ON_DEVICE_LOG_ACTION);
        LocalBroadcastManager.getInstance(context).sendBroadcast(onLog);
    }

    public HashSet<GitTagSignatureLog> getTagLogs(String pairingUUID) {
        synchronized (lock) {
            try {
                List<GitTagSignatureLog> logs = dbHelper.getGitTagSignatureLogDao().queryForEq("pairing_uuid", pairingUUID);
                return new HashSet<>(logs);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new HashSet<>();
        }
    }

    public HashSet<GitTagSignatureLog> getTagLogs(Pairing pairing) {
        return getTagLogs(pairing.getUUIDString());
    }

    public void appendToTagLogs(GitTagSignatureLog log) {
        synchronized (lock) {
            try {
                dbHelper.getGitTagSignatureLogDao().create(log);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        Intent onLog = new Intent(ON_DEVICE_LOG_ACTION);
        LocalBroadcastManager.getInstance(context).sendBroadcast(onLog);
    }

    public void appendToU2FLog(U2FSignatureLog log) {
        synchronized (lock) {
            try {
                dbHelper.getU2FSignatureLogDao().create(log);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        Intent onLog = new Intent(ON_DEVICE_LOG_ACTION);
        LocalBroadcastManager.getInstance(context).sendBroadcast(onLog);
    }

    public HashSet<U2FSignatureLog> getU2FLogs(String pairingUUID) {
        synchronized (lock) {
            try {
                List<U2FSignatureLog> logs = dbHelper.getU2FSignatureLogDao().queryForEq("pairing_uuid", pairingUUID);
                return new HashSet<>(logs);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new HashSet<>();
        }
    }

    public List<Log> getAllLogsTimeDescending(String pairingUUID) {
        synchronized (lock) {
            List<SSHSignatureLog> sshLogs = new LinkedList<>(getSSHLogs(pairingUUID));
            List<GitCommitSignatureLog> commitLogs = new LinkedList<>(getCommitLogs(pairingUUID));
            List<GitTagSignatureLog> tagLogs = new LinkedList<>(getTagLogs(pairingUUID));
            List<U2FSignatureLog> u2fLogs = new LinkedList<>(getU2FLogs(pairingUUID));

            List<Log> logs = new LinkedList<>();
            logs.addAll(sshLogs);
            logs.addAll(commitLogs);
            logs.addAll(tagLogs);
            logs.addAll(u2fLogs);
            java.util.Collections.sort(logs, (lhs, rhs) -> Long.compare(rhs.unixSeconds(), lhs.unixSeconds()));
            return logs;
        }
    }

    public List<Log> getAllLogsTimeDescending(Pairing pairing) {
        return getAllLogsTimeDescending(pairing.getUUIDString());
    }


    private static String getSettingsKey(Pairing pairing, String settingsKey) {
        return pairing.getUUIDString() + "." + settingsKey;
    }

    public boolean requireUnknownHostManualApproval(Pairing pairing) {
        synchronized (lock) {
            return preferences.getBoolean(getSettingsKey(pairing, REQUIRE_UNKNOWN_HOST_MANUAL_APPROVAL), true);
        }
    }

    public void setRequireUnknownHostManualApproval(Pairing pairing, boolean requireApproval) {
        synchronized (lock) {
            preferences.edit().putBoolean(getSettingsKey(pairing, REQUIRE_UNKNOWN_HOST_MANUAL_APPROVAL), requireApproval).apply();
        }
    }
}
