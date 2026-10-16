"""Build an HTML page from a Markdown doc in "Docs Text/".

Usage (from the repo root):
    python scripts/build-docs.py "Docs Text/Learning Notes.md"

Writes "Docs HTML/<same name>.html". Needs Node (uses the `marked` Markdown converter via npx).
"""
import html
import re
import subprocess
import sys
from pathlib import Path

MARKED = "marked@15.0.12"

src = Path(sys.argv[1])
out = Path("Docs HTML") / (src.stem + ".html")
title = "Namastute " + src.stem

body = subprocess.run(
    f'npx -y {MARKED} --gfm -i "{src}"',
    shell=True, check=True, capture_output=True, text=True, encoding="utf-8",
).stdout


def slug(text):
    # Same rule as GitHub, so the doc's own "Contents" links work in both places
    return re.sub(r"[^\w\- ]", "", text.lower()).replace(" ", "-")


toc = []  # [(title, slug, [(sub_title, sub_slug), ...])]


def add_id(m):
    level, inner = m.group(1), m.group(2)
    text = html.unescape(re.sub(r"<[^>]+>", "", inner))
    s = slug(text)
    if level == "2" and text != "Contents":
        toc.append((html.escape(text), s, []))
    elif level == "3" and toc:
        toc[-1][2].append((html.escape(text), s))
    return f'<h{level} id="{s}">{inner}</h{level}>'


body = re.sub(r"<h([1-3])>(.*?)</h\1>", add_id, body)
body = re.sub(r'<pre><code class="language-mermaid">(.*?)</code></pre>',
              r'<pre class="mermaid">\1</pre>', body, flags=re.S)

nav = []
for text, s, subs in toc:
    if subs:
        items = "".join(f'<a class="sub" href="#{ss}">{st}</a>' for st, ss in subs)
        nav.append(f'<details><summary><a href="#{s}">{text}</a></summary>{items}</details>')
    else:
        nav.append(f'<a class="topic" href="#{s}">{text}</a>')

page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<style>
  :root {{ --bg:#fff; --fg:#1f2328; --muted:#59636e; --line:#d1d9e0; --code:#f6f8fa; --link:#0969da; --hl:#ddf4ff; }}
  @media (prefers-color-scheme: dark) {{ :root {{ --bg:#0d1117; --fg:#e6edf3; --muted:#9198a1; --line:#30363d; --code:#161b22; --link:#4493f8; --hl:#0c2d6b; }} }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--fg); font:16px/1.65 system-ui,-apple-system,"Segoe UI",sans-serif; }}
  nav {{ position:fixed; top:0; left:0; bottom:0; width:290px; overflow-y:auto; padding:20px 14px; border-right:1px solid var(--line); }}
  nav strong {{ display:block; margin-bottom:12px; font-size:15px; }}
  nav a {{ color:var(--fg); text-decoration:none; }}
  nav a:hover {{ color:var(--link); }}
  nav .topic, nav summary {{ display:block; padding:4px 6px; font-size:14px; font-weight:600; border-radius:6px; cursor:pointer; }}
  nav summary {{ list-style:none; }}
  nav summary::-webkit-details-marker {{ display:none; }}
  nav summary::before {{ content:"▸"; display:inline-block; width:14px; color:var(--muted); transition:transform .15s; }}
  nav details[open] > summary::before {{ transform:rotate(90deg); }}
  nav .topic::before {{ content:""; display:inline-block; width:14px; }}
  nav .sub {{ display:block; padding:3px 6px 3px 26px; font-size:13px; color:var(--muted); border-radius:6px; }}
  nav a.active {{ color:var(--link); background:var(--hl); }}
  main {{ margin-left:290px; max-width:960px; padding:32px 40px 80px; }}
  a {{ color:var(--link); }}
  h1, h2 {{ border-bottom:1px solid var(--line); padding-bottom:.3em; }}
  h2 {{ margin-top:2.2em; }}
  h2, h3 {{ scroll-margin-top:16px; }}
  table {{ border-collapse:collapse; display:block; overflow-x:auto; margin:16px 0; }}
  th, td {{ border:1px solid var(--line); padding:6px 12px; text-align:left; vertical-align:top; }}
  code {{ overflow-wrap:anywhere; background:var(--code); padding:.15em .35em; border-radius:4px; font-size:.9em; }}
  pre {{ background:var(--code); padding:16px; border-radius:6px; overflow-x:auto; }}
  pre code {{ background:none; padding:0; overflow-wrap:normal; }}
  pre.mermaid {{ background:none; text-align:center; }}
  blockquote {{ margin:0; padding:0 1em; color:var(--muted); border-left:4px solid var(--line); }}
  @media (max-width: 800px) {{
    nav {{ position:static; width:auto; border-right:0; border-bottom:1px solid var(--line); max-height:50vh; }}
    main {{ margin-left:0; padding:16px; }}
  }}
</style>
</head>
<body>
<nav><strong>{html.escape(title)}</strong>
{chr(10).join(nav)}
</nav>
<main>
{body}
</main>
<script>
  // Highlight the section being read and open its topic in the sidebar
  const links = new Map([...document.querySelectorAll("nav a")].map(a => [a.getAttribute("href").slice(1), a]));
  const seen = new IntersectionObserver(entries => {{
    for (const e of entries) {{
      if (!e.isIntersecting) continue;
      const a = links.get(e.target.id);
      if (!a) continue;
      document.querySelectorAll("nav a.active").forEach(x => x.classList.remove("active"));
      a.classList.add("active");
      const d = a.closest("details");
      if (d) d.open = true;
    }}
  }}, {{ rootMargin: "0px 0px -75% 0px" }});
  document.querySelectorAll("main h2[id], main h3[id]").forEach(h => seen.observe(h));
</script>
<script type="module">
  if (document.querySelector("pre.mermaid")) {{
    const {{ default: mermaid }} = await import("https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.esm.min.mjs");
    mermaid.initialize({{ startOnLoad: false, theme: matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "default" }});
    await mermaid.run();
    // Drawing the diagrams moves everything below them, so jump to the linked section again
    if (location.hash) document.getElementById(decodeURIComponent(location.hash.slice(1)))?.scrollIntoView();
  }}
</script>
</body>
</html>
"""
out.write_text(page, encoding="utf-8")
print(f"{out}: {len(toc)} topics, {sum(len(t[2]) for t in toc)} subtopics")
