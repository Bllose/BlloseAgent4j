#!/usr/bin/env python3
"""
paper-download-mcp — MCP server for finding and downloading academic paper PDFs.

Provides tools to locate PDF URLs and download papers.

Usage:
    uv run --directory mcp/paper-download-mcp python server.py

Or add to Claude Desktop config:
    {
        "mcpServers": {
            "paper-download": {
                "command": "uv",
                "args": ["run", "--directory", "path/to/mcp/paper-download-mcp", "server.py"]
            }
        }
    }
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import os
import re
import sqlite3
import sys
from pathlib import Path

# Ensure this file's directory is on sys.path so imports work from any CWD
_self_dir = str(Path(__file__).resolve().parent)
if _self_dir not in sys.path:
    sys.path.insert(0, _self_dir)

import anyio
import httpx
from mcp.server import Server, NotificationOptions
from mcp.server.models import InitializationOptions
import mcp.server.stdio
import mcp.types as types

from models import DownloadResult, PdfSource
from providers import arxiv, semantic_scholar, unpaywall

# ── Logging ──────────────────────────────────────────────
logging.basicConfig(
    level=os.environ.get("LOG_LEVEL", "WARNING"),
    format="%(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("paper-download-mcp")

http_client: httpx.AsyncClient | None = None

# Safe filename: keep only alphanumeric, dash, underscore, dot
_FILENAME_CLEAN = re.compile(r"[^\w\-.]")


def get_client() -> httpx.AsyncClient:
    global http_client
    if http_client is None:
        http_client = httpx.AsyncClient(
            follow_redirects=True,
            limits=httpx.Limits(max_keepalive_connections=10),
            headers={
                "User-Agent": (
                    "paper-download-mcp/0.1.0"
                    " (mailto:user@example.org)"
                )
            },
        )
    return http_client


def _safe_filename(doi_or_url: str, suffix: str = ".pdf") -> str:
    """Generate a safe filename from a DOI or URL."""
    # Extract the last meaningful part
    name = doi_or_url.rstrip("/").split("/")[-1]
    # Remove query params
    name = name.split("?")[0]
    # Clean unsafe characters
    name = _FILENAME_CLEAN.sub("_", name)
    # Ensure it's not empty
    if not name:
        name = "paper"
    if not name.endswith(suffix):
        name += suffix
    return name


# ── Database cache ────────────────────────────────────────

_db_path: str | None = None

_default_output_dir = os.environ.get("PAPER_DOWNLOAD_DIR", "./downloads")


def set_db_path(path: str) -> None:
    global _db_path
    _db_path = path


def _check_cache(external_id: str) -> str | None:
    """Check if a paper is already in the download cache DB.

    Returns a formatted cached-result string if found and the file still exists,
    or None if not cached / file missing.
    """
    if not _db_path:
        return None
    try:
        conn = sqlite3.connect(f"file:{_db_path}?mode=ro", uri=True)
        try:
            cur = conn.execute(
                "SELECT file_name, file_size, source_url FROM paper_downloads WHERE external_id = ?",
                (external_id,),
            )
            row = cur.fetchone()
        finally:
            conn.close()
        if row is None:
            return None
        file_name, file_size, source_url = row
        if not os.path.isfile(file_name):
            return None
        size_mb = file_size / (1024 * 1024) if file_size else 0
        size_str = f"{size_mb:.1f} MB" if size_mb > 1 else f"{file_size / 1024:.0f} KB" if file_size else "unknown"
        lines = [
            f"Already downloaded (cached): {file_name}",
            f"Size: {size_str}",
        ]
        if source_url:
            lines.append(f"Source URL: {source_url}")
        lines.append(f"DOI/ID: {external_id}")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("Cache check failed for %s: %s", external_id, e)
        return None


def _save_to_cache(external_id: str, source_url: str, file_name: str, file_size: int) -> None:
    """Save a download record to the cache DB."""
    if not _db_path:
        logger.debug("No db_path set, skipping cache save")
        return
    try:
        conn = sqlite3.connect(_db_path)
        try:
            conn.execute(
                "INSERT OR IGNORE INTO paper_downloads"
                " (external_id, id_type, source_url, file_name, file_size)"
                " VALUES (?, 'DOI', ?, ?, ?)",
                (external_id, source_url, file_name, file_size),
            )
            conn.commit()
            logger.info("Saved to cache: %s -> %s", external_id, file_name)
        finally:
            conn.close()
    except Exception as e:
        logger.warning("Cache save failed for %s: %s", external_id, e)


# ── Tool: find_pdf_url ───────────────────────────────────


async def _find_pdf_url(doi: str) -> PdfSource | None:
    """Cascade through PDF URL providers, returning the first match."""
    client = get_client()

    # 1. arXiv — no API call, just pattern matching
    arxiv_url = arxiv.find_pdf_url(doi)
    if arxiv_url:
        return PdfSource(doi=doi, url=arxiv_url, source="arxiv", is_open_access=True)

    # 2. Unpaywall — best OA location
    unpaywall_url = await unpaywall.find_pdf_url(doi, client)
    if unpaywall_url:
        return PdfSource(doi=doi, url=unpaywall_url, source="unpaywall", is_open_access=True)

    # 3. Semantic Scholar — openAccessPdf
    s2_url = await semantic_scholar.find_pdf_url(doi, client)
    if s2_url:
        return PdfSource(doi=doi, url=s2_url, source="semantic_scholar", is_open_access=True)

    return None


# ── Download helpers ──────────────────────────────────────


async def _download_to(url: str, dest: Path) -> DownloadResult:
    """Download a URL to a local file path, streaming in chunks."""
    client = get_client()
    async with client.stream("GET", url) as response:
        response.raise_for_status()
        size = 0
        async with await anyio.open_file(dest, "wb") as f:
            async for chunk in response.aiter_bytes(chunk_size=65536):
                await f.write(chunk)
                size += len(chunk)
    return DownloadResult(
        url=url,
        file_path=str(dest.resolve()),
        file_size_bytes=size,
    )


async def _download_pdf(doi: str, output_dir: str) -> DownloadResult | str:
    """Find PDF URL for a DOI, download it, return result or error message."""
    # Check cache first
    cached = _check_cache(doi)
    if cached is not None:
        return cached

    source = await _find_pdf_url(doi)
    if source is None:
        return f"No PDF URL found for DOI {doi}."

    dest_dir = Path(output_dir).expanduser().resolve()
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest_path = dest_dir / _safe_filename(doi)

    try:
        result = await _download_to(source.url, dest_path)
        result.doi = doi
        _save_to_cache(doi, source.url, result.file_path, result.file_size_bytes)
        return result
    except httpx.HTTPStatusError as e:
        return f"Download failed for {doi}: HTTP {e.response.status_code}"
    except Exception as e:
        return f"Download failed for {doi}: {e}"


async def _download_from_url(url: str, output_dir: str) -> DownloadResult | str:
    """Download PDF from a direct URL, return result or error message."""
    # Check cache first (use URL as cache key)
    cached = _check_cache(url)
    if cached is not None:
        return cached

    dest_dir = Path(output_dir).expanduser().resolve()
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest_path = dest_dir / _safe_filename(url)

    try:
        result = await _download_to(url, dest_path)
        _save_to_cache(url, url, result.file_path, result.file_size_bytes)
        return result
    except httpx.HTTPStatusError as e:
        return f"Download failed from {url}: HTTP {e.response.status_code}"
    except Exception as e:
        return f"Download failed from {url}: {e}"


# ── MCP Server ───────────────────────────────────────────

server = Server("paper-download-mcp")


def _fmt_pdf_source(s: PdfSource | None, query_desc: str) -> str:
    if s is None:
        return f"No PDF URL found for {query_desc}."
    lines = [
        f"DOI: {s.doi}",
        f"PDF URL: {s.url}",
        f"Source: {s.source}",
    ]
    if s.is_open_access:
        lines.append("Open Access: Yes")
    return "\n".join(lines)


def _fmt_download_result(r: DownloadResult | str, query_desc: str) -> str:
    if isinstance(r, str):
        return r  # Error message
    size_kb = r.file_size_bytes / 1024
    size_mb = size_kb / 1024
    size_str = f"{size_mb:.1f} MB" if size_mb > 1 else f"{size_kb:.0f} KB"
    return (
        f"Downloaded: {r.file_path}\n"
        f"Size: {size_str}\n"
        f"DOI: {r.doi or 'N/A'}"
    )


@server.list_tools()
async def handle_list_tools() -> list[types.Tool]:
    return [
        types.Tool(
            name="find_pdf_url",
            description=(
                "Find a PDF download URL for a paper by DOI. "
                "Checks arXiv (no API), Unpaywall, and Semantic Scholar in order. "
                "Returns the first found URL with its source."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "doi": {
                        "type": "string",
                        "description": "DOI of the paper, e.g. '10.1038/s41586-023-06236-9'",
                    }
                },
                "required": ["doi"],
            },
        ),
        types.Tool(
            name="download_pdf",
            description=(
                "Find and download a PDF for a paper by DOI. "
                "Automatically locates the PDF URL, downloads it, "
                "and saves it to the specified output directory."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "doi": {
                        "type": "string",
                        "description": "DOI of the paper",
                    },
                    "output_dir": {
                        "type": "string",
                        "description": "Directory to save the PDF file",
                    },
                },
                "required": ["doi"],
            },
        ),
        types.Tool(
            name="download_pdf_from_url",
            description=(
                "Download a PDF from a direct URL. "
                "Saves the file to the specified output directory (defaults to ./downloads)."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "url": {
                        "type": "string",
                        "description": "Direct URL to a PDF file",
                    },
                    "output_dir": {
                        "type": "string",
                        "description": "Directory to save the PDF file (default: ./downloads)",
                    },
                },
                "required": ["url"],
            },
        ),
    ]


@server.call_tool()
async def handle_call_tool(
    name: str, arguments: dict | None
) -> list[types.TextContent | types.ImageContent | types.EmbeddedResource]:
    if arguments is None:
        arguments = {}

    if name == "find_pdf_url":
        doi = arguments.get("doi", "").strip()
        if not doi:
            raise ValueError("doi is required")
        result = await _find_pdf_url(doi)
        return [types.TextContent(type="text", text=_fmt_pdf_source(result, f"DOI {doi}"))]

    if name == "download_pdf":
        doi = arguments.get("doi", "").strip()
        if not doi:
            raise ValueError("doi is required")
        output_dir = arguments.get("output_dir", "").strip() or _default_output_dir
        result = await _download_pdf(doi, output_dir)
        return [types.TextContent(type="text", text=_fmt_download_result(result, f"DOI {doi}"))]

    if name == "download_pdf_from_url":
        url = arguments.get("url", "").strip()
        if not url:
            raise ValueError("url is required")
        output_dir = arguments.get("output_dir", "").strip() or _default_output_dir
        result = await _download_from_url(url, output_dir)
        return [types.TextContent(type="text", text=_fmt_download_result(result, f"URL {url}"))]

    raise ValueError(f"Unknown tool: {name}")


# ── Main ─────────────────────────────────────────────────


async def main() -> None:
    async with mcp.server.stdio.stdio_server() as (read_stream, write_stream):
        logger.info("paper-download-mcp server started (db_path=%s)", _db_path or "none")
        await server.run(
            read_stream,
            write_stream,
            InitializationOptions(
                server_name="paper-download-mcp",
                server_version="0.1.0",
                capabilities=server.get_capabilities(
                    notification_options=NotificationOptions(),
                    experimental_capabilities={},
                ),
            ),
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="paper-download-mcp")
    parser.add_argument("--db-path", default=os.environ.get("PAPER_DB_PATH"),
                        help="Path to SQLite download cache database")
    args, _ = parser.parse_known_args()
    if args.db_path:
        set_db_path(args.db_path)
    asyncio.run(main())
