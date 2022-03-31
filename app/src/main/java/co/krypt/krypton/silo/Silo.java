package co.krypt.krypton.silo;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.amazonaws.util.Base64;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.jakewharton.disklrucache.DiskLruCache;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import co.krypt.krypton.analytics.Analytics;
import co.krypt.krypton.crypto.RSASSHKeyPair;
import co.krypt.krypton.crypto.SSHKeyPairI;
import co.krypt.krypton.crypto.U2F;
import co.krypt.krypton.db.OpenDatabaseHelper;
import co.krypt.krypton.exception.CryptoException;
import co.krypt.krypton.exception.MismatchedHostKeyException;
import co.krypt.krypton.exception.ProtocolException;
import co.krypt.krypton.exception.TransportException;
import co.krypt.krypton.exception.Unrecoverable;
import co.krypt.krypton.git.CommitInfo;
import co.krypt.krypton.git.TagInfo;
import co.krypt.krypton.knownhosts.KnownHost;
import co.krypt.krypton.log.GitCommitSignatureLog;
import co.krypt.krypton.log.GitTagSignatureLog;
import co.krypt.krypton.log.SSHSignatureLog;
import co.krypt.krypton.log.U2FSignatureLog;
import co.krypt.krypton.me.MeStorage;
import co.krypt.krypton.onboarding.devops.TestSSHFragment;
import co.krypt.krypton.pairing.Pairing;
import co.krypt.krypton.pairing.Pairings;
import co.krypt.krypton.pgp.UserID;
import co.krypt.krypton.pgp.asciiarmor.AsciiArmor;
import co.krypt.krypton.pgp.packet.HashAlgorithm;
import co.krypt.krypton.pgp.packet.SignableUtils;
import co.krypt.krypton.policy.Policy;
import co.krypt.krypton.protocol.AckResponse;
import co.krypt.krypton.protocol.GitSignRequest;
import co.krypt.krypton.protocol.GitSignRequestBody;
import co.krypt.krypton.protocol.GitSignResponse;
import co.krypt.krypton.protocol.HostInfo;
import co.krypt.krypton.protocol.HostsRequest;
import co.krypt.krypton.protocol.HostsResponse;
import co.krypt.krypton.protocol.JSON;
import co.krypt.krypton.protocol.LogDecryptionRequest;
import co.krypt.krypton.protocol.MeRequest;
import co.krypt.krypton.protocol.MeResponse;
import co.krypt.krypton.protocol.NetworkMessage;
import co.krypt.krypton.protocol.Profile;
import co.krypt.krypton.protocol.ReadTeamRequest;
import co.krypt.krypton.protocol.Request;
import co.krypt.krypton.protocol.RequestBody;
import co.krypt.krypton.protocol.Response;
import co.krypt.krypton.protocol.SignRequest;
import co.krypt.krypton.protocol.SignResponse;
import co.krypt.krypton.protocol.SuccessOrTaggedErrorResult;
import co.krypt.krypton.protocol.TeamOperationRequest;
import co.krypt.krypton.protocol.U2FAuthenticateRequest;
import co.krypt.krypton.protocol.U2FAuthenticateResponse;
import co.krypt.krypton.protocol.U2FRegisterRequest;
import co.krypt.krypton.protocol.U2FRegisterResponse;
import co.krypt.krypton.protocol.UnpairRequest;
import co.krypt.krypton.protocol.UnpairResponse;
import co.krypt.krypton.protocol.UserAndHost;
import co.krypt.krypton.protocol.Versions;
import co.krypt.krypton.team.Native;
import co.krypt.krypton.team.Sigchain;
import co.krypt.krypton.team.TeamDataProvider;
import co.krypt.krypton.team.TeamService;
import co.krypt.krypton.team.TeamService.C;
import co.krypt.krypton.transport.BluetoothTransport;
import co.krypt.krypton.transport.SNSTransport;
import co.krypt.krypton.transport.SQSPoller;
import co.krypt.krypton.transport.SQSTransport;

/**
 * Created by Kevin King on 12/3/16.
 * Copyright 2016. KryptCo, Inc.
 */

public class Silo {
    private static final String TAG = "Silo";

    public static final String KNOWN_HOSTS_CHANGED_ACTION = "co.krypt.kryptonite.action.KNOWN_HOSTS_CHANGED";

    public static final long CLOCK_SKEW_TOLERANCE_SECONDS = 15*60;

    private static Silo singleton;

    private final Pairings pairingStorage;
    private final MeStorage meStorage;
    private Map<Pairing, SQSPoller> pollers = new HashMap<>();

    @Nullable
    private final BluetoothTransport bluetoothTransport;
    public final Context context;
    private final Map<Pairing, Long> lastRequestTimeSeconds = Collections.synchronizedMap(new HashMap<Pairing, Long>());
    private final LruCache<String, Response> responseMemCacheByRequestID = new LruCache<>(8192);
    @Nullable
    private DiskLruCache responseDiskCacheByRequestID;
    private final OpenDatabaseHelper dbHelper;

    private final Object pairingsLock = new Object();
    private final Object databaseLock = new Object();
    private final Object policyLock = new Object();

    private Silo(Context context) {
        this.context = context;
        pairingStorage = new Pairings(context);
        meStorage = new MeStorage(context);
        Set<Pairing> pairings = pairingStorage.loadAll();
        bluetoothTransport = BluetoothTransport.init(context);
        for (Pairing p : pairings) {
            if (bluetoothTransport != null) {
                bluetoothTransport.add(p);
            }
        }

        try {
             responseDiskCacheByRequestID = DiskLruCache.open(context.getCacheDir(), 0, 1, 2 << 19);
        } catch (IOException e) {
            e.printStackTrace();
        }
        dbHelper = OpenHelperManager.getHelper(context, OpenDatabaseHelper.class);
    }

    public static synchronized Silo shared(Context context) {
        if (singleton == null) {
            singleton = new Silo(context);
        }
        return singleton;
    }

    public boolean hasActivity(Pairing pairing) {
        return lastRequestTimeSeconds.get(pairing) != null;
    }

    public Pairings pairings() {
        return pairingStorage;
    }

    public MeStorage meStorage() {
        return meStorage;
    }

    public OpenDatabaseHelper dbHelper() { return dbHelper; }

    public void start() {
        synchronized (pairingsLock) {
            for (Pairing pairing : pairings().loadAll()) {
                Log.i(TAG, "starting "+ Base64.encodeAsString(pairing.workstationPublicKey));
                SQSPoller poller = pollers.remove(pairing);
                if (poller != null) {
                    poller.stop();
                }
                pollers.put(pairing, new SQSPoller(context, pairing));
            }
        }
    }

    public void stop() {
        Log.i(TAG, "stopping");
        synchronized (pairingsLock) {
            for (SQSPoller poller: pollers.values()) {
                poller.stop();
            }
            pollers.clear();
        }
    }

    public synchronized void exit() {
        if (bluetoothTransport != null) {
            bluetoothTransport.stop();
        }
        if (responseDiskCacheByRequestID != null && responseDiskCacheByRequestID.isClosed()) {
            try {
                responseDiskCacheByRequestID.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Pairing pair(Pairing pairing) throws CryptoException, TransportException {
        synchronized (pairingsLock) {
            Pairing oldPairing = pairings().getPairing(pairing.uuid);
            if (oldPairing != null) {
                Log.w(TAG, "already paired with " + pairing.workstationName);
                return oldPairing;
            }
            byte[] wrappedKey = pairing.wrapKey();
            NetworkMessage wrappedKeyMessage = new NetworkMessage(NetworkMessage.Header.WRAPPED_PUBLIC_KEY, wrappedKey);
            send(pairing, wrappedKeyMessage);

            pairingStorage.pair(pairing);
            pollers.put(pairing, new SQSPoller(context, pairing));
            if (bluetoothTransport != null) {
                bluetoothTransport.add(pairing);
                bluetoothTransport.send(pairing, wrappedKeyMessage);
            }
        }
        return pairing;
    }

    public void unpair(Pairing pairing, boolean sendResponse) {
        if (sendResponse) {
            Response unpairResponse = new Response();
            unpairResponse.requestID = "";
            unpairResponse.unpairResponse = new UnpairResponse();
            try {
                send(pairing, unpairResponse);
            } catch (CryptoException | TransportException e) {
                e.printStackTrace();
            }
        }
        synchronized (pairingsLock) {
            pairingStorage.unpair(pairing);
            SQSPoller poller = pollers.remove(pairing);
            if (poller != null) {
                poller.stop();
            }
            if (bluetoothTransport != null) {
                bluetoothTransport.remove(pairing);
            }
        }
    }

    public void unpairAll() {
        synchronized (pairingsLock) {
            for (Pairing pairing: pairings().loadAll()) {
                unpair(pairing, true);
            }
        }
    }

    private static final ExecutorService onMessageThreadPool = Executors.newFixedThreadPool(4);
    public void onMessage(UUID pairingUUID, byte[] incoming, String communicationMedium) {
        onMessageThreadPool.submit(() -> onMessageJob(pairingUUID, incoming, communicationMedium));
    }
    private void onMessageJob(UUID pairingUUID, byte[] incoming, String communicationMedium) {
        try {
            NetworkMessage message = NetworkMessage.parse(incoming);
            Pairing pairing = pairings().getPairing(pairingUUID);
            if (pairing == null) {
                Log.e(TAG, "not valid pairing: " + pairingUUID);
                return;
            }
            switch (message.header) {
                case CIPHERTEXT:
                    byte[] json = pairing.unseal(message.message);
                    Request request = JSON.fromJson(json, Request.class);
                    handle(pairing, request, communicationMedium);
                    break;
                case WRAPPED_KEY:
                    break;
                case WRAPPED_PUBLIC_KEY:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void send(Pairing pairing, Response response) throws CryptoException, TransportException {
        byte[] responseJson = JSON.toJson(response).getBytes();
        byte[] sealed = pairing.seal(responseJson);
        send(pairing, new NetworkMessage(NetworkMessage.Header.CIPHERTEXT, sealed));
    }

    private void send(final Pairing pairing, final NetworkMessage message) throws TransportException {
        if (bluetoothTransport != null) {
            bluetoothTransport.send(pairing, message);
        }
        SQSTransport.sendMessage(pairing, message);
    }

    private synchronized void trySetupCache() throws Unrecoverable {
        try {
            if (responseDiskCacheByRequestID == null || responseDiskCacheByRequestID.isClosed()) {
                responseDiskCacheByRequestID = DiskLruCache.open(context.getCacheDir(), 0, 1, 2 << 19);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new Unrecoverable(e);
        }
    }

    private synchronized boolean sendCachedResponseIfPresent(Pairing pairing, Request request) throws Unrecoverable {
        try {
            trySetupCache();
            if (responseDiskCacheByRequestID != null && !responseDiskCacheByRequestID.isClosed()) {
                DiskLruCache.Snapshot cacheEntry = responseDiskCacheByRequestID.get(request.requestIDCacheKey(pairing));
                if (cacheEntry != null) {
                    String cachedJSON = cacheEntry.getString(0);
                    if (cachedJSON != null) {
                        send(pairing, JSON.fromJson(cachedJSON, Response.class));
                        Log.i(TAG, "sent cached response to " + request.requestID);
                        return true;
                    } else {
                        Log.v(TAG, "no cache JSON");
                    }
                } else {
                    Log.v(TAG, "no cache entry");
                }
            }
            Response cachedResponse = responseMemCacheByRequestID.get(request.requestIDCacheKey(pairing));
            if (cachedResponse != null) {
                send(pairing, cachedResponse);
                Log.i(TAG, "sent memory cached response to " + request.requestID);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new Unrecoverable(e);
        }
        return false;
    }


    private void handle(Pairing pairing, Request request, String communicationMedium) throws Unrecoverable {
        //  Allow 15 minutes of clock skew
        if (Math.abs(request.unixSeconds - (System.currentTimeMillis() / 1000)) > CLOCK_SKEW_TOLERANCE_SECONDS) {
            throw new ProtocolException("invalid request time");
        }

        if (request.body instanceof UnpairRequest) {
            unpair(pairing, false);
            new Analytics(context).postEvent("device", "unpair", "request", null, false);
        }

        synchronized ((Silo.class.getName() + request.requestID).intern()) {
            if (sendCachedResponseIfPresent(pairing, request)) {
                return;
            }
        }

        EventBus.getDefault().post(new TeamService.UpdateTeamHomeDataIfOutdated(context));

        lastRequestTimeSeconds.put(pairing, System.currentTimeMillis() / 1000);

        if (Policy.isApprovedNow(this, context, pairing, request)) {
                respondToRequest(pairing, request, true);
                new Analytics(context).postEvent(request.analyticsCategory(), "automatic approval", communicationMedium, null, false);
        } else {
            if (Policy.requestApproval(context, pairing, request)) {
                new Analytics(context).postEvent(request.analyticsCategory(), "requires approval", communicationMedium, null, false);
            }
            if (request.sendACK != null && request.sendACK) {
                Response ackResponse = Response.with(request);
                ackResponse.ackResponse = new AckResponse();
                send(pairing, ackResponse);
            }
        }
    }

    public void respondToRequest(Pairing pairing, Request request, boolean requestAllowed) throws Unrecoverable {
        // Lock ordering: requestID -> Silo
        // Fine-grained locking allows performing signatures in parallel
        synchronized ((Silo.class.getName() + request.requestID).intern()) {
            synchronized (Silo.class) {
                if (sendCachedResponseIfPresent(pairing, request)) {
                    return;
                }
            }

            Response response = Response.with(request);
            Analytics analytics = new Analytics(context);
            if (analytics.isDisabled()) {
                response.trackingID = "disabled";
            } else {
                response.trackingID = analytics.getClientID();
            }

            request.body.visit(new RequestBody.Visitor<Void, Unrecoverable>() {
                @Override
                public Void visit(MeRequest meRequest) throws CryptoException {
                    if (meRequest.u2fOnly != null && meRequest.u2fOnly) {
                        response.meResponse = new MeResponse(new Profile());
                        String deviceName = null;
                        Profile profile = meStorage.load();
                        if (profile != null)  {
                            deviceName = profile.email;
                        }
                        if(TextUtils.isEmpty(deviceName)) {
                            deviceName = MeStorage.getDeviceName();
                        }
                        if (!TextUtils.isEmpty(deviceName)) {
                            response.meResponse.me.email = deviceName;
                        }
                        response.meResponse.me.deviceIdentifier = U2F.loadOrGenerateDeviceIdentifier();
                        HashSet<String> u2fAccounts = new HashSet<>();
                        for (U2F.KeyManager.Account account: U2F.getAccounts(context)) {
                            if (account.shortName != null) {
                                u2fAccounts.add(account.shortName);
                            }
                        }
                        response.meResponse.me.u2fAccounts = Lists.newArrayList(u2fAccounts).toArray(new String[]{});
                    } else {
                        response.meResponse = new MeResponse(meStorage.loadWithUserID(meRequest.userID()));
                    }
                    return null;
                }

                @Override
                public Void visit(SignRequest signRequest) throws Unrecoverable {
                    signRequest.validate();
                    response.signResponse = new SignResponse();
                    if (requestAllowed) {
                        try {
                            SSHKeyPairI key = MeStorage.getOrLoadKeyPair(context);
                            if (MessageDigest.isEqual(signRequest.publicKeyFingerprint, key.publicKeyFingerprint())) {
                                if (signRequest.verifyHostName()) {
                                    String hostName = signRequest.hostAuth.hostNames[0];
                                    String hostKey = Base64.encodeAsString(signRequest.hostAuth.hostKey);
                                    synchronized (databaseLock) {
                                        List<KnownHost> matchingKnownHosts = dbHelper.getKnownHostDao().queryForEq("host_name", hostName);
                                        try {
                                            Sigchain.NativeResult<List<Sigchain.PinnedHost>> teamPinnedHosts = TeamDataProvider.getPinnedKeysByHost(context, hostName);
                                            if (teamPinnedHosts.success != null) {
                                                for (Sigchain.PinnedHost pinnedHost : teamPinnedHosts.success) {
                                                    matchingKnownHosts.add(new KnownHost(pinnedHost.host, co.krypt.krypton.crypto.Base64.encode(pinnedHost.publicKey), 0));
                                                }
                                            }
                                        } catch (Native.NotLinked e) {
                                            e.printStackTrace();
                                        }
                                        if (matchingKnownHosts.size() == 0) {
                                            dbHelper.getKnownHostDao().create(new KnownHost(hostName, hostKey, System.currentTimeMillis() / 1000));
                                            broadcastKnownHostsChanged();
                                        } else {
                                            boolean foundKnownHost = false;
                                            List<String> pinnedPublicKeys = new ArrayList<>();
                                            for (KnownHost pinnedHost : matchingKnownHosts) {
                                                pinnedPublicKeys.add(pinnedHost.publicKey);
                                                if (pinnedHost.publicKey.equals(hostKey)) {
                                                    foundKnownHost = true;
                                                    break;
                                                }
                                            }
                                            if (!foundKnownHost) {
                                                throw new MismatchedHostKeyException(pinnedPublicKeys, "Expected " + pinnedPublicKeys.toString() + " received " + hostKey);
                                            }
                                        }
                                    }
                                }
                                String algo = signRequest.algo();
                                if ((key instanceof RSASSHKeyPair) && request.semVer().lessThan(Versions.KR_SUPPORTS_RSA_SHA256_512)) {
                                    algo = "ssh-rsa";
                                }
                                response.signResponse.signature = key.signDigestAppendingPubkey(signRequest.data, algo);
                                SSHSignatureLog log = new SSHSignatureLog(
                                        signRequest.data,
                                        true,
                                        signRequest.command,
                                        signRequest.user(),
                                        signRequest.firstHostnameIfExists(),
                                        System.currentTimeMillis() / 1000,
                                        signRequest.verifyHostName(),
                                        JSON.toJson(signRequest.hostAuth),
                                        pairing.getUUIDString(),
                                        pairing.workstationName);

                                co.krypt.krypton.team.log.Log teamLog = co.krypt.krypton.team.log.Log.fromSSHRequest(
                                        pairing,
                                        request,
                                        signRequest,
                                        co.krypt.krypton.team.log.Log.Body.SSH.Result.signature(response.signResponse.signature)
                                );
                                EventBus.getDefault().post(
                                        new TeamService.EncryptLog(
                                                C.background(context),
                                                teamLog
                                        )
                                );

                                pairingStorage.appendToSSHLog(log);
                                Notifications.notifySuccess(context, pairing, request, log);
                                if (signRequest.verifiedHostNameOrDefault("unknown host").equals("me.krypt.co")) {
                                    Intent sshMeIntent = new Intent(TestSSHFragment.SSH_ME_ACTION);
                                    LocalBroadcastManager.getInstance(context).sendBroadcast(sshMeIntent);
                                }
                                if (signRequest.hostAuth == null) {
                                    new Analytics(context).postEvent("host", "unknown", null, null, false);
                                } else if (!signRequest.verifyHostName()) {
                                    new Analytics(context).postEvent("host", "unverified", null, null, false);
                                }
                            } else {
                                Log.e(TAG, Base64.encodeAsString(signRequest.publicKeyFingerprint) + " != " + Base64.encodeAsString(key.publicKeyFingerprint()));
                                response.signResponse.error = "unknown key fingerprint";
                            }
                        } catch (SQLException e) {
                            StringWriter sw = new StringWriter();
                            PrintWriter pw = new PrintWriter(sw);
                            e.printStackTrace(pw);
                            response.signResponse.error = "SQL error: " + e.getMessage() + "\n" + sw.toString();
                            e.printStackTrace();
                        } catch (MismatchedHostKeyException e) {
                            response.signResponse.error = "host public key mismatched";

                            if (signRequest.hostAuth.hostKey != null) {
                                co.krypt.krypton.team.log.Log teamLog = co.krypt.krypton.team.log.Log.fromSSHRequest(
                                        pairing,
                                        request,
                                        signRequest,
                                        co.krypt.krypton.team.log.Log.Body.SSH.Result.hostMismatch(e.pinnedPublicKeys.toArray(new String[]{}))
                                );
                                EventBus.getDefault().post(
                                        new TeamService.EncryptLog(
                                                C.background(context),
                                                teamLog
                                        )
                                );
                            }

                            Notifications.notifyReject(context, pairing, request, "Host public key mismatched.");
                            e.printStackTrace();
                        }
                    } else {
                        response.signResponse.error = "rejected";
                        co.krypt.krypton.team.log.Log teamLog = co.krypt.krypton.team.log.Log.fromSSHRequest(
                                pairing,
                                request,
                                signRequest,
                                co.krypt.krypton.team.log.Log.Body.SSH.Result.userRejected()
                        );
                        EventBus.getDefault().post(
                                new TeamService.EncryptLog(
                                        C.background(context),
                                        teamLog
                                )
                        );

                        pairingStorage.appendToSSHLog(new SSHSignatureLog(
                                signRequest.data,
                                false,
                                signRequest.command,
                                signRequest.user(),
                                signRequest.firstHostnameIfExists(),
                                System.currentTimeMillis() / 1000,
                                signRequest.verifyHostName(),
                                JSON.toJson(signRequest.hostAuth),
                                pairing.getUUIDString(),
                                pairing.workstationName));
                    }
                    return null;
                }

                @Override
                public Void visit(GitSignRequest gitSignRequest) throws Unrecoverable {
                    if (requestAllowed) {
                        try {
                            SSHKeyPairI key = MeStorage.getOrLoadKeyPair(context);
                            new MeStorage(context).loadWithUserID(UserID.parse(gitSignRequest.userID));
                            co.krypt.krypton.team.log.Log.Body.GitSignatureResult gitSignatureResult = new co.krypt.krypton.team.log.Log.Body.GitSignatureResult();
                            co.krypt.krypton.log.Log log =
                                    gitSignRequest.body.visit(new GitSignRequestBody.Visitor<co.krypt.krypton.log.Log, Exception>() {
                                        @Override
                                        public co.krypt.krypton.log.Log visit(CommitInfo commit) throws Unrecoverable {
                                            byte[] signature = SignableUtils.signBinaryDocument(commit, key, HashAlgorithm.SHA512);
                                            response.gitSignResponse = new GitSignResponse(
                                                    signature,
                                                    null
                                            );
                                            gitSignatureResult.signature = signature;
                                            GitCommitSignatureLog commitLog = new GitCommitSignatureLog(
                                                    pairing,
                                                    commit,
                                                    new AsciiArmor(
                                                            AsciiArmor.HeaderLine.SIGNATURE,
                                                            AsciiArmor.backwardsCompatibleHeaders(request.semVer()),
                                                            signature
                                                    ).toString()
                                            );
                                            pairings().appendToCommitLogs(
                                                    commitLog
                                            );
                                            return commitLog;
                                        }

                                        @Override
                                        public co.krypt.krypton.log.Log visit(TagInfo tag) throws Unrecoverable {
                                            byte[] signature = SignableUtils.signBinaryDocument(tag, key, HashAlgorithm.SHA512);
                                            response.gitSignResponse = new GitSignResponse(
                                                    signature,
                                                    null
                                            );
                                            gitSignatureResult.signature = signature;
                                            GitTagSignatureLog tagLog = new GitTagSignatureLog(
                                                    pairing,
                                                    tag,
                                                    new AsciiArmor(
                                                            AsciiArmor.HeaderLine.SIGNATURE,
                                                            AsciiArmor.backwardsCompatibleHeaders(request.semVer()),
                                                            signature
                                                    ).toString()
                                            );
                                            pairings().appendToTagLogs(
                                                    tagLog
                                            );
                                            return tagLog;
                                        }
                                    });
                            co.krypt.krypton.team.log.Log teamLog = co.krypt.krypton.team.log.Log.fromGitRequest(
                                    pairing,
                                    request,
                                    gitSignRequest,
                                    gitSignatureResult
                            );
                            EventBus.getDefault().post(
                                    new TeamService.EncryptLog(
                                            C.background(context),
                                            teamLog
                                    )
                            );
                            Notifications.notifySuccess(context, pairing, request, log);
                        } catch (Exception e) {
                            e.printStackTrace();
                            response.gitSignResponse = new GitSignResponse(null, "unknown error");
                        }
                    } else {
                        response.gitSignResponse = new GitSignResponse(null, "rejected");
                        gitSignRequest.body.visit(new GitSignRequestBody.Visitor<Void, RuntimeException>() {

                            @Override
                            public Void visit(CommitInfo commit) throws RuntimeException {
                                pairings().appendToCommitLogs(
                                        new GitCommitSignatureLog(
                                                pairing,
                                                commit,
                                                null
                                        )
                                );
                                return null;
                            }

                            @Override
                            public Void visit(TagInfo tag) throws RuntimeException {
                                pairings().appendToTagLogs(
                                        new GitTagSignatureLog(
                                                pairing,
                                                tag,
                                                null
                                        )
                                );
                                return null;
                            }
                        });
                        co.krypt.krypton.team.log.Log teamLog = co.krypt.krypton.team.log.Log.fromGitRequest(
                                pairing,
                                request,
                                gitSignRequest,
                                co.krypt.krypton.team.log.Log.Body.GitSignatureResult.userRejected()
                        );
                        EventBus.getDefault().post(
                                new TeamService.EncryptLog(
                                        C.background(context),
                                        teamLog
                                )
                        );
                    }
                    return null;
                }

                @Override
                public Void visit(UnpairRequest unpairRequest) throws Unrecoverable {
                    //  Processed in handle()
                    return null;
                }

                @Override
                public Void visit(HostsRequest hostsRequest) throws Unrecoverable {
                    HostsResponse hostsResponse = new HostsResponse();

                    synchronized (databaseLock) {
                        try {
                            List<SSHSignatureLog> sshLogs = dbHelper.getSSHSignatureLogDao().queryBuilder()
                                    .groupByRaw("user, host_name").query();
                            List<UserAndHost> userAndHosts = new LinkedList<>();
                            for (SSHSignatureLog log : sshLogs) {
                                UserAndHost userAndHost = new UserAndHost();
                                userAndHost.user = log.user;
                                userAndHost.host = log.hostName;
                                userAndHosts.add(userAndHost);
                            }

                            HostInfo hostInfo = new HostInfo();
                            UserAndHost[] userAndHostsArray = new UserAndHost[userAndHosts.size()];
                            userAndHosts.toArray(userAndHostsArray);
                            hostInfo.hosts = userAndHostsArray;

                            List<UserID> userIDs = meStorage.getUserIDs();
                            List<String> userIDStrings = new LinkedList<>();
                            for (UserID userID : userIDs) {
                                userIDStrings.add(userID.toString());
                            }
                            String[] userIDArray = new String[userIDs.size()];
                            userIDStrings.toArray(userIDArray);
                            hostInfo.pgpUserIDs = userIDArray;

                            hostsResponse.hostInfo = hostInfo;

                        } catch (SQLException e1) {
                            hostsResponse.error = "sql exception";
                        }
                    }

                    response.hostsResponse = hostsResponse;
                    return null;
                }

                @Override
                public Void visit(ReadTeamRequest readTeamRequest) throws Unrecoverable {
                    SuccessOrTaggedErrorResult<JsonObject> result = new SuccessOrTaggedErrorResult<>();
                    response.readTeamResponse = result;
                    if (requestAllowed) {
                        try {
                            Sigchain.NativeResult<JsonObject> readToken = TeamDataProvider.signReadToken(context, readTeamRequest.publicKey);
                            if (readToken.success != null) {
                                result.success = readToken.success;
                            } else {
                                result.error = readToken.error;
                            }
                        } catch (Native.NotLinked e) {
                            e.printStackTrace();
                            result.error = e.getMessage();
                        }
                    } else {
                        response.readTeamResponse.error = "rejected";
                    }
                    return null;
                }

                @Override
                public Void visit(LogDecryptionRequest logDecryptionRequest) throws Unrecoverable {
                    SuccessOrTaggedErrorResult<JsonObject> result = new SuccessOrTaggedErrorResult<>();
                    response.logDecryptionResponse = result;
                    if (requestAllowed) {
                        try {
                            Sigchain.NativeResult<JsonObject> unwrappedKey = TeamDataProvider.unwrapKey(context, logDecryptionRequest.wrappedKey);
                            if (unwrappedKey.success != null) {
                                result.success = unwrappedKey.success;
                            } else {
                                result.error = unwrappedKey.error;
                            }
                        } catch (Native.NotLinked e) {
                            e.printStackTrace();
                            result.error = e.getMessage();
                        }
                    } else {
                        response.logDecryptionResponse.error = "rejected";
                    }
                    return null;
                }

                @Override
                public Void visit(TeamOperationRequest teamOperationRequest) throws Unrecoverable {
                    SuccessOrTaggedErrorResult<Sigchain.TeamOperationResponse> result = new SuccessOrTaggedErrorResult<>();
                    response.teamOperationResponse = result;
                    if (requestAllowed) {
                        try {
                            Sigchain.NativeResult<Sigchain.TeamOperationResponse> nativeResult = TeamDataProvider.requestTeamOperation(context, teamOperationRequest.operation);
                            if (nativeResult.success != null) {
                                response.teamOperationResponse.success = nativeResult.success;
                            } else {
                                response.teamOperationResponse.error = nativeResult.error;
                            }
                        } catch (Native.NotLinked e) {
                            e.printStackTrace();
                            result.error = e.getMessage();
                        }
                    } else {
                        response.teamOperationResponse.error = "rejected";
                    }
                    return null;
                }

                @Override
                public Void visit(U2FRegisterRequest u2FRegisterRequest) throws Unrecoverable {
                    SuccessOrTaggedErrorResult<U2FRegisterResponse> result = new SuccessOrTaggedErrorResult<>();
                    response.u2fRegisterResponse = result;
                    if (requestAllowed) {
                        U2F.KeyPair keyPair = U2F.KeyManager.generateAccountKeyPair(context, u2FRegisterRequest.appId);

                        result.success = keyPair.signU2FRegisterRequest(u2FRegisterRequest);
                        Notifications.notifySuccess(context, pairing, request, null);

                        U2FSignatureLog log = new U2FSignatureLog(u2FRegisterRequest, pairing);
                        pairingStorage.appendToU2FLog(log);
                    } else {
                        response.u2fRegisterResponse.error = "rejected";
                    }
                    return null;
                }

                @Override
                public Void visit(U2FAuthenticateRequest u2FAuthenticateRequest) throws Unrecoverable {
                    SuccessOrTaggedErrorResult<U2FAuthenticateResponse> result = new SuccessOrTaggedErrorResult<>();
                    response.u2fAuthenticateResponse = result;
                    if (requestAllowed) {
                        U2F.KeyPair keyPair = U2F.KeyManager.loadAccountKeyPair(context, u2FAuthenticateRequest.keyHandle);

                        result.success = keyPair.signU2FAuthenticateRequest(u2FAuthenticateRequest);
                        Notifications.notifySuccess(context, pairing, request, null);

                        U2FSignatureLog log = new U2FSignatureLog(u2FAuthenticateRequest, pairing);
                        pairingStorage.appendToU2FLog(log);
                    } else {
                        response.u2fAuthenticateResponse.error = "rejected";
                    }
                    return null;
                }
            });

            response.snsEndpointARN = SNSTransport.getInstance(context).getEndpointARN();
            synchronized (Silo.class) {
                try {
                    if (responseDiskCacheByRequestID != null && !responseDiskCacheByRequestID.isClosed()) {
                        DiskLruCache.Editor cacheEditor = responseDiskCacheByRequestID.edit(request.requestIDCacheKey(pairing));
                        cacheEditor.set(0, JSON.toJson(response));
                        cacheEditor.commit();
                        responseDiskCacheByRequestID.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new Unrecoverable(e);
                }
                responseMemCacheByRequestID.put(request.requestIDCacheKey(pairing), response);
            }

            send(pairing, response);
        }
    }

    public List<KnownHost> getKnownHosts() throws SQLException {
        synchronized (databaseLock) {
            return dbHelper.getKnownHostDao().queryForAll();
        }
    }

    public void deleteKnownHost(String hostName) throws SQLException {
        synchronized (databaseLock) {
            List<KnownHost> matchingHosts = dbHelper.getKnownHostDao().queryForEq("host_name", hostName);
            if (matchingHosts.size() == 0) {
                Log.e(TAG, "host to delete not found");
                return;
            }
            dbHelper.getKnownHostDao().delete(matchingHosts.get(0));
        }
        broadcastKnownHostsChanged();
    }

    public boolean hasKnownHost(String hostName) {
        synchronized (databaseLock) {
            try {
                if (dbHelper.getKnownHostDao().queryForEq("host_name", hostName).size() > 0) {
                    return true;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            try {
                Sigchain.NativeResult<List<Sigchain.PinnedHost>> teamResult = TeamDataProvider.getPinnedKeysByHost(context, hostName);
                return (teamResult.success != null && teamResult.success.size() > 0);
            } catch (Native.NotLinked notLinked) {
                notLinked.printStackTrace();
            }
            return false;
        }
    }

    private void broadcastKnownHostsChanged() {
        Intent intent = new Intent(KNOWN_HOSTS_CHANGED_ACTION);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

}
