package co.krypt.krypton.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;

import co.krypt.krypton.knownhosts.KnownHost;
import co.krypt.krypton.log.GitCommitSignatureLog;
import co.krypt.krypton.log.GitTagSignatureLog;
import co.krypt.krypton.log.SSHSignatureLog;
import co.krypt.krypton.log.U2FSignatureLog;
import co.krypt.krypton.policy.Approval;
import co.krypt.krypton.totp.TOTPAccount;
import co.krypt.krypton.u2f.RegisteredAccount;


/**
 * Created by Kevin King on 3/28/17.
 * Copyright 2016. KryptCo, Inc.
 */

public class OpenDatabaseHelper extends OrmLiteSqliteOpenHelper {

    public static final String DATABASE_NAME = "kryptonite";
    private static final int DATABASE_VERSION = 9;

    /**
     * The data access object used to interact with the Sqlite database to do C.R.U.D operations.
     */
    private Dao<SSHSignatureLog, Long> sshSignatureLogDao;
    private Dao<GitCommitSignatureLog, Long> gitCommitSignatureLogDao;
    private Dao<GitTagSignatureLog, Long> gitTagSignatureLogDao;
    private Dao<U2FSignatureLog, Long> u2FSignatureLogDao;
    private Dao<Approval, Long> approvalDao;
    private Dao<RegisteredAccount, String> registeredAccountDao;
    private Dao<TOTPAccount, String> totpAccountDao;

    private final Context context;

    private Dao<KnownHost, Long> knownHostDao;

    public OpenDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION) ;
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, SSHSignatureLog.class);
            TableUtils.createTable(connectionSource, KnownHost.class);
            TableUtils.createTable(connectionSource, GitCommitSignatureLog.class);
            TableUtils.createTable(connectionSource, GitTagSignatureLog.class);
            TableUtils.createTable(connectionSource, Approval.class);
            TableUtils.createTable(connectionSource, U2FSignatureLog.class);
            TableUtils.createTable(connectionSource, RegisteredAccount.class);
            TableUtils.createTable(connectionSource, TOTPAccount.class);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource,
                          int oldVersion, int newVersion) {
        try {
            /**
             * Recreates the database when onUpgrade is called by the framework
             */
            if (oldVersion < 2 && newVersion >= 2) {
                TableUtils.createTable(connectionSource, KnownHost.class);
            }
            if (oldVersion < 3 && newVersion >= 3) {
                TableUtils.createTable(connectionSource, GitCommitSignatureLog.class);
                TableUtils.createTable(connectionSource, GitTagSignatureLog.class);
            }

            if (oldVersion == 4 && newVersion >= 5) {
                database.execSQL("ALTER TABLE `git_commit_signature_log` ADD COLUMN merge_parents VARCHAR;");
            }

            if (oldVersion < 6 && newVersion >= 6) {
                TableUtils.createTable(connectionSource, Approval.class);
            }

            if (oldVersion < 7 && newVersion >= 7) {
                TableUtils.createTable(connectionSource, U2FSignatureLog.class);
            }

            if (oldVersion < 8 && newVersion >= 8) {
                TableUtils.createTable(connectionSource, RegisteredAccount.class);
            }

            if (oldVersion < 9 && newVersion >= 9) {
                TableUtils.createTable(connectionSource, TOTPAccount.class);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }

    /**
     * Returns an instance of the data access object
     * @return
     * @throws SQLException
     */
    public Dao<SSHSignatureLog, Long> getSSHSignatureLogDao() throws SQLException {
        if(sshSignatureLogDao == null) {
            sshSignatureLogDao = getDao(SSHSignatureLog.class);
        }
        return sshSignatureLogDao;
    }

    public Dao<GitCommitSignatureLog, Long> getGitCommitSignatureLogDao() throws SQLException {
        if(gitCommitSignatureLogDao == null) {
            gitCommitSignatureLogDao = getDao(GitCommitSignatureLog.class);
        }
        return gitCommitSignatureLogDao;
    }

    public Dao<GitTagSignatureLog, Long> getGitTagSignatureLogDao() throws SQLException {
        if(gitTagSignatureLogDao == null) {
            gitTagSignatureLogDao = getDao(GitTagSignatureLog.class);
        }
        return gitTagSignatureLogDao;
    }

    public Dao<U2FSignatureLog, Long> getU2FSignatureLogDao() throws SQLException {
        if(u2FSignatureLogDao == null) {
            u2FSignatureLogDao = getDao(U2FSignatureLog.class);
        }
        return u2FSignatureLogDao;
    }

    public Dao<KnownHost, Long> getKnownHostDao() throws SQLException {
        if(knownHostDao == null) {
            knownHostDao = getDao(KnownHost.class);
        }
        return knownHostDao;
    }
    public Dao<Approval, Long> getApprovalDao() throws SQLException {
        if(approvalDao == null) {
            approvalDao = getDao(Approval.class);
        }
        return approvalDao;
    }
    public Dao<RegisteredAccount, String> getRegisteredAccountDao() throws SQLException {
        if(registeredAccountDao == null) {
            registeredAccountDao = getDao(RegisteredAccount.class);
        }
        return registeredAccountDao;
    }
    public Dao<TOTPAccount, String> getTOTPAccountDao() throws SQLException {
        if(totpAccountDao == null) {
            totpAccountDao = getDao(TOTPAccount.class);
        }
        return totpAccountDao;
    }
}
