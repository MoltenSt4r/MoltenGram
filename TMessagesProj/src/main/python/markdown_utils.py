"""Markdown parsing for plugins (``markdown_utils.parse_markdown``).

The single most-used formatting entry point in real exteraGram plugins: of the
361 plugins in the public catalogue, 37 import ``parse_markdown`` at module
level, so a missing module does not degrade them — it stops them loading.

The contract those plugins rely on is narrow and fully covered here::

    parsed = parse_markdown(text)
    params.message = parsed.text
    for entity in parsed.entities:
        params.entities.add(entity.to_tlrpc_object())

``ParsedText.entities`` therefore holds :class:`MarkdownEntity` objects, NOT
ready-made TLRPC objects: materialisation is deferred to
``to_tlrpc_object()`` so that ``parse_markdown`` stays usable off the JVM
(host tests) and so that a plugin can inspect offsets before committing.

Offsets and lengths are UTF-16 code units, matching what Telegram's
MessageEntity expects — plugins that build their own entities alongside these
compute the same way, e.g.::

    len(parsed.text.encode('utf_16_le')) / 2

Dialect (from :mod:`extera_utils.text_formatting`): ``**bold**``,
``__italic__``, ``~~strike~~``, ``||spoiler||``, ``` `code` ```,
```` ```lang\\ncode``` ````, ``[text](url)``, ``>quote`` / ``**>collapsed``,
and ``![alt](tg://emoji?id=...)`` custom emoji.
"""

from typing import Any, List, Optional

from extera_utils.text_formatting import RawEntity, parse_raw

__all__ = [
    "MarkdownEntity",
    "ParsedMessage",
    "ParsedText",
    "count_chars_until",
    "parse_html",
    "parse_markdown",
    "to_utf16_len",
]


class MarkdownEntity:
    """One formatting span, materialised into TLRPC only on demand.

    Attributes mirror TLRPC.MessageEntity so that plugins can read
    ``entity.offset`` / ``entity.length`` before conversion.
    """

    __slots__ = ("offset", "length", "type", "url", "language",
                 "document_id", "collapsed", "_raw")

    def __init__(self, raw: RawEntity):
        self._raw = raw
        self.offset = raw.offset
        self.length = raw.length
        self.type = raw.type
        self.url = raw.url
        self.language = raw.language
        self.document_id = raw.document_id
        self.collapsed = raw.collapsed

    def to_tlrpc_object(self) -> Any:
        """Build the matching ``TLRPC.TL_messageEntity*`` instance.

        Reads the live attributes rather than the captured RawEntity, so a
        plugin that shifts ``entity.offset`` (common when prepending a header
        to the message) gets the shift honoured.
        """
        from extera_utils.text_formatting import _materialize_entities

        raw = RawEntity(
            offset=int(self.offset),
            length=int(self.length),
            type=self.type,
            url=self.url,
            language=self.language,
            document_id=self.document_id,
            collapsed=bool(self.collapsed),
        )
        return _materialize_entities([raw])[0]

    def __repr__(self) -> str:
        return (f"MarkdownEntity({self.type}, offset={self.offset}, "
                f"length={self.length})")


class ParsedText:
    """Result of :func:`parse_markdown`: plain ``text`` plus ``entities``."""

    __slots__ = ("text", "entities")

    def __init__(self, text: str, entities: List[MarkdownEntity]):
        self.text = text
        self.entities = entities

    # Some plugins unpack the result instead of using the attributes.
    def __iter__(self):
        return iter((self.text, self.entities))

    def __len__(self) -> int:
        return len(self.text)

    def __str__(self) -> str:
        return self.text

    def __repr__(self) -> str:
        return f"ParsedText({self.text!r}, {len(self.entities)} entities)"


def _parse(text: Any, parse_mode: str) -> ParsedText:
    plain, raw_entities = parse_raw("" if text is None else str(text), parse_mode)
    return ParsedText(plain, [MarkdownEntity(raw) for raw in raw_entities])


def parse_markdown(text: Any) -> ParsedText:
    """Parse Telegram-flavoured Markdown into plain text plus entities."""
    return _parse(text, "Markdown")


def parse_html(text: Any) -> ParsedText:
    """HTML counterpart of :func:`parse_markdown`, same result shape."""
    return _parse(text, "HTML")


ParsedMessage = ParsedText


def count_chars_until(string: str, stop_chars: str, start_index: int = 0) -> int:
    """Count characters from *start_index* until any character in *stop_chars*."""
    count = 0
    for i in range(start_index, len(string)):
        if string[i] in stop_chars:
            break
        count += 1
    return count


def to_utf16_len(s: str) -> int:
    """Return length of *s* in UTF-16 code units (matching Telegram entity offsets)."""
    return len(s.encode("utf-16-le")) // 2
