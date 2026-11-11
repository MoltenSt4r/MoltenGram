package app.exteraless.chats

import app.exteraless.OpenExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_account
import java.util.concurrent.ConcurrentHashMap

/**
 * P2P profile metadata sync via 100% invisible Unicode characters embedded in user's last_name.
 *
 * Encodes/decodes:
 * - Emoji status documentId (Long)
 * - Profile color ID (Int) & background emoji ID (Long)
 * - Name / quote / reply color ID (Int) & background emoji ID (Long)
 */
object ProfileInvisibleMetadata {

    data class Metadata(
        var emojiStatusDocId: Long = 0L,
        var nameColorId: Int = -1,
        var nameEmojiId: Long = 0L,
        var profileColorId: Int = -1,
        var profileEmojiId: Long = 0L
    ) {
        val isEmpty: Boolean
            get() = emojiStatusDocId <= 0L && nameColorId < 0 && nameEmojiId <= 0L && profileColorId < 0 && profileEmojiId <= 0L
    }

    // Framing markers using invisible characters (ZWJ + Word Joiner)
    const val START_TAG = "\u200D\u2060"
    const val END_TAG = "\u2060\u200D"

    // Invisible field keys
    const val KEY_STATUS = '\u206A'
    const val KEY_NAME_COLOR = '\u206B'
    const val KEY_NAME_EMOJI = '\u206C'
    const val KEY_PROFILE_COLOR = '\u206D'
    const val KEY_PROFILE_EMOJI = '\u206E'

    // Delimiter between fields (Zero Width Space)
    const val DELIMITER = '\u200B'

    private val TAG_REGEX = Regex(
        Regex.escape(START_TAG) + "([\\uFE00-\\uFE0F\\u200B\\u206A-\\u206E]+?)" + Regex.escape(END_TAG)
    )

    private val userMetaCache = ConcurrentHashMap<Long, Pair<String?, Metadata?>>()

    /**
     * Encodes a positive number into invisible Variation Selectors (VS1..VS16: U+FE00..U+FE0F) via hex digits.
     */
    @JvmStatic
    fun encodeNumber(num: Long): String {
        if (num <= 0L) return ""
        val hex = java.lang.Long.toHexString(num)
        val sb = StringBuilder(hex.length)
        for (i in 0 until hex.length) {
            val digit = Character.digit(hex[i], 16)
            if (digit in 0..15) {
                sb.append((0xFE00 + digit).toChar())
            }
        }
        return sb.toString()
    }

    /**
     * Decodes a sequence of Variation Selectors back into a positive Long number.
     */
    @JvmStatic
    fun decodeNumber(str: String): Long {
        if (str.isEmpty()) return 0L
        val hex = StringBuilder(str.length)
        for (i in 0 until str.length) {
            val code = str[i].code
            if (code in 0xFE00..0xFE0F) {
                hex.append(Integer.toHexString(code - 0xFE00))
            } else {
                return 0L
            }
        }
        return try {
            java.lang.Long.parseUnsignedLong(hex.toString(), 16)
        } catch (_: Exception) {
            0L
        }
    }

    @JvmStatic
    fun encode(meta: Metadata): String {
        if (meta.isEmpty) return ""
        val parts = mutableListOf<String>()
        if (meta.emojiStatusDocId > 0L) {
            parts.add("" + KEY_STATUS + encodeNumber(meta.emojiStatusDocId))
        }
        if (meta.nameColorId >= 0) {
            parts.add("" + KEY_NAME_COLOR + encodeNumber(meta.nameColorId.toLong() + 1L))
        }
        if (meta.nameEmojiId > 0L) {
            parts.add("" + KEY_NAME_EMOJI + encodeNumber(meta.nameEmojiId))
        }
        if (meta.profileColorId >= 0) {
            parts.add("" + KEY_PROFILE_COLOR + encodeNumber(meta.profileColorId.toLong() + 1L))
        }
        if (meta.profileEmojiId > 0L) {
            parts.add("" + KEY_PROFILE_EMOJI + encodeNumber(meta.profileEmojiId))
        }
        if (parts.isEmpty()) return ""

        val payload = StringBuilder()
        for (i in parts.indices) {
            if (i > 0) payload.append(DELIMITER)
            payload.append(parts[i])
        }
        return START_TAG + payload.toString() + END_TAG
    }

    @JvmStatic
    fun decode(text: String?): Metadata? {
        if (text.isNullOrEmpty()) return null
        val match = TAG_REGEX.find(text) ?: return null
        val payload = match.groupValues[1]
        val meta = Metadata()

        val tokens = payload.split(DELIMITER)
        for (tok in tokens) {
            if (tok.length < 2) continue
            val type = tok[0]
            val valueStr = tok.substring(1)
            val num = decodeNumber(valueStr)
            when (type) {
                KEY_STATUS -> meta.emojiStatusDocId = num
                KEY_NAME_COLOR -> meta.nameColorId = (num - 1L).toInt()
                KEY_NAME_EMOJI -> meta.nameEmojiId = num
                KEY_PROFILE_COLOR -> meta.profileColorId = (num - 1L).toInt()
                KEY_PROFILE_EMOJI -> meta.profileEmojiId = num
            }
        }
        return if (meta.isEmpty) null else meta
    }

    @JvmStatic
    fun cleanLastName(lastName: String?): String {
        if (lastName.isNullOrEmpty()) return ""
        return TAG_REGEX.replace(lastName, "").trimEnd()
    }

    @JvmStatic
    fun formatLastName(visibleLastName: String?, meta: Metadata?): String {
        val base = cleanLastName(visibleLastName)
        if (meta == null || meta.isEmpty) return base
        val tag = encode(meta)
        return if (base.isEmpty()) tag else "$base$tag"
    }

    @JvmStatic
    fun getMetadata(user: TLRPC.User?): Metadata? {
        if (user == null) return null
        val lastName = user.last_name
        val cached = userMetaCache[user.id]
        if (cached != null && cached.first == lastName) {
            val meta = cached.second
            applyMetaToUser(user, meta)
            return meta
        }
        val meta = decode(lastName)
        userMetaCache[user.id] = Pair(lastName, meta)
        applyMetaToUser(user, meta)
        return meta
    }

    private fun applyMetaToUser(user: TLRPC.User, meta: Metadata?) {
        if (meta == null || meta.isEmpty) return
        if (OpenExteraConfig.customEmojiStatus() && meta.emojiStatusDocId > 0L && user.emoji_status == null) {
            val status = TLRPC.TL_emojiStatus()
            status.document_id = meta.emojiStatusDocId
            user.emoji_status = status
            user.flags2 = user.flags2 or 1
        }
        if (OpenExteraConfig.freeNameColor() && meta.nameColorId >= 0 && user.color == null) {
            val peerColor = TLRPC.TL_peerColor()
            peerColor.flags = 1
            peerColor.color = meta.nameColorId
            if (meta.nameEmojiId > 0L) {
                peerColor.flags = peerColor.flags or 2
                peerColor.background_emoji_id = meta.nameEmojiId
            }
            user.color = peerColor
        }
        if (OpenExteraConfig.freeProfileAppearance() && meta.profileColorId >= 0 && user.profile_color == null) {
            val peerColor = TLRPC.TL_peerColor()
            peerColor.flags = 1
            peerColor.color = meta.profileColorId
            if (meta.profileEmojiId > 0L) {
                peerColor.flags = peerColor.flags or 2
                peerColor.background_emoji_id = meta.profileEmojiId
            }
            user.profile_color = peerColor
        }
    }

    @JvmStatic
    fun invalidateUser(userId: Long) {
        userMetaCache.remove(userId)
    }

    @JvmStatic
    fun getEmojiStatusDocumentId(user: TLRPC.User?): Long? {
        if (!OpenExteraConfig.customEmojiStatus()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.emojiStatusDocId > 0L) meta.emojiStatusDocId else null
    }

    @JvmStatic
    fun getNameColorId(user: TLRPC.User?): Int? {
        if (!OpenExteraConfig.freeNameColor()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.nameColorId >= 0) meta.nameColorId else null
    }

    @JvmStatic
    fun getNameEmojiId(user: TLRPC.User?): Long? {
        if (!OpenExteraConfig.freeNameColor()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.nameEmojiId > 0L) meta.nameEmojiId else null
    }

    @JvmStatic
    fun getProfileColorId(user: TLRPC.User?): Int? {
        if (!OpenExteraConfig.freeProfileAppearance()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.profileColorId >= 0) meta.profileColorId else null
    }

    @JvmStatic
    fun getProfileEmojiId(user: TLRPC.User?): Long? {
        if (!OpenExteraConfig.freeProfileAppearance()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.profileEmojiId > 0L) meta.profileEmojiId else null
    }

    @JvmStatic
    fun updateSelfColors(
        account: Int,
        nameColor: Int,
        nameEmoji: Long,
        profileColor: Int,
        profileEmoji: Long,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val user = UserConfig.getInstance(account).currentUser ?: return
        val currentMeta = decode(user.last_name) ?: Metadata()
        currentMeta.nameColorId = nameColor
        currentMeta.nameEmojiId = nameEmoji
        currentMeta.profileColorId = profileColor
        currentMeta.profileEmojiId = profileEmoji

        saveSelfMetadata(account, user, currentMeta, callback)
    }

    @JvmStatic
    fun updateSelfEmojiStatus(
        account: Int,
        documentId: Long,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val user = UserConfig.getInstance(account).currentUser ?: return
        val currentMeta = decode(user.last_name) ?: Metadata()
        currentMeta.emojiStatusDocId = documentId

        saveSelfMetadata(account, user, currentMeta, callback)
    }

    private fun saveSelfMetadata(
        account: Int,
        user: TLRPC.User,
        meta: Metadata,
        callback: ((Boolean) -> Unit)?
    ) {
        val newLastName = formatLastName(user.last_name, meta)
        user.last_name = newLastName
        userMetaCache[user.id] = Pair(newLastName, meta)

        // Apply locally immediately
        if (meta.emojiStatusDocId > 0L) {
            val status = TLRPC.TL_emojiStatus()
            status.document_id = meta.emojiStatusDocId
            user.emoji_status = status
            user.flags2 = user.flags2 or 1
        } else if (meta.emojiStatusDocId == 0L && user.emoji_status != null && !user.premium) {
            user.emoji_status = null
            user.flags2 = user.flags2 and 1.inv()
        }
        if (meta.nameColorId >= 0) {
            if (user.color == null) user.color = TLRPC.TL_peerColor()
            user.color.flags = user.color.flags or 1
            user.color.color = meta.nameColorId
            if (meta.nameEmojiId > 0L) {
                user.color.flags = user.color.flags or 2
                user.color.background_emoji_id = meta.nameEmojiId
            }
        }
        if (meta.profileColorId >= 0) {
            if (user.profile_color == null) user.profile_color = TLRPC.TL_peerColor()
            user.profile_color.flags = user.profile_color.flags or 1
            user.profile_color.color = meta.profileColorId
            if (meta.profileEmojiId > 0L) {
                user.profile_color.flags = user.profile_color.flags or 2
                user.profile_color.background_emoji_id = meta.profileEmojiId
            }
        } else if (user.profile_color != null && !user.premium) {
            user.profile_color.flags = user.profile_color.flags and 1.inv()
        }
        MessagesController.getInstance(account).putUser(user, true)

        val req = TL_account.updateProfile()
        req.flags = 2
        req.first_name = user.first_name ?: ""
        req.last_name = newLastName

        ConnectionsManager.getInstance(account).sendRequest(req) { _, error ->
            val success = error == null
            AndroidUtilities.runOnUIThread {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_EMOJI_STATUS or MessagesController.UPDATE_MASK_NAME
                )
                callback?.invoke(success)
            }
        }
    }
}
