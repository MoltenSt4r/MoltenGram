package app.exteraless.chats

import app.exteraless.OpenExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.Vector
import org.telegram.tgnet.tl.TL_bots
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Синхронизация невидимых метаданных профиля через служебного бота пользователя (@BotFather).
 *
 * Схема адресации (требует @username, так как MTProto не позволяет получить Bio чужого бота только по bot_id без access_hash):
 * - Имя бота: mgs<base36(ownerUserId)>_<base36(seed)>_bot
 * - В фамилии пользователя хранится только короткий невидимый seed (10 символов V3_BOT_REF).
 * - Полные метаданные V3 хранятся в about (до 120 символов) и description (до 512 символов) этого бота через TL_bots.setBotInfo.
 */
object BotMetadataSyncHelper {

    enum class ErrorType {
        NONE,
        NOT_AUTHORIZED,
        ALREADY_IN_PROGRESS,
        BOT_LIMIT_EXCEEDED,
        SPAM_BLOCK,
        BOTFATHER_BLOCKED,
        FLOOD_WAIT,
        BOTFATHER_REJECTED,
        TIMEOUT,
        NETWORK_ERROR
    }

    data class BotSetupResult(
        val success: Boolean,
        val botUser: TLRPC.User? = null,
        val botUsername: String = "",
        val seed: Int = 0,
        val errorType: ErrorType = ErrorType.NONE,
        val rawDetail: String = ""
    ) {
        fun formatUserMessage(): String {
            return when (errorType) {
                ErrorType.BOT_LIMIT_EXCEEDED -> LocaleController.getString(R.string.OEBotProfileSyncErrLimit)
                ErrorType.SPAM_BLOCK -> LocaleController.getString(R.string.OEBotProfileSyncErrSpam)
                ErrorType.BOTFATHER_BLOCKED -> LocaleController.getString(R.string.OEBotProfileSyncErrBlocked)
                ErrorType.FLOOD_WAIT -> LocaleController.formatString(
                    R.string.OEBotProfileSyncErrFlood,
                    rawDetail.ifEmpty { "FLOOD_WAIT" }
                )
                ErrorType.NONE -> ""
                else -> LocaleController.formatString(
                    R.string.OEBotProfileSyncErrGeneric,
                    rawDetail.ifEmpty { errorType.name }
                )
            }
        }
    }

    private const val PREF_BOT_ID_PREFIX = "bot_sync_id_"
    private const val PREF_BOT_USER_PREFIX = "bot_sync_username_"
    private const val PREF_BOT_SEED_PREFIX = "bot_sync_seed_"
    private const val PREF_PEER_BOT_ID_PREFIX = "bot_sync_peer_bot_id_"
    private const val PREF_PEER_META_PREFIX = "bot_sync_peer_meta_"
    private const val PREF_PEER_SEED_PREFIX = "bot_sync_peer_seed_"

    private const val CACHE_TTL_MS = 180_000L
    private const val NEGATIVE_TTL_MS = 120_000L
    private const val POLL_INTERVAL_MS = 650L
    private const val POLL_TIMEOUT_MS = 10_000L
    private const val MAX_USERNAME_RETRIES = 3

    private val setupInProgress = ConcurrentHashMap<Long, Boolean>()
    private val inFlightFetches = ConcurrentHashMap<Long, Boolean>()
    private val activeFetchCount = AtomicInteger(0)
    private const val MAX_CONCURRENT_FETCHES = 2
    @Volatile
    private var globalFloodUntilMs: Long = 0L

    private data class CachedPeerBotMeta(
        val seed: Int,
        val meta: ProfileInvisibleMetadata.Metadata?,
        val fetchedAtMs: Long,
        val isError: Boolean = false
    )

    private val peerMetaCache = ConcurrentHashMap<Long, CachedPeerBotMeta>()

    @JvmStatic
    fun buildBotUsername(ownerUserId: Long, seed: Int): String {
        val uid36 = java.lang.Long.toUnsignedString(ownerUserId, 36).lowercase()
        val seed36 = (seed and 0xFFFF).toString(36).lowercase()
        return "mgs${uid36}_${seed36}_bot"
    }

    @JvmStatic
    fun parseSeedFromUsername(ownerUserId: Long, username: String?): Int? {
        if (ownerUserId == 0L || username.isNullOrBlank()) return null
        val uid36 = java.lang.Long.toUnsignedString(ownerUserId, 36).lowercase()
        val lower = username.trim().removePrefix("@").lowercase()
        val prefix = "mgs${uid36}_"
        val suffix = "_bot"
        if (!lower.startsWith(prefix) || !lower.endsWith(suffix)) return null
        val middle = lower.substring(prefix.length, lower.length - suffix.length)
        if (middle.isEmpty()) return null
        val parsed = middle.toIntOrNull(36) ?: return null
        return if (parsed in 0..0xFFFF) parsed else null
    }

    @JvmStatic
    fun getSavedSelfBotSeed(ownerUserId: Long): Int? {
        if (ownerUserId == 0L) return null
        val prefs = OpenExteraConfig.getPreferences()
        val seed = prefs.getInt(PREF_BOT_SEED_PREFIX + ownerUserId, -1)
        return if (seed in 0..0xFFFF) seed else null
    }

    @JvmStatic
    fun getSavedSelfBotUsername(ownerUserId: Long): String? {
        if (ownerUserId == 0L) return null
        return OpenExteraConfig.getPreferences().getString(PREF_BOT_USER_PREFIX + ownerUserId, null)
            ?.takeIf { it.isNotBlank() }
    }

    @JvmStatic
    fun getSavedSelfBotId(ownerUserId: Long): Long {
        if (ownerUserId == 0L) return 0L
        return OpenExteraConfig.getPreferences().getLong(PREF_BOT_ID_PREFIX + ownerUserId, 0L)
    }

    private fun saveSelfBot(ownerUserId: Long, botUser: TLRPC.User, seed: Int) {
        val username = botUser.username ?: buildBotUsername(ownerUserId, seed)
        OpenExteraConfig.getPreferences().edit()
            .putLong(PREF_BOT_ID_PREFIX + ownerUserId, botUser.id)
            .putString(PREF_BOT_USER_PREFIX + ownerUserId, username)
            .putInt(PREF_BOT_SEED_PREFIX + ownerUserId, seed)
            .apply()
    }

    private fun clearSavedSelfBot(ownerUserId: Long) {
        OpenExteraConfig.getPreferences().edit()
            .remove(PREF_BOT_ID_PREFIX + ownerUserId)
            .remove(PREF_BOT_USER_PREFIX + ownerUserId)
            .remove(PREF_BOT_SEED_PREFIX + ownerUserId)
            .apply()
    }

    @JvmStatic
    fun getCachedPeerBotMetadata(ownerUserId: Long, expectedSeed: Int): ProfileInvisibleMetadata.Metadata? {
        if (ownerUserId == 0L) return null
        val mem = peerMetaCache[ownerUserId]
        if (mem != null && mem.seed == expectedSeed && !mem.isError) {
            return mem.meta
        }
        val prefs = OpenExteraConfig.getPreferences()
        val savedSeed = prefs.getInt(PREF_PEER_SEED_PREFIX + ownerUserId, -1)
        if (savedSeed == expectedSeed) {
            val raw = prefs.getString(PREF_PEER_META_PREFIX + ownerUserId, null)
            val decoded = ProfileInvisibleMetadata.decode(raw)
            if (decoded != null) {
                peerMetaCache[ownerUserId] = CachedPeerBotMeta(
                    seed = expectedSeed,
                    meta = decoded,
                    fetchedAtMs = System.currentTimeMillis() - CACHE_TTL_MS / 2
                )
                return decoded
            }
        }
        return null
    }

    @JvmStatic
    fun buildBotBioPayload(meta: ProfileInvisibleMetadata.Metadata?): String {
        if (meta == null || meta.isEmpty) {
            return "MoltenGram Profile Sync"
        }
        // Keep visible prefix so Telegram never strips trailing Variation Selectors as empty bio.
        val encoded = ProfileInvisibleMetadata.encodeV3Direct(meta)
        return if (encoded.isEmpty()) "MoltenGram Profile Sync" else "MoltenGram Sync $encoded"
    }

    /**
     * Синхронная проверка кэша и асинхронное обновление метаданных из @mgs...bot собеседника.
     */
    @JvmStatic
    fun fetchPeerBotMetadataIfNeeded(account: Int, ownerUserId: Long, seed: Int) {
        if (ownerUserId == 0L || seed !in 0..0xFFFF) return
        if (!OpenExteraConfig.customEmojiStatus() && !OpenExteraConfig.freeProfileAppearance()) return
        val now = System.currentTimeMillis()
        if (now < globalFloodUntilMs) return

        val mem = peerMetaCache[ownerUserId]
        if (mem != null && mem.seed == seed) {
            val ttl = if (mem.isError) NEGATIVE_TTL_MS else CACHE_TTL_MS
            if (now - mem.fetchedAtMs < ttl) return
        }

        if (inFlightFetches.putIfAbsent(ownerUserId, true) != null) return
        if (activeFetchCount.get() >= MAX_CONCURRENT_FETCHES) {
            inFlightFetches.remove(ownerUserId)
            return
        }
        activeFetchCount.incrementAndGet()

        val finish: () -> Unit = {
            activeFetchCount.decrementAndGet()
            inFlightFetches.remove(ownerUserId)
        }

        val mc = MessagesController.getInstance(account)
        val cachedBotId = OpenExteraConfig.getPreferences().getLong(PREF_PEER_BOT_ID_PREFIX + ownerUserId, 0L)
        val cachedBotUser = if (cachedBotId != 0L) mc.getUser(cachedBotId) else null
        val targetUsername = buildBotUsername(ownerUserId, seed)

        if (cachedBotUser != null && cachedBotUser.access_hash != 0L &&
            cachedBotUser.username != null && cachedBotUser.username.equals(targetUsername, ignoreCase = true)
        ) {
            loadPeerBotFull(account, ownerUserId, seed, cachedBotUser, allowResolveFallback = true, finish = finish)
            return
        }

        val resolveReq = TLRPC.TL_contacts_resolveUsername()
        resolveReq.username = targetUsername
        ConnectionsManager.getInstance(account).sendRequest(resolveReq) { response, error ->
            if (error != null) {
                handlePeerFetchError(error, ownerUserId, seed)
                finish()
                return@sendRequest
            }
            val resolved = response as? TLRPC.TL_contacts_resolvedPeer
            val botUser = resolved?.users?.firstOrNull { it.bot && it.username.equals(targetUsername, ignoreCase = true) }
                ?: resolved?.users?.firstOrNull { it.bot }
            if (botUser == null) {
                peerMetaCache[ownerUserId] = CachedPeerBotMeta(seed, null, System.currentTimeMillis(), isError = true)
                finish()
                return@sendRequest
            }
            AndroidUtilities.runOnUIThread {
                mc.putUser(botUser, false)
            }
            OpenExteraConfig.getPreferences().edit()
                .putLong(PREF_PEER_BOT_ID_PREFIX + ownerUserId, botUser.id)
                .apply()
            loadPeerBotFull(account, ownerUserId, seed, botUser, allowResolveFallback = false, finish = finish)
        }
    }

    private fun handlePeerFetchError(error: TLRPC.TL_error, ownerUserId: Long, seed: Int) {
        val text = error.text ?: ""
        if (text.startsWith("FLOOD_WAIT_")) {
            val sec = text.removePrefix("FLOOD_WAIT_").toLongOrNull() ?: 30L
            globalFloodUntilMs = System.currentTimeMillis() + (sec.coerceAtLeast(5L) * 1000L)
        }
        peerMetaCache[ownerUserId] = CachedPeerBotMeta(seed, null, System.currentTimeMillis(), isError = true)
    }

    private fun loadPeerBotFull(
        account: Int,
        ownerUserId: Long,
        seed: Int,
        botUser: TLRPC.User,
        allowResolveFallback: Boolean,
        finish: () -> Unit
    ) {
        val fullReq = TLRPC.TL_users_getFullUser()
        val inputUser = TLRPC.TL_inputUser()
        inputUser.user_id = botUser.id
        inputUser.access_hash = botUser.access_hash
        fullReq.id = inputUser

        ConnectionsManager.getInstance(account).sendRequest(fullReq) { response, error ->
            if (error != null) {
                if (allowResolveFallback) {
                    OpenExteraConfig.getPreferences().edit()
                        .remove(PREF_PEER_BOT_ID_PREFIX + ownerUserId)
                        .apply()
                }
                handlePeerFetchError(error, ownerUserId, seed)
                finish()
                return@sendRequest
            }
            val fullRes = response as? TLRPC.TL_users_userFull
            val fullUser = fullRes?.full_user
            val aboutText = fullUser?.about
            val descText = fullUser?.bot_info?.description
            val meta = ProfileInvisibleMetadata.decode(aboutText)
                ?: ProfileInvisibleMetadata.decode(descText)
                ?: ProfileInvisibleMetadata.Metadata()

            peerMetaCache[ownerUserId] = CachedPeerBotMeta(seed, meta, System.currentTimeMillis(), isError = false)
            val prefsEditor = OpenExteraConfig.getPreferences().edit()
                .putInt(PREF_PEER_SEED_PREFIX + ownerUserId, seed)
            if (!meta.isEmpty) {
                prefsEditor.putString(PREF_PEER_META_PREFIX + ownerUserId, ProfileInvisibleMetadata.encodeV3Direct(meta))
            } else {
                prefsEditor.remove(PREF_PEER_META_PREFIX + ownerUserId)
            }
            prefsEditor.apply()

            if (meta.emojiStatusDocId != 0L) {
                CustomEmojiStatusHelper.prefetchEmoji(account, meta.emojiStatusDocId)
            }
            if (meta.nameEmojiId != 0L) {
                CustomEmojiStatusHelper.prefetchEmoji(account, meta.nameEmojiId)
            }
            if (meta.profileEmojiId != 0L) {
                CustomEmojiStatusHelper.prefetchEmoji(account, meta.profileEmojiId)
            }

            ProfileInvisibleMetadata.invalidateUser(ownerUserId)
            AndroidUtilities.runOnUIThread {
                val mc = MessagesController.getInstance(account)
                val ownerUser = mc.getUser(ownerUserId)
                if (ownerUser != null) {
                    ProfileInvisibleMetadata.getMetadata(ownerUser)
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, ownerUser)
                }
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_EMOJI_STATUS or MessagesController.UPDATE_MASK_NAME
                )
            }
            finish()
        }
    }

    /**
     * Записывает актуальные метаданные владельца в about/description привязанного бота.
     */
    @JvmStatic
    fun syncMetadataToBot(
        account: Int,
        meta: ProfileInvisibleMetadata.Metadata,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val currentUser = UserConfig.getInstance(account).currentUser
        if (currentUser == null) {
            callback?.invoke(false)
            return
        }
        val botId = getSavedSelfBotId(currentUser.id)
        val mc = MessagesController.getInstance(account)
        val cachedBot = if (botId != 0L) mc.getUser(botId) else null
        if (cachedBot != null && cachedBot.access_hash != 0L) {
            sendSetBotInfo(account, currentUser.id, cachedBot, meta, callback)
            return
        }
        // Recover bot user if app cache cleared.
        findOwnedSyncBot(account, currentUser.id) { botUser, _ ->
            if (botUser != null) {
                val seed = parseSeedFromUsername(currentUser.id, botUser.username) ?: 0
                saveSelfBot(currentUser.id, botUser, seed)
                sendSetBotInfo(account, currentUser.id, botUser, meta, callback)
            } else {
                callback?.invoke(false)
            }
        }
    }

    private fun sendSetBotInfo(
        account: Int,
        ownerUserId: Long,
        botUser: TLRPC.User,
        meta: ProfileInvisibleMetadata.Metadata,
        callback: ((Boolean) -> Unit)?
    ) {
        val payload = buildBotBioPayload(meta)
        val req = TL_bots.setBotInfo()
        val inputBot = TLRPC.TL_inputUser()
        inputBot.user_id = botUser.id
        inputBot.access_hash = botUser.access_hash
        req.bot = inputBot
        req.lang_code = ""
        req.about = payload
        req.flags = 4 or 1

        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            if (error != null) {
                FileLog.d("BotMetadataSyncHelper setBotInfo failed: ${error.code} ${error.text}")
                callback?.invoke(false)
                return@sendRequest
            }
            val ok = response is TLRPC.TL_boolTrue
            if (ok) {
                val seed = parseSeedFromUsername(ownerUserId, botUser.username)
                    ?: getSavedSelfBotSeed(ownerUserId) ?: 0
                peerMetaCache[ownerUserId] = CachedPeerBotMeta(seed, meta, System.currentTimeMillis(), isError = false)
            }
            callback?.invoke(ok)
        }
    }

    private fun findOwnedSyncBot(
        account: Int,
        ownerUserId: Long,
        callback: (TLRPC.User?, TLRPC.TL_error?) -> Unit
    ) {
        val req = TL_bots.getAdminedBots()
        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            if (error != null) {
                callback(null, error)
                return@sendRequest
            }
            val mc = MessagesController.getInstance(account)
            var matched: TLRPC.User? = null
            if (response is Vector<*>) {
                for (obj in response.objects) {
                    val u = obj as? TLRPC.User ?: continue
                    if (u.bot && parseSeedFromUsername(ownerUserId, u.username) != null) {
                        matched = u
                        AndroidUtilities.runOnUIThread { mc.putUser(u, false) }
                        break
                    }
                }
            }
            callback(matched, null)
        }
    }

    /**
     * Проверяет, создан ли бот синхронизации (или создаёт через @BotFather).
     * Если @BotFather отказывает (лимит 20 ботов, спам-блок, флуд и т.д.),
     * возвращает BotSetupResult(success = false) и НЕ позволяет включить фичу.
     */
    @JvmStatic
    fun ensureBotReady(
        account: Int,
        allowInteractiveBotFather: Boolean,
        callback: (BotSetupResult) -> Unit
    ) {
        val self = UserConfig.getInstance(account).currentUser
        if (self == null || !UserConfig.getInstance(account).isClientActivated) {
            AndroidUtilities.runOnUIThread {
                callback(BotSetupResult(false, errorType = ErrorType.NOT_AUTHORIZED))
            }
            return
        }
        val ownerId = self.id
        if (setupInProgress.putIfAbsent(ownerId, true) != null) {
            AndroidUtilities.runOnUIThread {
                callback(BotSetupResult(false, errorType = ErrorType.ALREADY_IN_PROGRESS))
            }
            return
        }

        fun finish(result: BotSetupResult) {
            setupInProgress.remove(ownerId)
            AndroidUtilities.runOnUIThread { callback(result) }
        }

        // 1. Check existing admined bots first (avoids messaging @BotFather if bot already exists).
        findOwnedSyncBot(account, ownerId) { existingBot, adminErr ->
            if (existingBot != null) {
                val seed = parseSeedFromUsername(ownerId, existingBot.username) ?: 0
                saveSelfBot(ownerId, existingBot, seed)
                val initialMeta = ProfileInvisibleMetadata.getSelfMetadataSnapshot(account, self)
                sendSetBotInfo(account, ownerId, existingBot, initialMeta) { ok ->
                    if (ok) {
                        finish(BotSetupResult(true, existingBot, existingBot.username ?: "", seed))
                    } else if (!allowInteractiveBotFather) {
                        clearSavedSelfBot(ownerId)
                        finish(BotSetupResult(false, errorType = ErrorType.BOTFATHER_REJECTED, rawDetail = "SET_BOT_INFO_FAILED"))
                    } else {
                        clearSavedSelfBot(ownerId)
                        startBotFatherWizard(account, ownerId, ::finish)
                    }
                }
                return@findOwnedSyncBot
            }

            clearSavedSelfBot(ownerId)
            if (!allowInteractiveBotFather) {
                val errType = if (adminErr != null) ErrorType.NETWORK_ERROR else ErrorType.BOTFATHER_REJECTED
                finish(BotSetupResult(false, errorType = errType, rawDetail = adminErr?.text ?: "NO_EXISTING_BOT"))
                return@findOwnedSyncBot
            }
            startBotFatherWizard(account, ownerId, ::finish)
        }
    }

    private fun startBotFatherWizard(
        account: Int,
        ownerId: Long,
        finish: (BotSetupResult) -> Unit
    ) {
        val resolveReq = TLRPC.TL_contacts_resolveUsername()
        resolveReq.username = "BotFather"
        ConnectionsManager.getInstance(account).sendRequest(resolveReq) { response, error ->
            if (error != null) {
                finish(mapRpcErrorToResult(error))
                return@sendRequest
            }
            val resolved = response as? TLRPC.TL_contacts_resolvedPeer
            val botFather = resolved?.users?.firstOrNull { it.bot && it.username.equals("BotFather", ignoreCase = true) }
                ?: resolved?.users?.firstOrNull { it.bot }
            if (botFather == null || botFather.access_hash == 0L) {
                finish(BotSetupResult(false, errorType = ErrorType.BOTFATHER_REJECTED, rawDetail = "BOTFATHER_NOT_FOUND"))
                return@sendRequest
            }
            AndroidUtilities.runOnUIThread {
                MessagesController.getInstance(account).putUser(botFather, false)
            }

            val peer = TLRPC.TL_inputPeerUser()
            peer.user_id = botFather.id
            peer.access_hash = botFather.access_hash

            // Get latest message ID so we only inspect fresh BotFather replies.
            fetchLastMessageId(account, peer) { lastMsgId, histErr ->
                if (histErr != null) {
                    finish(mapRpcErrorToResult(histErr))
                    return@fetchLastMessageId
                }
                sendNewBotCommand(account, ownerId, peer, lastMsgId, finish)
            }
        }
    }

    private fun sendNewBotCommand(
        account: Int,
        ownerId: Long,
        botFatherPeer: TLRPC.InputPeer,
        baselineMsgId: Int,
        finish: (BotSetupResult) -> Unit
    ) {
        sendAndWaitBotFatherReply(account, botFatherPeer, "/newbot", baselineMsgId) { reply, rpcErr ->
            if (rpcErr != null) {
                finish(rpcErr)
                return@sendAndWaitBotFatherReply
            }
            val replyText = reply?.message ?: ""
            val lower = replyText.lowercase()
            detectBotFatherTextError(lower)?.let { errResult ->
                sendQuietCancel(account, botFatherPeer)
                finish(errResult)
                return@sendAndWaitBotFatherReply
            }

            // BotFather expects bot display name ("Alright, a new bot. How are we going to call it?...")
            if (!lower.contains("new bot") && !lower.contains("call it") && !lower.contains("choose a name")) {
                sendQuietCancel(account, botFatherPeer)
                finish(BotSetupResult(false, errorType = ErrorType.BOTFATHER_REJECTED, rawDetail = replyText.take(80)))
                return@sendAndWaitBotFatherReply
            }

            sendAndWaitBotFatherReply(account, botFatherPeer, "MoltenGram Sync", reply!!.id) { nameReply, nameErr ->
                if (nameErr != null) {
                    sendQuietCancel(account, botFatherPeer)
                    finish(nameErr)
                    return@sendAndWaitBotFatherReply
                }
                val nameText = nameReply?.message ?: ""
                val nameLower = nameText.lowercase()
                detectBotFatherTextError(nameLower)?.let { errResult ->
                    sendQuietCancel(account, botFatherPeer)
                    finish(errResult)
                    return@sendAndWaitBotFatherReply
                }
                if (!nameLower.contains("username") && !nameLower.contains("good")) {
                    sendQuietCancel(account, botFatherPeer)
                    finish(BotSetupResult(false, errorType = ErrorType.BOTFATHER_REJECTED, rawDetail = nameText.take(80)))
                    return@sendAndWaitBotFatherReply
                }

                trySubmitBotUsername(account, ownerId, botFatherPeer, nameReply!!.id, attempt = 0, finish = finish)
            }
        }
    }

    private fun trySubmitBotUsername(
        account: Int,
        ownerId: Long,
        botFatherPeer: TLRPC.InputPeer,
        afterMsgId: Int,
        attempt: Int,
        finish: (BotSetupResult) -> Unit
    ) {
        val seed = if (attempt == 0) {
            0
        } else {
            1 + Utilities.random.nextInt(0xFFFE)
        }
        val candidateUsername = buildBotUsername(ownerId, seed)
        sendAndWaitBotFatherReply(account, botFatherPeer, candidateUsername, afterMsgId) { reply, rpcErr ->
            if (rpcErr != null) {
                sendQuietCancel(account, botFatherPeer)
                finish(rpcErr)
                return@sendAndWaitBotFatherReply
            }
            val text = reply?.message ?: ""
            val lower = text.lowercase()
            detectBotFatherTextError(lower)?.let { errResult ->
                sendQuietCancel(account, botFatherPeer)
                finish(errResult)
                return@sendAndWaitBotFatherReply
            }

            if (lower.contains("done! congratulations") || lower.contains("token to access")) {
                // Created! Resolve bot user via getAdminedBots / resolveUsername and verify setBotInfo.
                val resolveReq = TLRPC.TL_contacts_resolveUsername()
                resolveReq.username = candidateUsername
                ConnectionsManager.getInstance(account).sendRequest(resolveReq) { resObj, resErr ->
                    val resolved = resObj as? TLRPC.TL_contacts_resolvedPeer
                    val botUser = resolved?.users?.firstOrNull { it.bot && it.username.equals(candidateUsername, ignoreCase = true) }
                        ?: resolved?.users?.firstOrNull { it.bot }
                    if (resErr != null || botUser == null) {
                        findOwnedSyncBot(account, ownerId) { owned, _ ->
                            if (owned != null) {
                                val actualSeed = parseSeedFromUsername(ownerId, owned.username) ?: seed
                                finalizeCreatedBot(account, ownerId, owned, actualSeed, finish)
                            } else {
                                finish(BotSetupResult(false, errorType = ErrorType.NETWORK_ERROR, rawDetail = resErr?.text ?: "BOT_RESOLVE_FAILED"))
                            }
                        }
                        return@sendRequest
                    }
                    AndroidUtilities.runOnUIThread {
                        MessagesController.getInstance(account).putUser(botUser, false)
                    }
                    finalizeCreatedBot(account, ownerId, botUser, seed, finish)
                }
                return@sendAndWaitBotFatherReply
            }

            val usernameRejected = lower.contains("already taken") ||
                lower.contains("username is invalid") ||
                lower.contains("must end in")
            if (usernameRejected && attempt + 1 < MAX_USERNAME_RETRIES) {
                trySubmitBotUsername(account, ownerId, botFatherPeer, reply!!.id, attempt + 1, finish)
                return@sendAndWaitBotFatherReply
            }

            sendQuietCancel(account, botFatherPeer)
            finish(BotSetupResult(false, errorType = ErrorType.BOTFATHER_REJECTED, rawDetail = text.take(80)))
        }
    }

    private fun finalizeCreatedBot(
        account: Int,
        ownerId: Long,
        botUser: TLRPC.User,
        seed: Int,
        finish: (BotSetupResult) -> Unit
    ) {
        saveSelfBot(ownerId, botUser, seed)
        val self = UserConfig.getInstance(account).currentUser
        val meta = if (self != null) ProfileInvisibleMetadata.getSelfMetadataSnapshot(account, self) else ProfileInvisibleMetadata.Metadata()
        sendSetBotInfo(account, ownerId, botUser, meta) { ok ->
            if (ok) {
                finish(BotSetupResult(true, botUser, botUser.username ?: buildBotUsername(ownerId, seed), seed))
            } else {
                clearSavedSelfBot(ownerId)
                finish(BotSetupResult(false, errorType = ErrorType.BOTFATHER_REJECTED, rawDetail = "SET_BOT_INFO_FAILED"))
            }
        }
    }

    private fun fetchLastMessageId(
        account: Int,
        peer: TLRPC.InputPeer,
        callback: (Int, TLRPC.TL_error?) -> Unit
    ) {
        val req = TLRPC.TL_messages_getHistory()
        req.peer = peer
        req.limit = 1
        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            if (error != null) {
                callback(0, error)
                return@sendRequest
            }
            val msgs = (response as? TLRPC.messages_Messages)?.messages
            val topId = msgs?.firstOrNull()?.id ?: 0
            callback(topId, null)
        }
    }

    private fun sendAndWaitBotFatherReply(
        account: Int,
        peer: TLRPC.InputPeer,
        text: String,
        baselineMsgId: Int,
        callback: (TLRPC.Message?, BotSetupResult?) -> Unit
    ) {
        val req = TLRPC.TL_messages_sendMessage()
        req.peer = peer
        req.message = text
        req.random_id = Utilities.random.nextLong().let { if (it == 0L) 1L else it }
        req.no_webpage = true
        req.silent = true

        ConnectionsManager.getInstance(account).sendRequest(req) { _, error ->
            if (error != null) {
                callback(null, mapRpcErrorToResult(error))
                return@sendRequest
            }
            val startedAt = System.currentTimeMillis()
            pollForBotFatherReply(account, peer, baselineMsgId, startedAt, callback)
        }
    }

    private fun pollForBotFatherReply(
        account: Int,
        peer: TLRPC.InputPeer,
        baselineMsgId: Int,
        startedAtMs: Long,
        callback: (TLRPC.Message?, BotSetupResult?) -> Unit
    ) {
        if (System.currentTimeMillis() - startedAtMs > POLL_TIMEOUT_MS) {
            callback(null, BotSetupResult(false, errorType = ErrorType.TIMEOUT, rawDetail = "BOTFATHER_TIMEOUT"))
            return
        }
        AndroidUtilities.runOnUIThread({
            val req = TLRPC.TL_messages_getHistory()
            req.peer = peer
            req.limit = 6
            ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
                if (error != null) {
                    callback(null, mapRpcErrorToResult(error))
                    return@sendRequest
                }
                val msgs = (response as? TLRPC.messages_Messages)?.messages
                val incoming = msgs
                    ?.filter { !it.out && it.id > baselineMsgId && !it.message.isNullOrEmpty() }
                    ?.maxByOrNull { it.id }
                if (incoming != null) {
                    callback(incoming, null)
                } else {
                    pollForBotFatherReply(account, peer, baselineMsgId, startedAtMs, callback)
                }
            }
        }, POLL_INTERVAL_MS)
    }

    private fun sendQuietCancel(account: Int, peer: TLRPC.InputPeer) {
        val req = TLRPC.TL_messages_sendMessage()
        req.peer = peer
        req.message = "/cancel"
        req.random_id = Utilities.random.nextLong().let { if (it == 0L) 2L else it }
        req.no_webpage = true
        req.silent = true
        ConnectionsManager.getInstance(account).sendRequest(req) { _, _ -> }
    }

    private fun detectBotFatherTextError(lower: String): BotSetupResult? {
        if (lower.contains("more than") && lower.contains("bot") ||
            lower.contains("too many bots") ||
            lower.contains("delete a bot") && lower.contains("create")
        ) {
            return BotSetupResult(false, errorType = ErrorType.BOT_LIMIT_EXCEEDED, rawDetail = "BOTFATHER_20_LIMIT")
        }
        if (lower.contains("flood") ||
            lower.contains("try again later") ||
            lower.contains("too many attempts") ||
            lower.contains("please wait")
        ) {
            return BotSetupResult(false, errorType = ErrorType.FLOOD_WAIT, rawDetail = "BOTFATHER_RATE_LIMIT")
        }
        if (lower.contains("restricted") || lower.contains("spam")) {
            return BotSetupResult(false, errorType = ErrorType.SPAM_BLOCK, rawDetail = "BOTFATHER_SPAM_RESTRICTION")
        }
        return null
    }

    private fun mapRpcErrorToResult(error: TLRPC.TL_error): BotSetupResult {
        val text = error.text ?: ""
        return when {
            text.equals("PEER_FLOOD", ignoreCase = true) ||
                text.equals("CHAT_WRITE_FORBIDDEN", ignoreCase = true) ->
                BotSetupResult(false, errorType = ErrorType.SPAM_BLOCK, rawDetail = text)
            text.equals("USER_IS_BLOCKED", ignoreCase = true) ||
                text.equals("YOU_BLOCKED_USER", ignoreCase = true) ->
                BotSetupResult(false, errorType = ErrorType.BOTFATHER_BLOCKED, rawDetail = text)
            text.equals("BOT_CREATE_LIMIT_EXCEEDED", ignoreCase = true) ->
                BotSetupResult(false, errorType = ErrorType.BOT_LIMIT_EXCEEDED, rawDetail = text)
            text.startsWith("FLOOD_WAIT_", ignoreCase = true) ->
                BotSetupResult(false, errorType = ErrorType.FLOOD_WAIT, rawDetail = text)
            else ->
                BotSetupResult(false, errorType = ErrorType.NETWORK_ERROR, rawDetail = text.ifEmpty { "RPC_${error.code}" })
        }
    }
}
