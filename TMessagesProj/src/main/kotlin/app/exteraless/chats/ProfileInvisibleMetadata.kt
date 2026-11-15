package app.exteraless.chats

import app.exteraless.OpenExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_account
import java.util.concurrent.ConcurrentHashMap

/**
 * P2P profile metadata sync via invisible Unicode Variation Selectors (U+FE00..U+FE0F).
 *
 * V3 compact bit-packed codec:
 * - Direct V3 (U+FE0B U+FE0C + kind 0x3 + 8-bit mask + packed fields + CRC4 + U+FE0C U+FE0B):
 *   stores up to all 7 fields in ~78 chars (used inside companion bot bio, or subset in last_name).
 * - Bot Reference V3 (U+FE0B U+FE0C + kind 0x4 + 16-bit seed + CRC4 + U+FE0C U+FE0B):
 *   10 invisible chars in user.last_name pointing to @mgs<base36(uid)>_<base36(seed)>_bot.
 * - Backward compatible decoding for legacy V2 and V1 tags.
 *
 * Never reads or writes the user's personal Bio (userFull.about).
 */
object ProfileInvisibleMetadata {

    const val MAX_LAST_NAME_LENGTH = 64
    private const val MAX_FIRST_NAME_LENGTH = 64
    private const val PREF_SELF_META_PREFIX = "profile_invisible_meta_self_"

    data class Metadata(
        var emojiStatusDocId: Long = 0L,
        var nameColorId: Int = -1,
        var nameEmojiId: Long = 0L,
        var profileColorId: Int = -1,
        var profileEmojiId: Long = 0L,
        var qrBackdropId: Int = -1,
        var qrEmojiId: Long = 0L,
        var emojiStatusCollectible: Boolean = false
    ) {
        val isEmpty: Boolean
            get() = emojiStatusDocId == 0L && nameColorId < 0 && nameEmojiId == 0L &&
                profileColorId < 0 && profileEmojiId == 0L && qrBackdropId < 0 && qrEmojiId == 0L

        fun copy(): Metadata = Metadata(
            emojiStatusDocId = emojiStatusDocId,
            nameColorId = nameColorId,
            nameEmojiId = nameEmojiId,
            profileColorId = profileColorId,
            profileEmojiId = profileEmojiId,
            qrBackdropId = qrBackdropId,
            qrEmojiId = qrEmojiId,
            emojiStatusCollectible = emojiStatusCollectible
        )
    }

    // V3 compact binary encoding in U+FE00..U+FE0F (16 values = 4 bits per UTF-16 char).
    const val V3_START_TAG = "\uFE0B\uFE0C"
    const val V3_END_TAG = "\uFE0C\uFE0B"
    private const val V3_KIND_METADATA = 0x3
    private const val V3_KIND_BOT_REF = 0x4

    private val V3_TAG_REGEX = Regex(
        Regex.escape(V3_START_TAG) + "([\\uFE00-\\uFE0F]{3,}?)" + Regex.escape(V3_END_TAG)
    )
    private val V3_ORPHAN_REGEX = Regex(
        Regex.escape(V3_START_TAG) + "[\\uFE00-\\uFE0F]{3,}"
    )

    // V2 base-14 encoding (kept for backward-compatible decoding & cleanup)
    const val V2_START_TAG = "\uFE0F\uFE0E"
    const val V2_END_TAG = "\uFE0E\uFE0F"
    const val V2_DELIMITER = '\uFE0E'

    private const val V2_KEY_STATUS = '\uFE00'
    private const val V2_KEY_NAME_COLOR = '\uFE01'
    private const val V2_KEY_NAME_EMOJI = '\uFE02'
    private const val V2_KEY_PROFILE_COLOR = '\uFE03'
    private const val V2_KEY_PROFILE_EMOJI = '\uFE04'
    private const val V2_KEY_QR_BACKDROP = '\uFE05'
    private const val V2_KEY_QR_EMOJI = '\uFE06'
    private const val V2_KEY_COLLECTIBLE_STATUS = '\uFE07'

    private val V2_TAG_REGEX = Regex(
        Regex.escape(V2_START_TAG) + "([\\uFE00-\\uFE0E]+?)" + Regex.escape(V2_END_TAG)
    )
    private val V2_ORPHAN_REGEX = Regex(
        Regex.escape(V2_START_TAG) + "[\\uFE00-\\uFE0E]{3,}"
    )

    // Legacy V1 framing markers (kept for backward-compatible decoding & cleanup)
    const val START_TAG = "\u200D\u2060"
    const val END_TAG = "\u2060\u200D"

    const val KEY_STATUS = '\u206A'
    const val KEY_NAME_COLOR = '\u206B'
    const val KEY_NAME_EMOJI = '\u206C'
    const val KEY_PROFILE_COLOR = '\u206D'
    const val KEY_PROFILE_EMOJI = '\u206E'
    const val KEY_QR_BACKDROP = '\u2062'
    const val KEY_QR_EMOJI = '\u2063'

    const val DELIMITER = '\u200B'

    private val TAG_REGEX = Regex(
        Regex.escape(START_TAG) + "([\\uFE00-\\uFE0F\\u200B\\u2061-\\u206E]+?)" + Regex.escape(END_TAG)
    )
    private val LEGACY_ORPHAN_REGEX = Regex(
        Regex.escape(START_TAG) + "[\\uFE00-\\uFE0F\\u200B-\\u200D\\u2060-\\u206E]{3,}"
    )

    private val userMetaCache = ConcurrentHashMap<Long, Pair<String?, Metadata?>>()
    private val appliedFakeStatusDocId = ConcurrentHashMap<Long, Long>()
    private val appliedFakeNameColor = ConcurrentHashMap<Long, Pair<Int, Long>>()
    private val appliedFakeProfileColor = ConcurrentHashMap<Long, Pair<Int, Long>>()

    private fun nibbleToChar(n: Int): Char = (0xFE00 + (n and 0xF)).toChar()

    private fun charToNibble(c: Char): Int {
        val code = c.code
        return if (code in 0xFE00..0xFE0F) code - 0xFE00 else -1
    }

    private fun computeCrc4(nibbles: List<Int>): Int {
        var h = 0x5
        for (n in nibbles) {
            h = ((h * 5) + n + 3) and 0xF
        }
        return h
    }

    private fun appendIntSmallOrExt(out: MutableList<Int>, value: Int) {
        val v = value.coerceAtLeast(0)
        if (v < 0xFF) {
            out.add((v ushr 4) and 0xF)
            out.add(v and 0xF)
        } else {
            out.add(0xF)
            out.add(0xF)
            val clamped = v.coerceAtMost(0xFFFF)
            out.add((clamped ushr 12) and 0xF)
            out.add((clamped ushr 8) and 0xF)
            out.add((clamped ushr 4) and 0xF)
            out.add(clamped and 0xF)
        }
    }

    private fun readIntSmallOrExt(nibbles: List<Int>, idxRef: IntArray): Int? {
        val i = idxRef[0]
        if (i + 2 > nibbles.size) return null
        val hi = nibbles[i]
        val lo = nibbles[i + 1]
        val b = (hi shl 4) or lo
        if (b < 0xFF) {
            idxRef[0] = i + 2
            return b
        }
        if (i + 6 > nibbles.size) return null
        val ext = (nibbles[i + 2] shl 12) or (nibbles[i + 3] shl 8) or (nibbles[i + 4] shl 4) or nibbles[i + 5]
        idxRef[0] = i + 6
        return ext
    }

    private fun appendU64Nibbles(out: MutableList<Int>, value: Long) {
        for (shift in 60 downTo 0 step 4) {
            out.add(((value ushr shift) and 0xFL).toInt())
        }
    }

    private fun readU64Nibbles(nibbles: List<Int>, idxRef: IntArray): Long? {
        val i = idxRef[0]
        if (i + 16 > nibbles.size) return null
        var value = 0L
        for (k in 0 until 16) {
            value = (value shl 4) or nibbles[i + k].toLong()
        }
        idxRef[0] = i + 16
        return value
    }

    @JvmStatic
    fun encodeBotRef(seed: Int): String {
        val s = seed and 0xFFFF
        val data = listOf(
            V3_KIND_BOT_REF,
            (s ushr 12) and 0xF,
            (s ushr 8) and 0xF,
            (s ushr 4) and 0xF,
            s and 0xF
        )
        val crc = computeCrc4(data)
        return buildString(10) {
            append(V3_START_TAG)
            for (n in data) append(nibbleToChar(n))
            append(nibbleToChar(crc))
            append(V3_END_TAG)
        }
    }

    @JvmStatic
    fun decodeBotRef(text: String?): Int? {
        if (text.isNullOrEmpty()) return null
        for (match in V3_TAG_REGEX.findAll(text)) {
            val raw = match.groupValues[1]
            if (raw.length != 6) continue
            val nibbles = ArrayList<Int>(6)
            var valid = true
            for (ch in raw) {
                val n = charToNibble(ch)
                if (n < 0) {
                    valid = false
                    break
                }
                nibbles.add(n)
            }
            if (!valid || nibbles[0] != V3_KIND_BOT_REF) continue
            val expectedCrc = computeCrc4(nibbles.subList(0, 5))
            if (nibbles[5] != expectedCrc) continue
            return (nibbles[1] shl 12) or (nibbles[2] shl 8) or (nibbles[3] shl 4) or nibbles[4]
        }
        return null
    }

    @JvmStatic
    fun encodeV3Direct(meta: Metadata): String {
        if (meta.isEmpty) return ""
        val mask =
            (if (meta.emojiStatusDocId != 0L) 0x01 else 0) or
                (if (meta.emojiStatusDocId != 0L && meta.emojiStatusCollectible) 0x02 else 0) or
                (if (meta.nameColorId >= 0) 0x04 else 0) or
                (if (meta.nameEmojiId != 0L) 0x08 else 0) or
                (if (meta.profileColorId >= 0) 0x10 else 0) or
                (if (meta.profileEmojiId != 0L) 0x20 else 0) or
                (if (meta.qrBackdropId >= 0) 0x40 else 0) or
                (if (meta.qrEmojiId != 0L) 0x80 else 0)
        if (mask == 0) return ""

        val data = ArrayList<Int>(74)
        data.add(V3_KIND_METADATA)
        data.add((mask ushr 4) and 0xF)
        data.add(mask and 0xF)
        if ((mask and 0x01) != 0) appendU64Nibbles(data, meta.emojiStatusDocId)
        if ((mask and 0x04) != 0) appendIntSmallOrExt(data, meta.nameColorId)
        if ((mask and 0x08) != 0) appendU64Nibbles(data, meta.nameEmojiId)
        if ((mask and 0x10) != 0) appendIntSmallOrExt(data, meta.profileColorId)
        if ((mask and 0x20) != 0) appendU64Nibbles(data, meta.profileEmojiId)
        if ((mask and 0x40) != 0) appendIntSmallOrExt(data, meta.qrBackdropId)
        if ((mask and 0x80) != 0) appendU64Nibbles(data, meta.qrEmojiId)
        val crc = computeCrc4(data)

        return buildString(data.size + 5) {
            append(V3_START_TAG)
            for (n in data) append(nibbleToChar(n))
            append(nibbleToChar(crc))
            append(V3_END_TAG)
        }
    }

    /**
     * Encodes a non-zero unsigned 64-bit value into Variation Selectors U+FE00..U+FE0D (base-14).
     */
    @JvmStatic
    fun encodeBase14(num: Long): String {
        if (num == 0L) return ""
        val digits = StringBuilder()
        var temp = num
        while (temp != 0L) {
            val rem = java.lang.Long.remainderUnsigned(temp, 14L).toInt()
            digits.append((0xFE00 + rem).toChar())
            temp = java.lang.Long.divideUnsigned(temp, 14L)
        }
        return digits.reverse().toString()
    }

    /**
     * Decodes a base-14 Variation Selectors sequence back into a Long value.
     */
    @JvmStatic
    fun decodeBase14(str: String): Long {
        if (str.isEmpty()) return 0L
        var value = 0L
        for (i in 0 until str.length) {
            val code = str[i].code
            if (code in 0xFE00..0xFE0D) {
                value = value * 14L + (code - 0xFE00).toLong()
            } else {
                return 0L
            }
        }
        return value
    }

    /**
     * Decodes legacy hex Variation Selectors back into a Long number.
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
    fun encodeEmojiStatusTag(documentId: Long, isCollectible: Boolean = false): String {
        if (documentId == 0L) return ""
        val meta = Metadata(emojiStatusDocId = documentId, emojiStatusCollectible = isCollectible)
        return encodeV3Direct(meta)
    }

    @JvmStatic
    fun encode(meta: Metadata): String = encodeWithLimit(meta, Int.MAX_VALUE)

    @JvmStatic
    fun encodeWithLimit(meta: Metadata, maxLen: Int): String {
        if (meta.isEmpty || maxLen < 10) return ""
        val full = encodeV3Direct(meta)
        if (full.length <= maxLen) return full

        // Prioritize global status, name/profile colors, QR backdrop, then large 16-char emoji IDs.
        val subset = Metadata()
        val tryAdd = { applyField: () -> Unit, revertField: () -> Unit ->
            applyField()
            if (encodeV3Direct(subset).length > maxLen) {
                revertField()
            }
        }

        if (meta.emojiStatusDocId != 0L) {
            val prevDoc = subset.emojiStatusDocId
            val prevColl = subset.emojiStatusCollectible
            tryAdd(
                {
                    subset.emojiStatusDocId = meta.emojiStatusDocId
                    subset.emojiStatusCollectible = meta.emojiStatusCollectible
                },
                {
                    subset.emojiStatusDocId = prevDoc
                    subset.emojiStatusCollectible = prevColl
                }
            )
        }
        if (meta.nameColorId >= 0) {
            val prev = subset.nameColorId
            tryAdd({ subset.nameColorId = meta.nameColorId }, { subset.nameColorId = prev })
        }
        if (meta.profileColorId >= 0) {
            val prev = subset.profileColorId
            tryAdd({ subset.profileColorId = meta.profileColorId }, { subset.profileColorId = prev })
        }
        if (meta.qrBackdropId >= 0) {
            val prev = subset.qrBackdropId
            tryAdd({ subset.qrBackdropId = meta.qrBackdropId }, { subset.qrBackdropId = prev })
        }
        if (meta.nameEmojiId != 0L) {
            val prev = subset.nameEmojiId
            tryAdd({ subset.nameEmojiId = meta.nameEmojiId }, { subset.nameEmojiId = prev })
        }
        if (meta.profileEmojiId != 0L) {
            val prev = subset.profileEmojiId
            tryAdd({ subset.profileEmojiId = meta.profileEmojiId }, { subset.profileEmojiId = prev })
        }
        if (meta.qrEmojiId != 0L) {
            val prev = subset.qrEmojiId
            tryAdd({ subset.qrEmojiId = meta.qrEmojiId }, { subset.qrEmojiId = prev })
        }

        return if (subset.isEmpty) "" else encodeV3Direct(subset)
    }

    @JvmStatic
    fun decode(text: String?): Metadata? {
        if (text.isNullOrEmpty()) return null

        // 1. V3 binary tag
        for (match in V3_TAG_REGEX.findAll(text)) {
            decodeV3Payload(match.groupValues[1])?.let { return it }
        }

        // 2. Legacy V2 tag
        val v2Match = V2_TAG_REGEX.find(text)
        if (v2Match != null) {
            val payload = v2Match.groupValues[1]
            val meta = Metadata()
            val tokens = payload.split(V2_DELIMITER)
            for (tok in tokens) {
                if (tok.length < 2) continue
                val type = tok[0]
                val num = decodeBase14(tok.substring(1))
                when (type) {
                    V2_KEY_STATUS -> {
                        meta.emojiStatusDocId = num
                        meta.emojiStatusCollectible = false
                    }
                    V2_KEY_COLLECTIBLE_STATUS -> {
                        meta.emojiStatusDocId = num
                        meta.emojiStatusCollectible = num != 0L
                    }
                    V2_KEY_NAME_COLOR -> meta.nameColorId = (num - 1L).toInt()
                    V2_KEY_NAME_EMOJI -> meta.nameEmojiId = num
                    V2_KEY_PROFILE_COLOR -> meta.profileColorId = (num - 1L).toInt()
                    V2_KEY_PROFILE_EMOJI -> meta.profileEmojiId = num
                    V2_KEY_QR_BACKDROP -> meta.qrBackdropId = (num - 1L).toInt()
                    V2_KEY_QR_EMOJI -> meta.qrEmojiId = num
                }
            }
            if (!meta.isEmpty) return meta
        }

        // 3. Legacy V1 tag
        val match = TAG_REGEX.find(text) ?: return null
        val payload = match.groupValues[1]
        val meta = Metadata()
        val tokens = payload.split(DELIMITER)
        for (tok in tokens) {
            if (tok.length < 2) continue
            val type = tok[0]
            val num = decodeNumber(tok.substring(1))
            when (type) {
                KEY_STATUS -> meta.emojiStatusDocId = num
                KEY_NAME_COLOR -> meta.nameColorId = (num - 1L).toInt()
                KEY_NAME_EMOJI -> meta.nameEmojiId = num
                KEY_PROFILE_COLOR -> meta.profileColorId = (num - 1L).toInt()
                KEY_PROFILE_EMOJI -> meta.profileEmojiId = num
                KEY_QR_BACKDROP -> meta.qrBackdropId = (num - 1L).toInt()
                KEY_QR_EMOJI -> meta.qrEmojiId = num
            }
        }
        return if (meta.isEmpty) null else meta
    }

    private fun decodeV3Payload(raw: String): Metadata? {
        if (raw.length < 4) return null
        val nibbles = ArrayList<Int>(raw.length)
        for (ch in raw) {
            val n = charToNibble(ch)
            if (n < 0) return null
            nibbles.add(n)
        }
        if (nibbles[0] != V3_KIND_METADATA) return null
        val crcIdx = nibbles.size - 1
        if (nibbles[crcIdx] != computeCrc4(nibbles.subList(0, crcIdx))) return null

        val data = nibbles.subList(0, crcIdx)
        val mask = (data[1] shl 4) or data[2]
        val idx = intArrayOf(3)
        val meta = Metadata()

        if ((mask and 0x01) != 0) {
            meta.emojiStatusDocId = readU64Nibbles(data, idx) ?: return null
            meta.emojiStatusCollectible = (mask and 0x02) != 0 && meta.emojiStatusDocId != 0L
        }
        if ((mask and 0x04) != 0) meta.nameColorId = readIntSmallOrExt(data, idx) ?: return null
        if ((mask and 0x08) != 0) meta.nameEmojiId = readU64Nibbles(data, idx) ?: return null
        if ((mask and 0x10) != 0) meta.profileColorId = readIntSmallOrExt(data, idx) ?: return null
        if ((mask and 0x20) != 0) meta.profileEmojiId = readU64Nibbles(data, idx) ?: return null
        if ((mask and 0x40) != 0) meta.qrBackdropId = readIntSmallOrExt(data, idx) ?: return null
        if ((mask and 0x80) != 0) meta.qrEmojiId = readU64Nibbles(data, idx) ?: return null

        if (idx[0] != data.size) return null
        return if (meta.isEmpty) null else meta
    }

    @JvmStatic
    fun stripAllTags(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var cleaned = V3_TAG_REGEX.replace(text, "")
        cleaned = V2_TAG_REGEX.replace(cleaned, "")
        cleaned = TAG_REGEX.replace(cleaned, "")
        cleaned = V3_ORPHAN_REGEX.replace(cleaned, "")
        cleaned = V2_ORPHAN_REGEX.replace(cleaned, "")
        cleaned = LEGACY_ORPHAN_REGEX.replace(cleaned, "")
        return cleaned
    }

    @JvmStatic
    fun cleanLastName(lastName: String?): String {
        if (lastName.isNullOrEmpty()) return ""
        return stripAllTags(lastName).trimEnd()
    }

    @JvmStatic
    fun formatLastName(visibleLastName: String?, meta: Metadata?): String {
        val base = cleanLastName(visibleLastName).take(MAX_LAST_NAME_LENGTH)
        if (meta == null || meta.isEmpty) return base
        val remaining = (MAX_LAST_NAME_LENGTH - base.length).coerceAtLeast(0)
        val tag = encodeWithLimit(meta, remaining)
        if (tag.isEmpty()) return base
        return if (base.isEmpty()) tag else "$base$tag"
    }

    /**
     * Packs (firstName, visibleLastName) together with an invisible tag.
     * Moves clean visible lastName into firstName separated by a space ONLY if lastName + tag > 64 chars.
     */
    @JvmStatic
    fun packProfileNamesWithTag(
        firstName: String?,
        visibleLastName: String?,
        tag: String?
    ): Pair<String, String> {
        val cleanFirst = (firstName ?: "").trim().take(MAX_FIRST_NAME_LENGTH)
        val cleanLast = cleanLastName(visibleLastName).take(MAX_LAST_NAME_LENGTH)
        val safeTag = tag ?: ""
        if (safeTag.isEmpty()) {
            return Pair(cleanFirst, cleanLast)
        }
        val clippedTag = safeTag.take(MAX_LAST_NAME_LENGTH)
        val maxLastVisible = (MAX_LAST_NAME_LENGTH - clippedTag.length).coerceAtLeast(0)

        if (cleanLast.length <= maxLastVisible) {
            return Pair(cleanFirst, if (cleanLast.isEmpty()) clippedTag else "$cleanLast$clippedTag")
        }

        // Last name does not fit together with invisible tag -> merge into first_name through space.
        val combinedFirst = if (cleanFirst.isEmpty()) cleanLast else "$cleanFirst $cleanLast"
        if (combinedFirst.length <= MAX_FIRST_NAME_LENGTH) {
            return Pair(combinedFirst, clippedTag)
        }

        val newFirst = combinedFirst.take(MAX_FIRST_NAME_LENGTH).trimEnd()
        val overflowLast = combinedFirst.substring(newFirst.length).trimStart().take(maxLastVisible)
        return Pair(newFirst, if (overflowLast.isEmpty()) clippedTag else "$overflowLast$clippedTag")
    }

    /**
     * Used by UserInfoActivity and ChangeNameActivity when editing the user's name.
     * Reuses the active invisible tag (bot ref or direct V3) and overflows surname into first_name if needed.
     */
    @JvmStatic
    fun packProfileNamesForEdit(
        account: Int,
        user: TLRPC.User?,
        newFirst: String?,
        newLast: String?
    ): Pair<String, String> {
        if (user == null) {
            return Pair((newFirst ?: "").trim(), cleanLastName(newLast))
        }
        val selfMeta = getSelfMetadataSnapshot(account, user)
        val tag = computeDesiredLastNameTag(user, selfMeta)
        return packProfileNamesWithTag(newFirst, newLast, tag)
    }

    private fun computeDesiredLastNameTag(user: TLRPC.User, meta: Metadata): String {
        if (meta.isEmpty) return ""
        if (OpenExteraConfig.botProfileSync()) {
            val savedSeed = BotMetadataSyncHelper.getSavedSelfBotSeed(user.id)
            if (savedSeed != null) {
                return encodeBotRef(savedSeed)
            }
        }
        return encodeWithLimit(meta, MAX_LAST_NAME_LENGTH)
    }

    @JvmStatic
    fun getMetadata(user: TLRPC.User?): Metadata? {
        return getMetadata(user, UserConfig.selectedAccount)
    }

    @JvmStatic
    fun getMetadata(user: TLRPC.User?, account: Int): Metadata? {
        if (user == null) return null
        val lastName = user.last_name
        val botSeed = decodeBotRef(lastName)

        if (botSeed != null) {
            val cachedBotMeta = if (user.self) {
                loadSelfMetadata(account, user).takeIf { !it.isEmpty }
            } else {
                BotMetadataSyncHelper.getCachedPeerBotMetadata(user.id, botSeed)
            }
            if (!user.self) {
                BotMetadataSyncHelper.fetchPeerBotMetadataIfNeeded(account, user.id, botSeed)
            }
            applyMetaToUser(user, cachedBotMeta)
            return cachedBotMeta
        }

        val cached = userMetaCache[user.id]
        val meta = if (cached != null && cached.first == lastName) {
            cached.second
        } else {
            decode(lastName).also {
                userMetaCache[user.id] = Pair(lastName, it)
            }
        }
        applyMetaToUser(user, meta)
        return meta
    }

    private fun applyMetaToUser(user: TLRPC.User, meta: Metadata?) {
        if (OpenExteraConfig.customEmojiStatus()) {
            val currentNativeId = DialogObject.getEmojiStatusDocumentId(user.emoji_status)
            val prevFakeId = appliedFakeStatusDocId[user.id] ?: 0L
            val isSyntheticOrEmpty = user.emoji_status == null || (prevFakeId != 0L && currentNativeId == prevFakeId)
            if (isSyntheticOrEmpty) {
                val metaDocId = meta?.emojiStatusDocId ?: 0L
                val helperDocId = if (metaDocId != 0L) metaDocId else (CustomEmojiStatusHelper.getEmojiStatusDocumentId(user.id) ?: 0L)
                if (helperDocId != 0L) {
                    val restored = if (user.self) CustomEmojiStatusHelper.getSavedSelfCollectible(user.id, helperDocId) else null
                    if (restored != null) {
                        user.emoji_status = restored
                    } else {
                        val status = TLRPC.TL_emojiStatus()
                        status.document_id = helperDocId
                        user.emoji_status = status
                    }
                    user.flags2 = user.flags2 or 1
                    appliedFakeStatusDocId[user.id] = helperDocId
                } else if (prevFakeId != 0L && currentNativeId == prevFakeId) {
                    user.emoji_status = null
                    user.flags2 = user.flags2 and 1.inv()
                    appliedFakeStatusDocId.remove(user.id)
                }
            } else if (currentNativeId != 0L) {
                appliedFakeStatusDocId.remove(user.id)
            }
        }

        if (OpenExteraConfig.freeProfileAppearance()) {
            val prevFake = appliedFakeNameColor[user.id]
            val isSyntheticOrEmpty = user.color == null ||
                (prevFake != null && user.color.color == prevFake.first && user.color.background_emoji_id == prevFake.second)
            if (isSyntheticOrEmpty) {
                if (meta != null && meta.nameColorId >= 0) {
                    val peerColor = TLRPC.TL_peerColor()
                    peerColor.flags = 1
                    peerColor.color = meta.nameColorId
                    if (meta.nameEmojiId != 0L) {
                        peerColor.flags = peerColor.flags or 2
                        peerColor.background_emoji_id = meta.nameEmojiId
                    }
                    user.color = peerColor
                    appliedFakeNameColor[user.id] = Pair(meta.nameColorId, meta.nameEmojiId)
                } else if (prevFake != null) {
                    user.color = null
                    appliedFakeNameColor.remove(user.id)
                }
            }
        }

        if (OpenExteraConfig.freeProfileAppearance()) {
            val prevFake = appliedFakeProfileColor[user.id]
            val isSyntheticOrEmpty = user.profile_color == null ||
                (prevFake != null && user.profile_color.color == prevFake.first && user.profile_color.background_emoji_id == prevFake.second)
            if (isSyntheticOrEmpty) {
                if (meta != null && meta.profileColorId >= 0) {
                    val peerColor = TLRPC.TL_peerColor()
                    peerColor.flags = 1
                    peerColor.color = meta.profileColorId
                    if (meta.profileEmojiId != 0L) {
                        peerColor.flags = peerColor.flags or 2
                        peerColor.background_emoji_id = meta.profileEmojiId
                    }
                    user.profile_color = peerColor
                    appliedFakeProfileColor[user.id] = Pair(meta.profileColorId, meta.profileEmojiId)
                } else if (prevFake != null) {
                    user.profile_color = null
                    appliedFakeProfileColor.remove(user.id)
                }
            }
        }
    }

    @JvmStatic
    fun invalidateUser(userId: Long) {
        userMetaCache.remove(userId)
    }

    @JvmStatic
    fun isEmojiStatusCollectible(user: TLRPC.User?): Boolean {
        if (user == null) return false
        if (DialogObject.isEmojiStatusCollectible(user.emoji_status)) return true
        if (!OpenExteraConfig.customEmojiStatus()) return false
        val meta = getMetadata(user)
        if (meta != null && meta.emojiStatusCollectible && meta.emojiStatusDocId != 0L) {
            return true
        }
        return CustomEmojiStatusHelper.isCollectibleStatus(user.id)
    }

    @JvmStatic
    fun getEmojiStatusDocumentId(user: TLRPC.User?): Long? {
        if (!OpenExteraConfig.customEmojiStatus()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.emojiStatusDocId != 0L) meta.emojiStatusDocId else null
    }

    @JvmStatic
    fun getNameColorId(user: TLRPC.User?): Int? {
        if (!OpenExteraConfig.freeProfileAppearance()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.nameColorId >= 0) meta.nameColorId else null
    }

    @JvmStatic
    fun getNameEmojiId(user: TLRPC.User?): Long? {
        if (!OpenExteraConfig.freeProfileAppearance()) return null
        val meta = getMetadata(user) ?: return null
        return if (meta.nameEmojiId != 0L) meta.nameEmojiId else null
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
        return if (meta.profileEmojiId != 0L) meta.profileEmojiId else null
    }

    @JvmStatic
    fun getSelfMetadataSnapshot(account: Int, user: TLRPC.User): Metadata {
        return loadSelfMetadata(account, user).copy()
    }

    private fun loadSelfMetadata(account: Int, user: TLRPC.User): Metadata {
        val serialized = OpenExteraConfig.getPreferences().getString(PREF_SELF_META_PREFIX + user.id, null)
        if (!serialized.isNullOrEmpty()) {
            decode(serialized)?.let { return it }
        }
        decode(user.last_name)?.let { return it }
        val docId = CustomEmojiStatusHelper.getEmojiStatusDocumentId(user.id) ?: 0L
        return Metadata(
            emojiStatusDocId = docId,
            emojiStatusCollectible = docId != 0L && CustomEmojiStatusHelper.isCollectibleStatus(user.id)
        )
    }

    private fun persistSelfMetadata(userId: Long, meta: Metadata) {
        val editor = OpenExteraConfig.getPreferences().edit()
        if (meta.isEmpty) {
            editor.remove(PREF_SELF_META_PREFIX + userId)
        } else {
            editor.putString(PREF_SELF_META_PREFIX + userId, encodeV3Direct(meta))
        }
        editor.apply()
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
        val currentMeta = loadSelfMetadata(account, user)
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
        updateSelfEmojiStatus(account, documentId, false, callback)
    }

    @JvmStatic
    fun updateSelfEmojiStatus(
        account: Int,
        documentId: Long,
        isCollectible: Boolean,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val user = UserConfig.getInstance(account).currentUser ?: return
        val currentMeta = loadSelfMetadata(account, user)
        currentMeta.emojiStatusDocId = documentId
        currentMeta.emojiStatusCollectible = documentId != 0L && isCollectible

        saveSelfMetadata(account, user, currentMeta, callback)
    }

    @JvmStatic
    fun updateSelfQrTheme(
        account: Int,
        backdropId: Int,
        emojiId: Long,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val user = UserConfig.getInstance(account).currentUser ?: return
        val currentMeta = loadSelfMetadata(account, user)
        currentMeta.qrBackdropId = backdropId
        currentMeta.qrEmojiId = emojiId

        saveSelfMetadata(account, user, currentMeta, callback)
    }

    @JvmStatic
    fun getQrThemeForUser(user: TLRPC.User?): Pair<Int, Long>? {
        if (user == null) return null
        val meta = getMetadata(user) ?: return null
        if (meta.qrBackdropId >= 0 || meta.qrEmojiId != 0L) {
            return Pair(meta.qrBackdropId, meta.qrEmojiId)
        }
        return null
    }

    /**
     * Повторно синхронизирует текущие метаданные пользователя при переключении режима бота (ON <-> OFF).
     */
    @JvmStatic
    fun resyncSelfMetadata(account: Int, callback: ((Boolean) -> Unit)? = null) {
        val user = UserConfig.getInstance(account).currentUser ?: run {
            callback?.invoke(false)
            return
        }
        val currentMeta = loadSelfMetadata(account, user)
        saveSelfMetadata(account, user, currentMeta, callback)
    }

    private fun saveSelfMetadata(
        account: Int,
        user: TLRPC.User,
        meta: Metadata,
        callback: ((Boolean) -> Unit)?
    ) {
        persistSelfMetadata(user.id, meta)

        // Ensure optimistic in-memory self visuals are up to date immediately.
        if (!user.premium) {
            if (meta.emojiStatusDocId != 0L) {
                val restored = if (meta.emojiStatusCollectible) {
                    CustomEmojiStatusHelper.getSavedSelfCollectible(user.id, meta.emojiStatusDocId)
                } else null
                if (restored != null) {
                    user.emoji_status = restored
                } else {
                    val status = TLRPC.TL_emojiStatus()
                    status.document_id = meta.emojiStatusDocId
                    user.emoji_status = status
                }
                user.flags2 = user.flags2 or 1
                appliedFakeStatusDocId[user.id] = meta.emojiStatusDocId
            } else if (appliedFakeStatusDocId[user.id] != null || DialogObject.getEmojiStatusDocumentId(user.emoji_status) == 0L) {
                user.emoji_status = null
                user.flags2 = user.flags2 and 1.inv()
                appliedFakeStatusDocId.remove(user.id)
            }
            if (meta.nameColorId >= 0) {
                val c = TLRPC.TL_peerColor()
                c.flags = 1 or (if (meta.nameEmojiId != 0L) 2 else 0)
                c.color = meta.nameColorId
                c.background_emoji_id = meta.nameEmojiId
                user.color = c
                appliedFakeNameColor[user.id] = Pair(meta.nameColorId, meta.nameEmojiId)
            } else {
                user.color = null
                appliedFakeNameColor.remove(user.id)
            }
            if (meta.profileColorId >= 0) {
                val c = TLRPC.TL_peerColor()
                c.flags = 1 or (if (meta.profileEmojiId != 0L) 2 else 0)
                c.color = meta.profileColorId
                c.background_emoji_id = meta.profileEmojiId
                user.profile_color = c
                appliedFakeProfileColor[user.id] = Pair(meta.profileColorId, meta.profileEmojiId)
            } else {
                user.profile_color = null
                appliedFakeProfileColor.remove(user.id)
            }
        }

        val usingBotSync = OpenExteraConfig.botProfileSync()
        val savedBotSeed = if (usingBotSync) BotMetadataSyncHelper.getSavedSelfBotSeed(user.id) else null

        if (usingBotSync && savedBotSeed == null) {
            // Try silent recovery of previously created sync bot via getAdminedBots before writing profile.
            BotMetadataSyncHelper.ensureBotReady(account, allowInteractiveBotFather = false) { res ->
                if (res.success) {
                    BotMetadataSyncHelper.syncMetadataToBot(account, meta, null)
                    commitProfileTag(account, user, encodeBotRef(res.seed), callback)
                } else {
                    commitProfileTag(account, user, encodeWithLimit(meta, MAX_LAST_NAME_LENGTH), callback)
                }
            }
            return
        }

        val tag = if (usingBotSync && savedBotSeed != null) {
            BotMetadataSyncHelper.syncMetadataToBot(account, meta, null)
            if (meta.isEmpty) "" else encodeBotRef(savedBotSeed)
        } else {
            encodeWithLimit(meta, MAX_LAST_NAME_LENGTH)
        }

        commitProfileTag(account, user, tag, callback)
    }

    private fun commitProfileTag(
        account: Int,
        user: TLRPC.User,
        tag: String,
        callback: ((Boolean) -> Unit)?
    ) {
        val oldFirst = user.first_name ?: ""
        val oldLast = user.last_name ?: ""
        val (newFirst, newLast) = packProfileNamesWithTag(oldFirst, oldLast, tag)

        invalidateUser(user.id)

        if (newFirst == oldFirst && newLast == oldLast) {
            AndroidUtilities.runOnUIThread {
                MessagesController.getInstance(account).putUser(user, true)
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.mainUserInfoChanged)
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_NAME or MessagesController.UPDATE_MASK_EMOJI_STATUS
                )
            }
            callback?.invoke(true)
            return
        }

        val req = TL_account.updateProfile()
        var flags = 0
        if (newFirst != oldFirst) {
            flags = flags or 1
            req.first_name = newFirst
        }
        flags = flags or 2
        req.last_name = newLast
        req.flags = flags

        user.first_name = newFirst
        user.last_name = newLast
        UserConfig.getInstance(account).setCurrentUser(user)
        UserConfig.getInstance(account).saveConfig(true)
        MessagesController.getInstance(account).putUser(user, true)

        AndroidUtilities.runOnUIThread {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.mainUserInfoChanged)
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
            NotificationCenter.getInstance(account).postNotificationName(
                NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_NAME or MessagesController.UPDATE_MASK_EMOJI_STATUS
            )
        }

        ConnectionsManager.getInstance(account).sendRequest(req) { response, error ->
            if (error != null) {
                FileLog.d("ProfileInvisibleMetadata updateProfile failed: ${error.code} ${error.text}")
                callback?.invoke(false)
                return@sendRequest
            }
            if (response is TLRPC.User) {
                val resUser = response
                AndroidUtilities.runOnUIThread {
                    val mc = MessagesController.getInstance(account)
                    val cur = UserConfig.getInstance(account).currentUser
                    if (cur != null) {
                        cur.first_name = resUser.first_name
                        cur.last_name = resUser.last_name
                        invalidateUser(cur.id)
                        getMetadata(cur, account)
                        UserConfig.getInstance(account).setCurrentUser(cur)
                        UserConfig.getInstance(account).saveConfig(true)
                        mc.putUser(cur, true)
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.mainUserInfoChanged)
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, cur)
                        NotificationCenter.getInstance(account).postNotificationName(
                            NotificationCenter.updateInterfaces,
                            MessagesController.UPDATE_MASK_NAME or MessagesController.UPDATE_MASK_EMOJI_STATUS
                        )
                    }
                }
            }
            callback?.invoke(true)
        }
    }
}
